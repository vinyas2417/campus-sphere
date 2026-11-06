package com.campussphere.model;

public final class OnDutyRequest extends LeaveRequest {
    public OnDutyRequest(Row row) { super(row); }
    @Override public String getType() { return "ON_DUTY"; }
}