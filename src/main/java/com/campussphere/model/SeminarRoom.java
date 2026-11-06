package com.campussphere.model;

public final class SeminarRoom extends Resource {
    public SeminarRoom(Row row) { super(row); }
    @Override public String getType() { return "SEMINAR_ROOM"; }
}