package com.campussphere.model;

public final class Laboratory extends Resource {
    public Laboratory(Row row) { super(row); }
    @Override public String getType() { return "LABORATORY"; }
}
