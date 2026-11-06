package com.campussphere.model;

import java.util.List;
import java.util.Map;

public final class Faculty extends Person {
    public Faculty(Row row) { super(row); }
    public Faculty(String name, String email, long departmentId, String designation) {
        this(new Row(0, Map.of("name", name, "email", email, "department_id", departmentId, "designation", designation)));
    }
    public static Faculty fromRow(Row row) { return new Faculty(row); }
    public long getDepartmentId() { return number("department_id"); }
    public String getDesignation() { return text("designation"); }
    public String getSpecialization() { return text("specialization"); }
    public int getExperience() { return (int) number("experience"); }
    public void setDesignation(String designation) { put("designation", designation); }
    @Override public Role getRole() { return Role.FACULTY; }
    @Override public String displayDetails() { return getName() + " · " + getDesignation(); }
    @Override public List<String> getDashboardAccess() {
        return List.of("courses", "timetable", "attendance", "assignments", "study_materials", "doubts", "projects");
    }
}
