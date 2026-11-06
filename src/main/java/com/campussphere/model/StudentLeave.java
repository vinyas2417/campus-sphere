package com.campussphere.model;

public final class StudentLeave extends LeaveRequest {
    public StudentLeave(Row row) { super(row); }
    @Override public String getType() { return "STUDENT_LEAVE"; }
}