package com.campussphere.validation;

/** Lambda-friendly validation hook invoked before a typed repository writes an entity. */
@FunctionalInterface
public interface EntityValidator<T> {
    void validate(T entity);
}