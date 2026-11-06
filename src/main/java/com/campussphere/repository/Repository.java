package com.campussphere.repository;

import java.util.List;
import java.util.Optional;

/** Persistence abstraction; callers enforce service authorization before using repositories. */
public interface Repository<T> {
    T add(T entity);
    T update(T entity);
    void delete(long id);
    Optional<T> findById(long id);
    List<T> findAll();
}