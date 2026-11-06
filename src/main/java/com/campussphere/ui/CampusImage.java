package com.campussphere.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.Bindings;

import java.net.URL;

/** Shared campus photograph background used by portal welcome panels. */
public final class CampusImage {
    private CampusImage() { }

    public static StackPane frame(Node content, String styleClass) {
        URL asset = CampusImage.class.getResource("/images/campus-building.jpg");
        if (asset == null) return fallback(content, styleClass);
        Image image = new Image(asset.toExternalForm(), false);
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) return fallback(content, styleClass);
        StackPane frame = "portal-hero".equals(styleClass)
                ? new AspectFrame(image.getWidth(), image.getHeight()) : new StackPane();
        frame.getStyleClass().add(styleClass);

        ImageView photo = new ImageView(image);
        photo.getStyleClass().add("campus-photo-layer");
        photo.setPreserveRatio(true);
        photo.setManaged(false);
        DoubleBinding scale = Bindings.createDoubleBinding(() -> {
            double widthScale = frame.getWidth() / image.getWidth();
            double heightScale = frame.getHeight() / image.getHeight();
            return "portal-hero".equals(styleClass) ? Math.min(widthScale, heightScale) : Math.max(widthScale, heightScale);
        }, frame.widthProperty(), frame.heightProperty());
        photo.fitWidthProperty().bind(scale.multiply(image.getWidth()));
        photo.fitHeightProperty().bind(scale.multiply(image.getHeight()));
        photo.translateXProperty().bind(frame.widthProperty().subtract(photo.fitWidthProperty()).divide(2));
        photo.translateYProperty().bind(frame.heightProperty().subtract(photo.fitHeightProperty()).divide(2));
        photo.setMouseTransparent(true);
        StackPane.setAlignment(photo, Pos.CENTER);

        Rectangle overlay = new Rectangle();
        overlay.widthProperty().bind(frame.widthProperty()); overlay.heightProperty().bind(frame.heightProperty());
        overlay.setManaged(false);
        overlay.setFill(Color.rgb(3, 31, 34, .34));
        overlay.setMouseTransparent(true);
        frame.getChildren().addAll(photo, overlay, content);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(frame.widthProperty()); clip.heightProperty().bind(frame.heightProperty());
        if ("portal-hero".equals(styleClass)) { clip.setArcWidth(36); clip.setArcHeight(36); }
        frame.setClip(clip);
        return frame;
    }

    private static StackPane fallback(Node content, String styleClass) {
        StackPane frame = new StackPane(content);
        frame.getStyleClass().add(styleClass);
        return frame;
    }

    /** Gives the dashboard banner a width-dependent height matching the complete source photo. */
    private static final class AspectFrame extends StackPane {
        private final double naturalHeight;

        private AspectFrame(double width, double height) {
            naturalHeight = height;
        }

        private double heightForWidth(double width) {
            Insets insets = getInsets();
            if (width < 0) return naturalHeight + insets.getTop() + insets.getBottom();
            double contentWidth = Math.max(0, width - insets.getLeft() - insets.getRight());
            double heightRatio = width < 640 ? 2.0 / 3.0 : 3.0 / 5.0;
            return contentWidth * heightRatio + insets.getTop() + insets.getBottom();
        }

        @Override protected double computeMinHeight(double width) { return heightForWidth(width); }
        @Override protected double computePrefHeight(double width) { return heightForWidth(width); }
        @Override protected double computeMaxHeight(double width) { return heightForWidth(width); }
    }
}
