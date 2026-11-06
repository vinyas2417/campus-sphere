package com.campussphere;

import com.campussphere.app.AppContext;
import com.campussphere.config.DatabaseConfig;
import com.campussphere.database.Database;
import com.campussphere.repository.JdbcRepository;
import com.campussphere.service.*;
import com.campussphere.controller.ShellController;
import com.campussphere.model.*;
import com.campussphere.task.Background;
import com.campussphere.ui.Ui;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.scene.image.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Offscreen JavaFX checks: no application windows and no changes to campus data. */
public final class PortalLayoutProbe {
    private static final Path OUTPUT=Path.of("target","portal-layout");
    private static AppContext context;
    private static Scene scene;
    private static User admin, student, teacher;
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Database db=new Database(new DatabaseConfig("jdbc:sqlite:"+OUTPUT.resolve("test-"+UUID.randomUUID()+".db").toAbsolutePath(),"",""));
        db.initialize(); JdbcRepository repo=new JdbcRepository(db); Session session=new Session();
        context=new AppContext(repo,session,new AuthService(repo,session),new CampusService(repo,session),new WorkflowService(repo,session));
        admin=context.auth().setupAdmin("Test Administrator","layout.admin","layout.admin@campus.test","Test123");
        long department=context.campus().list("departments").getFirst().id();
        Row faculty=context.campus().save("faculty",null,m("name","Test Teacher","department_id",department,"designation","Professor","email","teacher@campus.test"));
        Row learner=context.campus().save("students",null,m("name","Test Student","usn","LAYOUT001","department_id",department,"semester",3,"section","A","email","student@campus.test","admission_year",2026,"status","ACTIVE"));
        context.auth().createAccount("Test Teacher","layout.teacher","teacher@campus.test","Tea123",Role.FACULTY,faculty.id());
        context.auth().createAccount("Test Student","layout.student","student@campus.test","Stu123",Role.STUDENT,learner.id());
        teacher=context.auth().login("layout.teacher","Tea123",Role.FACULTY);
        student=context.auth().login("layout.student","Stu123",Role.STUDENT);
        session.signIn(admin);
        Row course=context.campus().save("courses",null,m("code","LAY101","name","Java Programming","credits",4,"department_id",department,"semester",3,"faculty_id",faculty.id(),"capacity",30));
        Row room=context.campus().save("resources",null,m("name","Room A","type","CLASSROOM","building","Academic Block","floor",1,"capacity",30,"status","AVAILABLE"));
        context.workflows().enroll(course.id(),learner.id());
        Row exam=context.campus().save("exam_schedule",null,m("course_id",course.id(),"title","Java Final","date","2026-12-10","start_time","09:00","end_time","11:00","resource_id",room.id()));
        context.workflows().markAttendance(course.id(),learner.id(),java.time.LocalDate.now().minusDays(1).toString(),"PRESENT");
        context.campus().save("seating_allotments",null,m("exam_id",exam.id(),"student_id",learner.id(),"resource_id",room.id(),"seat_number","A1","roll_number",learner.text("usn"),"bench_number","B001","seat_side","LEFT","room_number",room.text("name")));
        context.campus().save("hall_tickets",null,m("exam_id",exam.id(),"student_id",learner.id(),"ticket_number","HT001","issued_date","2026-12-01","status","ISSUED"));
        context.campus().save("student_fees",null,m("student_id",learner.id(),"title","Tuition","academic_year","2026-27","amount","1000.50","paid_amount","200.25","due_date","2026-12-01"));
        context.campus().save("faculty_salaries",null,m("faculty_id",faculty.id(),"salary_month","2026-10","amount","45000.25","status","PAID","paid_date","2026-10-17"));
        Row project=context.campus().save("projects",null,m("title","Campus project","description","Test project","mentor_id",faculty.id(),"status","ACTIVE"));
        context.campus().save("project_members",null,m("project_id",project.id(),"student_id",learner.id()));
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            for(User user:List.of(student,teacher,admin)) {
                session.signIn(user);
                fx(()->{scene=new Scene(new ShellController(context,()->{}).view(),1440,1000);Ui.style(scene);return null;});
                waitFor(()->node("dashboard-ready")!=null);
                fx(()->{snapshot(user.role()+"-dashboard",node("dashboard-ready"));verifyDashboard(user.role());return null;});
                List<String> modules=user.role()==Role.STUDENT?List.of("seating_allotments","exam_schedule","hall_tickets","student_fees","project_members"):
                    user.role()==Role.FACULTY?List.of("faculty_salaries","project_members"):List.of("seating_allotments","hall_tickets","student_fees","faculty_salaries","project_members");
                for(String key:modules) {
                    fx(()->{((Button)node("tile-"+key)).fire();return null;});
                    waitFor(()->scene.getRoot().lookup(".records-table") instanceof TableView<?> table && table.getItems().size()==1);
                    fx(()->{
                        boolean add=scene.getRoot().lookupAll(".button").stream().anyMatch(n->n instanceof Button b && b.getText().startsWith("+ Add"));
                        if(!key.equals("project_members")) require(add==(user.role()==Role.ADMIN),"Wrong edit permissions: "+key);
                        require(scene.getRoot().lookupAll(".button").stream().noneMatch(n->n instanceof Button b&&b.getText().contains("CSV")),"CSV export still present");
                        if(key.equals("hall_tickets")&&user.role()==Role.STUDENT) {
                            require(node("download-all-hall-tickets") instanceof Button,"Combined course hall-ticket download missing");
                            var document=new DocumentService(context.repository(),context.session()).prepareAllHallTickets();
                            require(document.entries().stream().anyMatch(entry->entry.label().equals("Course ticket 1")),"Combined PDF omitted the course ticket");
                            com.campussphere.util.PdfDocument.write(OUTPUT.resolve(user.role()+"-all-hall-tickets.pdf"),document);
                        } else if(Set.of("hall_tickets","student_fees","faculty_salaries").contains(key)) {
                            @SuppressWarnings("unchecked") TableView<Row> table=(TableView<Row>)scene.getRoot().lookup(".records-table");
                            require(((Button)node("download-document")).isDisabled(),"Download enabled without selection");
                            table.getSelectionModel().selectFirst();
                            require(!((Button)node("download-document")).isDisabled(),"Valid document download unavailable");
                            var document=new DocumentService(context.repository(),context.session()).prepare(key,table.getItems().getFirst().id());
                            com.campussphere.util.PdfDocument.write(OUTPUT.resolve(user.role()+"-"+key+".pdf"),document);
                        } else require(node("download-document")==null,"Unexpected download option");
                        snapshot(user.role()+"-"+key,scene.getRoot());
                        ((Button)node("home-button")).fire();return null;
                    });
                    waitFor(()->node("dashboard-ready")!=null);
                }
                if(user.role()==Role.FACULTY) {
                    fx(()->{((Button)node("tile-attendance")).fire();return null;});
                    waitFor(()->node("attendance-roster-ready")!=null);
                    fx(()->{
                        CheckBox mark=(CheckBox)node("attendance-mark-"+learner.id());require(!mark.isSelected(),"New attendance should start unchecked");
                        require(node("attendance-student-"+learner.id()).getStyleClass().contains("attendance-absent"),"Absent row not red");
                        mark.fire();require(mark.isSelected(),"Present mark was not checked");
                        require(node("attendance-student-"+learner.id()).getStyleClass().contains("attendance-present"),"Present row not green");
                        snapshot("FACULTY-attendance-present",scene.getRoot());((Button)node("attendance-save")).fire();return null;
                    });
                    waitFor(()->node("attendance-roster-ready")!=null && !((Button)node("attendance-save")).isDisabled());
                    require(context.workflows().attendanceRoster(course.id(),java.time.LocalDate.now().toString()).getFirst().text("attendance_status").equals("PRESENT"),"Present mark did not persist");
                    fx(()->{((CheckBox)node("attendance-mark-"+learner.id())).fire();snapshot("FACULTY-attendance-absent",scene.getRoot());((Button)node("attendance-save")).fire();return null;});
                    waitFor(()->node("attendance-roster-ready")!=null && !((Button)node("attendance-save")).isDisabled());
                    require(context.workflows().attendanceRoster(course.id(),java.time.LocalDate.now().toString()).getFirst().text("attendance_status").equals("ABSENT"),"Absent mark did not persist");
                    fx(()->{((Button)node("home-button")).fire();return null;});waitFor(()->node("dashboard-ready")!=null);
                }
                fx(()->{scene.getRoot().resize(980,720);scene.getRoot().layout();verifyDashboard(user.role());snapshot(user.role()+"-compact",scene.getRoot());return null;});
            }
            System.out.println("PORTAL_LAYOUT_OK: all roles, no sidebars, shared search, teal colors, welcome banners and role-scoped cards");
        } finally { Background.shutdown();Platform.exit(); }
    }
    /** Assert the current role dashboard contract and its compact breakpoint. */
    private static void verifyDashboard(Role role) {
        require(((BorderPane)scene.getRoot()).getLeft()==null,"Sidebar still present");
        require(node("dashboard-ready").lookup(".portal-hero")!=null,"Campus welcome banner missing");
        Region hero=(Region)node("dashboard-ready").lookup(".portal-hero");
        require(hasCampusPhoto(hero),"Official campus photograph did not load in the role dashboard");
        int expectedStats = role == Role.STUDENT ? 3 : 4;
        require(node("dashboard-ready").lookupAll(".portal-stat-card").size()==expectedStats,"Role summary cards missing");
        require(node("dashboard-ready").lookup(".portal-attendance-summary")==null,"Attendance percentage should not appear in the dashboard header");
        require(node("dashboard-ready").lookup(".portal-sidebar")==null,"Dashboard sidebar should be removed");
        require(node("dashboard-ready").lookup(".mini-profile")==null,"Dashboard right sidebar should be removed");
        for(String key:List.of("question-papers","resource_bookings","doubts","doubt_replies","project_milestones","notifications","leave_requests","projects","library_borrowings","library_reservations","faculty_substitutions","timetable_overrides")) require(node("tile-"+key)==null,"Removed tile present: "+key);
        require(node("tile-project_members")!=null,"Project members missing");
        if(role==Role.FACULTY) {
            require(node("tile-reports")==null && node("tile-faculty_appointments")==null,"Teacher-only removals still present");
            require(node("tile-faculty_salaries")!=null && node("tile-student_fees")==null,"Teacher finance visibility incorrect");
        }
        if(role==Role.STUDENT) for(String key:List.of("seating_allotments","exam_schedule","hall_tickets","student_fees")) require(node("tile-"+key)!=null,"Student tile missing: "+key);
        if(role==Role.STUDENT) require(node("tile-faculty_salaries")==null,"Student can see salaries");
        for(String style:List.of(".module-tile",".quick-tile")) for(Node tile:scene.getRoot().lookupAll(style)) {
            SVGPath icon=(SVGPath)tile.lookup(".icon-stroke");
            Color expected=Color.web("#078e89");
            require(expected.equals(icon.getStroke()),"Unexpected dashboard icon colour: "+tile.getId());
            require(tile.getLayoutX()+tile.getLayoutBounds().getMaxX()<=tile.getParent().getLayoutBounds().getWidth()+1,"Tile overflows grid: "+tile.getId());
        }
    }
    private static boolean hasCampusPhoto(Region region) {
        Node photo=region.lookup(".campus-photo-layer");
        return photo instanceof ImageView view && !view.getImage().isError() && view.getImage().getWidth()>0;
    }
    private static Node node(String id) {return scene.getRoot().lookup("#"+id);}
    private static void waitFor(BooleanSupplier ready) throws Exception {
        long end=System.currentTimeMillis()+15000;
        while(!fx(ready::getAsBoolean)) {if(System.currentTimeMillis()>end)throw new AssertionError("UI state timed out");Thread.sleep(100);}
        Thread.sleep(400);
    }
    private static <T>T fx(Callable<T> call) throws Exception {
        FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return call.call();});
        Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);
    }
    private static void snapshot(String name,Node node) throws Exception {
        node.setOpacity(1);WritableImage image=node.snapshot(null,null);
        BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(png,"png",OUTPUT.resolve(name+".png").toFile());
    }
    private static Map<String,Object> m(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)map.put(pairs[i].toString(),pairs[i+1]);return map;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
