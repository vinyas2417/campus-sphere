package com.campussphere.model;

/** A domain availability check; transactional overlap checks remain in the repository. */
public interface Bookable {
    boolean isBookable();
    void ensureBookable();
}
