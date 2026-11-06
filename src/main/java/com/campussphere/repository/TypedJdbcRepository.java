package com.campussphere.repository;

import com.campussphere.model.Row;
import com.campussphere.validation.EntityValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** A reusable JDBC adapter. No SQL, JDBC resources, or mutable database maps leak to models. */
public abstract class TypedJdbcRepository<T> implements Repository<T> {
    private final JdbcRepository jdbc;
    private final String table;
    private final Function<Row, T> mapper;
    private final ToLongFunction<T> identifier;
    private final Function<T, Map<String, Object>> values;
    private final EntityValidator<T> validator;

    protected TypedJdbcRepository(JdbcRepository jdbc, String table, Function<Row, T> mapper,
            ToLongFunction<T> identifier, Function<T, Map<String, Object>> values, EntityValidator<T> validator) {
        this.jdbc = jdbc; this.table = table; this.mapper = mapper;
        this.identifier = identifier; this.values = values; this.validator = validator;
    }
    @Override public T add(T entity) {
        validator.validate(entity);
        return mapper.apply(jdbc.insert(table, values.apply(entity)));
    }
    @Override public T update(T entity) {
        validator.validate(entity);
        return mapper.apply(jdbc.update(table, identifier.applyAsLong(entity), values.apply(entity)));
    }
    @Override public void delete(long id) { jdbc.delete(table, id); }
    @Override public Optional<T> findById(long id) { return jdbc.findById(table, id).map(mapper); }
    @Override public List<T> findAll() { return jdbc.findAll(table).stream().map(mapper).toList(); }
}