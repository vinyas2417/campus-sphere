package com.campussphere.service.workflow;

import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository;
import com.campussphere.service.Session;
import com.campussphere.exception.CampusException;
import java.time.*;
import java.util.*;

public final class RequestWorkflow extends WorkflowSupport {
    public RequestWorkflow(WorkflowRepository repository,Session session) { super(repository,session); }
    public Row leave(String type,String from,String to,String reason) {
        User actor=user(Role.STUDENT,Role.FACULTY);
        if(!type.equals("ON_DUTY") && !type.equals(actor.role()==Role.STUDENT?"STUDENT_LEAVE":"FACULTY_LEAVE")) throw new CampusException("Choose the leave type for your role.");
        LeaveRequest request=LeaveRequest.fromRow(new Row(0,Map.of("requester_id",actor.id(),"type",type,"request_date",LocalDate.now().toString(),"from_date",date(from).toString(),"to_date",date(to).toString(),"reason",required(reason,"a reason"),"status","PENDING")));
        return repository.transaction(tx -> { Row saved=tx.insert("leave_requests",request.toValues()); audit(tx,actor,"REQUEST_LEAVE","leave_requests",saved.id()); return saved; });
    }
    public Row permission(String day,String from,String to,String reason) {
        User actor=user(Role.STUDENT,Role.FACULTY); String date=date(day).toString();
        if(!time(to).isAfter(time(from))) throw new CampusException("The permission end must be after its start.");
        String explanation=required(reason,"a reason");
        return repository.transaction(tx -> { Row saved=tx.insert("permission_requests",Map.of("requester_id",actor.id(),"date",date,"from_time",time(from).toString(),"to_time",time(to).toString(),"reason",explanation,"status","PENDING")); audit(tx,actor,"REQUEST_PERMISSION","permission_requests",saved.id()); return saved; });
    }
    public void decide(String table,long id,boolean approve,String remarks) {
        User actor=user(Role.ADMIN); if(!Set.of("leave_requests","permission_requests").contains(table)) throw new CampusException("Unsupported request.");
        if(!approve) required(remarks,"a rejection reason");
        repository.transaction(tx -> {
            tx.lockFacultySchedule(); Row request=tx.lock(table,id); pending(request);
            if(request.number("requester_id")==actor.id()) throw new CampusException("You cannot approve your own request.");
            if(table.equals("leave_requests")) {
                LeaveRequest model=LeaveRequest.fromRow(request); if(approve) model.approve(); else model.reject(remarks);
                if(approve) for(Row f:tx.where("faculty",Map.of("user_id",request.number("requester_id")))) for(Row sub:tx.where("faculty_substitutions",Map.of("faculty_id",f.id()))) if(!date(sub.text("date")).isBefore(model.getFromDate()) && !date(sub.text("date")).isAfter(model.getToDate())) throw new CampusException("Reassign this faculty member's substitute classes before approving leave.");
            }
            tx.update(table,id,Map.of("status",approve?"APPROVED":"REJECTED","approval_remarks",remarks==null?"":remarks.trim()));
            audit(tx,actor,approve?"APPROVE":"REJECT",table,id); notify(tx,request.number("requester_id"),"Request updated","Your request #"+id+" was "+(approve?"approved.":"rejected. "+remarks)); return null;
        });
    }
    public void cancel(String table,long id) {
        User actor=user(); if(!Set.of("leave_requests","permission_requests").contains(table)) throw new CampusException("Unsupported request.");
        repository.transaction(tx -> { Row request=tx.lock(table,id); ownerOrAdmin(actor,request.number("requester_id")); pending(request); tx.update(table,id,Map.of("status","CANCELLED")); audit(tx,actor,"CANCEL",table,id); return null; });
    }
    public Row reply(long id,String body) {
        User actor=user(Role.FACULTY); String text=required(body,"your reply");
        return repository.transaction(tx -> {
            Row doubt=tx.lock("doubts",id); courseTeacher(tx,actor,tx.require("courses",doubt.number("course_id"))); Row faculty=profile(tx,"faculty",actor);
            Row reply=tx.insert("doubt_replies",Map.of("doubt_id",id,"faculty_id",faculty.id(),"body",text,"created_at",LocalDateTime.now().toString()));
            tx.update("doubts",id,Map.of("status","ANSWERED")); notify(tx,tx.require("students",doubt.number("student_id")).number("user_id"),"Your question has a reply",doubt.text("subject")); audit(tx,actor,"REPLY","doubt_replies",reply.id()); return reply;
        });
    }
    public void resolve(long id,String response) {
        User actor=user(Role.ADMIN); String answer=required(response,"a response");
        repository.transaction(tx -> { Row ticket=tx.lock("helpdesk_tickets",id); tx.update("helpdesk_tickets",id,Map.of("status","RESOLVED","response",answer)); notify(tx,ticket.number("user_id"),"Help desk response",answer); audit(tx,actor,"RESOLVE","helpdesk_tickets",id); return null; });
    }
    public void readNotification(long id) {
        User actor=user(); repository.transaction(tx -> { Row row=tx.require("notifications",id); if(row.number("user_id")!=actor.id()) throw new CampusException("This notification belongs to another account."); tx.update("notifications",id,Map.of("is_read",1)); return null; });
    }
}
