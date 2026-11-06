package com.campussphere.repository;

import com.campussphere.exception.CampusException;
import com.campussphere.model.Faculty;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FacultyRepository extends TypedJdbcRepository<Faculty> {
    public FacultyRepository(JdbcRepository jdbc) {
        super(jdbc, "faculty", Faculty::fromRow, Faculty::getId, Faculty::toValues, faculty -> {
            if (faculty.getName().isBlank()) throw new CampusException("Enter a faculty name.");
        });
    }
    public Optional<Faculty> searchFaculty(long id) { return findById(id); }
    public List<Faculty> searchFaculty(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return findAll().stream().filter(f -> (f.getName() + " " + f.getSpecialization() + " " + f.getDesignation())
                .toLowerCase(Locale.ROOT).contains(term)).sorted(Comparator.comparing(Faculty::getName)).toList();
    }
    public Map<Long, Long> countByDepartment() {
        return findAll().stream().collect(Collectors.groupingBy(Faculty::getDepartmentId, Collectors.counting()));
    }
}