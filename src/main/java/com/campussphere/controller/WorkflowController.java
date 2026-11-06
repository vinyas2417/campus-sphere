package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.*;
import com.campussphere.config.ModuleCatalog;
import com.campussphere.ui.*;
import com.campussphere.task.Background;
import com.campussphere.exception.CampusException;
import javafx.scene.layout.FlowPane;
import javafx.scene.control.ListView;
import java.util.*;
import java.util.function.*;
import static com.campussphere.ui.RecordForm.*;

public final class WorkflowController {
    private final AppContext context;
    public WorkflowController(AppContext context) { this.context=context; }
    public void actions(String key,FlowPane bar,Supplier<Row> selected,Runnable refresh) {
        Role role=context.session().requireUser().role(); boolean admin=role==Role.ADMIN, student=role==Role.STUDENT, faculty=role==Role.FACULTY;
        var w=context.workflows();
        switch(key) {
            case "courses","course_enrollments" -> {
                if(admin||student) addPrimary(bar,"Enroll student",()->form("Course enrollment",enrollmentFields("course_id","Course","courses",admin),Map.of(),v->w.enroll(n(v,"course_id"),admin?n(v,"student_id"):0),refresh));
                if(key.equals("course_enrollments")&&(admin||student)) addDestructive(bar,"Remove enrollment",()->run("Remove enrollment",()->w.removeEnrollment(require(selected).id()),refresh));
            }
            case "events","event_registrations" -> {
                if(admin||student) addPrimary(bar,"Register for event",()->form("Event registration",enrollmentFields("event_id","Event","events",admin),Map.of(),v->w.registerEvent(n(v,"event_id"),admin?n(v,"student_id"):0),refresh));
                if(key.equals("event_registrations")&&(admin||student)) addDestructive(bar,"Cancel registration",()->run("Cancel registration",()->w.cancelEventRegistration(require(selected).id()),refresh));
            }
            case "attendance" -> { if(admin||faculty) addPrimary(bar,"Mark attendance",()->form("Mark or correct attendance",List.of(reference("course_id","Course","courses"),reference("student_id","Student","students"),field("date","Class date","date"),choice("status","Attendance","PRESENT","ABSENT","LATE","EXCUSED")),Map.of(),v->w.markAttendance(n(v,"course_id"),n(v,"student_id"),s(v,"date"),s(v,"status")),refresh)); }
            case "exam_schedule" -> { if(admin) addSuccess(bar,"Allocate exam seats and issue eligible hall tickets",()->Background.run(()->new com.campussphere.service.ExamService(context.repository(),context.session()).allocateAll(),result->Ui.info("Exam allocation complete",result.ticketsIssued()+" hall ticket(s) issued and "+result.seatsAssigned()+" seat(s) allocated. "+result.ineligibleExamEntries()+" course enrollment(s) were below the attendance requirement."),Ui::error)); }
            case "timetable" -> { if(admin) {
                addPrimary(bar,"Schedule class",()->timetable(null,refresh));
                add(bar,"Edit class",()->timetable(require(selected),refresh));
                addDestructive(bar,"Delete class",()->run("Delete class",()->w.removeTimetable(require(selected).id()),refresh));
            } }
            case "leave_requests","permission_requests" -> {
                boolean leave=key.equals("leave_requests");
                if(!admin) addPrimary(bar,leave?"Request leave / on-duty":"Request permission",()->{
                    List<FieldSpec> fields=leave?List.of(choice("type","Request type",student?"STUDENT_LEAVE":"FACULTY_LEAVE","ON_DUTY"),field("from_date","From date","date"),field("to_date","To date","date"),field("reason","Reason","textarea")):List.of(field("date","Date","date"),field("from_time","From time (HH:mm)","text"),field("to_time","To time (HH:mm)","text"),field("reason","Reason","textarea"));
                    form("New request",fields,Map.of(),v->{ if(leave) w.requestLeave(s(v,"type"),s(v,"from_date"),s(v,"to_date"),s(v,"reason")); else w.requestPermission(s(v,"date"),s(v,"from_time"),s(v,"to_time"),s(v,"reason")); },refresh);
                });
                if(admin) { addSuccess(bar,"Approve request",()->{ Row row=require(selected); form("Approve request",List.of(field("remarks","Decision remarks","textarea")),Map.of(),v->{ if(leave) w.decideLeave(row.id(),true,s(v,"remarks")); else w.decidePermission(row.id(),true,s(v,"remarks")); },refresh); }); addDestructive(bar,"Reject request",()->{ Row row=require(selected); form("Reject request",List.of(field("remarks","Decision remarks","textarea")),Map.of(),v->{ if(leave) w.decideLeave(row.id(),false,s(v,"remarks")); else w.decidePermission(row.id(),false,s(v,"remarks")); },refresh); }); }
                addDestructive(bar,"Cancel request",()->run("Cancel request",()->{ if(leave) w.cancelLeave(require(selected).id()); else w.cancelPermission(require(selected).id()); },refresh));
            }
            case "library_resources" -> {
                
                if(admin||faculty)addPrimary(bar,"Upload e-library PDF",()->{
                    javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();chooser.setTitle("Select an e-library PDF (up to 25 MB)");chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF document","*.pdf"));
                    var file=chooser.showOpenDialog(bar.getScene().getWindow());if(file==null)return;
                    List<FieldSpec> fields=ModuleCatalog.get("library_resources").fields().stream().filter(f->!f.key().equals("url")).toList();
                    form("Upload "+file.getName(),fields,Map.of("title",file.getName(),"total_copies",1),values->new com.campussphere.service.MaterialService(context.campus(),context.session()).uploadLibrary(file.toPath(),values),refresh);
                });
            }
            case "study_materials" -> {
                
                if(admin||faculty) addPrimary(bar,"Upload document",()->{
                    javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();chooser.setTitle("Select a study document (up to 25 MB)");var file=chooser.showOpenDialog(bar.getScene().getWindow());if(file==null)return;
                    List<FieldSpec> fields=ModuleCatalog.get("study_materials").fields().stream().filter(f->!f.key().equals("url")).toList();
                    form("Upload "+file.getName(),fields,Map.of("title",file.getName()),values->new com.campussphere.service.MaterialService(context.campus(),context.session()).upload(file.toPath(),values),refresh);
                });
            }
            case "assignments" -> {
                if(student) addPrimary(bar,"Upload assignment  ↑",()->chooseAssignmentFile(selected.get(),bar,true,false,refresh));
                if(faculty||admin) addPrimary(bar,"Attach assignment PDF",()->chooseAssignmentFile(selected.get(),bar,false,faculty,refresh));
                if(faculty||admin) add(bar,"View course submissions",()->showSubmissions(selected.get()));
            }
            case "doubts" -> { if(faculty) addPrimary(bar,"Reply to question",()->{ Row row=require(selected); form("Reply to "+row.text("subject"),List.of(field("body","Your explanation","textarea")),Map.of(),v->w.replyToDoubt(row.id(),s(v,"body")),refresh); }); }
            case "helpdesk_tickets" -> { if(admin) addSuccess(bar,"Resolve ticket",()->{ Row row=require(selected); form("Respond to ticket",List.of(field("response","Response","textarea")),Map.of(),v->w.resolveTicket(row.id(),s(v,"response")),refresh); }); }
            case "notifications" -> add(bar,"Mark as read",()->run("Mark notification as read",()->w.markNotificationRead(require(selected).id()),refresh));
            case "users" -> {
                addPrimary(bar,"Create account",()->createAccount(refresh));
                add(bar,"Reset password",()->{ Row row=require(selected); form("Reset password for "+row.text("name"),List.of(field("password","New password (6+ characters)","password")),Map.of(),v->context.auth().resetPassword(row.id(),s(v,"password")),refresh); });
            }
            default -> { }
        }
    }
    private void chooseAssignmentFile(Row row,javafx.scene.Node owner,boolean submission,boolean faculty,Runnable refresh) {
        if(row==null) throw new CampusException("Select an assignment first.");
        javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();
        chooser.setTitle(submission?"Upload your assignment":"Attach assignment PDF");
        chooser.getExtensionFilters().add(submission?new javafx.stage.FileChooser.ExtensionFilter("Assignment files","*.pdf","*.doc","*.docx","*.txt","*.zip"):new javafx.stage.FileChooser.ExtensionFilter("PDF document","*.pdf"));
        var file=chooser.showOpenDialog(owner.getScene().getWindow()); if(file==null)return;
        Background.run(()->{
            var service=new com.campussphere.service.AssignmentService(context.repository(),context.session());
            if(submission) service.submit(file.toPath(),row.id()); else service.attach(file.toPath(),row.id(),faculty);
            return true;
        },ignored->{ Ui.info(submission?"Upload successful":"Document attached",submission?"Your assignment uploaded successfully.":"The assignment PDF was attached successfully.");refresh.run(); },error->{Ui.info(submission?"Upload unsuccessful":"Could not attach document",Ui.safeMessage(error));});
    }
    private void showSubmissions(Row assignment) {
        if(assignment==null)throw new CampusException("Select an assignment first.");
        Background.run(()->{
            var service=new com.campussphere.service.AssignmentService(context.repository(),context.session());
            List<Row> rows=service.submissions(assignment.number("course_id"));
            List<Row> students=context.campus().options("students");
            Map<Long,String> names=new HashMap<>(); students.forEach(s->names.put(s.id(),s.text("name")+" · "+s.text("usn")));
            return rows.stream().filter(r->r.number("assignment_id")==assignment.id()).map(r->new String[]{names.getOrDefault(r.number("student_id"),"Student #"+r.number("student_id")),r.text("file_name"),r.text("uploaded_at"),r.text("file_url"),Long.toString(r.number("course_id"))}).toList();
        },items->{
            ListView<String[]> list=new ListView<>();list.getItems().setAll(items);list.setPrefSize(720,360);
            list.setCellFactory(view->new javafx.scene.control.ListCell<>(){@Override protected void updateItem(String[] item,boolean empty){super.updateItem(item,empty);if(empty||item==null){setText(null);setGraphic(null);return;}setText(item[0]+"  ·  "+item[1]+"  ·  "+item[2]);setGraphic(Ui.button("Open file","link-button",()->openManaged(item[3],Long.parseLong(item[4]))));}});
            javafx.scene.control.Dialog<Void> dialog=new javafx.scene.control.Dialog<>();dialog.setTitle("Assignment submissions");dialog.setHeaderText(assignment.text("title")+" · "+items.size()+" submission(s)");dialog.getDialogPane().setContent(list);dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);Ui.dialogStyle(dialog);dialog.show();
        },Ui::error);
    }
    private void openManaged(String url,long courseId) {
        Background.run(()->{java.nio.file.Path path=new com.campussphere.service.AssignmentService(context.repository(),context.session()).openFile(url,courseId);java.awt.Desktop.getDesktop().open(path.toFile());return true;},ignored->{},Ui::error);
    }
    private List<FieldSpec> enrollmentFields(String key,String label,String table,boolean admin) { List<FieldSpec> result=new ArrayList<>(); result.add(reference(key,label,table)); if(admin) result.add(reference("student_id","Student","students")); return result; }
    private void timetable(Row row,Runnable refresh) { form(row==null?"Schedule a class":"Update scheduled class",ModuleCatalog.get("timetable").fields(),row==null?Map.of():row.values(),v->context.workflows().saveTimetable(row==null?null:row.id(),n(v,"course_id"),n(v,"faculty_id"),n(v,"resource_id"),(int)n(v,"day_of_week"),s(v,"start_time"),s(v,"end_time")),refresh); }
    private void createAccount(Runnable refresh) {
        form("Choose the account role",List.of(choice("role","Role","STUDENT","FACULTY","ADMIN")),Map.of(),v->{
            javafx.application.Platform.runLater(()-> {
                Role role=Role.valueOf(s(v,"role")); List<FieldSpec> fields=new ArrayList<>(List.of(field("name","Full name","text"),field("username","Username","text"),field("email","University email","email"),field("password","Password (6+ characters)","password")));
                if(role!=Role.ADMIN) fields.add(reference("profile","Link campus profile",role==Role.STUDENT?"students":"faculty"));
                form("Create "+role.name().toLowerCase()+" account",fields,Map.of(),data->context.auth().createAccount(s(data,"name"),s(data,"username"),s(data,"email"),s(data,"password"),role,role==Role.ADMIN?0:n(data,"profile")),refresh);
            });
        },()->{});
    }
    public void form(String title,List<FieldSpec> fields,Map<String,Object> initial,Consumer<Map<String,Object>> action,Runnable done) {
        Background.run(()-> {
            Map<String,List<Row>> options=new HashMap<>(); for(FieldSpec field:fields) if(field.type().equals("reference")&&!options.containsKey(field.reference())) options.put(field.reference(),context.campus().options(field.reference())); return options;
        },options->{ RecordForm form=new RecordForm(title,"Save changes",fields,initial,options); form.show(values->{ form.busy(true); Background.run(()->{ action.accept(values); return true; },ignored->{ form.close(); done.run(); },form::fail); }); },Ui::error);
    }
    private void add(FlowPane bar,String label,Runnable action)            { bar.getChildren().add(Ui.button(label,"secondary",   safe(action))); }
    private void addPrimary(FlowPane bar,String label,Runnable action)     { bar.getChildren().add(Ui.button(label,"primary",     safe(action))); }
    private void addDestructive(FlowPane bar,String label,Runnable action) { bar.getChildren().add(Ui.button(label,"destructive", safe(action))); }
    private void addSuccess(FlowPane bar,String label,Runnable action)     { bar.getChildren().add(Ui.button(label,"success",     safe(action))); }
    private Runnable safe(Runnable action) { return ()->{ try { action.run(); } catch(Exception e) { Ui.error(e); } }; }
    private Row require(Supplier<Row> selected) { Row row=selected.get(); if(row==null) throw new CampusException("Select a record in the table first."); return row; }
    private void run(String title,Runnable action,Runnable refresh) { if(Ui.confirm(title,"Apply this action to the selected record?")) Background.run(()->{ action.run(); return true; },ignored->refresh.run(),Ui::error); }
    private static String s(Map<String,Object> v,String key) { return Objects.toString(v.get(key),""); }
    private static long n(Map<String,Object> v,String key) { return Long.parseLong(s(v,key)); }
    private void openResource(Row row) {
        String url=row.text("url");if(url.isBlank())throw new CampusException("This record does not have a digital resource attached.");
        Background.run(()->{
            java.net.URI uri=java.net.URI.create(url);
            if(Set.of("http","https").contains(Objects.toString(uri.getScheme(),""))) java.awt.Desktop.getDesktop().browse(uri);
            else if("file".equals(uri.getScheme()) && (java.nio.file.Path.of(uri).toAbsolutePath().normalize().startsWith(java.nio.file.Path.of("data","materials").toAbsolutePath().normalize())||java.nio.file.Path.of(uri).toAbsolutePath().normalize().startsWith(java.nio.file.Path.of("data","library").toAbsolutePath().normalize()))) java.awt.Desktop.getDesktop().open(java.nio.file.Path.of(uri).toFile());
            else throw new CampusException("This resource address cannot be opened.");return true;
        },ignored->{},Ui::error);
    }
}
