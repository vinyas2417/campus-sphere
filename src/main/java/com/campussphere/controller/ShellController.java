package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.*;
import com.campussphere.ui.*;
import com.campussphere.task.Background;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.campussphere.ui.RecordForm.field;

public final class ShellController {
    private final AppContext context; private final Runnable logout;
    private final BorderPane root=new BorderPane(); private final Label title=Ui.label("Overview","top-title");
    public ShellController(AppContext context,Runnable logout) { this.context=context; this.logout=logout; }
    public Parent view() {
        User user=context.session().requireUser();
        Button search=Ui.button("Search campus    Ctrl K","search-command",this::search); search.setGraphic(Icons.icon("search",17));
        Button avatar=Ui.button(initials(user.name()),"avatar",()->navigate("profile")); avatar.setId("header-profile"); avatar.setAccessibleText("Open my profile"); avatar.setTooltip(new Tooltip("My profile"));
        String role=switch(user.role()){case STUDENT->"STUDENT PORTAL";case FACULTY->"TEACHER PORTAL";case ADMIN->"ADMINISTRATOR PORTAL";};
        VBox identity=new VBox(3,Ui.label(user.name(),"header-name"),Ui.label(role,"header-role")); identity.setAlignment(Pos.CENTER_RIGHT);
        Button signOut=Ui.button("","header-icon",()->{context.auth().logout();logout.run();}); signOut.setId("sign-out"); signOut.setGraphic(Icons.icon("logout",20)); signOut.setAccessibleText("Sign out"); signOut.setTooltip(new Tooltip("Sign out"));
        HBox command=new HBox(18,Ui.brand(),Ui.spacer(),search,identity,avatar,signOut); command.setAlignment(Pos.CENTER_LEFT); command.getStyleClass().add("command-bar"); root.setTop(command);
        Button home=Ui.button("Home","secondary",()->navigate("overview")); home.setId("home-button"); home.setGraphic(Icons.icon("home",16));
        HBox breadcrumb=new HBox(12,home,title,Ui.spacer(),Ui.label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")),"caption")); breadcrumb.setAlignment(Pos.CENTER_LEFT); breadcrumb.getStyleClass().add("breadcrumb-bar");
        BorderPane workspace=new BorderPane(); workspace.setTop(breadcrumb); root.setCenter(workspace); root.getStyleClass().add("app-shell");
        Platform.runLater(()->{if(root.getScene()!=null) {
            var shortcut=new KeyCodeCombination(KeyCode.K,KeyCombination.CONTROL_DOWN);
            root.getScene().getAccelerators().remove(shortcut);
            root.getScene().getAccelerators().put(shortcut,this::search);
        }}); navigate("overview"); return root;
    }
    private String initials(String name) { String[] parts=name.trim().split("\\s+"); return (parts[0].substring(0,1)+(parts.length>1?parts[parts.length-1].substring(0,1):"")).toUpperCase(Locale.ROOT); }
    private void navigate(String key) {
        Role role=context.session().requireUser().role();
        if(!com.campussphere.config.ModuleCatalog.visibleInPortal(key,role)) return;
        title.setText(key.equals("overview")?"Home  /  Overview":key.equals("profile")?"Home  /  My profile":key.equals("reports")?"Home  /  Analytics & reports":com.campussphere.config.ModuleCatalog.get(key).group()+"  /  "+com.campussphere.config.ModuleCatalog.get(key).title());
        Parent page=switch(key){case "overview"->new DashboardController(context,this::navigate).view();case "profile"->new ProfileController(context,this::settings).view();case "reports"->new ReportController(context).view();case "project_members"->projectMembers();case "assignments"->role==Role.ADMIN?new ModuleController(context,key).view():new AssignmentController(context).view();default->new ModuleController(context,key).view();}; ((BorderPane)root.getCenter()).setCenter(page); Ui.animate(page);
    }
    private Parent projectMembers() {
        Tab members=new Tab("Members",new ModuleController(context,"project_members").view());
        Tab projects=new Tab("Projects",new ModuleController(context,"projects").view());
        TabPane tabs=new TabPane(members,projects); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }
    private void settings() {
        User user=context.session().requireUser();
        new WorkflowController(context).form("Account settings · "+user.name()+" · "+user.role(),List.of(field("old","Current password","password"),field("new","New password (6+ characters)","password")),Map.of(),values->context.auth().changePassword(values.get("old").toString(),values.get("new").toString()),()->Ui.info("Password changed","Your new password is ready to use."));
    }
    private void help() { Ui.info("Welcome to Namma Sphere","Start with Departments, then add Faculty and Students. Create Courses, enroll students and schedule classes.\n\nAdministrators can create linked student/faculty accounts under User accounts.\n\nSelect a row before using Edit, Details or approval actions. Search and column headers filter and sort records. Hall tickets and payment receipts can be downloaded from their modules.\n\nTimes use 24-hour HH:mm; timestamps use yyyy-mm-ddTHH:mm. Dates and schedules use this computer's local campus timezone.\n\nAcademic project: SMART CAMPUS INTEGRATED MANAGEMENT SYSTEM USING JAVA AND OBJECT-ORIENTED PROGRAMMING.\n\nSee README.md and docs/OOP-GUIDE.md in the project folder for setup and academic documentation."); }
    private record Hit(String module,Row row,String title) { @Override public String toString(){return title+"  /  "+RecordForm.describe(row);} }
    private void search() {
        Dialog<Void> dialog=new Dialog<>(); dialog.setTitle("Search your campus"); dialog.setHeaderText("Find people, courses, resources and campus records"); Ui.dialogStyle(dialog);
        TextField input=new TextField(); input.setPromptText("Type a name, USN, course code or keyword, then press Enter"); ListView<Hit> results=new ListView<>(); results.setPrefSize(700,380); Label status=Ui.label("Results respect your account permissions.","muted");
        Runnable find=()-> { String query=input.getText().trim(); if(query.isEmpty())return; status.setText("Searching campus records…"); Background.run(()->{ List<Hit> hits=new ArrayList<>(); for(ModuleSpec module:context.campus().modules()) for(Row row:context.campus().search(module.key(),query)) { hits.add(new Hit(module.key(),row,module.title())); if(hits.size()>=100)return hits; } return hits; },hits->{results.getItems().setAll(hits);status.setText(hits.size()+" results · Double-click to open the module");},e->status.setText(Ui.safeMessage(e))); };
        input.setOnAction(e->find.run()); results.setOnMouseClicked(e->{if(e.getClickCount()==2&&results.getSelectionModel().getSelectedItem()!=null){Hit hit=results.getSelectionModel().getSelectedItem();dialog.close();navigate(hit.module());}});
        dialog.getDialogPane().setContent(new VBox(14,new HBox(10,input,Ui.button("Search","primary",find)),status,results)); HBox.setHgrow(input,Priority.ALWAYS); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.show(); input.requestFocus();
    }
}
