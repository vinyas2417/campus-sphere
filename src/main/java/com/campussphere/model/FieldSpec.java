package com.campussphere.model;

import java.util.List;

public record FieldSpec(String key, String label, String type, boolean required, String reference, List<String> choices) {
    public FieldSpec { reference = reference == null ? "" : reference; choices = choices == null ? List.of() : List.copyOf(choices); }
}
