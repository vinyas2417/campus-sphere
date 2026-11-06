package com.campussphere.service;

import com.campussphere.config.ModuleCatalog;
import com.campussphere.exception.CampusException;
import com.campussphere.model.*;
import com.campussphere.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds an authorized, current snapshot for the three downloadable documents. */
public final class DocumentService {
    private final WorkflowRepository work;
    private final Session session;
    public DocumentService(JdbcRepository repo,Session session) {work=new WorkflowRepository(repo.database());this.session=session;}
    public static String unavailableReason(String key,Row row) {
        return switch(key) {
            case "hall_tickets" -> row.text("status").equals("ISSUED")?"":"This hall ticket has not been issued.";
            case "student_fees" -> new BigDecimal(row.text("paid_amount")).signum()>0?"":"A receipt is available after a payment is recorded.";
            case "faculty_salaries" -> row.text("status").equals("PAID")&&!row.text("paid_date").isBlank()?"":"A salary receipt is available after payment is recorded.";
            default -> "This module has no downloadable document.";
        };
    }
    public DownloadDocument prepare(String key,long id) {
        User actor=session.requireUser();
        if(!Set.of("hall_tickets","student_fees","faculty_salaries").contains(key) || !ModuleCatalog.get(key).readers().contains(actor.role())) throw new CampusException("Your role cannot download this document.");
        return work.transaction(tx->{
            Row row=tx.require(key,id);
            if(!new AccessPolicy(tx,actor).visible(key,row)) throw new CampusException("You cannot download another person's document.");
            String unavailable=unavailableReason(key,row); if(!unavailable.isEmpty()) throw new CampusException(unavailable);
            List<DownloadDocument.Entry> entries=new ArrayList<>();
            String title,reference,note;
            if(key.equals("hall_tickets")) {
                title="Hall Ticket"; reference=row.text("ticket_number");
                appendHallTicket(tx,row,entries,0);
                note="Bring this hall ticket and your campus identification to the examination.";
            } else if(key.equals("student_fees")) {
                Row student=tx.require("students",row.number("student_id"));
                title="Fee Receipt";reference="FEE-"+row.id();
                add(entries,"Student",student.text("name"));add(entries,"USN",student.text("usn"));
                add(entries,"Fee",row.text("title"));add(entries,"Academic year",row.text("academic_year"));
                add(entries,"Total fee (INR)",money(row.text("amount")));add(entries,"Paid to date (INR)",money(row.text("paid_amount")));
                add(entries,"Balance (INR)",money(new BigDecimal(row.text("amount")).subtract(new BigDecimal(row.text("paid_amount"))).toPlainString()));
                add(entries,"Due date",row.text("due_date"));
                note="Cumulative receipt for payments recorded by campus administration against this fee. It is not a separate payment transaction.";
            } else {
                Row faculty=tx.require("faculty",row.number("faculty_id"));
                title="Salary Receipt";reference="SAL-"+row.id();
                add(entries,"Faculty",faculty.text("name"));add(entries,"Faculty ID",Long.toString(faculty.id()));
                add(entries,"Designation",faculty.text("designation"));add(entries,"Salary month",row.text("salary_month"));
                add(entries,"Amount paid (INR)",money(row.text("amount")));add(entries,"Payment date",row.text("paid_date"));add(entries,"Status","PAID");
                note="Salary payment recorded by campus administration for the month shown above.";
            }
            add(entries,"Generated on",LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            return new DownloadDocument(title,reference,entries,note);
        });
    }
    public DownloadDocument prepareAllHallTickets() {
        User actor=session.requireUser();
        if(actor.role()!=Role.STUDENT)throw new CampusException("Only students can download their combined course hall tickets.");
        return work.transaction(tx->{
            AccessPolicy policy=new AccessPolicy(tx,actor);
            List<Row> tickets=tx.all("hall_tickets").stream().filter(r->policy.visible("hall_tickets",r)&&r.text("status").equals("ISSUED"))
                    .filter(r->eligible(tx,r)).sorted(Comparator.comparing(r->tx.require("exam_schedule",r.number("exam_id")).text("date")))
                    .toList();
            if(tickets.isEmpty())throw new CampusException("No eligible issued hall tickets are available to download.");
            Row student=tx.all("students").stream().filter(r->r.number("user_id")==actor.id()).findFirst()
                    .orElseThrow(()->new CampusException("Your student profile could not be found."));
            List<DownloadDocument.Entry> entries=new ArrayList<>();
            add(entries,"Student",student.text("name"));add(entries,"USN",student.text("usn"));
            for(int i=0;i<tickets.size();i++)appendHallTicket(tx,tickets.get(i),entries,i+1);
            add(entries,"Generated on",LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            return new DownloadDocument("Course Hall Tickets",student.text("usn"),entries,
                    "Bring this combined hall ticket document and your campus identification to the examinations.");
        });
    }
    private static boolean eligible(WorkflowRepository.Transaction tx,Row ticket) {
        Row exam=tx.require("exam_schedule",ticket.number("exam_id"));
        List<Row> attendance=tx.where("attendance",Map.of("course_id",exam.number("course_id"),"student_id",ticket.number("student_id")));
        long present=attendance.stream().filter(a->Set.of("PRESENT","LATE").contains(a.text("status"))).count();
        return !attendance.isEmpty()&&present*100.0/attendance.size()>75.0;
    }
    private static void appendHallTicket(WorkflowRepository.Transaction tx,Row ticket,List<DownloadDocument.Entry> entries,int courseIndex) {
        Row student=tx.require("students",ticket.number("student_id"));
        Row exam=tx.require("exam_schedule",ticket.number("exam_id"));
        Row course=tx.require("courses",exam.number("course_id"));
        if(courseIndex==0){add(entries,"Student",student.text("name"));add(entries,"USN",student.text("usn"));}
        if(courseIndex>0)add(entries,"Course ticket "+courseIndex,course.text("code")+" - "+course.text("name"));
        add(entries,"Ticket number",ticket.text("ticket_number"));add(entries,"Exam",exam.text("title"));
        add(entries,"Exam date",exam.text("date"));add(entries,"Time",exam.text("start_time")+" - "+exam.text("end_time"));
        Optional<Row> seat=tx.where("seating_allotments",Map.of("exam_id",exam.id(),"student_id",student.id())).stream().findFirst();
        Row room=tx.require("resources",seat.map(r->r.number("resource_id")).orElse(exam.number("resource_id")));
        add(entries,"Room",room.text("name"));add(entries,"Building",room.text("building"));
        add(entries,"Seat",seat.map(r->r.text("seat_number")).orElse("Not allotted"));
        add(entries,"Roll number",seat.map(r->r.text("roll_number")).orElse(student.text("usn")));
        add(entries,"Bench",seat.map(r->r.text("bench_number")).orElse("Not allotted"));
        add(entries,"Side",seat.map(r->r.text("seat_side")).orElse("Not allotted"));
        add(entries,"Issued on",ticket.text("issued_date"));add(entries,"Status","ISSUED");
    }
    private static void add(List<DownloadDocument.Entry> entries,String label,String value) {entries.add(new DownloadDocument.Entry(label,value));}
    private static String money(String value) {return new BigDecimal(value).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
}
