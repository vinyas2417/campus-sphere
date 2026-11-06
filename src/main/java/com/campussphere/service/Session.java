package com.campussphere.service;

import com.campussphere.exception.CampusException;
import com.campussphere.model.User;
import com.campussphere.model.ServiceToken;
import java.util.Optional;
import java.util.Objects;
import java.util.Queue;
import java.util.LinkedList;

public final class Session {
    private volatile User current;
    private final Queue<ServiceToken> activeTokens = new LinkedList<>();

    public User requireUser() { return user().orElseThrow(() -> new CampusException("Please sign in to continue.")); }
    public Optional<User> user() { return Optional.ofNullable(current); }
    public void signIn(User user) { 
        current = Objects.requireNonNull(user); 
        activeTokens.offer(new ServiceToken(user.id()));
        if (activeTokens.size() > 10) activeTokens.poll(); // Keep only recent tokens
    }
    public void clear() { 
        current = null; 
        activeTokens.clear();
    }
}
