package com.campussphere.config;

public record DatabaseConfig(String url, String username, String password) {
    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(value("CAMPUS_DB_URL", "jdbc:sqlite:data/campussphere.db"), value("CAMPUS_DB_USER", ""), value("CAMPUS_DB_PASSWORD", ""));
    }
    private static String value(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
