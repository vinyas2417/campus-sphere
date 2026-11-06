package com.campussphere.model;

public final class FacultyLeave extends LeaveRequest {
    public FacultyLeave(Row row) { super(row); }
    @Override public String getType() { return "FACULTY_LEAVE"; }
}