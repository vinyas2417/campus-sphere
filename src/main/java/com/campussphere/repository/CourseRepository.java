package com.campussphere.repository;

import com.campussphere.exception.CampusException;
import com.campussphere.model.Course;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CourseRepository extends TypedJdbcRepository<Course> {
    public CourseRepository(JdbcRepository jdbc) {
        super(jdbc, "courses", Course::fromRow, Course::id, Course::toValues, course -> {
            if (course.code().isBlank() || course.name().isBlank()) throw new CampusException("Enter a course code and name.");
            if (course.capacity() < 1) throw new CampusException("Course capacity must be at least one.");
        });
    }
    public Optional<Course> searchCourse(long id) { return findById(id); }
    public List<Course> searchCourse(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return findAll().stream().filter(c -> (c.code() + " " + c.name()).toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(Course::code)).toList();
    }
}