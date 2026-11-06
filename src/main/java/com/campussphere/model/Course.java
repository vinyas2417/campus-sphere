package com.campussphere.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** A typed, immutable course value used by the generic repository. */
public record Course(long id, String code, String name, int credits, long departmentId,
                     int semester, long facultyId, int capacity) {
    public static Course fromRow(Row row) {
        return new Course(row.id(), row.text("code"), row.text("name"), (int) row.number("credits"),
                row.number("department_id"), (int) row.number("semester"), row.number("faculty_id"),
                (int) row.number("capacity"));
    }
    public Map<String, Object> toValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", code); values.put("name", name); values.put("credits", credits);
        values.put("department_id", departmentId); values.put("semester", semester);
        values.put("faculty_id", facultyId == 0 ? null : facultyId); values.put("capacity", capacity);
        return values;
    }
}