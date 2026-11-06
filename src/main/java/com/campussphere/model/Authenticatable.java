package com.campussphere.model;

public interface Authenticatable {
    boolean authenticate(String password);
    void logout();
}
