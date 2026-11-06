package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.Role;
import com.campussphere.model.Row;
import com.campussphere.model.User;
import com.campussphere.task.Background;
import com.campussphere.ui.Icons;
import com.campussphere.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The signed-in person's profile, using the same scoped service as the record screens. */
public final class ProfileController {
    private final AppContext context;
    private final Runnable changePassword;
    private final VBox content = new VBox(24);

    public ProfileController(AppContext context, Runnable changePassword) {
        this.context = context;
        this.changePassword = changePassword;
        content.getStyleClass().addAll("profile-page", "profile-content");
        content.setMaxWidth(1080);
        content.setMinWidth(0);
    }

    public Parent view() {
        StackPane container = new StackPane(content);
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        load();
        return Ui.scroll(container);
    }

    private void load() {
        User user = context.session().requireUser();
        content.setId("profile-loading");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        VBox loading = new VBox(12, progress, Ui.label("Loading your profile...", "muted"));
        loading.setAlignment(Pos.CENTER);
        loading.setMinHeight(240);
        content.getChildren().setAll(heading(), loading);
        Background.run(() -> loadProfile(user), profile -> {
            if (sameSession(user)) render(profile);
        }, error -> {
            if (!sameSession(user)) return;
            content.setId("profile-error");
            Label message = Ui.label(Ui.safeMessage(error), "error-text");
            message.setWrapText(true);
            content.getChildren().setAll(heading(), Ui.card(message,
                    Ui.button("Try again", "primary", this::load)));
        });
    }

    private boolean sameSession(User user) {
        return context.session().user().filter(user::equals).isPresent();
    }

    private ProfileData loadProfile(User user) {
        if (user.role() == Role.ADMIN) return new ProfileData(user, null, "");
        String module = user.role() == Role.STUDENT ? "students" : "faculty";
        Row row = context.campus().list(module).stream()
                .filter(record -> record.number("user_id") == user.id()).findFirst().orElse(null);
        String department = row == null ? "" : context.campus().list("departments").stream()
                .filter(record -> record.id() == row.number("department_id"))
                .map(record -> record.text("name")).findFirst().orElse("");
        return new ProfileData(user, row, department);
    }

    private HBox heading() {
        Label title = Ui.label("My profile", "display-title");
        Label subtitle = Ui.label("Your campus identity, all in one place.", "muted");
        subtitle.setWrapText(true);
        VBox copy = new VBox(6, title, subtitle);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Button refresh = Ui.button("Refresh", "secondary", this::load);
        refresh.setGraphic(Icons.icon("refresh", 16));
        refresh.setMinWidth(Button.USE_PREF_SIZE);
        HBox heading = new HBox(16, copy, refresh);
        heading.setAlignment(Pos.CENTER_LEFT);
        return heading;
    }

    private void render(ProfileData data) {
        User user = data.user();
        Row profile = data.profile();
        content.getChildren().setAll(heading(), hero(data));

        if (profile != null) {
            if (user.role() == Role.STUDENT) {
                addSection("Academic details", List.of(
                        detail("id", "USN", profile.text("usn")),
                        detail("graduation", "Discipline", data.department()),
                        detail("calendar", "Semester", profile.text("semester")),
                        detail("users", "Section", profile.text("section")),
                        detail("calendar", "Admission year", profile.text("admission_year")),
                        detail("user", "Student status", titleCase(profile.text("status")))));
            } else {
                addSection("Teaching profile", List.of(
                        detail("graduation", "Department", data.department()),
                        detail("briefcase", "Designation", profile.text("designation")),
                        detail("award", "Qualification", profile.text("qualification")),
                        detail("graduation", "Specialization", profile.text("specialization")),
                        detail("clock", "Experience", years(profile.text("experience"))),
                        detail("calendar", "Joining date", date(profile.text("joining_date")))));
            }
            List<Node> contact = new ArrayList<>();
            contact.add(detail("mail", "Email", profile.text("email")));
            contact.add(detail("phone", "Phone", profile.text("phone")));
            if (user.role() == Role.STUDENT) contact.add(detail("calendar", "Date of birth", date(profile.text("dob"))));
            contact.add(detail("location", "Address", profile.text("address")));
            addSection("Contact details", contact);
            Label note = Ui.label("To update your campus details, contact the campus administration.", "profile-note");
            note.setWrapText(true);
            content.getChildren().add(note);
        } else if (user.role() != Role.ADMIN) {
            Label message = Ui.label("Your campus profile is not linked to this account yet. Contact the campus administration to link your "
                    + (user.role() == Role.STUDENT ? "student" : "teacher") + " record.", "muted");
            message.setId("profile-unlinked");
            message.setWrapText(true);
            content.getChildren().add(Ui.card(Ui.label("Profile details unavailable", "card-title"), message));
        }

        addSection("Account", List.of(
                detail("user", "Username", user.username()),
                detail("id", "Account type", roleName(user.role()))));

        Button password = Ui.button("Change password", "primary", changePassword);
        password.setGraphic(Icons.icon("lock", 16));
        Label securityCopy = Ui.label("Manage the password you use to sign in to your campus portal.", "muted");
        securityCopy.setWrapText(true);
        VBox security = Ui.card(Ui.label("Account security", "card-title"), securityCopy, password);
        security.getStyleClass().add("profile-account-action");
        content.getChildren().add(security);
        content.setId("profile-ready");
        Ui.animate(content);
    }

    private Node hero(ProfileData data) {
        String name = data.profile() != null && !data.profile().text("name").isBlank()
                ? data.profile().text("name") : data.user().name();
        Label initials = Ui.label(initials(name), "profile-initials");
        StackPane avatar = new StackPane(initials);
        avatar.getStyleClass().add("profile-avatar");
        avatar.setMinSize(88, 88);
        avatar.setPrefSize(88, 88);
        avatar.setMaxSize(88, 88);
        avatar.setAccessibleText("Profile initials for " + name);
        Label title = Ui.label(name, "profile-name");
        title.setId("profile-name");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        Label role = Ui.label(roleName(data.user().role()).toUpperCase(Locale.ROOT), "profile-role");
        role.setId("profile-role");
        VBox hero = new VBox(13, avatar, title, role);
        if (data.profile() != null && data.user().role() == Role.STUDENT) {
            Label usn = Ui.label(data.profile().text("usn"), "profile-subtitle");
            hero.getChildren().add(usn);
        }
        hero.setAlignment(Pos.CENTER);
        hero.setMaxWidth(370);
        hero.setMinWidth(0);
        hero.setMinHeight(260);
        hero.getStyleClass().add("profile-hero");
        StackPane centered = new StackPane(hero);
        centered.setMinWidth(0);
        return centered;
    }

    private void addSection(String title, List<Node> cards) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMinWidth(0);
        arrange(grid, cards, 1);
        grid.widthProperty().addListener((observable, before, width) -> {
            int columns = width.doubleValue() >= 680 ? 2 : 1;
            if (grid.getColumnConstraints().size() != columns) arrange(grid, cards, columns);
        });
        VBox section = new VBox(14, Ui.label(title, "profile-section-title"), grid);
        section.setMinWidth(0);
        content.getChildren().add(section);
    }

    private void arrange(GridPane grid, List<Node> cards, int columns) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setHgrow(Priority.ALWAYS);
            constraint.setMinWidth(0);
            grid.getColumnConstraints().add(constraint);
        }
        for (int index = 0; index < cards.size(); index++) {
            grid.add(cards.get(index), index % columns, index / columns);
            GridPane.setHgrow(cards.get(index), Priority.ALWAYS);
            GridPane.setFillHeight(cards.get(index), true);
        }
    }

    private Node detail(String iconName, String title, String value) {
        StackPane icon = Icons.badge(iconName, 24);
        icon.getStyleClass().add("profile-detail-icon");
        icon.setMinSize(48, 48);
        icon.setPrefSize(48, 48);
        icon.setMaxSize(48, 48);
        icon.setMouseTransparent(true);
        Label caption = Ui.label(title, "profile-detail-title");
        Label text = Ui.label(value == null || value.isBlank() ? "Not provided" : value, "profile-detail-value");
        text.setId("profile-" + title.toLowerCase(Locale.ROOT).replace(' ', '-'));
        text.setWrapText(true);
        text.setMinWidth(0);
        VBox labels = new VBox(6, caption, text);
        labels.setMinWidth(0);
        HBox.setHgrow(labels, Priority.ALWAYS);
        HBox card = new HBox(17, icon, labels);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("profile-detail-card");
        return card;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "U";
        String[] words = name.trim().split("\\s+");
        String first = words[0].substring(0, words[0].offsetByCodePoints(0, 1));
        String last = words.length > 1 ? words[words.length - 1].substring(0, words[words.length - 1].offsetByCodePoints(0, 1)) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String roleName(Role role) {
        return switch (role) {
            case STUDENT -> "Student";
            case FACULTY -> "Teacher";
            case ADMIN -> "Administrator";
        };
    }

    private static String titleCase(String value) {
        return value.isBlank() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String years(String value) {
        return value.isBlank() ? "" : value + (value.equals("1") ? " year" : " years");
    }

    private static String date(String value) {
        if (value.isBlank()) return "";
        try {
            return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private record ProfileData(User user, Row profile, String department) { }
}
