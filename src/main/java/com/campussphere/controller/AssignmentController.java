package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.exception.CampusException;
import com.campussphere.model.*;
import com.campussphere.service.AssignmentService;
import com.campussphere.task.Background;
import com.campussphere.ui.*;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import java.awt.Desktop;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static com.campussphere.ui.RecordForm.*;

/** Course picker, student submission status, and assigned-faculty review inbox. */
public final class AssignmentController {
    private final AppContext context;
    private final Role role;
    private final VBox root=new VBox(16);
    private final ComboBox<Row> course=new ComboBox<>();
    private final TableView<Row> assignments=new TableView<>();
    private final TableView<Row> submissions=new TableView<>();
    private final Label message=Ui.label("Loading your courses...","muted");
    private final Label details=Ui.label("Select an assignment to see its details.","muted");
    private final Button fileButton=Ui.button("Open assignment file","secondary",this::openAssignment);
    private final Button uploadButton=Ui.button("Upload assignment","primary",this::upload);
    private final Button openSubmission=Ui.button("Open submission","secondary",this::openSubmission);
    private final Button gradeButton=Ui.button("Grade and give feedback","primary",()->review("GRADED"));
    private final Button returnButton=Ui.button("Return to student","secondary",()->review("RETURNED"));
    private final WorkflowController forms;

    public AssignmentController(AppContext context){this.context=context;role=context.session().requireUser().role();forms=new WorkflowController(context);}

    public Parent view(){
        root.getStyleClass().add("module-page");
        root.getChildren().addAll(Ui.label("Assignments","page-title"),Ui.label("Course work, submissions, and feedback.","muted"));
        configureCoursePicker();
        if(role==Role.STUDENT) studentView(); else if(role==Role.FACULTY) facultyView();
        root.getChildren().add(message);
        loadCourses();
        return Ui.scroll(root);
    }

    private void configureCoursePicker(){
        course.setPromptText("Choose a course");course.setPrefWidth(470);
        course.setConverter(new StringConverter<>(){
            @Override public String toString(Row row){return row==null?"":row.text("code")+" · "+row.text("name");}
            @Override public Row fromString(String value){return null;}
        });
        course.valueProperty().addListener((o,old,value)->{if(value!=null){if(role==Role.STUDENT)loadStudentAssignments();else loadFacultyAssignments();}});
    }

    private void studentView(){
        course.setId("student-assignment-course");
        addTextColumn(assignments,"Assignment","title",220);addTextColumn(assignments,"Due date","due_date",130);
        addLinkColumn(assignments,"Resource link","attachment_url",210);
        addTextColumn(assignments,"Submission status","submission_status",160);addTextColumn(assignments,"Uploaded at","submitted_at",190);addTextColumn(assignments,"Grade","grade",90);
        assignments.getSelectionModel().selectedItemProperty().addListener((o,old,row)->studentSelection(row));
        fileButton.setGraphic(Icons.icon("file",16));fileButton.setDisable(true);fileButton.setId("assignment-instructions-open");
        uploadButton.setGraphic(Icons.icon("file",16));uploadButton.setDisable(true);uploadButton.setId("assignment-upload");
        HBox picker=new HBox(12,new VBox(6,Ui.label("Course","field-label"),course));picker.setAlignment(Pos.CENTER_LEFT);
        HBox actions=new HBox(10,fileButton,uploadButton);actions.setAlignment(Pos.CENTER_LEFT);
        details.setWrapText(true);details.getStyleClass().add("portal-content-card");
        root.getChildren().addAll(picker,actions,assignments,Ui.label("Submission details","section-title"),details);
        VBox.setVgrow(assignments,Priority.ALWAYS);
    }

    private void facultyView(){
        course.setId("faculty-assignment-course");
        addTextColumn(assignments,"Assignment","title",230);addTextColumn(assignments,"Due date","due_date",130);
        addLinkColumn(assignments,"Resource link","attachment_url",210);
        addTextColumn(assignments,"Instructions","description",300);
        assignments.getSelectionModel().selectedItemProperty().addListener((o,old,row)->facultySelection(row));
        addTextColumn(submissions,"Student","student_name",200);addTextColumn(submissions,"File","file_name",220);
        addTextColumn(submissions,"Status","status",110);addTextColumn(submissions,"Uploaded at","uploaded_at",190);
        addTextColumn(submissions,"Grade","grade",90);addTextColumn(submissions,"Feedback","feedback",240);
        submissions.getSelectionModel().selectedItemProperty().addListener((o,old,row)->submissionSelection(row));
        fileButton.setText("Open assignment instructions");fileButton.setDisable(true);fileButton.setId("faculty-assignment-instructions");
        openSubmission.setGraphic(Icons.icon("file",16));openSubmission.setDisable(true);openSubmission.setId("faculty-submission-open");
        gradeButton.setDisable(true);gradeButton.setId("faculty-submission-grade");returnButton.setDisable(true);returnButton.setId("faculty-submission-return");
        Button create=Ui.button("Create assignment","primary",this::createAssignment);create.setId("faculty-assignment-create");
        Button refresh=Ui.button("Refresh","secondary",this::loadFacultyAssignments);
        HBox picker=new HBox(12,new VBox(6,Ui.label("Course","field-label"),course),create,refresh);picker.setAlignment(Pos.BOTTOM_LEFT);
        HBox assignmentActions=new HBox(10,fileButton,Ui.button("Attach instruction PDF","secondary",this::attachInstructions));
        HBox submissionActions=new HBox(10,openSubmission,gradeButton,returnButton);
        root.getChildren().addAll(picker,Ui.label("Assignments","section-title"),assignmentActions,assignments,
                Ui.label("Course submissions","section-title"),submissionActions,submissions);
        VBox.setVgrow(assignments,Priority.ALWAYS);VBox.setVgrow(submissions,Priority.ALWAYS);
    }

    private void loadCourses(){
        message.setText("Loading your courses...");
        Background.run(()->{
            List<Row> courses=context.campus().list("courses");
            if(role==Role.STUDENT){
                Set<Long> enrolled=new HashSet<>();context.campus().list("course_enrollments").forEach(row->enrolled.add(row.number("course_id")));
                courses=courses.stream().filter(row->enrolled.contains(row.id())).toList();
            }
            return courses;
        },items->{course.getItems().setAll(items);if(!items.isEmpty())course.getSelectionModel().selectFirst();else message.setText("No courses are available for your account.");},error->message.setText(Ui.safeMessage(error)));
    }

    private void loadStudentAssignments(){
        Row selected=course.getValue();if(selected==null)return;
        assignments.setPlaceholder(new ProgressIndicator());message.setText("Loading course assignments...");
        Background.run(()->new AssignmentService(context.repository(),context.session()).studentAssignments(selected.id()),rows->{
            assignments.getItems().setAll(rows);message.setText(rows.isEmpty()?"No assignments have been posted for this course yet.":rows.size()+" assignment(s) in "+selected.text("name"));
            if(!rows.isEmpty())assignments.getSelectionModel().selectFirst();else studentSelection(null);
        },error->{assignments.getItems().clear();message.setText(Ui.safeMessage(error));});
    }

    private void studentSelection(Row row){
        boolean selected=row!=null;fileButton.setDisable(!selected||row.text("attachment_url").isBlank());uploadButton.setDisable(!selected);
        if(!selected){details.setText("Select an assignment to see its details.");return;}
        String submitted=row.text("submitted_at").isBlank()?"Not submitted":row.text("submitted_at");
        String text="Due: "+row.text("due_date")+"\nStatus: "+row.text("submission_status")+"\nUploaded: "+submitted;
        if(!row.text("submission_file").isBlank())text+="\nFile: "+row.text("submission_file");
        if(!row.text("grade").isBlank())text+="\nGrade: "+row.text("grade")+" / 100";
        if(!row.text("feedback").isBlank())text+="\nFeedback: "+row.text("feedback");
        details.setText(text);
    }

    private void upload(){
        Row row=assignments.getSelectionModel().getSelectedItem();if(row==null)return;
        FileChooser chooser=new FileChooser();chooser.setTitle("Upload your assignment (up to 25 MB)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Assignment files","*.pdf","*.doc","*.docx","*.txt","*.zip"));
        var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        uploadButton.setDisable(true);
        Background.run(()->new AssignmentService(context.repository(),context.session()).submit(file.toPath(),row.id()),saved->{
            Ui.info("Upload successful","Your assignment was uploaded successfully.");loadStudentAssignments();
        },error->{uploadButton.setDisable(false);Ui.info("Upload unsuccessful",Ui.safeMessage(error));});
    }

    private void loadFacultyAssignments(){
        Row selectedCourse=course.getValue();if(selectedCourse==null)return;
        message.setText("Loading assignments and submissions...");
        Background.run(()->{
            List<Row> assignments=context.campus().list("assignments").stream().filter(row->row.number("course_id")==selectedCourse.id()).toList();
            List<Row> submissions=new AssignmentService(context.repository(),context.session()).submissions(selectedCourse.id());
            List<Row> students=context.campus().options("students");Map<Long,String> names=new HashMap<>();students.forEach(st->names.put(st.id(),st.text("name")+" · "+st.text("usn")));
            List<Row> enriched=new ArrayList<>();
            for(Row row:submissions){Map<String,Object> values=new LinkedHashMap<>(row.values());values.put("student_name",names.getOrDefault(row.number("student_id"),"Student #"+row.number("student_id")));enriched.add(new Row(row.id(),values));}
            return new FacultyData(assignments,enriched);
        },data->{
            assignments.getItems().setAll(data.assignments());submissions.getItems().clear();
            message.setText(data.assignments().size()+" assignment(s) · "+data.submissions().size()+" submission(s) in "+selectedCourse.text("name"));
            if(!data.assignments().isEmpty())assignments.getSelectionModel().selectFirst();else facultySelection(null);
        },error->message.setText(Ui.safeMessage(error)));
    }

    private void facultySelection(Row assignment){
        boolean selected=assignment!=null;
        fileButton.setDisable(!selected||assignment.text("attachment_url").isBlank());
        if(!selected){submissions.getItems().clear();return;}
        Row selectedCourse=course.getValue();if(selectedCourse==null)return;
        Background.run(()->{
            List<Row> rows=new AssignmentService(context.repository(),context.session()).submissions(selectedCourse.id());
            List<Row> students=context.campus().options("students");Map<Long,String> names=new HashMap<>();students.forEach(st->names.put(st.id(),st.text("name")+" · "+st.text("usn")));
            List<Row> result=new ArrayList<>();for(Row row:rows)if(row.number("assignment_id")==assignment.id()){
                Map<String,Object> values=new LinkedHashMap<>(row.values());values.put("student_name",names.getOrDefault(row.number("student_id"),"Student #"+row.number("student_id")));result.add(new Row(row.id(),values));
            }return result;
        },rows->{submissions.getItems().setAll(rows);message.setText(rows.size()+" submission(s) for "+assignment.text("title"));},error->message.setText(Ui.safeMessage(error)));
    }

    private void submissionSelection(Row row){
        boolean selected=row!=null;openSubmission.setDisable(!selected);gradeButton.setDisable(!selected);returnButton.setDisable(!selected);
    }

    private void createAssignment(){
        Row selected=course.getValue();if(selected==null){Ui.info("Choose a course","Select one of your courses first.");return;}
        var fields=List.of(field("title","Assignment title","text"),field("description","Instructions","textarea"),field("due_date","Due date","date"));
        forms.form("Create assignment for "+selected.text("name"),fields,Map.of("due_date",LocalDate.now().plusDays(7).toString()),values->{
            Map<String,Object> record=new LinkedHashMap<>(values);record.put("course_id",selected.id());record.put("attachment_url",null);
            context.campus().save("assignments",null,record);
        },this::loadFacultyAssignments);
    }

    private void attachInstructions(){
        Row row=assignments.getSelectionModel().getSelectedItem();if(row==null)return;
        FileChooser chooser=new FileChooser();chooser.setTitle("Attach assignment instructions PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));
        var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        Background.run(()->new AssignmentService(context.repository(),context.session()).attach(file.toPath(),row.id(),true),saved->{Ui.info("Instructions attached","The PDF was attached to this course assignment.");loadFacultyAssignments();},error->Ui.error(error));
    }

    private void openAssignment(){
        Row row=assignments.getSelectionModel().getSelectedItem();if(row==null||row.text("attachment_url").isBlank())return;
        String url=row.text("attachment_url");
        if(url.startsWith("http://")||url.startsWith("https://")){
            ModuleController.openLink(url);
            return;
        }
        openManaged(row.text("attachment_url"),row.number("course_id"));
    }

    private void openSubmission(){
        Row row=submissions.getSelectionModel().getSelectedItem();if(row==null)return;
        openManaged(row.text("file_url"),row.number("course_id"));
    }

    private void openManaged(String url,long courseId){
        Background.run(()->{Path path=new AssignmentService(context.repository(),context.session()).openFile(url,courseId);Desktop.getDesktop().open(path.toFile());return true;},ignored->{},Ui::error);
    }

    private void review(String status){
        Row selected=submissions.getSelectionModel().getSelectedItem();if(selected==null)return;
        List<FieldSpec> fields=List.of(new FieldSpec("grade","Grade (0-100)","decimal",status.equals("GRADED"),"",List.of()),new FieldSpec("feedback","Feedback","textarea",false,"",List.of()));
        forms.form(status.equals("RETURNED")?"Return submission with feedback":"Grade submission",fields,Map.of(),values->{
            new AssignmentService(context.repository(),context.session()).review(selected.id(),Objects.toString(values.get("grade"),""),Objects.toString(values.get("feedback"),""),status);
        },()->{Row assignment=assignments.getSelectionModel().getSelectedItem();if(assignment!=null)facultySelection(assignment);});
    }

    private void addTextColumn(TableView<Row> table,String title,String key,double width){
        TableColumn<Row,String> column=new TableColumn<>(title);column.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(cell.getValue().text(key)));
        column.setPrefWidth(width);table.getColumns().add(column);
    }

    private void addLinkColumn(TableView<Row> table,String title,String key,double width){
        TableColumn<Row,String> column=new TableColumn<>(title);column.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(cell.getValue().text(key)));
        column.setPrefWidth(width);
        column.setCellFactory(col->new TableCell<Row,String>(){
            private final Hyperlink link=new Hyperlink();
            {
                link.getStyleClass().add("table-hyperlink");
                link.setStyle("-fx-text-fill: #1a73e8; -fx-underline: true; -fx-cursor: hand; -fx-font-weight: 500; -fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
                link.setMaxWidth(Double.MAX_VALUE);
                link.setTextOverrun(OverrunStyle.ELLIPSIS);
                link.setOnMouseClicked(e->e.consume());
                link.setOnAction(e->{String u=getItem();if(u!=null&&!u.isBlank())ModuleController.openLink(u);});
            }
            @Override protected void updateItem(String item,boolean empty){
                super.updateItem(item,empty);
                if(empty||item==null||item.isBlank()){setGraphic(null);setText(null);}
                else{link.setText(item);link.setTooltip(new Tooltip(item));setGraphic(link);setText(null);}
            }
        });
        table.getColumns().add(column);
    }

    private record FacultyData(List<Row> assignments,List<Row> submissions){}
}
