package com.campussphere.ui;

import com.campussphere.exception.CampusException;
import javafx.animation.FadeTransition;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

public final class Ui {
    private Ui() { }
    public static void style(Scene scene) { scene.getStylesheets().add(Ui.class.getResource("/css/campus.css").toExternalForm()); }
    public static Label label(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); return label; }
    public static HBox brand() {
        StackPane symbol = Icons.badge("graduation", 28); symbol.getStyleClass().add("brand-crest");
        HBox wordmark = new HBox(0, label("Namma", "brand-name"), label("Sphere", "brand-accent"));
        VBox text = new VBox(1, wordmark, label("CONNECTED CAMPUS", "brand-caption"));
        HBox box = new HBox(10, symbol, text); box.setAlignment(Pos.CENTER_LEFT); return box;
    }
    public static Button button(String title, String style, Runnable action) {
        Button button = new Button(title); button.getStyleClass().add(style); button.setOnAction(event -> action.run());
        button.setOnMousePressed(event -> { button.setScaleX(.98); button.setScaleY(.98); });
        button.setOnMouseReleased(event -> { button.setScaleX(1); button.setScaleY(1); }); return button;
    }
    public static Region spacer() { Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); VBox.setVgrow(spacer, Priority.ALWAYS); return spacer; }
    public static VBox card(Node... nodes) { VBox box = new VBox(14, nodes); box.getStyleClass().add("card"); return box; }
    public static void animate(Node node) { FadeTransition fade = new FadeTransition(Duration.millis(170), node); fade.setFromValue(.3); fade.setToValue(1); fade.play(); }
    public static void dialogStyle(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(Ui.class.getResource("/css/campus.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("campus-dialog");
    }
    public static void info(String title, String message) { Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(message); dialogStyle(alert); alert.showAndWait(); }
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(title); dialogStyle(alert); return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    public static String safeMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null) { if (cause instanceof CampusException) return cause.getMessage(); cause = cause.getCause(); }
        java.util.logging.Logger.getLogger("Namma Sphere").log(java.util.logging.Level.WARNING, "Application operation failed", error);
        return "We could not complete that action. Check your entries and database connection, then try again.";
    }
    public static void error(Throwable error) { info("Action needs attention", safeMessage(error)); }
    public static ScrollPane scroll(Node content) { ScrollPane pane = new ScrollPane(content); pane.setFitToWidth(true); pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); return pane; }
}
