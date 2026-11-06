package com.campussphere.model;

/** Pending requests expose the same approval contract to workflow services. */
public interface Approvable {
    void approve();
    void reject(String reason);
    void cancel();
    String getStatus();
}