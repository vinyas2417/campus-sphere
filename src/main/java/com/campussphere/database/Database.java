package com.campussphere.database;

import com.campussphere.config.DatabaseConfig;
import com.campussphere.exception.CampusException;
import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

public final class Database {
    private final DatabaseConfig config;
    public Database(DatabaseConfig config) { this.config = config; }
    public boolean isMysql() { return config.url().startsWith("jdbc:mysql:"); }
    public Connection open() throws SQLException {
        Connection connection = isMysql() ? DriverManager.getConnection(config.url(),config.username(),config.password()) : DriverManager.getConnection(config.url());
        if (!isMysql()) try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA foreign_keys=ON"); statement.execute("PRAGMA busy_timeout=15000"); }
        return connection;
    }
    public void initialize() {
        try {
            if (!isMysql() && config.url().startsWith("jdbc:sqlite:") && !config.url().contains(":memory:")) {
                Path file = Path.of(config.url().substring("jdbc:sqlite:".length())).toAbsolutePath();
                if (file.getParent() != null) Files.createDirectories(file.getParent());
            }
            try (Connection connection = open(); Statement statement = connection.createStatement();
                 InputStream source = Database.class.getResourceAsStream("/database/" + (isMysql() ? "mysql.sql" : "sqlite.sql"))) {
                if (source == null) throw new IOException("Schema missing");
                if (!isMysql()) statement.execute("PRAGMA journal_mode=WAL");
                for (String sql : new String(source.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (sql.isBlank()) continue;
                    try { statement.execute(sql); } catch (SQLException error) {
                        if (!(isMysql() && error.getErrorCode() == 1061)) throw error;
                    }
                }
                ensureAssignmentStorage(connection, isMysql());
                ensureColumn(connection,"users","account_status","VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'");
                ensureCampusEvents(connection, isMysql());
                statement.execute("CREATE TABLE IF NOT EXISTS campus_lock (id INTEGER PRIMARY KEY)");
                statement.execute(isMysql() ? "INSERT IGNORE INTO campus_lock(id) VALUES(1)" : "INSERT OR IGNORE INTO campus_lock(id) VALUES(1)");
            }
        } catch (SQLException | IOException error) { throw new CampusException("Could not initialize the campus database. Check its configuration and folder access.",error); }
    }

    private static void ensureAssignmentStorage(Connection connection, boolean mysql) throws SQLException {
        boolean attachmentColumn = false;
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "assignments", null)) {
            while (columns.next()) if ("attachment_url".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) attachmentColumn = true;
        }
        if (!attachmentColumn) try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE assignments ADD COLUMN attachment_url TEXT");
        }
        try (Statement statement = connection.createStatement()) {
            if (mysql) statement.execute("CREATE TABLE IF NOT EXISTS assignment_submissions (id BIGINT PRIMARY KEY AUTO_INCREMENT, assignment_id BIGINT NOT NULL, course_id BIGINT NOT NULL, student_id BIGINT NOT NULL, file_url TEXT NOT NULL, file_name VARCHAR(255) NOT NULL, uploaded_at VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED', grade VARCHAR(32), feedback TEXT, reviewed_by BIGINT, reviewed_at VARCHAR(32), FOREIGN KEY(assignment_id) REFERENCES assignments(id) ON DELETE CASCADE, FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE, FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE, FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL, UNIQUE KEY uq_assignment_student(assignment_id,student_id), INDEX idx_assignment_submissions_course(course_id,student_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            else {
                statement.execute("CREATE TABLE IF NOT EXISTS assignment_submissions (id INTEGER PRIMARY KEY AUTOINCREMENT, assignment_id BIGINT NOT NULL, course_id BIGINT NOT NULL, student_id BIGINT NOT NULL, file_url TEXT NOT NULL, file_name VARCHAR(255) NOT NULL, uploaded_at VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED', grade VARCHAR(32), feedback TEXT, reviewed_by BIGINT, reviewed_at VARCHAR(32), FOREIGN KEY(assignment_id) REFERENCES assignments(id) ON DELETE CASCADE, FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE, FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE, FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL, UNIQUE(assignment_id,student_id))");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_assignment_submissions_course ON assignment_submissions(course_id,student_id)");
            }
            for (String[] column : new String[][]{{"status","VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED'"},{"grade","VARCHAR(32)"},{"feedback","TEXT"},{"reviewed_by","BIGINT"},{"reviewed_at","VARCHAR(32)"}}) {
                ensureColumn(connection, "assignment_submissions", column[0], column[1]);
            }
            ensureColumn(connection,"library_resources","course_id","BIGINT");
            ensureColumn(connection,"events","coordinator_id","BIGINT");
            ensureColumn(connection,"seating_allotments","roll_number","VARCHAR(64)");
            ensureColumn(connection,"seating_allotments","bench_number","VARCHAR(32)");
            ensureColumn(connection,"seating_allotments","seat_side","VARCHAR(8)");
            ensureColumn(connection,"seating_allotments","room_number","VARCHAR(64)");
        }
    }

    private static void ensureCampusEvents(Connection connection, boolean mysql) throws SQLException {
        String[][] events={{"ScrapTech","fac00005"},{"PixelCraft","fac00007"},{"Hackathon","fac00008"}};
        for(String[] item:events) {
            try(PreparedStatement faculty=connection.prepareStatement("SELECT f.id FROM faculty f LEFT JOIN users u ON u.id=f.user_id WHERE LOWER(TRIM(u.username))=LOWER(?) OR LOWER(TRIM(u.email))=LOWER(?) OR LOWER(TRIM(u.name))=LOWER(?) OR LOWER(TRIM(f.name))=LOWER(?)")) {
                for(int i=1;i<=4;i++) faculty.setString(i,item[1]);
                try(ResultSet result=faculty.executeQuery()) {
                    if(!result.next()) continue;
                    long facultyId=result.getLong(1);
                    try(PreparedStatement existing=connection.prepareStatement("SELECT id FROM events WHERE LOWER(title)=LOWER(?)")) {
                        existing.setString(1,item[0]);
                        try(ResultSet found=existing.executeQuery()) {
                            if(found.next()) {
                                try(PreparedStatement update=connection.prepareStatement("UPDATE events SET coordinator_id=? WHERE id=? AND coordinator_id IS NULL")) { update.setLong(1,facultyId); update.setLong(2,found.getLong(1)); update.executeUpdate(); }
                            } else {
                                try(PreparedStatement insert=connection.prepareStatement("INSERT INTO events(title,description,location,start_time,end_time,capacity,coordinator_id) VALUES(?,?,?,?,?,?,?)")) {
                                    insert.setString(1,item[0]); insert.setString(2,"Campus event registration."); insert.setString(3,"Campus");
                                    insert.setString(4,"2026-12-12T09:00"); insert.setString(5,"2026-12-12T17:00"); insert.setInt(6,500); insert.setLong(7,facultyId); insert.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void ensureColumn(Connection connection,String table,String column,String type) throws SQLException {
        boolean exists=false;
        try(ResultSet columns=connection.getMetaData().getColumns(null,null,table,null)){
            while(columns.next())if(column.equalsIgnoreCase(columns.getString("COLUMN_NAME")))exists=true;
        }
        if(!exists)try(Statement statement=connection.createStatement()){
            statement.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type);
        }
    }
}
