package com.campussphere.model;

public interface Notifiable {
    void notifyUser(String message);
    boolean hasUnreadNotifications();
}
