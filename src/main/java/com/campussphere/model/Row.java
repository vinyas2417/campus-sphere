package com.campussphere.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, ordered JDBC result used by metadata-driven screens. */
public record Row(long id, Map<String,Object> values) {
    public Row { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    public Object get(String key) { return "id".equals(key) ? id : values.get(key); }
    public String text(String key) { Object value = get(key); return value == null ? "" : String.valueOf(value); }
    public long number(String key) {
        Object value = get(key);
        if (value == null || value.toString().isBlank()) return 0;
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
    public double decimal(String key) {
        Object value = get(key);
        if (value == null || value.toString().isBlank()) return 0;
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
    }
}
