package com.campussphere.model;

import com.campussphere.exception.CampusException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public abstract class Resource implements Bookable {
    private final long resourceId;
    private String resourceName;
    private final String building;
    private final int floor;
    private final int capacity;
    private String status;

    protected Resource(Row row) {
        resourceId = row.id(); resourceName = row.text("name"); building = row.text("building");
        floor = (int) row.number("floor"); capacity = (int) row.number("capacity");
        status = row.text("status").toUpperCase(Locale.ROOT);
    }
    public static Resource fromRow(Row row) {
        return switch (row.text("type").toUpperCase(Locale.ROOT)) {
            case "CLASSROOM" -> new Classroom(row);
            case "LABORATORY", "LAB" -> new Laboratory(row);
            case "EQUIPMENT" -> new Equipment(row);
            case "SEMINAR_ROOM", "SEMINAR ROOM" -> new SeminarRoom(row);
            default -> throw new CampusException("Choose a supported resource type.");
        };
    }
    public long getId() { return resourceId; }
    public long getResourceId() { return resourceId; }
    public String getResourceName() { return resourceName; }
    public String getBuilding() { return building; }
    public int getFloor() { return floor; }
    public int getCapacity() { return capacity; }
    public String getStatus() { return status; }
    public void setResourceName(String name) {
        if (name == null || name.isBlank()) throw new CampusException("Enter a resource name.");
        resourceName = name.strip();
    }
    public void setStatus(String value) {
        if (!java.util.Set.of("AVAILABLE", "OCCUPIED", "RESERVED", "MAINTENANCE").contains(value))
            throw new CampusException("Choose a valid resource status.");
        status = value;
    }
    public abstract String getType();
    @Override public boolean isBookable() { return "AVAILABLE".equals(status) || "RESERVED".equals(status); }
    @Override public void ensureBookable() {
        if (!isBookable()) throw new CampusException(resourceName + " is not available for booking.");
    }
    public Map<String, Object> toValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", resourceName); values.put("type", getType()); values.put("building", building);
        values.put("floor", floor); values.put("capacity", capacity); values.put("status", status);
        return values;
    }
}
