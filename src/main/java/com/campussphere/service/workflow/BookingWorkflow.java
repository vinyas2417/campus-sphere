package com.campussphere.service.workflow;

import com.campussphere.exception.*;
import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository;
import com.campussphere.service.Session;
import java.time.LocalDateTime;
import java.util.Map;

public final class BookingWorkflow extends WorkflowSupport {
    public BookingWorkflow(WorkflowRepository repository, Session session) { super(repository, session); }

    public Row book(long resourceId, String startValue, String endValue, String purposeValue) {
        User actor = user();
        LocalDateTime start = dateTime(startValue), end = dateTime(endValue);
        if (!end.isAfter(start)) throw new CampusException("The booking end must be after its start.");
        String purpose = required(purposeValue, "a booking purpose");
        return repository.transaction(tx -> {
            Row resource = tx.lock("resources", resourceId);
            Resource.fromRow(resource).ensureBookable();
            boolean conflict = tx.where("resource_bookings", Map.of("resource_id", resourceId)).stream()
                    .filter(row -> !row.text("status").equals("CANCELLED") && !row.text("status").equals("REJECTED"))
                    .anyMatch(row -> overlap(start, end, row.text("start_time"), row.text("end_time")));
            if (conflict) throw new BookingConflictException(resource.text("name") + " is already reserved during the selected time.");
            // A dated booking also respects weekly teaching slots and dated room overrides.
            for (var day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
                for (Row slot : tx.where("timetable", Map.of("day_of_week", day.getDayOfWeek().getValue()))) {
                    final String selectedDate = day.toString();
                    Row override = tx.where("timetable_overrides", Map.of("timetable_id", slot.id(), "date", selectedDate))
                            .stream().findFirst().orElse(slot);
                    if (override.number("resource_id") == resourceId && overlap(start, end,
                            selectedDate + "T" + override.text("start_time"), selectedDate + "T" + override.text("end_time")))
                        throw new BookingConflictException("This resource is assigned to a class during the selected time.");
                }
            }
            Row booking = tx.insert("resource_bookings", Map.of("resource_id", resourceId, "user_id", actor.id(),
                    "start_time", start.toString(), "end_time", end.toString(), "purpose", purpose, "status", "APPROVED"));
            audit(tx, actor, "BOOK", "resource_bookings", booking.id());
            notify(tx, actor.id(), "Resource booked", resource.text("name") + " is reserved from " + start + " to " + end + ".");
            return booking;
        });
    }

    public void cancel(long id) {
        User actor = user();
        repository.transaction(tx -> {
            Row booking = tx.lock("resource_bookings", id);
            ownerOrAdmin(actor, booking.number("user_id"));
            if (booking.text("status").equals("CANCELLED")) throw new CampusException("This booking is already cancelled.");
            tx.update("resource_bookings", id, Map.of("status", "CANCELLED"));
            audit(tx, actor, "CANCEL", "resource_bookings", id);
            notify(tx, booking.number("user_id"), "Booking cancelled", "Resource booking #" + id + " has been cancelled.");
            return null;
        });
    }
}
