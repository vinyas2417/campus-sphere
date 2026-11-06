package com.campussphere.model;

import java.util.List;
import java.util.Set;

public record ModuleSpec(String key, String title, String singular, String group, List<FieldSpec> fields, Set<Role> readers, Set<Role> writers) {
    public ModuleSpec { fields = List.copyOf(fields); readers = Set.copyOf(readers); writers = Set.copyOf(writers); }
}
