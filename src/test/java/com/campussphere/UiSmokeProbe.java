package com.campussphere;

import com.campussphere.app.AppContext;
import com.campussphere.config.DatabaseConfig;
import com.campussphere.database.Database;
import com.campussphere.repository.JdbcRepository;
import com.campussphere.service.*;
import com.campussphere.controller.*;
import com.campussphere.task.Background;
import com.campussphere.ui.Ui;
import com.campussphere.model.*;
import java.time.LocalDate;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.Region;
import javafx.stage.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;

/** Explicit desktop smoke probe. Test fixtures stay under target, never in the user's campus DB. */
public final class UiSmokeProbe {
    private static AppContext context; private static Stage stage;
    private static final Path OUTPUT=Path.of("target","ui-smoke");
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT); Path databaseFile=OUTPUT.resolve("test-"+UUID.randomUUID()+".db");
        System.setProperty("campussphere.rememberedUserFile",OUTPUT.resolve("remembered-test-user.txt").toAbsolutePath().toString());
        Database database=new Database(new DatabaseConfig("jdbc:sqlite:"+databaseFile.toAbsolutePath(),"","")); database.initialize();
        JdbcRepository repo=new JdbcRepository(database); Session session=new Session(); context=new AppContext(repo,session,new AuthService(repo,session),new CampusService(repo,session),new WorkflowService(repo,session));
        // Clear only this newly created test database's catalog to exercise the create form.
        repo.execute("DELETE FROM departments");
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->fail(error));
        Platform.startup(()->{
            try {
                stage=new Stage(); stage.setTitle("Namma Sphere · UI verification");
                Scene scene=new Scene(new LoginController(context,UiSmokeProbe::signedIn).view(),1440,900); Ui.style(scene); stage.setScene(scene); stage.show();
                later(1200,()->{
                    snapshot("01-setup"); verifyCampusPhoto(stage.getScene().lookup(".login-story"),"login welcome panel");
                    require(stage.getScene().getRoot().getLayoutBounds().getHeight()<1200,"Login view exceeded its desktop window height");
                    text("setup-name","UI Test Administrator");text("setup-email","ui-test@campus.edu");text("login-username","ui.test");text("login-password","UiSmokePass!123");button("login-submit").fire();
                });
                later(120000,()->fail(new AssertionError("UI probe timed out")));
            } catch(Throwable error) { fail(error); }
        });
    }
    private static void signedIn() {
        stage.getScene().setRoot(new ShellController(context,()->{}).view());
        later(1500,()->{ snapshot("02-dashboard"); nav("Departments").fire(); later(900,()->{
            snapshot("03-departments"); findButton(stage.getScene().getRoot(),"+ Add department").fire();
            later(800,()->{ Parent dialog=dialog(); ((ComboBox<?>)dialog.lookup("#field-name")).getSelectionModel().selectFirst();((TextField)dialog.lookup("#field-code")).setText("UITEST");snapshotWindow(dialog,"04-create-form");((Button)dialog.lookup("#record-submit")).fire();
                later(1000,()->{
                    if(context.campus().list("departments").size()!=1) throw new AssertionError("Form submission did not persist exactly one department");
                    snapshot("05-saved-department"); nav("Overview").fire(); later(1000,()->{
                        snapshot("06-refreshed-dashboard"); nav("Analytics & reports").fire();
                        later(700,()->{
                            @SuppressWarnings("unchecked") ComboBox<com.campussphere.model.ModuleSpec> modules=(ComboBox<com.campussphere.model.ModuleSpec>)stage.getScene().lookup("#report-module");
                            modules.getItems().stream().filter(m->m.key().equals("departments")).findFirst().ifPresent(modules::setValue);button("report-generate").fire();
                            later(1000,()->{
                                TableView<?> results=(TableView<?>)stage.getScene().lookup("#report-results");if(results.getItems().size()!=1)throw new AssertionError("Grouped report did not match saved department data");snapshot("07-report");
                                portalChecks();
                            });
                        });
                    });
                });
            });
        });});
    }
    private static void portalChecks() {
        Background.run(()->{
            long department=context.campus().list("departments").getFirst().id();
            Row teacher=context.campus().save("faculty",null,values("name","Test Teacher","department_id",department,"designation","Professor","email","teacher@campus.test","qualification","M.Tech","specialization","Computer Science"));
            Row student=context.campus().save("students",null,values("name","Test Student","usn","TEST2026001","department_id",department,"semester",3,"section","A","email","student@campus.test","admission_year",2025,"status","ACTIVE"));
            context.auth().createAccount("Test Teacher","test.teacher","teacher@campus.test","PortalPass!123",Role.FACULTY,teacher.id());
            context.auth().createAccount("Test Student","test.student","student@campus.test","PortalPass!123",Role.STUDENT,student.id());
            Row course=context.campus().save("courses",null,values("code","CS201","name","Object Oriented Programming","credits",4,"department_id",department,"semester",3,"faculty_id",teacher.id(),"capacity",30));
            Row room=context.campus().save("resources",null,values("name","Computing Lab","type","LABORATORY","building","Academic Block","floor",1,"capacity",30,"status","AVAILABLE"));
            context.workflows().enroll(course.id(),student.id());
            context.workflows().markAttendance(course.id(),student.id(),LocalDate.now().toString(),"PRESENT");
            context.workflows().saveTimetable(null,course.id(),teacher.id(),room.id(),LocalDate.now().getDayOfWeek().getValue(),"09:00","10:00");
            context.campus().save("assignments",null,values("course_id",course.id(),"title","Classes and objects","description","Build your first Java class.","due_date",LocalDate.now().plusDays(7).toString()));
            context.campus().save("announcements",null,values("title","Welcome to your campus portal","body","Explore your timetable, study materials and campus activities.","audience","ALL"));
            return true;
        }, ignored->{
            context.auth().logout(); showLogin();
            await(()->node("login-submit")!=null,()->{
                snapshot("08-student-login");
                login("test.student","PortalPass!123");
                await(()->node("dashboard-ready")!=null,UiSmokeProbe::studentChecks);
            });
        },UiSmokeProbe::fail);
    }
    private static void studentChecks() {
        verifyGrid();
        require(node("tile-users")==null,"Student can see account administration");
        require(node("quick-attendance")!=null && node("tile-exam_schedule")!=null,"Student launcher links missing");
        snapshot("10-student-dashboard");
        button("quick-attendance").fire();
        await(()->loadedRows()==1,()->{
            snapshot("11-student-attendance");
            button("header-profile").fire();
            await(()->node("profile-ready")!=null,()->{
                require(((Label)node("profile-usn")).getText().equals("TEST2026001"),"Student profile does not show own USN");
                require(((Label)node("profile-name")).getText().equals("Test Student"),"Wrong profile name");
                snapshot("12-student-profile");
                stage.setWidth(1080); stage.setHeight(720);
                later(500,()->{
                    snapshot("13-profile-compact"); button("home-button").fire();
                    await(()->node("dashboard-ready")!=null,()->{
                        verifyGrid();
                        snapshot("14-dashboard-compact");
                        button("sign-out").fire();
                        await(()->node("login-submit")!=null,()->{
                            snapshot("15-teacher-login-compact");
                            login("test.teacher","PortalPass!123");
                            await(()->node("dashboard-ready")!=null,UiSmokeProbe::teacherChecks);
                        });
                    });
                });
            });
        });
    }
    private static void teacherChecks() {
        require(context.session().requireUser().role()==Role.FACULTY,"Teacher entered wrong workspace");
        require(node("tile-users")==null,"Teacher can see account administration");
        require(node("tile-students")!=null,"Teacher students link is missing");
        stage.setWidth(1440); stage.setHeight(900);
        later(500,()->{
            snapshot("16-teacher-dashboard");
            button("tile-attendance").fire();
            await(()->node("attendance-roster-ready")!=null,()->{
                require(stage.getScene().getRoot().lookupAll(".button").stream().anyMatch(n->n instanceof Button b&&b.getText().contains("attendance")),"Teacher attendance action missing");
                snapshot("17-teacher-attendance");
                button("header-profile").fire();
                await(()->node("profile-ready")!=null,()->{
                    require(((Label)node("profile-name")).getText().equals("Test Teacher"),"Teacher profile mismatch");
                    require(node("profile-usn")==null,"Teacher profile shows student fields");
                    snapshot("18-teacher-profile");
                    button("sign-out").fire();
                    await(()->node("login-submit")!=null,()->{
                        snapshot("19-administrator-login");
                        login("ui.test","UiSmokePass!123");
                        await(()->node("dashboard-ready")!=null,()->{
                            require(node("tile-users")!=null,"Administrator account tools missing");
                            snapshot("20-administrator-dashboard");
                            System.out.println("UI_SMOKE_OK: setup, CRUD, reporting, automatic database-role login, student/teacher/admin dashboards, attendance, own profiles and compact layouts verified");
                            Background.shutdown();stage.close();Platform.exit();System.exit(0);
                        });
                    });
                });
            });
        });
    }
    private static void showLogin() { stage.getScene().setRoot(new LoginController(context,()->stage.getScene().setRoot(new ShellController(context,UiSmokeProbe::showLogin).view())).view()); }
    private static void login(String username,String password) { text("login-username",username);text("login-password",password);button("login-submit").fire(); }
    private static Node node(String id) { return stage.getScene().lookup("#"+id); }
    private static int loadedRows() { Node table=stage.getScene().lookup(".records-table"); return table instanceof TableView<?> t ? t.getItems().size() : -1; }
    private static void require(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    private static void verifyCampusPhoto(Node panel,String location) {
        require(panel instanceof Region,"Campus image panel missing: "+location);
        Node photo=panel.lookup(".campus-photo-layer");
        require(photo instanceof ImageView view && !view.getImage().isError() && view.getImage().getWidth()>0,
                "Official campus photograph did not load in "+location);
    }
    private static void verifyGrid() {
        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        Node first=node("tile-attendance"), fourth=node("tile-courses");
        require(first!=null && fourth!=null,"Academic tiles missing");
        require(fourth.getBoundsInParent().getMaxX()<=fourth.getParent().getLayoutBounds().getWidth()+1,"Card grid overflows its container");
    }
    private static void await(BooleanSupplier ready,Runnable action) { await(ready,action,System.currentTimeMillis()+15000); }
    private static void await(BooleanSupplier ready,Runnable action,long deadline) {
        if(ready.getAsBoolean()) later(250,action);
        else if(System.currentTimeMillis()>deadline) fail(new AssertionError("Timed out waiting for UI state"));
        else later(100,()->await(ready,action,deadline));
    }
    private static Map<String,Object> values(Object... pairs) { Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),pairs[i+1]);return result; }
    private static Button nav(String name) {
        return button(switch(name) { case "Overview" -> "home-button"; case "Departments" -> "tile-departments"; case "Analytics & reports" -> "tile-reports"; default -> throw new IllegalArgumentException(name); });
    }
    private static Parent dialog() { return Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()).map(w->w.getScene().getRoot()).findFirst().orElseThrow(); }
    private static Button findButton(Parent parent,String text) { return parent.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow(); }
    private static void text(String id,String value) { ((TextField)stage.getScene().lookup("#"+id)).setText(value); }
    private static Button button(String id) { return (Button)stage.getScene().lookup("#"+id); }
    private static void later(long millis,Runnable action) { PauseTransition pause=new PauseTransition(Duration.millis(millis));pause.setOnFinished(e->{try{action.run();}catch(Throwable error){fail(error);}});pause.play(); }
    private static void snapshot(String name) { snapshotWindow(stage.getScene().getRoot(),name); }
    private static void snapshotWindow(Parent node,String name) {
        try { node.applyCss();node.layout();WritableImage image=node.snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);PixelReader reader=image.getPixelReader();for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,reader.getArgb(x,y));ImageIO.write(png,"png",OUTPUT.resolve(name+".png").toFile()); }
        catch(Exception error){throw new RuntimeException(error);}
    }
    private static void fail(Throwable error) { error.printStackTrace();Background.shutdown();Platform.exit();System.exit(1); }
}
