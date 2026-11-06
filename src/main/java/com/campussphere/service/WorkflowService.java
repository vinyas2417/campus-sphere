package com.campussphere.service;

import com.campussphere.model.Row;
import com.campussphere.repository.*;
import com.campussphere.service.workflow.*;
import java.util.List;

/** Small application facade; each workflow owns its domain rules. */
public final class WorkflowService {
    private final BookingWorkflow bookings; private final AcademicWorkflow academic;
    private final RequestWorkflow requests; private final LibraryWorkflow library;
    public WorkflowService(JdbcRepository jdbc,Session session) { WorkflowRepository repo=new WorkflowRepository(jdbc.database()); bookings=new BookingWorkflow(repo,session); academic=new AcademicWorkflow(repo,session); requests=new RequestWorkflow(repo,session); library=new LibraryWorkflow(repo,session); }
    public Row bookResource(long id,String start,String end,String purpose) { return bookings.book(id,start,end,purpose); }
    public void cancelBooking(long id) { bookings.cancel(id); }
    public Row enroll(long course,long student) { return academic.enroll(course,student); }
    public void removeEnrollment(long id) { academic.removeEnrollment(id); }
    public Row registerEvent(long event,long student) { return academic.registerEvent(event,student); }
    public void cancelEventRegistration(long id) { academic.cancelEventRegistration(id); }
    public List<Row> attendanceRoster(long course,String date) { return academic.attendanceRoster(course,date); }
    public void markAttendanceBatch(long course,String date,java.util.Map<Long,String> marks) { academic.attendanceBatch(course,date,marks); }
    public Row markAttendance(long course,long student,String date,String status) { return academic.attendance(course,student,date,status); }
    public Row saveTimetable(Long id,long course,long faculty,long resource,int day,String start,String end) { return academic.saveTimetable(id,course,faculty,resource,day,start,end); }
    public void removeTimetable(long id) { academic.removeTimetable(id); }
    public List<Row> availableFaculty(long id,String date) { return academic.availableFaculty(id,date); }
    public Row allocateSubstitute(long id,long faculty,String date) { return academic.substitute(id,faculty,date); }
    public Row saveTimetableOverride(long id,String date,long resource,String start,String end,String reason) { return academic.override(id,date,resource,start,end,reason); }
    public Row requestLeave(String type,String from,String to,String reason) { return requests.leave(type,from,to,reason); }
    public void decideLeave(long id,boolean approve,String remarks) { requests.decide("leave_requests",id,approve,remarks); }
    public void cancelLeave(long id) { requests.cancel("leave_requests",id); }
    public Row requestPermission(String date,String from,String to,String reason) { return requests.permission(date,from,to,reason); }
    public void decidePermission(long id,boolean approve,String remarks) { requests.decide("permission_requests",id,approve,remarks); }
    public void cancelPermission(long id) { requests.cancel("permission_requests",id); }
    public Row borrowBook(long book,long student,String due) { return library.borrow(book,student,due); }
    public void returnBook(long id) { library.returnBook(id); }
    public Row reserveBook(long id) { return library.reserve(id); }
    public void cancelReservation(long id) { library.cancelReservation(id); }
    public Row replyToDoubt(long id,String body) { return requests.reply(id,body); }
    public void resolveTicket(long id,String response) { requests.resolve(id,response); }
    public void markNotificationRead(long id) { requests.readNotification(id); }
}
