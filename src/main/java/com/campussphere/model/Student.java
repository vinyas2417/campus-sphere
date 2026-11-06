package com.campussphere.model;

import java.util.List;
import java.util.Map;

public final class Student extends Person {
    public Student(Row row) { super(row); }
    public Student(String usn, String name, String email, long departmentId, int semester) {
        this(new Row(0, Map.of("usn", usn, "name", name, "email", email,
                "department_id", departmentId, "semester", semester, "status", "ACTIVE")));
    }
    public static Student fromRow(Row row) { return new Student(row); }
    public String getUsn() { return text("usn"); }
    public long getDepartmentId() { return number("department_id"); }
    public int getSemester() { return (int) number("semester"); }
    public String getSection() { return text("section"); }
    public String getStatus() { return text("status"); }
    public void setSemester(int semester) {
        if (semester < 1 || semester > 12) throw new IllegalArgumentException("Semester must be between 1 and 12.");
        put("semester", semester);
    }
    public void setSection(String section) { put("section", section); }
    @Override public Role getRole() { return Role.STUDENT; }
    @Override public String displayDetails() { return getName() + " · " + getUsn() + " · Semester " + getSemester(); }
    @Override public List<String> getDashboardAccess() {
        return List.of("courses", "timetable", "attendance", "assignments", "library_resources", "events", "leave_requests");
    }
}
