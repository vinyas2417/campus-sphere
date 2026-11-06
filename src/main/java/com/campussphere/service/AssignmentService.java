package com.campussphere.service;

import com.campussphere.exception.CampusException;
import com.campussphere.model.*;
import com.campussphere.repository.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Course-scoped assignment documents and private student submissions. */
public final class AssignmentService {
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private final WorkflowRepository work;
    private final Session session;
    public AssignmentService(JdbcRepository repository, Session session) {
        work = new WorkflowRepository(repository.database()); this.session = session;
    }

    public Row attach(Path source, long assignmentId) {
        return attach(source, assignmentId, session.requireUser().role() == Role.FACULTY);
    }

    public Row attach(Path source, long assignmentId, boolean enforceAssignedFaculty) {
        User actor = session.requireUser();
        if (actor.role() == Role.STUDENT) throw new CampusException("Only the assigned faculty or an administrator can attach assignment documents.");
        if (enforceAssignedFaculty && actor.role() != Role.FACULTY) throw new CampusException("Only faculty can use this action.");
        Row assignment = work.transaction(tx -> {
            Row row = tx.require("assignments", assignmentId);
            if (actor.role() == Role.FACULTY && !assigned(tx, actor, row.number("course_id"))) throw new CampusException("You can attach files only to assignments in your assigned courses.");
            return row;
        });
        Path copied = null;
        try {
            checkFile(source, Set.of(".pdf"));
            copied = copy(source, "assignments");
            Map<String,Object> values = new LinkedHashMap<>(assignment.values());
            values.put("attachment_url", copied.toUri().toString());
            return work.transaction(tx -> {
                Row current = tx.require("assignments", assignmentId);
                if (actor.role() == Role.FACULTY && !assigned(tx, actor, current.number("course_id"))) throw new CampusException("The assignment no longer belongs to one of your courses.");
                return tx.update("assignments", assignmentId, values);
            });
        } catch (IOException error) { cleanup(copied); throw new CampusException("The assignment PDF could not be stored.", error); }
        catch (RuntimeException error) { cleanup(copied); throw error; }
    }

    public Row submit(Path source, long assignmentId) {
        User actor = session.requireUser();
        if (actor.role() != Role.STUDENT) throw new CampusException("Only students can upload assignment submissions.");
        long studentId = work.transaction(tx -> {
            Row student = tx.all("students").stream().filter(row -> row.number("user_id") == actor.id()).findFirst().orElseThrow(() -> new CampusException("Your account is not linked to a student profile."));
            Row assignment = tx.require("assignments", assignmentId);
            if (tx.where("course_enrollments", Map.of("course_id", assignment.number("course_id"), "student_id", student.id())).isEmpty()) throw new CampusException("You can submit only assignments for a course you are enrolled in.");
            if (LocalDate.parse(assignment.text("due_date")).isBefore(LocalDate.now())) throw new CampusException("The due date has passed, so this assignment can no longer be submitted.");
            return student.id();
        });
        Path copied = null;
        try {
            checkFile(source, Set.of(".pdf", ".doc", ".docx", ".txt", ".zip"));
            copied = copy(source, "assignment-submissions");
            Path stored = copied;
            String original = source.getFileName().toString();
            return work.transaction(tx -> {
                Row assignment = tx.require("assignments", assignmentId);
                if (tx.where("course_enrollments", Map.of("course_id", assignment.number("course_id"), "student_id", studentId)).isEmpty()) throw new CampusException("Your course enrollment changed. Refresh and try again.");
                if (LocalDate.parse(assignment.text("due_date")).isBefore(LocalDate.now())) throw new CampusException("The due date has passed, so this assignment can no longer be submitted.");
                List<Row> old = tx.where("assignment_submissions", Map.of("assignment_id", assignmentId, "student_id", studentId));
                Map<String,Object> values = new LinkedHashMap<>();
                values.put("assignment_id",assignmentId);values.put("course_id",assignment.number("course_id"));values.put("student_id",studentId);
                values.put("file_url",stored.toUri().toString());values.put("file_name",original);values.put("uploaded_at",LocalDateTime.now().toString());
                values.put("status","SUBMITTED");values.put("grade",null);values.put("feedback",null);values.put("reviewed_by",null);values.put("reviewed_at",null);
                if (old.isEmpty()) return tx.insert("assignment_submissions", values);
                for (int i=1;i<old.size();i++) tx.delete("assignment_submissions",old.get(i).id());
                Row saved = tx.update("assignment_submissions", old.getFirst().id(), values);
                for (Row stale : old) if (stale.id() != saved.id()) deleteStored(stale.text("file_url"));
                return saved;
            });
        } catch (IOException error) { cleanup(copied); throw new CampusException("Your submission could not be stored.", error); }
        catch (RuntimeException error) { cleanup(copied); throw error; }
    }

    public List<Row> submissions(long courseId) {
        User actor = session.requireUser();
        return work.transaction(tx -> {
            if (actor.role() == Role.STUDENT) throw new CampusException("Submission lists are available only to assigned course faculty and administrators.");
            if (actor.role() == Role.FACULTY && !assigned(tx, actor, courseId)) throw new CampusException("You can view submissions only for your assigned courses.");
            return tx.where("assignment_submissions", Map.of("course_id", courseId)).stream()
                    .filter(row -> ModulePolicy.visibleSubmission(tx, actor, row)).toList();
        });
    }

    /** Assignment rows enriched with the current student's status and returned work. */
    public List<Row> studentAssignments(long courseId) {
        User actor=session.requireUser();
        if(actor.role()!=Role.STUDENT) throw new CampusException("This assignment view is for students.");
        return work.transaction(tx->{
            long student=studentId(tx,actor);
            if(tx.where("course_enrollments",Map.of("course_id",courseId,"student_id",student)).isEmpty()) throw new CampusException("You are not enrolled in this course.");
            Row course=tx.require("courses",courseId);
            List<Row> result=new ArrayList<>();
            for(Row assignment:tx.where("assignments",Map.of("course_id",courseId))){
                Map<String,Object> values=new LinkedHashMap<>(assignment.values());
                values.put("course_name",course.text("name"));values.put("course_code",course.text("code"));
                List<Row> own=tx.where("assignment_submissions",Map.of("assignment_id",assignment.id(),"student_id",student));
                if(own.isEmpty()){
                    values.put("submission_id",0L);values.put("submission_status","NOT SUBMITTED");values.put("submitted_at","");values.put("submission_file","" );values.put("grade","");values.put("feedback","");
                }else{
                    Row submission=own.getFirst();values.put("submission_id",submission.id());values.put("submission_status",submission.text("status"));
                    values.put("submitted_at",submission.text("uploaded_at"));values.put("submission_file",submission.text("file_name"));
                    values.put("grade",submission.text("grade"));values.put("feedback",submission.text("feedback"));
                }
                result.add(new Row(assignment.id(),values));
            }
            return result;
        });
    }

    /** Grade or return a submission and notify its student in the same transaction. */
    public Row review(long submissionId,String gradeValue,String feedbackValue,String statusValue) {
        User actor=session.requireUser();
        if(actor.role()==Role.STUDENT) throw new CampusException("Only assigned faculty can review submissions.");
        String status=Objects.toString(statusValue,"").trim().toUpperCase(Locale.ROOT);
        if(!Set.of("GRADED","RETURNED").contains(status)) throw new CampusException("Choose Graded or Returned.");
        String grade=Objects.toString(gradeValue,"").trim();
        if(!grade.isEmpty()){
            try{BigDecimal score=new BigDecimal(grade);if(score.signum()<0||score.compareTo(new BigDecimal("100"))>0||score.stripTrailingZeros().scale()>2)throw new Exception();grade=score.stripTrailingZeros().toPlainString();}
            catch(Exception error){throw new CampusException("Enter a grade from 0 to 100 with at most two decimal places.");}
        }
        if(status.equals("GRADED")&&grade.isEmpty())throw new CampusException("Enter a grade before marking this submission as graded.");
        String feedback=Objects.toString(feedbackValue,"").trim();
        if(feedback.length()>10000)throw new CampusException("Feedback is too long (maximum 10,000 characters).");
        String finalGrade=grade;
        return work.transaction(tx->{
            Row submission=tx.require("assignment_submissions",submissionId);
            if(actor.role()==Role.FACULTY&&!assigned(tx,actor,submission.number("course_id")))throw new CampusException("You can review only submissions for your assigned courses.");
            Row student=tx.require("students",submission.number("student_id"));
            Row assignment=tx.require("assignments",submission.number("assignment_id"));
            Map<String,Object> values=new LinkedHashMap<>();values.put("status",status);values.put("grade",finalGrade.isEmpty()?null:finalGrade);
            values.put("feedback",feedback.isEmpty()?null:feedback);values.put("reviewed_by",actor.id());values.put("reviewed_at",LocalDateTime.now().toString());
            Row saved=tx.update("assignment_submissions",submissionId,values);
            if(student.number("user_id")>0){
                String title=status.equals("RETURNED")?"Assignment returned":"Assignment graded";
                String body=assignment.text("title")+" has been "+status.toLowerCase(Locale.ROOT)+"."+(finalGrade.isEmpty()?"":" Grade: "+finalGrade+"/100.")+(feedback.isEmpty()?"":" Feedback: "+feedback);
                tx.insert("notifications",Map.of("user_id",student.number("user_id"),"title",title,"body",body,"created_at",LocalDateTime.now().toString()));
            }
            return saved;
        });
    }

    public Path openFile(String fileUrl, long courseId) {
        User actor = session.requireUser();
        return work.transaction(tx -> {
            if (actor.role() == Role.STUDENT) {
                boolean ownSubmission = tx.all("assignment_submissions").stream().anyMatch(row -> row.text("file_url").equals(fileUrl) && row.number("course_id") == courseId && ownStudent(tx, actor, row.number("student_id")));
                boolean enrolledCourse = tx.where("course_enrollments", Map.of("course_id", courseId, "student_id", studentId(tx, actor))).size() > 0;
                boolean courseDocument = tx.where("assignments", Map.of("course_id", courseId)).stream().anyMatch(row -> row.text("attachment_url").equals(fileUrl));
                if (!enrolledCourse || (!ownSubmission && !courseDocument)) throw new CampusException("This file is not available to your account.");
            } else if (actor.role() == Role.FACULTY) {
                if (!assigned(tx, actor, courseId) || tx.all("assignment_submissions").stream().noneMatch(row -> row.text("file_url").equals(fileUrl) && row.number("course_id") == courseId)
                        && tx.where("assignments", Map.of("course_id", courseId)).stream().noneMatch(row -> row.text("attachment_url").equals(fileUrl))) throw new CampusException("This file is not available to your assigned courses.");
            } else if (!tx.all("assignment_submissions").stream().anyMatch(row -> row.text("file_url").equals(fileUrl) && row.number("course_id") == courseId)
                    && tx.where("assignments", Map.of("course_id", courseId)).stream().noneMatch(row -> row.text("attachment_url").equals(fileUrl))) throw new CampusException("This file is not available.");
            return managedPath(fileUrl);
        });
    }

    private static boolean assigned(WorkflowRepository.Transaction tx, User actor, long courseId) {
        long faculty = tx.all("faculty").stream().filter(row -> row.number("user_id") == actor.id()).mapToLong(Row::id).findFirst().orElse(-1);
        return tx.find("courses", courseId).map(row -> row.number("faculty_id") == faculty).orElse(false);
    }
    private static long studentId(WorkflowRepository.Transaction tx, User actor) {
        return tx.all("students").stream().filter(row -> row.number("user_id") == actor.id()).mapToLong(Row::id).findFirst().orElseThrow(() -> new CampusException("Your account is not linked to a student profile."));
    }
    private static boolean ownStudent(WorkflowRepository.Transaction tx, User actor, long id) {
        return tx.find("students", id).map(row -> row.number("user_id") == actor.id()).orElse(false);
    }
    private static void checkFile(Path source, Set<String> allowed) throws IOException {
        if (source == null || !Files.isRegularFile(source) || Files.size(source) == 0 || Files.size(source) > MAX_BYTES) throw new CampusException("Choose a non-empty file no larger than 25 MB.");
        String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (allowed.stream().noneMatch(lower::endsWith)) throw new CampusException("Choose a supported file type: " + String.join(", ", allowed) + ".");
        if (lower.endsWith(".pdf")) {
            byte[] signature = new byte[5];
            try (var input = Files.newInputStream(source)) { if (input.read(signature) != signature.length || !Arrays.equals(signature, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new CampusException("The selected file is not a valid PDF document."); }
        }
    }
    private static Path copy(Path source, String folder) throws IOException {
        Path directory = Path.of("data", folder).toAbsolutePath().normalize(); Files.createDirectories(directory);
        String name = source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        return Files.copy(source, directory.resolve(UUID.randomUUID() + "-" + name));
    }
    private static Path managedPath(String url) {
        try {
            Path path = Path.of(java.net.URI.create(url)).toAbsolutePath().normalize();
            Path materials = Path.of("data", "materials").toAbsolutePath().normalize();
            Path assignments = Path.of("data", "assignments").toAbsolutePath().normalize();
            Path submissions = Path.of("data", "assignment-submissions").toAbsolutePath().normalize();
            if (!path.startsWith(materials) && !path.startsWith(assignments) && !path.startsWith(submissions)) throw new CampusException("The file address is outside managed course storage.");
            if (!Files.isRegularFile(path)) throw new CampusException("This file is no longer present in course storage.");
            return path;
        } catch (IllegalArgumentException error) { throw new CampusException("The course file address is invalid.", error); }
    }
    private static void deleteStored(String url) { try { Files.deleteIfExists(managedPath(url)); } catch (Exception ignored) { } }
    private static void cleanup(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private static final class ModulePolicy {
        static boolean visibleSubmission(WorkflowRepository.Transaction tx, User actor, Row row) {
            return actor.role() == Role.ADMIN || assigned(tx, actor, row.number("course_id"));
        }
    }
}
