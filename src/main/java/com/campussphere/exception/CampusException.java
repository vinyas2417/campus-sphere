package com.campussphere.exception;

/** The message is safe for presentation to the user; technical details remain in the cause. */
public class CampusException extends RuntimeException {
    public CampusException(String message) { super(message); }
    public CampusException(String message, Throwable cause) { super(message, cause); }
}
