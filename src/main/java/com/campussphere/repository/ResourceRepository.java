package com.campussphere.repository;

import com.campussphere.exception.CampusException;
import com.campussphere.model.Resource;
import java.util.Comparator;
import java.util.List;

public final class ResourceRepository extends TypedJdbcRepository<Resource> {
    public ResourceRepository(JdbcRepository jdbc) {
        super(jdbc, "resources", Resource::fromRow, Resource::getId, Resource::toValues, resource -> {
            if (resource.getResourceName().isBlank()) throw new CampusException("Enter a resource name.");
            if (resource.getCapacity() < 1) throw new CampusException("Resource capacity must be at least one.");
        });
    }
    public List<Resource> findAvailable() {
        return findAll().stream().filter(Resource::isBookable).sorted(Comparator.comparing(Resource::getResourceName)).toList();
    }
}