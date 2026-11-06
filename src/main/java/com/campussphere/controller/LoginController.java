package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.task.Background;
import com.campussphere.ui.Icons;
import com.campussphere.ui.CampusImage;
import com.campussphere.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LoginController {
    private final Path rememberedUser = Path.of(System.getProperty("campussphere.rememberedUserFile", "data/remembered-user.txt"));
    private final AppContext context;
    private final Runnable authenticated;
    private final VBox form = new VBox(16);
    private final Label status = Ui.label("", "login-status");
    private boolean setup;
    private String enteredUsername = "";
    private boolean rememberUsername;

    public LoginController(AppContext context, Runnable authenticated) {
        this.context = context;
        this.authenticated = authenticated;
    }

    public Parent view() {
        form.getStyleClass().add("login-form");
        form.setMaxWidth(540);
        StackPane story = story();
        VBox panel = new VBox(form);
        panel.getStyleClass().add("login-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(36, 42, 36, 42));
        panel.setMinWidth(560);
        panel.setFillWidth(true);
        ScrollPane panelScroll = Ui.scroll(panel);
        panelScroll.getStyleClass().add("login-scroll");
        panelScroll.setFitToHeight(true);
        panelScroll.setMinWidth(560);
        panelScroll.setPrefWidth(700);
        HBox.setHgrow(panelScroll, Priority.ALWAYS);

        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(32, 32);
        form.getChildren().setAll(loading, Ui.label("Opening your campus portal...", "muted"));
        loadPortal();
        HBox root = new HBox(story, panelScroll);
        root.getStyleClass().add("login-root");
        return root;
    }

    private void loadPortal() {
        Background.run(context.auth()::needsSetup, first -> {
            setup = first;
            if (!setup) {
                try { if (Files.exists(rememberedUser)) enteredUsername = Files.readString(rememberedUser); }
                catch (IOException ignored) { }
                rememberUsername = !enteredUsername.isBlank();
            }
            buildForm();
        }, failure -> {
            Label message = Ui.label(Ui.safeMessage(failure), "error-text");
            message.setWrapText(true);
            form.getChildren().setAll(Ui.label("We could not open your campus", "page-title"), message,
                Ui.button("Try again", "primary", this::loadPortal));
        });
    }

    private StackPane story() {
        Label headline = Ui.label("Your campus,\nall in one place.", "login-headline");
        headline.setWrapText(true);
        Label description = Ui.label("Namma Sphere brings academics, campus life and administration together in one connected campus platform.", "login-description");
        description.setWrapText(true);
        VBox features = new VBox(24,
            feature("graduation", "Academics at a glance", "Attendance, courses and study materials"),
            feature("calendar", "Every day, organised", "Timetables, events and campus resources"),
            feature("users", "One connected community", "A dedicated space for students and teachers"));
        features.getStyleClass().add("login-story-features");
        VBox about = new VBox(7, Ui.label("About Namma Sphere", "login-story-section-title"),
                Ui.label("An integrated platform for academics, campus life and administration — designed to keep your university journey connected.", "login-story-feature-copy"));
        Label featureTitle = Ui.label("Key features", "login-story-section-title");
        Label footer = Ui.label("NAMMA SPHERE  /  YOUR UNIVERSITY PORTAL", "login-footer");
        footer.setWrapText(true);
        VBox storyContent = new VBox(26, Ui.brand(), Ui.spacer(), Ui.label("LEARN  /  TEACH  /  CONNECT", "login-kicker"),
            headline, description, about, featureTitle, features, Ui.spacer(), footer);
        storyContent.getStyleClass().add("login-story-content");
        StackPane story = CampusImage.frame(storyContent, "login-story");
        story.setPrefWidth(600);
        story.setMinWidth(350);
        features.visibleProperty().bind(story.heightProperty().greaterThanOrEqualTo(760));
        features.managedProperty().bind(features.visibleProperty());
        HBox.setHgrow(story, Priority.ALWAYS);
        return story;
    }

    private HBox feature(String icon, String title, String description) {
        Label copy = Ui.label(description, "login-story-feature-copy");
        copy.setWrapText(true);
        VBox text = new VBox(5, Ui.label(title, "login-story-feature-title"), copy);
        HBox row = new HBox(15, Icons.badge(icon, 22), text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("login-story-feature");
        return row;
    }

    private void buildForm() {
        status.setId("login-error");
        status.setText("");
        status.setVisible(false);
        status.setManaged(false);
        status.setWrapText(true);
        TextField name = new TextField(), email = new TextField(), username = new TextField(enteredUsername);
        name.setId("setup-name"); name.setPromptText("Your full name");
        email.setId("setup-email"); email.setPromptText("you@university.edu");
        username.setId("login-username");
        username.setPromptText(setup ? "Choose an administrator username" : "Username or university email");
        username.textProperty().addListener((observable, oldValue, value) -> enteredUsername = value);
        PasswordField password = new PasswordField();
        password.setId("login-password");
        password.setPromptText(setup ? "At least 6 characters" : "Enter your password");
        TextField visiblePassword = new TextField();
        visiblePassword.setId("login-password-visible");
        visiblePassword.setPromptText(password.getPromptText());
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        visiblePassword.setVisible(false); visiblePassword.setManaged(false);
        CheckBox show = new CheckBox("Show password");
        show.selectedProperty().addListener((observable, oldValue, visible) -> {
            password.setVisible(!visible); password.setManaged(!visible);
            visiblePassword.setVisible(visible); visiblePassword.setManaged(visible);
        });
        CheckBox remember = new CheckBox("Remember username");
        remember.setSelected(rememberUsername);
        remember.selectedProperty().addListener((observable, oldValue, value) -> rememberUsername = value);

        Label title = Ui.label(setup ? "Welcome to your campus" : "Welcome back", "page-title");
        Label subtitle = Ui.label(setup ? "Set up your administrator account to open the campus." : "Sign in with your campus username and password.", "muted");
        subtitle.setWrapText(true);
        form.getChildren().setAll(Ui.label("CAMPUS ACCESS", "eyebrow"), title, subtitle);
        Label portalTitle = Ui.label(setup ? "Administrator setup" : "Secure campus sign in", "portal-heading");
        Label portalDescription = Ui.label(setup ? "This first account manages campus profiles and access." : "Your account type will be verified automatically.", "portal-description");
        portalDescription.setWrapText(true);
        VBox heading = new VBox(4, portalTitle, portalDescription);
        form.getChildren().add(heading);
        if (setup) form.getChildren().addAll(field("Full name", name), field("University email", email));
        form.getChildren().addAll(field(setup ? "Username" : "Username or university email", username),
            field("Password", new VBox(password, visiblePassword)), new HBox(18, show, remember), status);

        String submitTitle = setup ? "Create administrator account" : "Sign in";
        Button submit = Ui.button(submitTitle, "primary", () -> { });
        submit.setGraphic(Icons.icon("arrow", 17));
        submit.setContentDisplay(ContentDisplay.RIGHT); submit.setGraphicTextGap(14);
        submit.setMaxWidth(Double.MAX_VALUE); submit.setDefaultButton(true); submit.setId("login-submit");
        form.getChildren().add(submit);
        if (!setup) form.getChildren().add(Ui.button("Forgot your password?", "link-button", () -> Ui.info("Password recovery",
            "Ask a campus administrator to reset your account password in User accounts. Your student or teacher account is created by your campus administrator.")));
        Label security = Ui.label(setup ? "Student and teacher accounts can be added after setup." : "Use the account provided by your university.", "portal-security");
        security.setWrapText(true); security.setGraphic(Icons.icon("lock", 14)); security.setGraphicTextGap(7);
        form.getChildren().add(security);

        submit.setOnAction(event -> {
            String n = name.getText(), u = username.getText(), e = email.getText(), p = password.getText();
            boolean keep = remember.isSelected();
            if (u == null || u.isBlank() || p == null || p.isBlank()) {
                showStatus("Enter your username and password.", true);
                if (u == null || u.isBlank()) username.requestFocus(); else password.requestFocus();
                return;
            }
            form.setDisable(true);
            submit.setText(setup ? "Creating your account..." : "Signing in...");
            showStatus("Connecting to your campus...", false);
            Background.run(() -> {
                var user = setup ? context.auth().setupAdmin(n, u, e, p) : context.auth().login(u, p);
                try {
                    Path parent = rememberedUser.toAbsolutePath().getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.writeString(rememberedUser, keep ? u : "");
                } catch (IOException ignored) { }
                return user;
            }, user -> authenticated.run(), failure -> {
                form.setDisable(false); submit.setText(submitTitle);
                showStatus(Ui.safeMessage(failure), true);
            });
        });
    }

    private void showStatus(String message, boolean error) {
        status.getStyleClass().removeAll("error-text", "login-loading");
        status.getStyleClass().add(error ? "error-text" : "login-loading");
        status.setText(message); status.setVisible(true); status.setManaged(true);
    }

    private static VBox field(String title, Node control) { return new VBox(7, Ui.label(title, "field-label"), control); }
}
