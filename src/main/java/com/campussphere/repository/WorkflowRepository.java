package com.campussphere.repository;

import com.campussphere.database.Database;
import com.campussphere.exception.CampusException;
import com.campussphere.model.Row;

import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** JDBC unit of work. A transaction keeps validation and writes on one connection. */
public final class WorkflowRepository {
    private static final Logger LOG = Logger.getLogger(WorkflowRepository.class.getName());
    private static final Set<String> TABLES = Set.of("users", "students", "faculty", "departments",
            "courses", "course_enrollments", "resources", "resource_bookings", "events", "event_registrations",
            "leave_requests", "permission_requests", "timetable", "timetable_overrides", "faculty_substitutions",
            "attendance", "library_resources", "library_borrowings", "library_reservations", "doubts", "doubt_replies",
            "helpdesk_tickets", "notifications", "activity_logs", "faculty_appointments", "projects", "project_members",
            "administrators", "class_teacher_assignments", "classrooms", "laboratories", "equipment", "assignments",
            "study_materials", "exam_schedule", "project_milestones", "announcements",
            "seating_allotments", "hall_tickets", "student_fees", "faculty_salaries", "assignment_submissions");
    private final Database database;

    public WorkflowRepository(Database database) { this.database = Objects.requireNonNull(database); }

    /**
     * SQLite obtains its write reservation before reading availability. MySQL uses
     * explicit parent-row locks; READ_COMMITTED makes subsequent validation see
     * the preceding transaction's committed result after waiting for that lock.
     */
    public <T> T transaction(Function<Transaction, T> operation) {
        try (Connection connection = database.open()) {
            boolean mysql = database.isMysql();
            if (mysql) {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                try (Statement mutex = connection.createStatement(); ResultSet ignored = mutex.executeQuery("SELECT id FROM campus_lock WHERE id=1 FOR UPDATE")) { ignored.next(); }
            } else {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA busy_timeout=15000");
                    statement.execute("BEGIN IMMEDIATE");
                }
            }
            try {
                T result = operation.apply(new Transaction(connection, mysql));
                if (mysql) connection.commit();
                else try (Statement statement = connection.createStatement()) { statement.execute("COMMIT"); }
                return result;
            } catch (RuntimeException | SQLException failure) {
                try {
                    if (mysql) connection.rollback();
                    else try (Statement statement = connection.createStatement()) { statement.execute("ROLLBACK"); }
                } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                if (failure instanceof CampusException campusFailure) throw campusFailure;
                if (failure instanceof SQLException sqlFailure) throw databaseFailure(sqlFailure);
                throw (RuntimeException) failure;
            }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    private static CampusException databaseFailure(SQLException failure) {
        String message = Objects.toString(failure.getMessage(), "").toLowerCase(Locale.ROOT);
        if (message.contains("unique") || message.contains("duplicate"))
            return new CampusException("That request already exists. Refresh the records and try again.");
        if (message.contains("foreign key"))
            return new CampusException("A related record changed. Refresh the records and try again.");
        if (message.contains("locked") || message.contains("deadlock") || message.contains("lock wait"))
            return new CampusException("Another user is updating this record. Please try again.");
        LOG.log(Level.WARNING, "Workflow database operation failed", failure);
        return new CampusException("The change could not be saved. Check the database connection and try again.");
    }

    private static String table(String name) {
        if (!TABLES.contains(name)) throw new IllegalArgumentException("Unsupported workflow table");
        return name;
    }

    private static String column(String name) {
        if (name == null || !name.matches("[a-z][a-z_]*")) throw new IllegalArgumentException("Invalid column");
        return name;
    }

    public static final class Transaction {
        private final Connection connection;
        private final boolean mysql;

        private Transaction(Connection connection, boolean mysql) { this.connection = connection; this.mysql = mysql; }

        public Row require(String table, long id) {
            return find(table, id).orElseThrow(() -> new CampusException("The selected record no longer exists."));
        }

        public Optional<Row> find(String table, long id) {
            return query("SELECT * FROM " + table(table) + " WHERE id = ?", List.of(id)).stream().findFirst();
        }

        /** Lock a stable parent record before checking its dependent rows. */
        public Row lock(String table, long id) {
            return query("SELECT * FROM " + table(table) + " WHERE id = ?" + (mysql ? " FOR UPDATE" : ""), List.of(id))
                    .stream().findFirst().orElseThrow(() -> new CampusException("The selected record no longer exists."));
        }

        /** Timetable changes share one deterministic faculty lock order. */
        public void lockFacultySchedule() {
            query("SELECT * FROM faculty ORDER BY id" + (mysql ? " FOR UPDATE" : ""), List.of());
        }

        public List<Row> all(String table) { return query("SELECT * FROM " + table(table) + " ORDER BY id", List.of()); }

        public List<Row> where(String table, Map<String, Object> conditions) {
            List<Object> arguments = new ArrayList<>();
            List<String> predicates = new ArrayList<>();
            conditions.forEach((key, value) -> {
                predicates.add(column(key) + (value == null ? " IS NULL" : " = ?"));
                if (value != null) arguments.add(value);
            });
            return query("SELECT * FROM " + table(table) + (predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates)) + " ORDER BY id", arguments);
        }

        public Row insert(String table, Map<String, Object> values) {
            List<String> columns = values.keySet().stream().map(WorkflowRepository::column).toList();
            String sql = "INSERT INTO " + table(table) + " (" + String.join(",", columns) + ") VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, new ArrayList<>(values.values()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return require(table, keys.getLong(1));
                }
                throw new CampusException("The saved record could not be located.");
            } catch (SQLException failure) { throw databaseFailure(failure); }
        }

        public Row update(String table, long id, Map<String, Object> values) {
            if (values.isEmpty()) return require(table, id);
            List<String> assignments = values.keySet().stream().map(key -> column(key) + " = ?").toList();
            List<Object> arguments = new ArrayList<>(values.values());
            arguments.add(id);
            execute("UPDATE " + table(table) + " SET " + String.join(",", assignments) + " WHERE id = ?", arguments);
            return require(table, id);
        }

        public void delete(String table, long id) { execute("DELETE FROM " + table(table) + " WHERE id = ?", List.of(id)); }

        private List<Row> query(String sql, List<Object> arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, arguments);
                try (ResultSet results = statement.executeQuery()) {
                    List<Row> rows = new ArrayList<>();
                    ResultSetMetaData meta = results.getMetaData();
                    while (results.next()) {
                        Map<String, Object> values = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String key = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                            if (!key.equals("id")) values.put(key, results.getObject(i));
                        }
                        rows.add(new Row(results.getLong("id"), values));
                    }
                    return rows;
                }
            } catch (SQLException failure) { throw databaseFailure(failure); }
        }

        private void execute(String sql, List<Object> arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, arguments);
                statement.executeUpdate();
            } catch (SQLException failure) { throw databaseFailure(failure); }
        }

        private static void bind(PreparedStatement statement, List<Object> arguments) throws SQLException {
            for (int i = 0; i < arguments.size(); i++) statement.setObject(i + 1, arguments.get(i));
        }
    }
}
