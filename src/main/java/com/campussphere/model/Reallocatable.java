package com.campussphere.model;

import java.util.List;

public interface Reallocatable {
    List<Faculty> findAvailableFaculty();
    boolean hasConflict(Faculty faculty);
    void assignReplacement(Faculty faculty);
}
