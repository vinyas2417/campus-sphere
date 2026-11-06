package com.campussphere.repository;

import com.campussphere.exception.CampusException;
import com.campussphere.model.Student;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StudentRepository extends TypedJdbcRepository<Student> {
    public StudentRepository(JdbcRepository jdbc) {
        super(jdbc, "students", Student::fromRow, Student::getId, Student::toValues, student -> {
            if (student.getUsn().isBlank()) throw new CampusException("Enter a student USN.");
            if (student.getSemester() < 1 || student.getSemester() > 12)
                throw new CampusException("Semester must be between 1 and 12.");
        });
    }
    public Optional<Student> searchStudent(long id) { return findById(id); }
    public List<Student> searchStudent(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return findAll().stream().filter(s -> (s.getName() + " " + s.getUsn() + " " + s.getEmail() + " " + s.getSection())
                .toLowerCase(Locale.ROOT).contains(term)).sorted(Comparator.comparing(Student::getName)).toList();
    }
    public List<Student> searchStudent(long departmentId, int semester) {
        return findAll().stream().filter(s -> s.getDepartmentId() == departmentId && s.getSemester() == semester)
                .sorted(Comparator.comparing(Student::getName)).toList();
    }
    public Map<Integer, Long> countBySemester() {
        return findAll().stream().collect(Collectors.groupingBy(Student::getSemester, Collectors.counting()));
    }
}