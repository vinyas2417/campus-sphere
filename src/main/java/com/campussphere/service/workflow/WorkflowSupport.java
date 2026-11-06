package com.campussphere.service.workflow;

import com.campussphere.exception.CampusException;
import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository;
import com.campussphere.repository.WorkflowRepository.Transaction;
import com.campussphere.service.Session;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Shared validation, identity lookup and transactional audit/notification behavior. */
abstract class WorkflowSupport {
    protected final WorkflowRepository repository;
    protected final Session session;

    protected WorkflowSupport(WorkflowRepository repository, Session session) {
        this.repository = repository;
        this.session = session;
    }

    protected User user(Role... allowed) {
        User user = session.requireUser();
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(role -> role == user.role()))
            throw new CampusException("Your account does not have permission to perform this action.");
        return user;
    }

    protected Row profile(Transaction tx, String table, User user) {
        return tx.where(table, Map.of("user_id", user.id())).stream().findFirst()
                .orElseThrow(() -> new CampusException("Ask an administrator to link your account to a campus profile."));
    }

    protected Row student(Transaction tx, long id, User user) {
        if (user.role() == Role.STUDENT) {
            Row own = profile(tx, "students", user);
            if (id > 0 && id != own.id()) throw new CampusException("You can only change your own student records.");
            return own;
        }
        if (id <= 0) throw new CampusException("Select a student first.");
        return tx.require("students", id);
    }

    protected void ownerOrAdmin(User user, long ownerId) {
        if (user.role() != Role.ADMIN && user.id() != ownerId)
            throw new CampusException("You can only change your own requests.");
    }

    protected void courseTeacher(Transaction tx, User user, Row course) {
        if (user.role() == Role.ADMIN) return;
        if (user.role() != Role.FACULTY || profile(tx, "faculty", user).id() != course.number("faculty_id"))
            throw new CampusException("Only the assigned faculty member or an administrator can change this course.");
    }

    protected void audit(Transaction tx, User actor, String action, String entity, long id) {
        tx.insert("activity_logs", Map.of("user_id", actor.id(), "action", action, "entity", entity,
                "entity_id", id, "created_at", LocalDateTime.now().toString()));
    }

    protected void notify(Transaction tx, long userId, String title, String body) {
        if (userId > 0) tx.insert("notifications", Map.of("user_id", userId, "title", title, "body", body,
                "created_at", LocalDateTime.now().toString(), "is_read", 0));
    }

    protected String required(String value, String label) {
        if (value == null || value.isBlank()) throw new CampusException("Enter " + label + ".");
        String result = value.trim();
        if (result.length() > 10000) throw new CampusException("The " + label + " is too long.");
        return result;
    }

    protected LocalDate date(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) { throw new CampusException("Enter a valid date in YYYY-MM-DD format."); }
    }

    protected LocalDateTime dateTime(String value) {
        try { return LocalDateTime.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) { throw new CampusException("Enter a valid date and time, for example 2026-10-20T10:00."); }
    }

    protected LocalTime time(String value) {
        try { return LocalTime.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) { throw new CampusException("Enter a valid time in HH:mm format."); }
    }

    protected boolean overlap(LocalDateTime start, LocalDateTime end, String otherStart, String otherEnd) {
        return start.isBefore(dateTime(otherEnd)) && end.isAfter(dateTime(otherStart));
    }

    protected boolean timeOverlap(LocalTime start, LocalTime end, String otherStart, String otherEnd) {
        return start.isBefore(time(otherEnd)) && end.isAfter(time(otherStart));
    }

    protected void pending(Row row) {
        if (!row.text("status").equals("PENDING")) throw new CampusException("Only a pending request can be changed.");
    }
}
