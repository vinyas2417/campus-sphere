package com.campussphere.service;

import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository.Transaction;
import com.campussphere.exception.CampusException;
import java.util.*;

/** Request-scoped permission graph. Never relies on menu visibility. */
final class AccessPolicy {
    private final Transaction tx; private final User actor;
    private final Map<String,List<Row>> cache=new HashMap<>();
    AccessPolicy(Transaction tx,User actor) { this.tx=tx; this.actor=actor; }
    List<Row> rows(String table) { return cache.computeIfAbsent(table,tx::all); }
    long profile(String table) { return rows(table).stream().filter(r -> r.number("user_id")==actor.id()).mapToLong(Row::id).findFirst().orElse(-1); }
    boolean teacher(long course) { return rows("courses").stream().anyMatch(r -> r.id()==course && r.number("faculty_id")==profile("faculty")); }
    boolean enrolled(long course) { return rows("course_enrollments").stream().anyMatch(r -> r.number("course_id")==course && r.number("student_id")==profile("students")); }
    boolean member(long project) { return rows("project_members").stream().anyMatch(r -> r.number("project_id")==project && r.number("student_id")==profile("students")); }
    boolean mentor(long project) { return rows("projects").stream().anyMatch(r -> r.id()==project && r.number("mentor_id")==profile("faculty")); }
    boolean eventCoordinator(long event) { return rows("events").stream().anyMatch(r -> r.id()==event && r.number("coordinator_id")==profile("faculty")); }
    boolean visible(String table,Row row) {
        if(table.equals("study_materials") && row.text("type").equals("QUESTION_PAPER")) return false;
        if(table.equals("notifications")) return row.number("user_id")==actor.id();
        if(actor.role()==Role.ADMIN) return true;
        boolean student=actor.role()==Role.STUDENT;
        return switch(table) {
            case "users","administrators","activity_logs" -> false;
            case "students" -> student ? row.id()==profile("students") : rows("course_enrollments").stream().anyMatch(e -> e.number("student_id")==row.id() && teacher(e.number("course_id"))) ||
                    actor.role()==Role.FACULTY && rows("event_registrations").stream().anyMatch(reg -> reg.number("student_id")==row.id() && eventCoordinator(reg.number("event_id")));
            case "faculty","departments","resources","events" -> true;
            case "courses" -> student ? enrolled(row.id()) : row.number("faculty_id")==profile("faculty");
            case "library_resources" -> student ? row.number("course_id")==0 || enrolled(row.number("course_id")) : actor.role()==Role.FACULTY ? row.number("course_id")==0 || teacher(row.number("course_id")) : true;
            case "announcements" -> Set.of("ALL",actor.role().name()).contains(row.text("audience"));
            case "resource_bookings","helpdesk_tickets" -> row.number("user_id")==actor.id();
            case "leave_requests","permission_requests" -> row.number("requester_id")==actor.id();
            case "library_borrowings","library_reservations" -> student && row.number("student_id")==profile("students");
            case "event_registrations" -> student ? row.number("student_id")==profile("students") : actor.role()==Role.FACULTY && eventCoordinator(row.number("event_id"));
            case "student_fees","seating_allotments","hall_tickets" -> student && row.number("student_id")==profile("students");
            case "faculty_salaries" -> !student && row.number("faculty_id")==profile("faculty");
            case "course_enrollments","attendance","doubts" -> student ? row.number("student_id")==profile("students") : teacher(row.number("course_id"));
            case "timetable","assignments","study_materials","exam_schedule" -> student ? enrolled(row.number("course_id")) : teacher(row.number("course_id"));
            case "assignment_submissions" -> student ? row.number("student_id")==profile("students") && enrolled(row.number("course_id")) : teacher(row.number("course_id"));
            case "timetable_overrides" -> rows("timetable").stream().anyMatch(t -> t.id()==row.number("timetable_id") && visible("timetable",t));
            case "faculty_substitutions" -> !student && row.number("faculty_id")==profile("faculty");
            case "faculty_appointments" -> student ? row.number("student_id")==profile("students") : row.number("faculty_id")==profile("faculty");
            case "doubt_replies" -> rows("doubts").stream().anyMatch(d -> d.id()==row.number("doubt_id") && visible("doubts",d));
            case "projects" -> student ? member(row.id()) : row.number("mentor_id")==profile("faculty");
            case "project_members","project_milestones" -> student ? member(row.number("project_id")) : mentor(row.number("project_id"));
            case "class_teacher_assignments" -> !student && row.number("faculty_id")==profile("faculty");
            default -> false;
        };
    }
    void writable(String table,Row candidate,Row old) {
        if(actor.role()==Role.ADMIN) return;
        if(old!=null && !visible(table,old)) throw new CampusException("You do not have permission to change this record.");
        boolean allowed=switch(table) {
            case "assignments","study_materials","exam_schedule" -> actor.role()==Role.FACULTY && teacher(candidate.number("course_id"));
            case "library_resources" -> actor.role()==Role.FACULTY && candidate.number("course_id")>0 && teacher(candidate.number("course_id"));
            case "assignment_submissions" -> actor.role()==Role.STUDENT && candidate.number("student_id")==profile("students") && enrolled(candidate.number("course_id"));
            case "projects" -> actor.role()==Role.FACULTY && candidate.number("mentor_id")==profile("faculty");
            case "project_members","project_milestones" -> actor.role()==Role.FACULTY && mentor(candidate.number("project_id"));
            case "announcements" -> actor.role()==Role.FACULTY;
            case "doubts" -> actor.role()==Role.STUDENT && candidate.number("student_id")==profile("students") && enrolled(candidate.number("course_id"));
            case "faculty_appointments" -> actor.role()==Role.STUDENT ? candidate.number("student_id")==profile("students") : old!=null && candidate.number("faculty_id")==profile("faculty");
            case "helpdesk_tickets" -> old==null || old.number("user_id")==actor.id();
            default -> false;
        };
        if(!allowed) throw new CampusException("Your role cannot change this record or its selected relationships.");
    }
}
