package com.campussphere.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Small, resolution-independent campus icons. No fonts or network assets required. */
public final class Icons {
    private Icons() { }

    public static Node icon(String name, double size) {
        SVGPath path = new SVGPath();
        path.setContent(path(name));
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web("#8b3439"));
        path.setStrokeWidth(1.8);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.getStyleClass().add("icon-stroke");
        Rectangle bounds = new Rectangle(24, 24, Color.TRANSPARENT);
        Group drawing = new Group(bounds, path);
        drawing.setScaleX(size / 24);
        drawing.setScaleY(size / 24);
        StackPane frame = new StackPane(drawing);
        frame.setMinSize(size, size);
        frame.setPrefSize(size, size);
        frame.setMaxSize(size, size);
        frame.setMouseTransparent(true);
        return frame;
    }

    public static StackPane badge(String name, double size) {
        StackPane badge = new StackPane(icon(name, size));
        badge.getStyleClass().add("icon-badge");
        badge.setMinSize(size + 26, size + 26);
        badge.setPrefSize(size + 26, size + 26);
        badge.setMaxSize(size + 26, size + 26);
        return badge;
    }

    public static String forModule(String key) {
        return switch (key) {
            case "overview" -> "home";
            case "profile", "faculty", "students", "users" -> "users";
            case "reports", "activity_logs" -> "chart";
            case "departments", "resources" -> "campus";
            case "courses", "class_teacher_assignments" -> "graduation";
            case "course_enrollments" -> "registration";
            case "attendance" -> "attendance";
            case "timetable", "timetable_overrides", "events", "event_registrations" -> "calendar";
            case "assignments", "project_milestones" -> "clipboard";
            case "study_materials", "library_resources", "library_borrowings", "library_reservations" -> "book";
            case "exam_schedule" -> "calendar";
            case "seating_allotments" -> "seat";
            case "hall_tickets" -> "ticket";
            case "student_fees", "faculty_salaries" -> "wallet";
            case "resource_bookings", "permission_requests" -> "ticket";
            case "leave_requests", "faculty_substitutions" -> "refresh";
            case "faculty_appointments" -> "appointment";
            case "doubts", "doubt_replies", "helpdesk_tickets" -> "chat";
            case "projects", "project_members" -> "layers";
            case "announcements" -> "megaphone";
            case "notifications" -> "bell";
            default -> "grid";
        };
    }

    private static String path(String name) {
        return switch (name) {
            case "wallet" -> "M3 6 H20 V21 H3 Z M3 6 V3 H17 V6 M15 11 H22 V17 H15 Z M18 14 H19";
            case "seat" -> "M6 3 H18 V13 H6 Z M4 13 H20 V17 H4 Z M6 17 V22 M18 17 V22";
            case "home" -> "M3 10 L12 3 L21 10 M5 9 V21 H10 V14 H14 V21 H19 V9";
            case "graduation", "student", "course", "award" -> "M2 8 L12 3 L22 8 L12 13 Z M6 10 V16 Q12 21 18 16 V10 M22 8 V16";
            case "teacher", "presentation" -> "M3 3 H21 V15 H3 Z M8 21 L12 15 L16 21 M7 7 H12 M7 11 H16";
            case "attendance" -> "M4 4 H20 V20 H4 Z M8 11 L11 14 L17 8 M8 3 V5 M16 3 V5";
            case "calendar", "timetable" -> "M4 5 H20 V21 H4 Z M4 10 H20 M8 3 V7 M16 3 V7 M8 14 H9 M12 14 H13 M16 14 H17 M8 17 H9 M12 17 H13";
            case "chart", "reports" -> "M4 3 V21 H21 M8 17 V11 M13 17 V6 M18 17 V9";
            case "file", "document" -> "M5 3 H14 L20 9 V21 H5 Z M14 3 V9 H20 M9 13 H16 M9 17 H16";
            case "book" -> "M12 5 Q7 2 3 4 V19 Q7 17 12 20 Q17 17 21 19 V4 Q17 2 12 5 V20";
            case "registration", "scheme" -> "M3 4 H8 V9 H3 Z M3 14 H8 V19 H3 Z M13 4 H18 V9 H13 Z M12 20 L14 15 L20 9 L23 12 L17 18 Z";
            case "exam" -> "M5 3 H19 V21 H5 Z M9 7 H15 M9 11 H15 M9 16 L11 18 L16 14";
            case "clipboard" -> "M8 5 H4 V21 H20 V5 H16 M8 3 H16 V7 H8 Z M8 12 H16 M8 16 H14";
            case "campus", "building", "department" -> "M3 21 H21 M5 21 V9 H19 V21 M3 9 L12 3 L21 9 Z M9 12 V17 M15 12 V17";
            case "ticket" -> "M3 5 H21 V10 Q17 12 21 14 V19 H3 V14 Q7 12 3 10 Z M14 7 V9 M14 11 V13 M14 15 V17";
            case "users", "user", "profile" -> "M8 12 A4 4 0 1 0 8 4 A4 4 0 1 0 8 12 M2 21 V19 Q2 15 8 15 Q14 15 14 19 V21 Z M16 4 Q22 4 20 10 Q19 12 16 12 M17 15 Q22 15 22 19 V21 H18";
            case "chat" -> "M3 4 H21 V17 H10 L5 21 V17 H3 Z M7 8 H17 M7 12 H14";
            case "layers", "project" -> "M3 3 H15 V6 M6 6 H18 V9 M9 9 H21 V21 H9 Z";
            case "megaphone" -> "M3 9 H8 L19 4 V20 L8 15 H3 Z M6 15 L8 21 H12 L10 16 M22 9 V15";
            case "bell" -> "M5 17 H19 L17 14 V9 Q17 4 12 4 Q7 4 7 9 V14 Z M10 20 Q12 22 14 20 M12 2 V4";
            case "refresh" -> "M4 8 A9 9 0 1 1 3 14 M4 3 V9 H10 M12 7 V12 L15 14";
            case "appointment" -> "M3 4 H21 V21 H3 Z M3 8 H21 M8 2 V5 M16 2 V5 M12 11 A2 2 0 1 0 12 15 A2 2 0 1 0 12 11 M8 19 Q8 16 12 16 Q16 16 16 19";
            case "search" -> "M10 3 A7 7 0 1 0 10 17 A7 7 0 1 0 10 3 M15 15 L21 21";
            case "menu" -> "M4 6 H20 M4 12 H20 M4 18 H20";
            case "logout" -> "M9 4 H4 V20 H9 M9 12 H21 L17 8 M21 12 L17 16";
            case "arrow", "chevron" -> "M5 12 H20 M14 6 L20 12 L14 18";
            case "lock", "shield", "admin", "password" -> "M6 10 H18 V21 H6 Z M8 10 V6 A4 4 0 0 1 16 6 V10 M12 14 V17";
            case "mail", "email" -> "M3 5 H21 V19 H3 Z M3 6 L12 13 L21 6";
            case "phone" -> "M5 3 H9 L10 8 L7 10 Q9 15 14 17 L16 14 L21 15 V19 Q21 22 17 21 Q4 18 3 6 Q2 3 5 3 Z";
            case "id", "id-card", "badge" -> "M3 6 H21 V21 H3 Z M10 3 H14 V9 H10 Z M8 12 A2 2 0 1 0 8 16 A2 2 0 1 0 8 12 M5 19 Q8 16 11 19 M15 13 H18 M15 17 H18";
            case "location", "address" -> "M12 22 Q3 13 4 8 A8 8 0 0 1 20 8 Q21 13 12 22 Z M12 6 A3 3 0 1 0 12 12 A3 3 0 1 0 12 6";
            case "briefcase", "work" -> "M3 7 H21 V21 H3 Z M8 7 V3 H16 V7 M3 12 Q12 17 21 12 M12 12 V16";
            case "clock" -> "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 M12 7 V12 L16 14";
            case "check" -> "M4 12 L9 17 L20 6";
            case "help" -> "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 M9 8 Q9 5 12 6 Q17 7 13 11 L12 13 M12 17 V17.1";
            default -> "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z";
        };
    }
}
