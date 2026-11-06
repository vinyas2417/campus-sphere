package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.*;
import com.campussphere.task.Background;
import com.campussphere.ui.*;
import javafx.geometry.*;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Role-aware campus dashboard for the existing campus workflows. */
public final class DashboardController {
    private final AppContext context;
    private final Consumer<String> navigate;
    private final VBox content = new VBox(24);
    private final Set<String> available = new HashSet<>();

    public DashboardController(AppContext context, Consumer<String> navigate) {
        this.context = context; this.navigate = navigate;
    }

    public Parent view() {
        content.getStyleClass().add("dashboard-page");
        context.campus().modules().forEach(module -> available.add(module.key()));
        if(context.session().requireUser().role()!=Role.FACULTY) available.add("reports"); load(); return Ui.scroll(content);
    }

    private void load() {
        User actor = context.session().requireUser();
        content.setId("dashboard-loading");
        content.getChildren().setAll(new ProgressIndicator());
        Background.run(() -> {
            Map<String, List<Row>> data = new HashMap<>();
            List<String> keys = new ArrayList<>(List.of("courses", "timetable", "resources", "announcements"));
            if (actor.role() == Role.STUDENT) keys.addAll(List.of("students", "departments", "course_enrollments", "attendance", "assignments", "exam_schedule", "student_fees"));
            else if (actor.role() == Role.FACULTY) keys.addAll(List.of("faculty", "departments", "attendance", "assignments", "students", "faculty_salaries"));
            else keys.addAll(List.of("students", "faculty", "departments", "users"));
            for (String key : keys)
                if (available.contains(key)) data.put(key, context.campus().list(key));
            return data;
        }, data -> { if (context.session().user().filter(actor::equals).isPresent()) render(data); },
            error -> content.getChildren().setAll(Ui.label(Ui.safeMessage(error), "error-text"), Ui.button("Try again", "primary", this::load)));
    }

    private void render(Map<String, List<Row>> data) {
        User user = context.session().requireUser();
        boolean teacher = user.role() == Role.FACULTY, admin = user.role() == Role.ADMIN;
        String role = admin ? "ADMINISTRATOR" : teacher ? "TEACHER" : "STUDENT";
        VBox intro = new VBox(5, Ui.label(role + " PORTAL", "eyebrow"),
                Ui.label("Your workspace", "page-title"), Ui.label("Your campus, connected.", "muted"));
        Button refresh = Ui.button("Refresh", "secondary", this::load); refresh.setGraphic(Icons.icon("refresh", 15));
        HBox heading = new HBox(16, intro, Ui.spacer());
        heading.getChildren().add(refresh); heading.setAlignment(Pos.CENTER_LEFT);
        VBox main = new VBox(24); main.setMinWidth(0); HBox.setHgrow(main, Priority.ALWAYS);
        main.getChildren().add(heroBanner(user, data, teacher, admin));
        main.getChildren().add(summaryCards(data, teacher, admin));
        GridPane quick = new GridPane(); quick.setHgap(14);
        String[] quickKeys = admin ? new String[]{"students", "timetable", "reports", "users", "courses"} : teacher ?
                new String[]{"attendance", "timetable", "assignments", "study_materials", "students"} :
                new String[]{"study_materials", "library_resources", "attendance", "assignments", "exam_schedule"};
        String[] quickTitles = admin ? new String[]{"Students", "Time Table", "Reports", "User Accounts", "Courses"} : teacher ?
                new String[]{"Mark Attendance", "Time Table", "Assignments", "Study Materials", "My Students"} :
                new String[]{"Study Materials", "E-Library", "Attendance", "Assignments", "Exam Timetable"};
        String[] quickThemes = {"blue", "blue", "purple", "green", "neutral"};
        for (int i = 0; i < quickKeys.length; i++) {
            ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(100.0 / quickKeys.length); col.setHgrow(Priority.ALWAYS); col.setMinWidth(0);
            quick.getColumnConstraints().add(col);
            Button tile = tile(quickKeys[i], quickTitles[i], quickThemes[i], true);
            tile.setMaxWidth(Double.MAX_VALUE); quick.add(tile, i, 0);
        }
        VBox shortcuts = new VBox(14, sectionHeading("QUICK ACCESS", "gold", "grid"), quick);
        shortcuts.getStyleClass().add("quick-section"); main.getChildren().add(shortcuts);
        if (!teacher && !admin) main.getChildren().add(studentCourses(data));
        main.getChildren().add(section("ACADEMICS", "blue", "graduation", new String[][]{
                {"attendance", teacher ? "Mark Attendance" : "Attendance"}, {"timetable", "Time Table"},
                {"reports", "Reports"}, {"courses", teacher ? "My Courses" : "Courses"},
                {"course_enrollments", "Course Enrollment"}, {"study_materials", "Study Materials"},
                {"assignments", "Assignments"}}));
        main.getChildren().add(section("EXAMINATIONS", "purple", "exam", new String[][]{
                {"seating_allotments", "Seating Allotment"}, {"exam_schedule", "Exam Timetable"}, {"hall_tickets", "Hall Ticket"}}));
        main.getChildren().add(section("CAMPUS", "coral", "campus", new String[][]{
                {"events", "Events"}, {"resources", "Campus Resources"},
                {"library_resources", "E-Library"}, {"event_registrations", "Event Registrations"}}));
        main.getChildren().add(section("COMMUNITY", "gold", "users", new String[][]{
                {"project_members", "View Project Members"},
                {"announcements", "Announcements"}, {"helpdesk_tickets", "Help Desk"}}));
        main.getChildren().add(section("REQUESTS & SUPPORT", "green", "clipboard", new String[][]{
                {"permission_requests", "Permissions"},
                {"faculty_appointments", teacher ? "Appointments" : "Meet a Teacher"}}));
        main.getChildren().add(section("FINANCE", "neutral", "wallet", new String[][]{
                {"student_fees", "Fees"}, {"faculty_salaries", "Faculty Salary"}}));
        if (admin) main.getChildren().add(section("ADMINISTRATION", "maroon", "shield", new String[][]{
                {"students", "Students"}, {"faculty", "Teachers"}, {"departments", "Departments"},
                {"class_teacher_assignments", "Class Teachers"}, {"users", "User Accounts"}, {"activity_logs", "Activity Logs"}}));
        else if (teacher) main.getChildren().add(section("MY TEACHING", "maroon", "presentation", new String[][]{
                {"students", "My Students"}, {"class_teacher_assignments", "Class Teacher"}, {"faculty", "Faculty Directory"}}));
        HBox body = new HBox(main); body.setAlignment(Pos.TOP_LEFT); body.getStyleClass().add("portal-dashboard");
        content.getChildren().setAll(heading, body, Ui.label("Namma Sphere  /  " + role + " PORTAL  /  One campus. One intelligent platform.", "caption"));
        content.setId("dashboard-ready"); Ui.animate(content);
    }

    private VBox heroBanner(User user, Map<String,List<Row>> data, boolean teacher, boolean admin) {
        String first = user.name().trim().split("\\s+")[0];
        String subtitle = admin ? "Your campus at a glance. Keep every department moving." : teacher ?
                "Your classes, your students, and everything in between." : "Your classes, campus life, and next steps — all in one place.";
        Label welcome = Ui.label("Welcome back, " + first + ".", "portal-hero-title");
        welcome.setWrapText(true);
        Label message = Ui.label(subtitle, "portal-hero-copy");
        message.setWrapText(true);
        VBox copy = new VBox(9, Ui.label("YOUR CAMPUS, EVERY DAY", "portal-hero-kicker"), welcome, message);
        if (!teacher && !admin) {
            String context = studentContext(user, data);
            if (!context.isBlank()) { Label details = Ui.label(context, "portal-hero-copy"); details.setWrapText(true); copy.getChildren().add(details); }
        }
        copy.getStyleClass().add("portal-hero-copy-panel");
        copy.setMaxWidth(560);
        copy.setMaxHeight(Region.USE_PREF_SIZE);
        copy.setAlignment(Pos.CENTER_LEFT);
        Label badge = Ui.label(teacher ? "TEACHER PORTAL" : admin ? "ADMINISTRATION" : "STUDENT PORTAL", "portal-hero-badge");
        StackPane banner = CampusImage.frame(copy, "portal-hero");
        StackPane.setAlignment(copy, Pos.CENTER_LEFT);
        StackPane.setMargin(copy, new Insets(28, 36, 28, 36));
        copy.translateXProperty().bind(banner.widthProperty().multiply(.09));
        return new VBox(16, badge, banner);
    }

    private String studentContext(User user, Map<String,List<Row>> data) {
        Row profile = rows(data,"students").stream().filter(row -> row.number("user_id") == user.id()).findFirst().orElse(null);
        if (profile == null) return "";
        String department = rows(data,"departments").stream().filter(row -> row.id() == profile.number("department_id"))
                .map(row -> row.text("name")).findFirst().orElse("");
        String details = java.util.stream.Stream.of(department,
                profile.text("semester").isBlank() ? "" : "Semester " + profile.text("semester"),
                profile.text("section").isBlank() ? "" : "Section " + profile.text("section"))
                .filter(value -> !value.isBlank()).collect(Collectors.joining("  ·  "));
        Row next = rows(data,"timetable").stream().filter(row -> row.number("day_of_week") == LocalDate.now().getDayOfWeek().getValue())
                .filter(row -> row.text("start_time").compareTo(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) >= 0)
                .min(Comparator.comparing(row -> row.text("start_time"))).orElse(null);
        String nextText = next == null ? "No more classes scheduled today" : "Next class " + next.text("start_time") + " · " + courseName(data,next.number("course_id"));
        return (details.isBlank() ? "" : details + "\n") + nextText;
    }

    private String courseName(Map<String,List<Row>> data, long courseId) {
        return rows(data,"courses").stream().filter(row -> row.id() == courseId).map(row -> row.text("name")).findFirst().orElse("Class");
    }

    private Node summaryCards(Map<String, List<Row>> data, boolean teacher, boolean admin) {
        List<Node> stats = new ArrayList<>();
        if (admin) {
            stats.add(stat("Students", value(data,"students"), "", "users"));
            stats.add(stat("Teachers", value(data,"faculty"), "", "presentation"));
            stats.add(stat("Courses", value(data,"courses"), "", "graduation"));
            stats.add(stat("Campus resources", value(data,"resources"), "", "campus"));
        } else if (teacher) {
            stats.add(stat("Assigned courses", value(data,"courses"), "", "graduation"));
            stats.add(stat("Enrolled students", value(data,"students"), "Across your classes", "users"));
            stats.add(stat("Attendance records", value(data,"attendance"), "Available to review", "attendance"));
            stats.add(stat("Assignments", value(data,"assignments"), "Across assigned courses", "clipboard"));
        } else {
            List<Row> attendance = rows(data,"attendance");
            long attended = attendance.stream().filter(r -> Set.of("PRESENT","LATE").contains(r.text("status").toUpperCase(Locale.ROOT))).count();
            String percentage = attendance.isEmpty() ? "—" : Math.round(100.0 * attended / attendance.size()) + "%";
            long enrolled = rows(data,"course_enrollments").size();
            long deadlines = rows(data,"assignments").stream().filter(r -> validOnOrAfter(r.text("due_date"), LocalDate.now())).count();
            List<Row> fees = rows(data,"student_fees");
            double balance = fees.stream().mapToDouble(r -> Math.max(0, r.decimal("amount") - r.decimal("paid_amount"))).sum();
            stats.add(stat("Active courses", Long.toString(enrolled), "Current enrollments", "book"));
            stats.add(stat("Upcoming deadlines", Long.toString(deadlines), "Assignments due from today", "clipboard"));
            stats.add(stat("Fees outstanding", fees.isEmpty() ? "—" : String.format(Locale.ROOT, "₹%,.2f", balance),
                    fees.isEmpty() ? "No fee records" : "Balance from fee records", "wallet"));
        }
        HBox cards = new HBox(12); cards.setAlignment(Pos.CENTER_LEFT); cards.setFillHeight(true);
        for (Node stat : stats) { HBox.setHgrow(stat, Priority.ALWAYS); cards.getChildren().add(stat); }
        return cards;
    }

    private VBox stat(String title, String value, String detail, String icon) {
        VBox card = new VBox(8, new HBox(8, Icons.icon(icon,18), Ui.label(title,"portal-stat-label")),
                Ui.label(value,"portal-stat-value"), Ui.label(detail,"portal-stat-detail"));
        card.getStyleClass().add("portal-stat-card"); card.setMaxWidth(Double.MAX_VALUE); return card;
    }
    private String value(Map<String,List<Row>> data, String key) { return Integer.toString(rows(data,key).size()); }
    private boolean validOnOrAfter(String raw, LocalDate date) {
        try { return !raw.isBlank() && !LocalDate.parse(raw).isBefore(date); } catch (RuntimeException ignored) { return false; }
    }

    private Node studentCourses(Map<String,List<Row>> data) {
        Map<Long,Row> byId = new HashMap<>();
        rows(data,"courses").forEach(course -> byId.put(course.id(),course));
        List<Row> courses = rows(data,"course_enrollments").stream().map(enrollment -> byId.get(enrollment.number("course_id")))
                .filter(Objects::nonNull).limit(6).toList();
        VBox cards = new VBox(10);
        if (courses.isEmpty()) cards.getChildren().add(note("No enrolled courses yet", "Your course list will appear here when enrollment records are available."));
        for (Row course : courses) {
            VBox description = new VBox(5, Ui.label(course.text("name"),"card-title"),
                    Ui.label(course.text("code") + "  ·  " + course.number("credits") + " credits", "caption"));
            HBox row = new HBox(14, Icons.badge("graduation",20), description, Ui.spacer(), Ui.button("Open", "link-button", () -> navigate.accept("courses")));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("student-course-row"); cards.getChildren().add(row);
        }
        VBox section = new VBox(13, sectionHeading("MY COURSES","blue","graduation"), cards);
        section.getStyleClass().add("portal-content-card"); return section;
    }

    private VBox portalSidebar(User user, boolean teacher, boolean admin) {
        String label = admin ? "ADMIN" : teacher ? "TEACHER" : "STUDENT";
        VBox sidebar = new VBox(11); sidebar.getStyleClass().add("portal-sidebar");
        HBox wordmark = new HBox(0, Ui.label("Campus", "portal-sidebar-brand"), Ui.label("Sphere", "portal-sidebar-brand-accent"));
        sidebar.getChildren().addAll(wordmark, Ui.label(label + " WORKSPACE", "portal-sidebar-caption"));
        sidebar.getChildren().add(sidebarLink("Home", "home", "overview", true));
        sidebar.getChildren().add(Ui.label("YOUR WORKSPACE", "portal-sidebar-caption"));
        String[][] links = admin ? new String[][]{{"Students", "students", "users"}, {"Teachers", "faculty", "presentation"},
                {"Courses", "courses", "book"}, {"Timetable", "timetable", "calendar"}, {"Reports", "reports", "chart"}, {"User accounts", "users", "shield"}} :
                teacher ? new String[][]{{"My courses", "courses", "book"}, {"Attendance", "attendance", "check"}, {"Assignments", "assignments", "clipboard"},
                        {"Timetable", "timetable", "calendar"}, {"My students", "students", "users"}} :
        new String[][]{{"My courses", "courses", "book"}, {"Timetable", "timetable", "calendar"}, {"Attendance", "attendance", "check"},
                        {"Assignments · upload", "assignments", "file"}, {"Study materials", "study_materials", "book"}, {"Exam timetable", "exam_schedule", "calendar"},
                        {"Hall tickets", "hall_tickets", "ticket"}, {"Fees", "student_fees", "wallet"}};
        for (String[] link : links) if (link[1].equals("overview") || available.contains(link[1]))
            sidebar.getChildren().add(sidebarLink(link[0], link[2], link[1], false));
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS); sidebar.getChildren().add(spacer);
        sidebar.getChildren().add(sidebarLink("My profile", "users", "profile", false));
        return sidebar;
    }

    private Button sidebarLink(String label, String icon, String destination, boolean active) {
        Button link = Ui.button(label, "portal-nav-link", () -> navigate.accept(destination));
        if (active) link.getStyleClass().add("portal-nav-active");
        link.setGraphic(Icons.icon(icon, 17)); link.setMaxWidth(Double.MAX_VALUE); link.setAlignment(Pos.CENTER_LEFT); return link;
    }

    private Node sectionHeading(String text, String theme, String icon) {
        Region accent = new Region(); accent.getStyleClass().add("section-accent"); accent.setMinSize(4, 23); accent.setMaxSize(4, 23);
        HBox pill = new HBox(8, Icons.icon(icon, 16), Ui.label(text, "section-label"));
        pill.setAlignment(Pos.CENTER_LEFT); pill.getStyleClass().add("section-pill");
        HBox heading = new HBox(12, accent, pill); heading.setAlignment(Pos.CENTER_LEFT); heading.getStyleClass().add("theme-neutral"); return heading;
    }

    private VBox section(String name, String theme, String icon, String[][] items) {
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(14); grid.setMinWidth(0);
        List<Button> tiles = new ArrayList<>();
        for (String[] item : items) if (available.contains(item[0])) {
            Button button = tile(item[0], item[1], theme, false);
            tiles.add(button);
        }
        arrangeTiles(grid, tiles, 4);
        grid.widthProperty().addListener((observable, oldWidth, width) -> {
            int columns = Math.max(2, Math.min(4, (int) ((width.doubleValue() + 14) / 165)));
            if (grid.getColumnConstraints().size() != columns) arrangeTiles(grid, tiles, columns);
        });
        VBox section = new VBox(15, sectionHeading(name, theme, icon), grid); section.getStyleClass().add("module-section"); return section;
    }

    private void arrangeTiles(GridPane grid, List<Button> tiles, int columns) {
        grid.getChildren().clear(); grid.getColumnConstraints().clear();
        for (int index = 0; index < columns; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns); column.setMinWidth(0); column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < tiles.size(); index++) grid.add(tiles.get(index), index % columns, index / columns);
    }

    private Button tile(String key, String title, String theme, boolean quick) {
        Button button = Ui.button("", quick ? "quick-tile" : "module-tile", () -> navigate.accept(key));
        button.setId((quick ? "quick-" : "tile-") + key); button.getStyleClass().add("theme-neutral");
        button.setAccessibleText(title); button.setTooltip(new Tooltip(title));
        Label label = Ui.label(title, quick ? "quick-tile-label" : "tile-label"); label.setWrapText(true);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER); label.setAlignment(Pos.CENTER); label.setMaxWidth(175);
        VBox graphic = new VBox(11, quick ? Icons.icon(Icons.forModule(key), 29) : Icons.badge(Icons.forModule(key), 25), label);
        graphic.setAlignment(quick ? Pos.CENTER_LEFT : Pos.CENTER);
        if (quick) { label.setAlignment(Pos.CENTER_LEFT); label.setTextAlignment(javafx.scene.text.TextAlignment.LEFT); }
        graphic.setMouseTransparent(true); button.setGraphic(graphic);
        button.setMinWidth(0); button.setPrefWidth(151); button.setPrefHeight(quick ? 121 : 126); button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); return button;
    }

    private VBox sideRail(Map<String, List<Row>> data, User user) {
        String initials = Arrays.stream(user.name().trim().split("\\s+")).limit(2).map(part -> part.substring(0, 1)).collect(Collectors.joining()).toUpperCase(Locale.ROOT);
        VBox person = new VBox(10, Ui.label(initials, "mini-profile-avatar"), Ui.label(user.name(), "mini-profile-name"),
                Ui.label(user.role() == Role.FACULTY ? "Teacher" : user.role() == Role.ADMIN ? "Administrator" : "Student", "mini-profile-role"));
        person.setAlignment(Pos.CENTER); person.getStyleClass().add("mini-profile");
        Button profile = Ui.button("View my profile", "profile-link", () -> navigate.accept("profile"));
        profile.setGraphic(Icons.icon("arrow", 15)); profile.setContentDisplay(ContentDisplay.RIGHT); person.getChildren().add(profile);
        VBox classes = Ui.card(Ui.label("Today's classes", "section-title"), Ui.label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), "caption"));
        Map<Long, String> courses = rows(data, "courses").stream().collect(Collectors.toMap(Row::id, r -> r.text("name")));
        Map<Long, String> rooms = rows(data, "resources").stream().collect(Collectors.toMap(Row::id, r -> r.text("name")));
        List<Row> today = rows(data, "timetable").stream().filter(row -> row.number("day_of_week") == LocalDate.now().getDayOfWeek().getValue())
                .sorted(Comparator.comparing(row -> row.text("start_time"))).limit(3).toList();
        if (today.isEmpty()) classes.getChildren().add(note("Your day is clear", "No regular classes scheduled for today."));
        for (Row row : today) classes.getChildren().add(railItem(row.text("start_time") + " - " + row.text("end_time"),
                courses.getOrDefault(row.number("course_id"), "Class"), rooms.getOrDefault(row.number("resource_id"), "Room to be assigned")));
        classes.getChildren().add(Ui.button("Open time table", "link-button", () -> navigate.accept("timetable")));
        VBox exams = Ui.card(Ui.label("Upcoming exams", "section-title"));
        List<Row> upcoming = rows(data,"exam_schedule").stream().filter(row -> validOnOrAfter(row.text("date"),LocalDate.now()))
                .sorted(Comparator.comparing((Row row) -> row.text("date")).thenComparing(row -> row.text("start_time"))).limit(3).toList();
        if (upcoming.isEmpty()) exams.getChildren().add(note("No upcoming exams", "Published exam dates will appear here."));
        for (Row row : upcoming) exams.getChildren().add(railItem(row.text("date") + " · " + row.text("start_time"),
                row.text("title"), courses.getOrDefault(row.number("course_id"), "Course")));
        if (!upcoming.isEmpty()) exams.getChildren().add(Ui.button("Open exam timetable", "link-button", () -> navigate.accept("exam_schedule")));
        VBox notices = Ui.card(Ui.label("Notice board", "section-title"));
        List<Row> announcements = rows(data, "announcements").stream().sorted(Comparator.comparing((Row row) -> row.text("created_at")).reversed()).limit(2).toList();
        if (announcements.isEmpty()) notices.getChildren().add(note("All caught up", "Campus announcements will appear here."));
        for (Row row : announcements) notices.getChildren().add(railItem("CAMPUS UPDATE", row.text("title"), shorten(row.text("body"))));
        notices.getChildren().add(Ui.button("All announcements", "link-button", () -> navigate.accept("announcements")));
        return user.role() == Role.STUDENT ? new VBox(18, person, classes, exams, notices) : new VBox(18, person, classes, notices);
    }

    private VBox railItem(String time, String title, String detail) {
        Label name = Ui.label(title, "card-title"); name.setWrapText(true);
        Label description = Ui.label(detail, "caption"); description.setWrapText(true);
        VBox item = new VBox(6, Ui.label(time, "eyebrow"), name, description); item.getStyleClass().add("activity-item"); return item;
    }
    private VBox note(String title, String description) {
        Label body = Ui.label(description, "muted"); body.setWrapText(true);
        VBox note = new VBox(8, Ui.label(title, "card-title"), body); note.getStyleClass().add("rail-note"); return note;
    }
    private String shorten(String text) { return text.length() > 120 ? text.substring(0, 117) + "..." : text; }
    private List<Row> rows(Map<String, List<Row>> data, String key) { return data.getOrDefault(key, List.of()); }
}
