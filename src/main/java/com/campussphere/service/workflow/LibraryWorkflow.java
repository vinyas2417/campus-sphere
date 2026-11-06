package com.campussphere.service.workflow;

import com.campussphere.model.*;
import com.campussphere.repository.WorkflowRepository;
import com.campussphere.service.Session;
import com.campussphere.exception.CampusException;
import java.time.*;
import java.util.*;

public final class LibraryWorkflow extends WorkflowSupport {
    public LibraryWorkflow(WorkflowRepository repository,Session session) { super(repository,session); }
    public Row borrow(long bookId,long studentId,String due) {
        User actor=user(Role.ADMIN,Role.STUDENT); LocalDate dueDate=date(due);
        if(dueDate.isBefore(LocalDate.now())) throw new CampusException("The due date cannot be in the past.");
        return repository.transaction(tx -> {
            Row book=tx.lock("library_resources",bookId), student=student(tx,studentId,actor);
            List<Row> active=tx.where("library_borrowings",Map.of("library_resource_id",bookId)).stream().filter(r -> r.text("returned_date").isEmpty()).toList();
            if(active.size()>=book.number("total_copies")) throw new CampusException("All copies are currently on loan.");
            if(active.stream().anyMatch(r -> r.number("student_id")==student.id())) throw new CampusException("This student already has a copy on loan.");
            Queue<Row> queue=new ArrayDeque<>(tx.where("library_reservations",Map.of("library_resource_id",bookId,"status","PENDING")));
            if(!queue.isEmpty() && queue.peek().number("student_id")!=student.id()) throw new CampusException("The next available copy is reserved for another student.");
            Row loan=tx.insert("library_borrowings",Map.of("library_resource_id",bookId,"student_id",student.id(),"borrowed_date",LocalDate.now().toString(),"due_date",dueDate.toString()));
            if(!queue.isEmpty()) tx.update("library_reservations",queue.remove().id(),Map.of("status","FULFILLED"));
            audit(tx,actor,"BORROW","library_borrowings",loan.id()); notify(tx,student.number("user_id"),"Library loan",book.text("title")+" is due on "+dueDate+"."); return loan;
        });
    }
    public void returnBook(long id) {
        User actor=user(Role.ADMIN,Role.STUDENT);
        repository.transaction(tx -> { Row loan=tx.lock("library_borrowings",id); Row student=student(tx,loan.number("student_id"),actor); tx.lock("library_resources",loan.number("library_resource_id")); if(!loan.text("returned_date").isEmpty()) throw new CampusException("This loan has already been returned."); tx.update("library_borrowings",id,Map.of("returned_date",LocalDate.now().toString())); audit(tx,actor,"RETURN","library_borrowings",id); notify(tx,student.number("user_id"),"Book returned","Your library return has been recorded."); return null; });
    }
    public Row reserve(long bookId) {
        User actor=user(Role.STUDENT);
        return repository.transaction(tx -> { tx.lock("library_resources",bookId); Row student=student(tx,0,actor); if(!tx.where("library_reservations",Map.of("library_resource_id",bookId,"student_id",student.id(),"status","PENDING")).isEmpty()) throw new CampusException("You already have a pending reservation for this book."); Row row=tx.insert("library_reservations",Map.of("library_resource_id",bookId,"student_id",student.id(),"reserved_date",LocalDate.now().toString(),"status","PENDING")); audit(tx,actor,"RESERVE","library_reservations",row.id()); return row; });
    }
    public void cancelReservation(long id) {
        User actor=user(Role.ADMIN,Role.STUDENT); repository.transaction(tx -> { Row r=tx.lock("library_reservations",id); student(tx,r.number("student_id"),actor); pending(r); tx.update("library_reservations",id,Map.of("status","CANCELLED")); audit(tx,actor,"CANCEL","library_reservations",id); return null; });
    }
}
