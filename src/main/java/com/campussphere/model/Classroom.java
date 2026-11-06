package com.campussphere.model;

public final class Classroom extends Resource {
    public Classroom(Row row) { super(row); }
    @Override public String getType() { return "CLASSROOM"; }
}
