package com.campussphere.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared, encapsulated profile state. Authentication is kept in User/AuthService. */
public abstract class Person {
    private final long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private final Map<String, Object> profile;

    protected Person(Row row) {
        id = row.id();
        profile = new LinkedHashMap<>(row.values());
        setName(row.text("name"));
        email = row.text("email");
        phone = row.text("phone");
        address = row.text("address");
    }

    public static Person fromRow(Row row, Role role) {
        return switch (Objects.requireNonNull(role)) {
            case STUDENT -> new Student(row);
            case FACULTY -> new Faculty(row);
            case ADMIN -> new Administrator(row);
        };
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public void setName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Enter a name.");
        name = value.strip();
    }
    public void setEmail(String value) { email = value == null ? "" : value.strip(); }
    public void setPhone(String value) { phone = value == null ? "" : value.strip(); }
    public void setAddress(String value) { address = value == null ? "" : value.strip(); }
    protected String text(String key) { return Objects.toString(profile.get(key), ""); }
    protected long number(String key) {
        Object value = profile.get(key);
        return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
    }
    protected void put(String key, Object value) { profile.put(key, value); }
    public Map<String, Object> toValues() {
        Map<String, Object> result = new LinkedHashMap<>(profile);
        result.remove("id");
        result.put("name", name);
        if (getRole() != Role.ADMIN) {
            result.put("email", email);
            result.put("phone", phone);
            result.put("address", address);
        }
        return result;
    }

    public abstract Role getRole();
    public abstract String displayDetails();
    public abstract List<String> getDashboardAccess();
}
