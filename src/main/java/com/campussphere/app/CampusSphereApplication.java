package com.campussphere.app;

import com.campussphere.config.DatabaseConfig;
import com.campussphere.database.Database;
import com.campussphere.repository.JdbcRepository;
import com.campussphere.service.*;
import com.campussphere.controller.*;
import com.campussphere.task.Background;
import com.campussphere.ui.Ui;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class CampusSphereApplication extends Application {
    private Stage stage;
    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("Namma Sphere · Smart Campus Integrated Management System");
        stage.setMinWidth(1080); stage.setMinHeight(720);
        VBox loading = new VBox(20, Ui.brand(), new ProgressIndicator(), Ui.label("Preparing your campus workspace…", "muted"));
        loading.setAlignment(javafx.geometry.Pos.CENTER);
        Scene scene = new Scene(loading, 1440, 900); Ui.style(scene); stage.setScene(scene); stage.show();
        Background.run(() -> {
            Database database = new Database(DatabaseConfig.fromEnvironment()); database.initialize();
            JdbcRepository repo = new JdbcRepository(database); Session session = new Session();
            return new AppContext(repo, session, new AuthService(repo, session), new CampusService(repo, session), new WorkflowService(repo, session));
        }, this::showLogin, failure -> {
            loading.getChildren().setAll(Ui.brand(), Ui.label("Your database could not be opened", "page-title"),
                Ui.label("Check the database configuration and write access, then restart Namma Sphere.", "muted"));
            Ui.error(failure);
        });
    }
    private void showLogin(AppContext context) {
        stage.getScene().setRoot(new LoginController(context, () -> showShell(context)).view());
        System.getLogger("Namma Sphere").log(System.Logger.Level.INFO,"Namma Sphere is running. Database initialized; sign-in/setup window is open.");
    }
    private void showShell(AppContext context) {
        stage.getScene().setRoot(new ShellController(context, () -> showLogin(context)).view());
    }
    @Override public void stop() { Background.shutdown(); }
    public static void main(String[] args) { launch(args); }
}
