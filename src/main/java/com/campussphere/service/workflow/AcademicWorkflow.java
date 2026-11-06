package com.campussphere.service.workflow;

import com.campussphere.exception.*;
import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository;
import com.campussphere.repository.WorkflowRepository.Transaction;
import com.campussphere.service.Session;

import java.time.*;
import java.util.*;

public final class AcademicWorkflow extends WorkflowSupport {
    public AcademicWorkflow(WorkflowRepository repository, Session session) { super(repository, session); }

    public void removeTimetable(long id) {
        User actor=user(Role.ADMIN);
        repository.transaction(tx->{tx.lockFacultySchedule();tx.require("timetable",id);for(Row row:tx.where("faculty_substitutions",Map.of("timetable_id",id)))tx.delete("faculty_substitutions",row.id());for(Row row:tx.where("timetable_overrides",Map.of("timetable_id",id)))tx.delete("timetable_overrides",row.id());tx.delete("timetable",id);audit(tx,actor,"DELETE_SCHEDULE","timetable",id);return null;});
    }

    public Row enroll(long courseId, long studentId) {
        User actor = user();
        return repository.transaction(tx -> {
            Row course = tx.lock("courses", courseId);
            if (actor.role() == Role.FACULTY) courseTeacher(tx, actor, course);
            Row student = student(tx, studentId, actor);
            if (!student.text("status").equals("ACTIVE")) throw new CampusException("Only active students can enroll in a course.");
            List<Row> enrolled = tx.where("course_enrollments", Map.of("course_id", courseId));
            if (enrolled.stream().anyMatch(row -> row.number("student_id") == student.id()))
                throw new CampusException("This student is already enrolled in the course.");
            if (enrolled.size() >= course.number("capacity")) throw new CourseCapacityExceededException("This course has reached its maximum capacity.");
            Row enrollment = tx.insert("course_enrollments", Map.of("course_id", courseId, "student_id", student.id()));
            audit(tx, actor, "ENROLL", "course_enrollments", enrollment.id());
            notify(tx, student.number("user_id"), "Course enrollment", "You are enrolled in " + course.text("name") + ".");
            return enrollment;
        });
    }

    public void removeEnrollment(long id) {
        User actor = user();
        repository.transaction(tx -> {
            Row enrollment = tx.require("course_enrollments", id);
            Row course = tx.lock("courses", enrollment.number("course_id"));
            if (actor.role() == Role.STUDENT) student(tx, enrollment.number("student_id"), actor);
            else courseTeacher(tx, actor, course);
            tx.delete("course_enrollments", id);
            audit(tx, actor, "REMOVE_ENROLLMENT", "course_enrollments", id);
            return null;
        });
    }

    public Row registerEvent(long eventId, long studentId) {
        User actor = user(Role.ADMIN, Role.STUDENT);
        return repository.transaction(tx -> {
            Row event = tx.lock("events", eventId);
            Row student = student(tx, studentId, actor);
            if (dateTime(event.text("end_time")).isBefore(LocalDateTime.now()))
                throw new CampusException("Registration is closed because this event has ended.");
            List<Row> registrations = tx.where("event_registrations", Map.of("event_id", eventId));
            if (registrations.stream().anyMatch(row -> row.number("student_id") == student.id()))
                throw new CampusException("This student is already registered for the event.");
            if (registrations.size() >= event.number("capacity")) throw new CampusException("This event is full.");
            Row registration = tx.insert("event_registrations", Map.of("event_id", eventId, "student_id", student.id()));
            audit(tx, actor, "REGISTER", "event_registrations", registration.id());
            notify(tx, student.number("user_id"), "Event registration", "You are registered for " + event.text("title") + ".");
            return registration;
        });
    }

    public void cancelEventRegistration(long id) {
        User actor = user(Role.ADMIN, Role.STUDENT);
        repository.transaction(tx -> {
            Row registration = tx.require("event_registrations", id);
            tx.lock("events", registration.number("event_id"));
            student(tx, registration.number("student_id"), actor);
            tx.delete("event_registrations", id);
            audit(tx, actor, "CANCEL_REGISTRATION", "event_registrations", id);
            return null;
        });
    }

    public Row attendance(long courseId, long studentId, String dateValue, String statusValue) {
        User actor = user(Role.ADMIN, Role.FACULTY);
        String attendanceDate = date(dateValue).toString();
        if (date(dateValue).isAfter(LocalDate.now())) throw new InvalidAttendanceException("Attendance cannot be recorded for a future date.");
        String status = required(statusValue, "an attendance status").toUpperCase(Locale.ROOT);
        if (!Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED").contains(status)) throw new InvalidAttendanceException("Select PRESENT, ABSENT, LATE or EXCUSED.");
        return repository.transaction(tx -> {
            Row course = tx.lock("courses", courseId);
            courseTeacher(tx, actor, course);
            tx.require("students", studentId);
            if (tx.where("course_enrollments", Map.of("course_id", courseId, "student_id", studentId)).isEmpty())
                throw new InvalidAttendanceException("The student must be enrolled in this course before attendance can be recorded.");
            Map<String, Object> values = Map.of("course_id", courseId, "student_id", studentId, "date", attendanceDate, "status", status);
            Optional<Row> existing = tx.where("attendance", Map.of("course_id", courseId, "student_id", studentId, "date", attendanceDate)).stream().findFirst();
            Row attendance = existing.isPresent() ? tx.update("attendance", existing.get().id(), values) : tx.insert("attendance", values);
            audit(tx, actor, "MARK_ATTENDANCE", "attendance", attendance.id());
            return attendance;
        });
    }

    public List<Row> attendanceRoster(long courseId,String dateValue) {
        User actor=user(Role.ADMIN,Role.FACULTY);
        String day=date(dateValue).toString();
        return repository.transaction(tx->{
            courseTeacher(tx,actor,tx.require("courses",courseId));
            Map<Long,String> statuses=new HashMap<>();
            tx.where("attendance",Map.of("course_id",courseId,"date",day)).forEach(r->statuses.put(r.number("student_id"),r.text("status")));
            List<Row> result=new ArrayList<>();
            for(Row enrollment:tx.where("course_enrollments",Map.of("course_id",courseId))) {
                Row student=tx.require("students",enrollment.number("student_id"));
                result.add(new Row(student.id(),Map.of("name",student.text("name"),"usn",student.text("usn"),"attendance_status",statuses.getOrDefault(student.id(),""))));
            }
            result.sort(Comparator.comparing(r->r.text("name"),String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(result);
        });
    }

    public void attendanceBatch(long courseId,String dateValue,Map<Long,String> marks) {
        User actor=user(Role.ADMIN,Role.FACULTY);
        LocalDate day=date(dateValue);
        if(day.isAfter(LocalDate.now())) throw new InvalidAttendanceException("Attendance cannot be recorded for a future date.");
        if(marks==null || marks.isEmpty()) throw new InvalidAttendanceException("Load the enrolled students before saving attendance.");
        Map<Long,String> checked=new LinkedHashMap<>();
        marks.forEach((id,status)->{
            if(id==null || status==null || !Set.of("PRESENT","ABSENT","LATE","EXCUSED").contains(status)) throw new InvalidAttendanceException("Select a valid attendance mark for every student.");
            checked.put(id,status);
        });
        repository.transaction(tx->{
            Row course=tx.lock("courses",courseId); courseTeacher(tx,actor,course);
            Set<Long> enrolled=new HashSet<>(); tx.where("course_enrollments",Map.of("course_id",courseId)).forEach(r->enrolled.add(r.number("student_id")));
            if(!enrolled.equals(checked.keySet())) throw new InvalidAttendanceException("The class roster has changed. Reload the students before saving attendance.");
            for(var entry:checked.entrySet()) {
                Map<String,Object> values=Map.of("course_id",courseId,"student_id",entry.getKey(),"date",day.toString(),"status",entry.getValue());
                Optional<Row> old=tx.where("attendance",Map.of("course_id",courseId,"student_id",entry.getKey(),"date",day.toString())).stream().findFirst();
                Row saved=old.isPresent()?tx.update("attendance",old.get().id(),values):tx.insert("attendance",values);
                audit(tx,actor,"MARK_ATTENDANCE","attendance",saved.id());
            }
            return null;
        });
    }

    public Row saveTimetable(Long id, long courseId, long facultyId, long resourceId, int day, String startValue, String endValue) {
        User actor = user(Role.ADMIN);
        LocalTime start = time(startValue), end = time(endValue);
        if (day < 1 || day > 7) throw new CampusException("Select a weekday from 1 (Monday) to 7 (Sunday).");
        if (!end.isAfter(start)) throw new CampusException("The class end must be after its start on the same day.");
        return repository.transaction(tx -> {
            tx.lockFacultySchedule();
            Row faculty = tx.require("faculty", facultyId);
            Row resource = tx.lock("resources", resourceId);
            Resource.fromRow(resource).ensureBookable();
            Row course = tx.lock("courses", courseId);
            if (course.number("faculty_id") != faculty.id()) throw new CampusException("Select the faculty member assigned to this course.");
            if (id != null) {
                tx.lock("timetable", id);
                if (!tx.where("faculty_substitutions", Map.of("timetable_id", id)).isEmpty()
                        || !tx.where("timetable_overrides", Map.of("timetable_id", id)).isEmpty())
                    throw new CampusException("Remove this class's dated substitutions and overrides before changing its weekly schedule.");
            }
            for (Row slot : tx.where("timetable", Map.of("day_of_week", day))) {
                if (id != null && slot.id() == id) continue;
                if (!timeOverlap(start, end, slot.text("start_time"), slot.text("end_time"))) continue;
                if (slot.number("faculty_id") == facultyId) throw new FacultyConflictException("This faculty member already teaches another class during that time.");
                if (slot.number("resource_id") == resourceId) throw new BookingConflictException("This room already has a class during that time.");
                Row otherCourse = tx.require("courses", slot.number("course_id"));
                if (sameCohort(course, otherCourse)) throw new CampusException("Another class for this department and semester overlaps this time.");
            }
            for (Row booking : tx.where("resource_bookings", Map.of("resource_id", resourceId))) {
                if (Set.of("CANCELLED", "REJECTED").contains(booking.text("status"))) continue;
                LocalDateTime bookedStart = dateTime(booking.text("start_time")), bookedEnd = dateTime(booking.text("end_time"));
                if (bookedEnd.isBefore(LocalDateTime.now())) continue;
                for (LocalDate date = bookedStart.toLocalDate(); !date.isAfter(bookedEnd.toLocalDate()); date = date.plusDays(1))
                    if (date.getDayOfWeek().getValue() == day && overlap(date.atTime(start), date.atTime(end), booking.text("start_time"), booking.text("end_time")))
                        throw new BookingConflictException("This room has a resource booking that conflicts with the class on " + date + ".");
            }
            for (Row substitution : tx.where("faculty_substitutions", Map.of("faculty_id", facultyId))) {
                LocalDate substituteDate = date(substitution.text("date"));
                if (substituteDate.getDayOfWeek().getValue() == day && !substituteDate.isBefore(LocalDate.now())
                        && timeOverlap(start, end, substitution.text("start_time"), substitution.text("end_time")))
                    throw new FacultyConflictException("This faculty member is covering another class on " + substituteDate + ".");
            }
            Map<String, Object> values = Map.of("course_id", courseId, "faculty_id", facultyId, "resource_id", resourceId,
                    "day_of_week", day, "start_time", start.toString(), "end_time", end.toString());
            Row saved = id == null ? tx.insert("timetable", values) : tx.update("timetable", id, values);
            audit(tx, actor, "SCHEDULE", "timetable", saved.id());
            return saved;
        });
    }

    public List<Row> availableFaculty(long timetableId, String dateValue) {
        user(Role.ADMIN, Role.FACULTY);
        LocalDate on = date(dateValue);
        return repository.transaction(tx -> {
            Row slot = tx.require("timetable", timetableId);
            requireDay(slot, on);
            Row course = tx.require("courses", slot.number("course_id"));
            Row effective = effectiveSlot(tx, slot, on);
            return tx.all("faculty").stream()
                    .filter(faculty -> faculty.id() != slot.number("faculty_id"))
                    .filter(faculty -> faculty.number("department_id") == course.number("department_id"))
                    .filter(faculty -> !facultyBusy(tx, faculty, on, time(effective.text("start_time")), time(effective.text("end_time")), timetableId))
                    .sorted(Comparator.comparing(row -> row.text("name"))).toList();
        });
    }

    public Row substitute(long timetableId, long facultyId, String dateValue) {
        User actor = user(Role.ADMIN);
        LocalDate on = date(dateValue);
        return repository.transaction(tx -> {
            tx.lockFacultySchedule();
            Row slot = tx.lock("timetable", timetableId);
            requireDay(slot, on);
            Row faculty = tx.require("faculty", facultyId);
            Row course = tx.require("courses", slot.number("course_id"));
            if (facultyId == slot.number("faculty_id")) throw new CampusException("Choose a replacement other than the regular faculty member.");
            if (faculty.number("department_id") != course.number("department_id")) throw new CampusException("Select a replacement from the course's department.");
            Row effective = effectiveSlot(tx, slot, on);
            if (facultyBusy(tx, faculty, on, time(effective.text("start_time")), time(effective.text("end_time")), timetableId))
                throw new FacultyConflictException("This faculty member is unavailable during the selected class.");
            Map<String, Object> values = Map.of("timetable_id", timetableId, "faculty_id", facultyId, "date", on.toString(),
                    "start_time", effective.text("start_time"), "end_time", effective.text("end_time"));
            Optional<Row> existing = tx.where("faculty_substitutions", Map.of("timetable_id", timetableId, "date", on.toString())).stream().findFirst();
            Row saved = existing.isPresent() ? tx.update("faculty_substitutions", existing.get().id(), values) : tx.insert("faculty_substitutions", values);
            audit(tx, actor, "REALLOCATE", "faculty_substitutions", saved.id());
            notify(tx, faculty.number("user_id"), "Class reallocation", "You will cover " + course.text("name") + " on " + on + " at " + effective.text("start_time") + ".");
            return saved;
        });
    }

    public Row override(long timetableId, String dateValue, long resourceId, String startValue, String endValue, String reasonValue) {
        User actor = user(Role.ADMIN);
        LocalDate on = date(dateValue);
        LocalTime start = time(startValue), end = time(endValue);
        if (!end.isAfter(start)) throw new CampusException("The class end must be after its start.");
        String reason = required(reasonValue, "a reason for the schedule change");
        return repository.transaction(tx -> {
            tx.lockFacultySchedule();
            Row resource = tx.lock("resources", resourceId);
            Resource.fromRow(resource).ensureBookable();
            Row slot = tx.lock("timetable", timetableId);
            requireDay(slot, on);
            Row course = tx.require("courses", slot.number("course_id"));
            Optional<Row> substitution = tx.where("faculty_substitutions", Map.of("timetable_id", timetableId, "date", on.toString())).stream().findFirst();
            long facultyId = substitution.map(row -> row.number("faculty_id")).orElse(slot.number("faculty_id"));
            if (facultyBusy(tx, tx.require("faculty", facultyId), on, start, end, timetableId))
                throw new FacultyConflictException("The assigned faculty member is unavailable at the new time.");
            for (Row other : tx.where("timetable", Map.of("day_of_week", on.getDayOfWeek().getValue()))) {
                if (other.id() == timetableId) continue;
                Row effective = effectiveSlot(tx, other, on);
                if (timeOverlap(start, end, effective.text("start_time"), effective.text("end_time"))
                        && (effective.number("resource_id") == resourceId || sameCohort(course, tx.require("courses", other.number("course_id")))))
                    throw new BookingConflictException("The room or student cohort already has another class at the new time.");
            }
            if (tx.where("resource_bookings", Map.of("resource_id", resourceId)).stream()
                    .filter(row -> !Set.of("CANCELLED", "REJECTED").contains(row.text("status")))
                    .anyMatch(row -> overlap(on.atTime(start), on.atTime(end), row.text("start_time"), row.text("end_time"))))
                throw new BookingConflictException("The room is booked at the new time.");
            Map<String, Object> values = Map.of("timetable_id", timetableId, "date", on.toString(), "resource_id", resourceId,
                    "start_time", start.toString(), "end_time", end.toString(), "reason", reason);
            Optional<Row> existing = tx.where("timetable_overrides", Map.of("timetable_id", timetableId, "date", on.toString())).stream().findFirst();
            Row saved = existing.isPresent() ? tx.update("timetable_overrides", existing.get().id(), values) : tx.insert("timetable_overrides", values);
            substitution.ifPresent(row -> tx.update("faculty_substitutions", row.id(), Map.of("start_time", start.toString(), "end_time", end.toString())));
            audit(tx, actor, "OVERRIDE_SCHEDULE", "timetable_overrides", saved.id());
            return saved;
        });
    }

    private boolean sameCohort(Row first, Row second) {
        return first.number("department_id") == second.number("department_id") && first.number("semester") == second.number("semester");
    }

    private void requireDay(Row slot, LocalDate on) {
        if (slot.number("day_of_week") != on.getDayOfWeek().getValue()) throw new CampusException("Select a date on this class's scheduled weekday.");
    }

    private Row effectiveSlot(Transaction tx, Row slot, LocalDate on) {
        return tx.where("timetable_overrides", Map.of("timetable_id", slot.id(), "date", on.toString())).stream().findFirst().orElse(slot);
    }

    private boolean facultyBusy(Transaction tx, Row faculty, LocalDate on, LocalTime start, LocalTime end, long ignoredTimetable) {
        if (faculty.number("user_id") > 0 && tx.where("leave_requests", Map.of("requester_id", faculty.number("user_id"), "status", "APPROVED")).stream()
                .anyMatch(leave -> !on.isBefore(date(leave.text("from_date"))) && !on.isAfter(date(leave.text("to_date"))))) return true;
        List<Row> substitutions = tx.where("faculty_substitutions", Map.of("date", on.toString()));
        if (substitutions.stream().filter(row -> row.number("faculty_id") == faculty.id() && row.number("timetable_id") != ignoredTimetable)
                .anyMatch(row -> timeOverlap(start, end, row.text("start_time"), row.text("end_time")))) return true;
        for (Row slot : tx.where("timetable", Map.of("faculty_id", faculty.id(), "day_of_week", on.getDayOfWeek().getValue()))) {
            if (slot.id() == ignoredTimetable || substitutions.stream().anyMatch(row -> row.number("timetable_id") == slot.id())) continue;
            Row effective = effectiveSlot(tx, slot, on);
            if (timeOverlap(start, end, effective.text("start_time"), effective.text("end_time"))) return true;
        }
        return false;
    }
}
