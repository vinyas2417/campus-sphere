package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.*;
import com.campussphere.config.ModuleCatalog;
import com.campussphere.task.Background;
import com.campussphere.ui.*;
import com.campussphere.service.DocumentService;
import com.campussphere.util.PdfDocument;
import com.campussphere.exception.CampusException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.util.*;

public final class ModuleController {
    private final AppContext context; private final ModuleSpec spec;
    private final TableView<Row> table=new TableView<>(); private final VBox root=new VBox(20);
    private final TextField search=new TextField(); private final Label count=Ui.label("Loading records…","caption");
    private final ComboBox<String> filter=new ComboBox<>(); private final ComboBox<String> filter2=new ComboBox<>(); private final Map<String,Map<Long,String>> labels=new HashMap<>();
    private final WorkflowController workflows;
    public ModuleController(AppContext context,String key) { this.context=context; spec=ModuleCatalog.get(key); workflows=new WorkflowController(context); }
    public Parent view() {
        if(spec.key().equals("attendance") && context.session().requireUser().role()==Role.FACULTY) return new AttendanceController(context).view();
        HBox heading=new HBox(new VBox(6,Ui.label(spec.title(),"page-title"),Ui.label(description(),"muted")),Ui.spacer(),Ui.label(spec.group().toUpperCase(),"pill"));
        FlowPane actions=new FlowPane(8,8);
        if(spec.writers().contains(context.session().requireUser().role())) {
            actions.getChildren().addAll(Ui.button("+ Add "+spec.singular().toLowerCase(),"primary",()->edit(null)),Ui.button("Edit selected","secondary",()->{ Row row=selected(); if(row!=null) edit(row); }),Ui.button("Delete","destructive",this::delete));
        }
        workflows.actions(spec.key(),actions,()->table.getSelectionModel().getSelectedItem(),this::load);
        if(spec.key().equals("assignments")) {
            Button fileAction=Ui.button("Open assignment file","secondary",this::openAssignmentFile);fileAction.setGraphic(Icons.icon("file",16));fileAction.setId("assignment-open-file");fileAction.setDisable(true);
            table.getSelectionModel().selectedItemProperty().addListener((o,old,row)->fileAction.setDisable(row==null||row.text("attachment_url").isBlank()));actions.getChildren().add(fileAction);
        }
        if(spec.key().equals("library_resources")) {
            Button downloadBook=Ui.button("Open / download book","secondary",this::downloadBook);downloadBook.setGraphic(Icons.icon("file",16));downloadBook.setId("library-download-book");downloadBook.setDisable(true);
            table.getSelectionModel().selectedItemProperty().addListener((o,old,row)->downloadBook.setDisable(row==null||row.text("url").isBlank()));actions.getChildren().add(downloadBook);
        }
        actions.getChildren().addAll(Ui.button("View details","secondary",()->{ Row row=selected(); if(row!=null) details(row); }),Ui.button("↻ Refresh","secondary",this::load));
        if(spec.key().equals("hall_tickets")&&context.session().requireUser().role()==Role.STUDENT) {
            Button download=Ui.button("Download all eligible course hall tickets","primary",this::downloadAllHallTickets);
            download.setId("download-all-hall-tickets");actions.getChildren().add(download);
        } else if(Set.of("hall_tickets","student_fees","faculty_salaries").contains(spec.key())) {
            Button download=Ui.button(spec.key().equals("hall_tickets")?"Download hall ticket":"Download receipt","primary",this::download);
            download.setId("download-document"); download.setDisable(true);
            table.getSelectionModel().selectedItemProperty().addListener((o,old,row)->{
                String reason=row==null?"Select a record to download.":DocumentService.unavailableReason(spec.key(),row);
                download.setDisable(!reason.isEmpty()); download.setTooltip(new Tooltip(reason.isEmpty()?"Save as PDF":reason));
            });
            actions.getChildren().add(download);
        }
        boolean hasSearch = Set.of("courses","departments","faculty","timetable","students","resources","events","assignments","exam_schedule","library_resources").contains(spec.key());
        Label searchIcon = Ui.label("⌕", "eyebrow");
        searchIcon.setStyle("-fx-font-size:16px; -fx-padding: 0 4 0 0;");
        search.setPromptText("Search " + spec.title().toLowerCase() + "…");
        search.setPrefWidth(hasSearch ? 260 : 230);
        Button clearBtn = Ui.button("✕", "icon-button", () -> { search.clear(); search.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED,"","",javafx.scene.input.KeyCode.BACK_SPACE,false,false,false,false)); });
        clearBtn.setStyle("-fx-font-size:10px; -fx-padding: 2 6;");
        HBox searchBox = new HBox(4, searchIcon, search, clearBtn);
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-bar-box");
        filter.setPromptText("All records"); filter.setPrefWidth(200);
        filter.getStyleClass().add("search-filter-combo");
        // Second filter for semester/type on specific modules
        filter2.getStyleClass().add("search-filter-combo");
        boolean showFilter2 = Set.of("courses","timetable","students","faculty").contains(spec.key());
        if (showFilter2) {
            if (spec.key().equals("courses") || spec.key().equals("timetable")) { filter2.setPromptText("All semesters"); filter2.getItems().addAll("All semesters","1","2","3","4","5","6","7","8"); filter2.setPrefWidth(150); }
            else if (spec.key().equals("faculty")) { filter2.setPromptText("All designations"); filter2.getItems().addAll("All designations","Professor","Associate Professor","Assistant Professor","HOD","Dean","Lecturer","Lab Instructor","Coordinator"); filter2.setPrefWidth(180); }
            else if (spec.key().equals("students")) { filter2.setPromptText("All sections"); filter2.getItems().addAll("All sections","A","B","C","D","E"); filter2.setPrefWidth(140); }
        }
        FlowPane controls = new FlowPane(10, 10, filter, showFilter2 ? filter2 : new javafx.scene.layout.Region(), count);
        if(context.session().requireUser().role()==Role.ADMIN||spec.key().equals("library_resources")) controls.getChildren().add(0,searchBox);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        table.getStyleClass().add("records-table"); table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<Row,String> id=new TableColumn<>("ID"); id.setCellValueFactory(c->new SimpleStringProperty("#"+c.getValue().id())); id.setPrefWidth(74); table.getColumns().add(id);
        for(FieldSpec field:spec.fields()) {
            TableColumn<Row,String> column=new TableColumn<>(field.label());
            column.setCellValueFactory(c->new SimpleStringProperty(display(field,c.getValue())));
            if(field.key().equals("url") || field.key().endsWith("_url")) {
                column.setPrefWidth(240);
                column.setCellFactory(col -> new TableCell<Row, String>() {
                    private final Hyperlink link = new Hyperlink();
                    {
                        link.getStyleClass().add("table-hyperlink");
                        link.setStyle("-fx-text-fill: #1a73e8; -fx-underline: true; -fx-cursor: hand; -fx-font-weight: 500; -fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
                        link.setMaxWidth(Double.MAX_VALUE);
                        link.setTextOverrun(OverrunStyle.ELLIPSIS);
                        link.setOnMouseClicked(e -> e.consume());
                        link.setOnAction(e -> {
                            String u = getItem();
                            if(u != null && !u.isBlank()) openLink(u);
                        });
                    }
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if(empty || item == null || item.isBlank()) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            link.setText(item);
                            link.setTooltip(new Tooltip(item));
                            setGraphic(link);
                            setText(null);
                        }
                    }
                });
            } else {
                column.setPrefWidth(field.type().equals("textarea")?270:field.type().equals("reference")?190:160);
            }
            table.getColumns().add(column);
        }
        if(spec.key().equals("event_registrations")) {
            for(String key:List.of("semester","branch")) {
                String label=key.equals("semester")?"Semester":"Branch";
                TableColumn<Row,String> column=new TableColumn<>(label);
                column.setCellValueFactory(c->new SimpleStringProperty(labels.getOrDefault("student_"+key,Map.of()).getOrDefault(c.getValue().number("student_id"),"—")));
                column.setPrefWidth(150); table.getColumns().add(column);
            }
        }
        if(spec.key().equals("student_fees")) {
            TableColumn<Row,String> balance=new TableColumn<>("Balance (INR)"); balance.setPrefWidth(160);
            balance.setCellValueFactory(c->new SimpleStringProperty(balance(c.getValue()))); table.getColumns().add(balance);
        }
        if(spec.key().equals("resources")) { TableColumn<Row,String> next=new TableColumn<>("Next reservation"); next.setPrefWidth(330); next.setCellValueFactory(c->new SimpleStringProperty(labels.getOrDefault("next_bookings",Map.of()).getOrDefault(c.getValue().id(),"No upcoming reservations"))); table.getColumns().add(next); }
        if(spec.key().equals("attendance")) { TableColumn<Row,String> percent=new TableColumn<>("Course attendance");percent.setPrefWidth(150);percent.setCellValueFactory(c->new SimpleStringProperty(labels.getOrDefault("attendance_percent",Map.of()).getOrDefault(c.getValue().id(),"0%")));table.getColumns().add(percent); }
        // Operational fields are stored even where their values are controlled by a workflow.
        if(spec.key().equals("helpdesk_tickets")) for(String key:List.of("status","response")) { TableColumn<Row,String> column=new TableColumn<>(key.equals("status")?"Status":"Response"); column.setCellValueFactory(c->new SimpleStringProperty(c.getValue().text(key))); column.setPrefWidth(200); table.getColumns().add(column); }
        table.setRowFactory(t->{ TableRow<Row> row=new TableRow<>(); row.setOnMouseClicked(e->{ if(e.getClickCount()==2&&!row.isEmpty()) details(row.getItem()); }); return row; });
        VBox surface=Ui.card(controls,table); VBox.setVgrow(table,Priority.ALWAYS); VBox.setVgrow(surface,Priority.ALWAYS);
        root.getChildren().addAll(heading,actions,surface); root.getStyleClass().add("module-page"); load(); return root;
    }
    private void openAssignmentFile() {
        Row row=selected();if(row==null)return;
        String url=row.text("attachment_url");
        if(url!=null&&(url.startsWith("http://")||url.startsWith("https://"))) {
            openLink(url);
            return;
        }
        Background.run(()->{java.nio.file.Path path=new com.campussphere.service.AssignmentService(context.repository(),context.session()).openFile(row.text("attachment_url"),row.number("course_id"));java.awt.Desktop.getDesktop().open(path.toFile());return true;},ignored->{},Ui::error);
    }
    private void downloadBook() {
        Row row=selected();if(row==null||row.text("url").isBlank())return;
        try{
            java.net.URI uri=java.net.URI.create(row.text("url"));
            if(Set.of("http","https").contains(Objects.toString(uri.getScheme(),""))){java.awt.Desktop.getDesktop().browse(uri);Ui.info("Digital book opened","The book opened in your browser. Use the book page or PDF viewer's download control to save it.");return;}
            if(!"file".equals(uri.getScheme()))throw new com.campussphere.exception.CampusException("This book address cannot be opened.");
            java.nio.file.Path source=java.nio.file.Path.of(uri).toAbsolutePath().normalize();
            java.nio.file.Path library=java.nio.file.Path.of("data","library").toAbsolutePath().normalize();java.nio.file.Path materials=java.nio.file.Path.of("data","materials").toAbsolutePath().normalize();
            if((!source.startsWith(library)&&!source.startsWith(materials))||!java.nio.file.Files.isRegularFile(source))throw new com.campussphere.exception.CampusException("This book file is not available in managed storage.");
            FileChooser chooser=new FileChooser();chooser.setTitle("Download e-library book");chooser.setInitialFileName(source.getFileName().toString());chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));
            var destination=chooser.showSaveDialog(root.getScene().getWindow());if(destination==null)return;
            Background.run(()->{java.nio.file.Files.copy(source,destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return true;},ignored->Ui.info("Download complete","Saved "+destination.getName()),Ui::error);
        }catch(Exception error){Ui.error(error);}
    }
    private void load() {
        count.setText("Loading records…"); table.setPlaceholder(new ProgressIndicator());
        Background.run(()-> {
            List<Row> rows=context.campus().list(spec.key()); Map<String,Map<Long,String>> lookup=new HashMap<>();
            if(spec.key().equals("resources")) lookup.put("next_bookings",context.campus().nextBookings());
            if(spec.key().equals("attendance")) {
                Map<String,int[]> totals=new HashMap<>();
                for(Row row:rows) { int[] total=totals.computeIfAbsent(row.number("course_id")+":"+row.number("student_id"),ignored->new int[2]); total[1]++; if(Set.of("PRESENT","LATE").contains(row.text("status"))) total[0]++; }
                Map<Long,String> percentages=new HashMap<>();
                for(Row row:rows) { int[] total=totals.get(row.number("course_id")+":"+row.number("student_id")); percentages.put(row.id(),String.format(Locale.ROOT,"%.1f%%",100.0*total[0]/total[1])); }
                lookup.put("attendance_percent",percentages);
            }
            for(FieldSpec f:spec.fields()) if(f.type().equals("reference")&&!lookup.containsKey(f.reference())) { Map<Long,String> map=new HashMap<>(); for(Row row:context.campus().options(f.reference())) map.put(row.id(),RecordForm.describe(row)); lookup.put(f.reference(),map); }
            if(spec.key().equals("event_registrations")) {
                Map<Long,String> semesters=new HashMap<>(),branches=new HashMap<>();
                Map<Long,String> departments=new HashMap<>();
                for(Row department:context.campus().options("departments")) departments.put(department.id(),department.text("name"));
                for(Row student:context.campus().options("students")) {
                    semesters.put(student.id(),student.text("semester"));
                    branches.put(student.id(),departments.getOrDefault(student.number("department_id"),"—"));
                }
                lookup.put("student_semester",semesters); lookup.put("student_branch",branches);
            }
            return new Loaded(rows,lookup);
        },loaded->{
            labels.clear(); labels.putAll(loaded.labels());
            FilteredList<Row> filtered=new FilteredList<>(FXCollections.observableArrayList(loaded.rows()));
            String filterKey=spec.fields().stream().filter(f->Set.of("status","department_id","semester","type","category").contains(f.key())).map(FieldSpec::key).findFirst().orElse("");
            Map<String,String> filterValues=new LinkedHashMap<>(); filterValues.put("All records","");
            if(!filterKey.isBlank()) for(Row r:loaded.rows()) { FieldSpec f=spec.fields().stream().filter(x->x.key().equals(filterKey)).findFirst().orElseThrow(); filterValues.put(display(f,r),r.text(filterKey)); }
            filter.getItems().setAll(filterValues.keySet()); filter.setValue("All records"); filter.setDisable(filterValues.size()==1);
            Runnable apply=()-> { String term=search.getText().toLowerCase(Locale.ROOT); String chosen=filterValues.getOrDefault(filter.getValue(),""); filtered.setPredicate(r->(chosen.isBlank()||r.text(filterKey).equals(chosen)) && (r.text("id").contains(term)||spec.fields().stream().anyMatch(f->display(f,r).toLowerCase(Locale.ROOT).contains(term)))); count.setText(filtered.size()+" records"); };
            search.setOnKeyReleased(e->apply.run()); filter.setOnAction(e->apply.run());
            SortedList<Row> sorted=new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
            VBox empty=new VBox(8,Ui.label("A fresh start.","section-title"),Ui.label(spec.writers().contains(context.session().requireUser().role())?"No records here yet. Use Add to enter a record.":"No records have been published for your account yet.","muted")); empty.setAlignment(javafx.geometry.Pos.CENTER); table.setPlaceholder(empty); apply.run();
        },failure->{ count.setText("Could not load records"); table.setPlaceholder(Ui.label(Ui.safeMessage(failure),"error-text")); });
    }
    private String description() {
        return switch(spec.key()) {
            case "student_fees" -> "Fee amounts and balance in INR. Select a record with a payment to download its PDF receipt.";
            case "faculty_salaries" -> "Monthly salary in INR. Select a paid record to download its PDF receipt.";
            case "seating_allotments" -> "Your assigned exam room and seat number, published by administration.";
            case "hall_tickets" -> "Select an issued hall ticket to download its PDF.";
            default -> "Your campus records, connected and up to date.";
        };
    }
    private String balance(Row row) { return new java.math.BigDecimal(row.text("amount")).subtract(new java.math.BigDecimal(row.text("paid_amount"))).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString(); }
    private record Loaded(List<Row> rows,Map<String,Map<Long,String>> labels) { }
    private String display(FieldSpec f,Row r) { if(f.type().equals("decimal")&&!r.text(f.key()).isBlank()) return new java.math.BigDecimal(r.text(f.key())).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString(); if(f.type().equals("reference")) return r.number(f.key())==0?"—":labels.getOrDefault(f.reference(),Map.of()).getOrDefault(r.number(f.key()),"#"+r.number(f.key())); return f.type().equals("datetime")?r.text(f.key()).replace('T',' '):r.text(f.key()); }
    private Row selected() { Row row=table.getSelectionModel().getSelectedItem(); if(row==null) Ui.info("Select a record","Select a row in the table to continue."); return row; }
    private void edit(Row row) { workflows.form((row==null?"Add ":"Edit ")+spec.singular().toLowerCase(),spec.fields(),row==null?Map.of():row.values(),values->context.campus().save(spec.key(),row==null?null:row.id(),values),this::load); }
    private void delete() { Row row=selected(); if(row!=null&&Ui.confirm("Delete "+spec.singular().toLowerCase()+"?","Delete record #"+row.id()+"? Related dependent records may also be removed. This cannot be undone.")) Background.run(()->{context.campus().delete(spec.key(),row.id()); return true;},ignored->load(),Ui::error); }
    public void details(Row row) {
        StringBuilder text=new StringBuilder("Record #"+row.id()+"\n\n");
        if(spec.key().equals("students")||spec.key().equals("faculty")) text.append(Person.fromRow(row,spec.key().equals("students")?Role.STUDENT:Role.FACULTY).displayDetails()).append("\n\n");
        for(FieldSpec f:spec.fields()) text.append(f.label()).append(": ").append(display(f,row)).append("\n");
        if(spec.key().equals("student_fees")) text.append("Balance (INR): ").append(balance(row)).append("\n");
        if(spec.key().equals("helpdesk_tickets")) text.append("Status: ").append(row.text("status")).append("\nResponse: ").append(row.text("response"));
        Dialog<Void> dialog=new Dialog<>(); dialog.setTitle(spec.singular()+" details"); dialog.setHeaderText(RecordForm.describe(row)); TextArea area=new TextArea(text.toString()); area.setEditable(false); area.setWrapText(true); area.setPrefSize(600,470); dialog.getDialogPane().setContent(area); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); Ui.dialogStyle(dialog); dialog.show();
    }
    private void download() {
        Row row=selected(); if(row==null)return;
        FileChooser chooser=new FileChooser(); chooser.setTitle(spec.key().equals("hall_tickets")?"Download hall ticket":"Download receipt");
        chooser.setInitialFileName(spec.key()+"-"+row.id()+".pdf"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));
        var file=chooser.showSaveDialog(root.getScene().getWindow()); if(file==null)return;
        count.setText("Preparing download...");
        Background.run(()->{
            var document=new DocumentService(context.repository(),context.session()).prepare(spec.key(),row.id());
            PdfDocument.write(file.toPath(),document); return true;
        },ignored->{count.setText("PDF saved");Ui.info("Download complete","Saved "+file.getName());},error->{count.setText("Download failed");Ui.error(error);});
    }
    private void downloadAllHallTickets() {
        FileChooser chooser=new FileChooser();chooser.setTitle("Download all course hall tickets");
        chooser.setInitialFileName("all-course-hall-tickets.pdf");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));
        var file=chooser.showSaveDialog(root.getScene().getWindow());if(file==null)return;
        count.setText("Preparing hall tickets...");
        Background.run(()->{
            var document=new DocumentService(context.repository(),context.session()).prepareAllHallTickets();
            PdfDocument.write(file.toPath(),document);return true;
        },ignored->{count.setText("PDF saved");Ui.info("Download complete","Saved all eligible course hall tickets to "+file.getName());},error->{count.setText("Download failed");Ui.error(error);});
    }
    public static void openLink(String rawUrl) {
        if(rawUrl==null||rawUrl.isBlank()) return;
        Background.run(()->{
            String trimmed=rawUrl.trim();
            if(!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") && !trimmed.startsWith("file:")) {
                trimmed="https://"+trimmed;
            }
            java.net.URI uri=java.net.URI.create(trimmed);
            String scheme=Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT);
            if(Set.of("http","https").contains(scheme)) {
                boolean browsed=false;
                try {
                    if(java.awt.Desktop.isDesktopSupported()&&java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(uri);
                        browsed=true;
                    }
                } catch(Exception ignored){}
                if(!browsed && System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")) {
                    new ProcessBuilder("rundll32","url.dll,FileProtocolHandler",uri.toString()).start();
                }
            } else if("file".equals(scheme)) {
                java.nio.file.Path path=java.nio.file.Path.of(uri).toAbsolutePath().normalize();
                if(path.startsWith(java.nio.file.Path.of("data","materials").toAbsolutePath().normalize())||path.startsWith(java.nio.file.Path.of("data","library").toAbsolutePath().normalize())) {
                    java.awt.Desktop.getDesktop().open(path.toFile());
                } else throw new CampusException("This resource address cannot be opened.");
            } else throw new CampusException("This resource address cannot be opened.");
            return true;
        },ignored->{},Ui::error);
    }
}
