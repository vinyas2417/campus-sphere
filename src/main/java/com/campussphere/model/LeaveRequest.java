package com.campussphere.model;

import com.campussphere.exception.CampusException;
import com.campussphere.exception.InvalidLeaveDateException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public abstract class LeaveRequest implements Approvable {
    private final long requestId;
    private final long requesterId;
    private final LocalDate requestDate;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final String reason;
    private String status;
    private String approvalRemarks;

    protected LeaveRequest(Row row) {
        requestId = row.id(); requesterId = row.number("requester_id");
        try {
            requestDate = row.text("request_date").isBlank() ? LocalDate.now() : LocalDate.parse(row.text("request_date").substring(0, 10));
            fromDate = LocalDate.parse(row.text("from_date"));
            toDate = LocalDate.parse(row.text("to_date"));
        } catch (RuntimeException ex) {
            throw new InvalidLeaveDateException("Enter valid leave dates.");
        }
        if (toDate.isBefore(fromDate)) throw new InvalidLeaveDateException("Leave end date must be on or after the start date.");
        reason = row.text("reason");
        if (reason.isBlank()) throw new CampusException("Enter a reason for your request.");
        status = row.text("status").isBlank() ? "PENDING" : row.text("status");
        approvalRemarks = row.text("approval_remarks");
    }
    public static LeaveRequest fromRow(Row row) {
        return switch (row.text("type").toUpperCase(Locale.ROOT)) {
            case "STUDENT", "STUDENT_LEAVE" -> new StudentLeave(row);
            case "FACULTY", "FACULTY_LEAVE" -> new FacultyLeave(row);
            case "ON_DUTY", "ONDUTY", "ON-DUTY" -> new OnDutyRequest(row);
            default -> throw new CampusException("Choose a supported leave type.");
        };
    }
    public long getId() { return requestId; }
    public long getRequestId() { return requestId; }
    public long getRequesterId() { return requesterId; }
    public LocalDate getRequestDate() { return requestDate; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public String getReason() { return reason; }
    @Override public String getStatus() { return status; }
    public String getApprovalRemarks() { return approvalRemarks; }
    public abstract String getType();
    private void requirePending() {
        if (!"PENDING".equals(status)) throw new CampusException("Only pending requests can be changed.");
    }
    @Override public void approve() { requirePending(); status = "APPROVED"; }
    @Override public void reject(String remarks) {
        requirePending();
        if (remarks == null || remarks.isBlank()) throw new CampusException("Enter a reason for rejecting this request.");
        approvalRemarks = remarks.strip(); status = "REJECTED";
    }
    @Override public void cancel() { requirePending(); status = "CANCELLED"; }
    public Map<String, Object> toValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("requester_id", requesterId); values.put("type", getType());
        values.put("request_date", requestDate.toString()); values.put("from_date", fromDate.toString());
        values.put("to_date", toDate.toString()); values.put("reason", reason);
        values.put("status", status); values.put("approval_remarks", approvalRemarks);
        return values;
    }
}