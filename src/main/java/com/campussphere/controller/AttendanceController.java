package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.Row;
import com.campussphere.task.Background;
import com.campussphere.ui.*;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.util.*;

/** Named class roster with explicit, atomic saving of present/absent marks. */
public final class AttendanceController {
    private final AppContext context;
    private final VBox root=new VBox(18), roster=new VBox(10);
    private final ComboBox<Row> course=new ComboBox<>();
    private final DatePicker date=new DatePicker(LocalDate.now());
    private final Label message=Ui.label("Loading your courses...","muted"), summary=Ui.label("","caption");
    private final Map<Long,String> marks=new LinkedHashMap<>();
    private Button save,reset;
    private boolean busy,dirty,initializing=true;
    private int request;
    public AttendanceController(AppContext context){this.context=context;}
    public Parent view() {
        root.setId("attendance-roster-page");root.getStyleClass().add("module-page");
        course.setId("attendance-course");course.setPrefWidth(370);course.setPromptText("Choose a course");
        course.setConverter(new StringConverter<>(){public String toString(Row r){return r==null?"":r.text("code")+" - "+r.text("name");}public Row fromString(String s){return null;}});
        date.setId("attendance-date");date.setDayCellFactory(picker->new DateCell(){@Override public void updateItem(LocalDate day,boolean empty){super.updateItem(day,empty);setDisable(empty||day.isAfter(LocalDate.now()));}});
        save=Ui.button("Save attendance","primary",this::save);save.setId("attendance-save");
        reset=Ui.button("Reload / reset marks","secondary",this::load);reset.setId("attendance-reset");
        FlowPane selectors=new FlowPane(12,12,new VBox(6,Ui.label("Course","field-label"),course),new VBox(6,Ui.label("Class date","field-label"),date));
        FlowPane actions=new FlowPane(12,12,save,reset,summary);
        ScrollPane scroll=Ui.scroll(roster);scroll.setFitToWidth(true);VBox.setVgrow(scroll,Priority.ALWAYS);
        VBox card=Ui.card(scroll);VBox.setVgrow(card,Priority.ALWAYS);
        root.getChildren().addAll(Ui.label("Mark attendance","page-title"),Ui.label("Check a name for Present (green). Leave it unchecked for Absent (red). Save to record the entire class.","muted"),selectors,actions,message,card);
        course.valueProperty().addListener((o,old,value)->{if(!initializing)load();});
        date.valueProperty().addListener((o,old,value)->{if(!initializing)load();});
        busy=true;controls();
        Background.run(()->context.campus().list("courses"),courses->{
            course.getItems().setAll(courses);if(!courses.isEmpty())course.getSelectionModel().selectFirst();
            initializing=false;busy=false;load();
        },error->{initializing=false;busy=false;message.setText(Ui.safeMessage(error));controls();});
        return root;
    }
    private void load() {
        root.setId("attendance-roster-loading");int ticket=++request;marks.clear();roster.getChildren().clear();dirty=false;summary.setText("");
        Row selected=course.getValue();LocalDate day=date.getValue();
        if(selected==null||day==null||day.isAfter(LocalDate.now())) {
            busy=false;message.setText(selected==null?"No assigned course. Ask the administrator to assign your course and enroll students.":"Choose today's date or an earlier date.");controls();return;
        }
        busy=true;message.setText("Loading enrolled students...");controls();
        Background.run(()->context.workflows().attendanceRoster(selected.id(),day.toString()),students->{
            if(ticket!=request)return;
            busy=false;
            for(Row student:students) {
                String stored=student.text("attendance_status");marks.put(student.id(),stored.isEmpty()?"ABSENT":stored);
                roster.getChildren().add(studentRow(student,stored));
            }
            message.setText(students.isEmpty()?"No students are enrolled in this course yet.":students.stream().anyMatch(s->s.text("attendance_status").isEmpty())?"New marks are not saved yet. Unchecked students will be recorded as absent when you save.":"Saved attendance loaded. Change marks and save to update this class.");
            updateSummary();controls();root.setId("attendance-roster-ready");
        },error->{if(ticket!=request)return;busy=false;message.setText(Ui.safeMessage(error));controls();});
    }
    private HBox studentRow(Row student,String stored) {
        CheckBox mark=new CheckBox("Present");mark.setId("attendance-mark-"+student.id());mark.setSelected(Set.of("PRESENT","LATE").contains(stored));
        mark.setAccessibleText("Mark "+student.text("name")+" present");
        Label name=Ui.label(student.text("name"),"card-title");name.setWrapText(true);
        VBox identity=new VBox(5,name,Ui.label(student.text("usn"),"caption"));identity.setMinWidth(0);HBox.setHgrow(identity,Priority.ALWAYS);
        Label state=Ui.label("","attendance-state");state.setMinWidth(140);state.setAlignment(Pos.CENTER_RIGHT);
        HBox row=new HBox(20,mark,identity,state);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("attendance-student");row.setId("attendance-student-"+student.id());
        paint(row,state,marks.get(student.id()),stored.isEmpty());
        mark.selectedProperty().addListener((o,old,present)->{
            String status=present?"PRESENT":"ABSENT";marks.put(student.id(),status);dirty=true;
            paint(row,state,status,true);message.setText("Unsaved changes. Save attendance or reset marks before changing the course or date.");updateSummary();controls();
        });
        return row;
    }
    private void paint(HBox row,Label label,String state,boolean unsaved) {
        row.getStyleClass().removeAll("attendance-present","attendance-absent","attendance-other");
        row.getStyleClass().add(state.equals("PRESENT")||state.equals("LATE")?"attendance-present":state.equals("ABSENT")?"attendance-absent":"attendance-other");
        label.setText(state.substring(0,1)+state.substring(1).toLowerCase(Locale.ROOT)+(unsaved?" (not saved)":""));
    }
    private void controls() {
        course.setDisable(busy||dirty);date.setDisable(busy||dirty);roster.setDisable(busy);
        save.setDisable(busy||marks.isEmpty());reset.setDisable(busy||course.getValue()==null);
    }
    private void updateSummary() {
        long present=marks.values().stream().filter("PRESENT"::equals).count(),absent=marks.values().stream().filter("ABSENT"::equals).count();
        long other=marks.size()-present-absent;summary.setText(marks.size()+" students  |  "+present+" present  |  "+absent+" absent"+(other>0?"  |  "+other+" late/excused":""));
    }
    private void save() {
        try {date.commitValue();}catch(Exception e){message.setText("Enter a valid class date.");return;}
        if(marks.isEmpty()||course.getValue()==null||date.getValue()==null)return;
        long courseId=course.getValue().id();String day=date.getValue().toString();Map<Long,String> snapshot=Map.copyOf(marks);
        busy=true;controls();message.setText("Saving attendance...");
        Background.run(()->{context.workflows().markAttendanceBatch(courseId,day,snapshot);return true;},ignored->{
            busy=false;dirty=false;load();
        },error->{busy=false;message.setText(Ui.safeMessage(error));controls();});
    }
}
