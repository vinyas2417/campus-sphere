package com.campussphere.model;

import java.util.List;

public final class Administrator extends Person {
    public Administrator(Row row) { super(row); }
    @Override public Role getRole() { return Role.ADMIN; }
    @Override public String displayDetails() { return getName() + " · Campus administrator"; }
    @Override public List<String> getDashboardAccess() {
        return List.of("students", "faculty", "departments", "courses", "resources", "leave_requests", "activity_logs");
    }
}
