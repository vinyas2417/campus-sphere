package com.campussphere.service;

import com.campussphere.model.*;
import com.campussphere.config.ModuleCatalog;
import com.campussphere.repository.*;
import com.campussphere.validation.RecordValidator;
import com.campussphere.exception.CampusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public final class CampusService {
    private final WorkflowRepository work; private final Session session; private final StudentRepository students;
    public CampusService(JdbcRepository repository,Session session) { work=new WorkflowRepository(repository.database()); this.session=session; students=new StudentRepository(repository); }
    public List<ModuleSpec> modules() { Role role=session.requireUser().role(); return ModuleCatalog.all().stream().filter(m -> m.readers().contains(role) && ModuleCatalog.visibleInPortal(m.key(),role)).toList(); }
    public List<Row> list(String key) {
        User actor=session.requireUser(); ModuleSpec spec=ModuleCatalog.get(key);
        if(!spec.readers().contains(actor.role())) throw new CampusException("Your role cannot access this module.");
        return work.transaction(tx -> { AccessPolicy policy=new AccessPolicy(tx,actor); return tx.all(key).stream().filter(r -> policy.visible(key,r)).map(r -> sanitize(key,r,actor,tx)).toList(); });
    }
    private Row sanitize(String key,Row row,User user,WorkflowRepository.Transaction tx) {
        Map<String,Object> values=new LinkedHashMap<>(row.values()); values.remove("password_hash");
        if(key.equals("faculty") && user.role()==Role.STUDENT) for(String field:List.of("phone","address","joining_date","user_id")) values.remove(field);
        return new Row(row.id(),values);
    }
    public List<Row> options(String key) {
        if(key.equals("users") && session.requireUser().role()!=Role.ADMIN) { User u=session.requireUser(); return List.of(new Row(u.id(),Map.of("name",u.name()))); }
        return list(key);
    }
    public List<Row> search(String key,String query) {
        String term=query.toLowerCase(Locale.ROOT).trim();
        if(key.equals("students") && session.requireUser().role()==Role.ADMIN && !term.matches("\\d+")) return students.searchStudent(term).stream().map(s -> new Row(s.getId(),s.toValues())).toList();
        return list(key).stream().filter(r -> r.values().values().stream().filter(Objects::nonNull).anyMatch(v -> v.toString().toLowerCase(Locale.ROOT).contains(term)) || r.text("id").equals(term)).toList();
    }
    public Map<Long,String> nextBookings() {
        session.requireUser(); return work.transaction(tx -> {
            Map<Long,String> next=new HashMap<>();
            tx.all("resource_bookings").stream().filter(r -> !Set.of("CANCELLED","REJECTED").contains(r.text("status")) && r.text("end_time").compareTo(LocalDateTime.now().toString())>0).sorted(Comparator.comparing(r->r.text("start_time"))).forEach(r -> next.putIfAbsent(r.number("resource_id"),r.text("start_time").replace('T',' ')+" – "+r.text("end_time").replace('T',' ')));
            return next;
        });
    }
    public Row save(String key,Long id,Map<String,Object> input) {
        User actor=session.requireUser(); ModuleSpec spec=ModuleCatalog.get(key); requireWrite(spec,actor);
        Map<String,Object> values=RecordValidator.validate(spec,input);
        return work.transaction(tx -> {
            Row old=id==null?null:tx.require(key,id); AccessPolicy policy=new AccessPolicy(tx,actor);
            policy.writable(key,new Row(id==null?0:id,values),old);
            if(key.equals("announcements")) values.put("created_at",old==null?LocalDateTime.now().toString():old.text("created_at"));
            if(key.equals("helpdesk_tickets")) { values.put("user_id",old==null?actor.id():old.number("user_id")); if(old==null) values.put("status","OPEN"); }
            if(key.equals("doubts") && actor.role()==Role.STUDENT) values.put("status",old==null?"OPEN":old.text("status"));
            if(key.equals("faculty_appointments") && actor.role()==Role.STUDENT) {
                if(old==null) values.put("status","PENDING");
                else if(!Objects.equals(values.get("status"),"CANCELLED") && !Objects.equals(values.get("status"),old.text("status"))) throw new CampusException("Only faculty can confirm or complete appointments.");
            }
            validateRelationships(tx,key,id,values);
            Row saved=id==null?tx.insert(key,values):tx.update(key,id,values);
            if(key.equals("assignments") && (old==null||!old.text("due_date").equals(saved.text("due_date")))) {
                Row course=tx.require("courses",saved.number("course_id"));
                String title="Upcoming assignment: "+saved.text("title");
                String body=course.text("code")+" · "+course.text("name")+" · Due "+saved.text("due_date");
                for(Row enrollment:tx.where("course_enrollments",Map.of("course_id",course.id()))) {
                    Row student=tx.require("students",enrollment.number("student_id"));
                    if(student.number("user_id")>0) tx.insert("notifications",Map.of("user_id",student.number("user_id"),"title",title,"body",body,"created_at",LocalDateTime.now().toString()));
                }
            }
            if(key.equals("resources")) {
                for(String subtype:List.of("classrooms","laboratories","equipment")) for(Row r:tx.where(subtype,Map.of("resource_id",saved.id()))) tx.delete(subtype,r.id());
                String subtype=switch(saved.text("type")) { case "CLASSROOM","SEMINAR_ROOM" -> "classrooms"; case "LABORATORY" -> "laboratories"; default -> "equipment"; };
                tx.insert(subtype,Map.of("resource_id",saved.id()));
            }
            tx.insert("activity_logs",Map.of("user_id",actor.id(),"action",id==null?"CREATE":"UPDATE","entity",key,"entity_id",saved.id(),"created_at",LocalDateTime.now().toString()));
            return saved;
        });
    }
    private void validateRelationships(WorkflowRepository.Transaction tx,String key,Long id,Map<String,Object> values) {
        Row r=new Row(id==null?0:id,values);
        if(key.equals("assignments")) {
            LocalDate due;
            try { due=LocalDate.parse(r.text("due_date")); } catch(Exception error) { throw new CampusException("Enter a valid assignment due date."); }
            boolean changed=id==null;
            if(id!=null) changed=tx.require("assignments",id).text("due_date").equals(r.text("due_date"))==false;
            if(changed&&due.isBefore(LocalDate.now())) throw new CampusException("The assignment due date cannot be in the past.");
        }
        if(Set.of("student_fees","faculty_salaries").contains(key)) {
            for(String field:key.equals("student_fees")?List.of("amount","paid_amount"):List.of("amount")) {
                java.math.BigDecimal amount=new java.math.BigDecimal(values.get(field).toString());
                if(amount.signum()<0 || amount.compareTo(new java.math.BigDecimal("9999999999.99"))>0 || amount.stripTrailingZeros().scale()>2)
                    throw new CampusException("Enter a non-negative amount with at most two decimal places.");
            }
            if(key.equals("student_fees") && new java.math.BigDecimal(values.get("paid_amount").toString()).compareTo(new java.math.BigDecimal(values.get("amount").toString()))>0)
                throw new CampusException("The amount paid cannot exceed the fee amount.");
            if(key.equals("faculty_salaries")) {
                try { YearMonth.parse(r.text("salary_month")); } catch(Exception e) { throw new CampusException("Enter the salary month as YYYY-MM."); }
                if(r.text("status").equals("PAID") && r.text("paid_date").isBlank()) throw new CampusException("Enter the payment date for a paid salary.");
                if(r.text("status").equals("PENDING") && !r.text("paid_date").isBlank()) throw new CampusException("A pending salary cannot have a payment date.");
            }
        }
        if(Set.of("seating_allotments","hall_tickets").contains(key)) {
            Row exam=tx.require("exam_schedule",r.number("exam_id"));
            if(tx.where("course_enrollments",Map.of("course_id",exam.number("course_id"),"student_id",r.number("student_id"))).isEmpty())
                throw new CampusException("Enroll this student in the exam's course first.");
            List<Row> attendance=tx.where("attendance",Map.of("course_id",exam.number("course_id"),"student_id",r.number("student_id")));
            long attended=attendance.stream().filter(mark->Set.of("PRESENT","LATE").contains(mark.text("status"))).count();
            double percentage=attendance.isEmpty()?0.0:100.0*attended/attendance.size();
            if(percentage<=75.0)throw new CampusException("Hall tickets and exam seats require attendance above 75%. Current attendance: "+String.format(Locale.ROOT,"%.1f",percentage)+"%.");
            if(key.equals("seating_allotments")) {
                Row room=tx.require("resources",r.number("resource_id"));
                if(!Set.of("CLASSROOM","SEMINAR_ROOM","LABORATORY").contains(room.text("type")) || room.text("status").equals("MAINTENANCE"))
                    throw new CampusException("Choose an available classroom, seminar room or laboratory.");
                long allocated=tx.all(key).stream().filter(a->(id==null||a.id()!=id) && a.number("exam_id")==exam.id() && a.number("resource_id")==room.id()).count();
                if(allocated>=room.number("capacity")) throw new CampusException("The exam room is at capacity.");
                values.put("seat_number",r.text("seat_number").toUpperCase(Locale.ROOT));
            }
        }
        if(values.containsKey("semester") && (r.number("semester")<1 || r.number("semester")>12)) throw new CampusException("Semester must be between 1 and 12.");
        if(values.containsKey("capacity") && r.number("capacity")<1) throw new CampusException("Capacity must be at least one.");
        if(key.equals("students") && (r.number("admission_year")<1900 || r.number("admission_year")>2200)) throw new CampusException("Enter a valid admission year.");
        if(key.equals("faculty") && values.get("experience")==null) values.put("experience",0);
        if(key.equals("faculty") && r.number("experience")<0) throw new CampusException("Experience cannot be negative.");
        if(key.equals("courses")) {
            if(r.number("credits")<1 || r.number("credits")>30) throw new CampusException("Credits must be between 1 and 30.");
            if(id!=null && tx.where("course_enrollments",Map.of("course_id",id)).size()>r.number("capacity")) throw new CampusException("Capacity cannot be below current enrollment.");
        }
        if(key.equals("events") && id!=null && tx.where("event_registrations",Map.of("event_id",id)).size()>r.number("capacity")) throw new CampusException("Capacity cannot be below current registrations.");
        if(key.equals("library_resources")) {
            long active=id==null?0:tx.where("library_borrowings",Map.of("library_resource_id",id)).stream().filter(b -> b.text("returned_date").isEmpty()).count();
            if(r.number("total_copies")<active || r.number("total_copies")<0) throw new CampusException("Copies cannot be below the number currently borrowed.");
        }
        if(values.containsKey("start_time") && r.text("start_time").compareTo(r.text("end_time"))>=0) throw new CampusException("The end time must be after the start time.");
        if(key.equals("faculty_appointments") && !r.text("status").equals("CANCELLED")) {
            for(Row a:tx.all(key)) if((id==null || a.id()!=id) && !a.text("status").equals("CANCELLED") && (a.number("faculty_id")==r.number("faculty_id") || a.number("student_id")==r.number("student_id")) && a.text("start_time").compareTo(r.text("end_time"))<0 && a.text("end_time").compareTo(r.text("start_time"))>0) throw new CampusException("This appointment overlaps another appointment for the faculty member or student.");
        }
    }
    public void delete(String key,long id) {
        User actor=session.requireUser(); requireWrite(ModuleCatalog.get(key),actor);
        work.transaction(tx -> { Row old=tx.require(key,id); new AccessPolicy(tx,actor).writable(key,old,old); tx.delete(key,id); tx.insert("activity_logs",Map.of("user_id",actor.id(),"action","DELETE","entity",key,"entity_id",id,"created_at",LocalDateTime.now().toString())); return null; });
    }
    private void requireWrite(ModuleSpec spec,User actor) { if(!spec.writers().contains(actor.role())) throw new CampusException("Use the authorized workflow action to change this module."); }
    public Map<String,Long> dashboard() {
        Map<String,Long> counts=new LinkedHashMap<>(); for(ModuleSpec module:modules()) counts.put(module.key(),(long)list(module.key()).size()); return counts;
    }
    public Map<String,Long> groupCounts(String module,String field) { return list(module).stream().collect(Collectors.groupingBy(r -> r.text(field),TreeMap::new,Collectors.counting())); }
}
