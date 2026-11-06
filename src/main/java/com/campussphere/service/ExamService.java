package com.campussphere.service;

import com.campussphere.exception.CampusException;
import com.campussphere.model.*;
import com.campussphere.repository.*;
import java.time.LocalDate;
import java.util.*;

/** Issues exam seats and hall tickets only when course attendance exceeds 75%. */
public final class ExamService {
    private final WorkflowRepository work;
    private final Session session;
    public ExamService(JdbcRepository repository,Session session){work=new WorkflowRepository(repository.database());this.session=session;}

    public Result allocateAll(){
        if(session.requireUser().role()!=Role.ADMIN)throw new CampusException("Only an administrator can allocate exam seating and issue hall tickets.");
        return work.transaction(tx->{
            int seats=0,tickets=0,ineligible=0;
            for(Row exam:tx.all("exam_schedule")){
                Row course=tx.require("courses",exam.number("course_id"));
                Row room=tx.require("resources",exam.number("resource_id"));
                List<Row> roomSeats=tx.where("seating_allotments",Map.of("exam_id",exam.id(),"resource_id",room.id()));
                Set<String> occupied=new HashSet<>();roomSeats.forEach(s->occupied.add(s.text("seat_number")));
                for(Row enrollment:tx.where("course_enrollments",Map.of("course_id",course.id()))){
                    long studentId=enrollment.number("student_id");
                    List<Row> marks=tx.where("attendance",Map.of("course_id",course.id(),"student_id",studentId));
                    long attended=marks.stream().filter(m->Set.of("PRESENT","LATE").contains(m.text("status"))).count();
                    double percent=marks.isEmpty()?0.0:100.0*attended/marks.size();
                    if(percent<=75.0){ineligible++;continue;}
                    Row student=tx.require("students",studentId);
                    boolean hasSeat=tx.where("seating_allotments",Map.of("exam_id",exam.id(),"student_id",studentId)).size()>0;
                    if(!hasSeat){
                        if(roomSeats.size()>=room.number("capacity"))throw new CampusException("Room "+room.text("name")+" does not have enough seats for "+course.text("name")+".");
                        int seatIndex=roomSeats.size();String side=seatIndex%2==0?"LEFT":"RIGHT";int bench=seatIndex/2+1;
                        String seat=String.format(Locale.ROOT,"B%03d-%s",bench,side.equals("LEFT")?"L":"R");
                        while(occupied.contains(seat)){seatIndex++;side=seatIndex%2==0?"LEFT":"RIGHT";bench=seatIndex/2+1;seat=String.format(Locale.ROOT,"B%03d-%s",bench,side.equals("LEFT")?"L":"R");}
                        Map<String,Object> values=new LinkedHashMap<>();values.put("exam_id",exam.id());values.put("student_id",studentId);values.put("resource_id",room.id());values.put("seat_number",seat);
                        values.put("roll_number",student.text("usn"));values.put("bench_number",String.format(Locale.ROOT,"B%03d",bench));values.put("seat_side",side);values.put("room_number",room.text("name"));
                        tx.insert("seating_allotments",values);occupied.add(seat);roomSeats.add(new Row(0,values));seats++;
                    }
                    if(tx.where("hall_tickets",Map.of("exam_id",exam.id(),"student_id",studentId)).isEmpty()){
                        String ticket="GMU-"+course.text("code")+"-"+exam.text("date").replace("-","")+"-"+student.text("usn");
                        tx.insert("hall_tickets",Map.of("exam_id",exam.id(),"student_id",studentId,"ticket_number",ticket,"issued_date",LocalDate.now().toString(),"status","ISSUED"));tickets++;
                    }
                }
            }
            return new Result(seats,tickets,ineligible);
        });
    }

    public record Result(int seatsAssigned,int ticketsIssued,int ineligibleExamEntries){}
}
