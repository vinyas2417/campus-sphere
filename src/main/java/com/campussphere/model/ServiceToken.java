package com.campussphere.model;

import java.util.UUID;

public class ServiceToken {
    private final String token;
    private final long userId;
    private final long createdAt;

    public ServiceToken(long userId) {
        this.token = UUID.randomUUID().toString();
        this.userId = userId;
        this.createdAt = System.currentTimeMillis();
    }

    public String getToken() { return token; }
    public long getUserId() { return userId; }
    public long getCreatedAt() { return createdAt; }
}
