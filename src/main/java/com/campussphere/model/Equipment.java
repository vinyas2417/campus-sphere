package com.campussphere.model;

public final class Equipment extends Resource {
    public Equipment(Row row) { super(row); }
    @Override public String getType() { return "EQUIPMENT"; }
}
