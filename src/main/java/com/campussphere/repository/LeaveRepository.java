package com.campussphere.repository;

import com.campussphere.exception.LeaveRequestNotFoundException;
import com.campussphere.model.LeaveRequest;
import java.util.Comparator;
import java.util.List;

public final class LeaveRepository extends TypedJdbcRepository<LeaveRequest> {
    public LeaveRepository(JdbcRepository jdbc) {
        super(jdbc, "leave_requests", LeaveRequest::fromRow, LeaveRequest::getId, LeaveRequest::toValues, request -> {
            if (request.getToDate().isBefore(request.getFromDate()))
                throw new com.campussphere.exception.InvalidLeaveDateException("Check the leave date range.");
        });
    }
    public LeaveRequest require(long id) {
        return findById(id).orElseThrow(() -> new LeaveRequestNotFoundException("This leave request no longer exists."));
    }
    public List<LeaveRequest> findPending() {
        return findAll().stream().filter(r -> "PENDING".equals(r.getStatus()))
                .sorted(Comparator.comparing(LeaveRequest::getRequestDate)).toList();
    }
}