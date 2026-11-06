package com.campussphere.repository;

import com.campussphere.database.Database;
import com.campussphere.model.Row;
import com.campussphere.exception.CampusException;
import java.sql.*;
import java.util.*;

public final class JdbcRepository {
    private final Database database;
    private final WorkflowRepository work;
    public JdbcRepository(Database database) { this.database = database; work = new WorkflowRepository(database); }
    public Database database() { return database; }
    public List<Row> findAll(String table) { return work.transaction(tx -> tx.all(table)); }
    public Optional<Row> findById(String table,long id) { return work.transaction(tx -> tx.find(table,id)); }
    public Row insert(String table,Map<String,Object> values) { return work.transaction(tx -> tx.insert(table,values)); }
    public Row update(String table,long id,Map<String,Object> values) { return work.transaction(tx -> tx.update(table,id,values)); }
    public void delete(String table,long id) { work.transaction(tx -> { tx.require(table,id); tx.delete(table,id); return null; }); }
    public List<Row> query(String sql,Object... params) {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement,params);
            try (ResultSet result = statement.executeQuery()) {
                List<Row> rows = new ArrayList<>(); var metadata = result.getMetaData();
                while (result.next()) {
                    long id = 0; Map<String,Object> values = new LinkedHashMap<>();
                    for (int i=1;i<=metadata.getColumnCount();i++) { String key = metadata.getColumnLabel(i).toLowerCase(Locale.ROOT); if (key.equals("id")) id = result.getLong(i); else values.put(key,result.getObject(i)); }
                    rows.add(new Row(id,values));
                } return rows;
            }
        } catch (SQLException error) { throw new CampusException("Could not load records. Check the database connection.",error); }
    }
    public int execute(String sql,Object... params) {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) { bind(statement,params); return statement.executeUpdate(); }
        catch (SQLException error) { throw new CampusException("Could not save records. Check related records and unique values.",error); }
    }
    private static void bind(PreparedStatement statement,Object[] params) throws SQLException { for(int i=0;i<params.length;i++) statement.setObject(i+1,params[i]); }
}
