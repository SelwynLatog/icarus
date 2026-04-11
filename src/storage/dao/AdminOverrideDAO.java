package storage.dao;

// Audit trail for admin decision overrides.
// Records every instance an admin overrules the engine — nothing is deleted from this table.

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import storage.DBConnection;

public class AdminOverrideDAO {

    public int recordOverride(
            int logId,
            String originalDecision,
            String newDecision,
            String reason
    ) throws SQLException {

        String sql = """
            INSERT INTO admin_overrides
              (log_id, original_decision, new_decision, reason, overridden_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, logId);
            ps.setString(2, originalDecision);
            ps.setString(3, newDecision);
            ps.setString(4, reason);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert succeeded but no override_id returned.");
            }
        }
    }

    public Optional<Map<String, Object>> findById(int overrideId) throws SQLException {
        String sql = "SELECT * FROM admin_overrides WHERE override_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, overrideId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Map<String, Object>> getAll() throws SQLException {
        String sql = "SELECT * FROM admin_overrides ORDER BY overridden_at DESC";
        return queryList(sql);
    }

    // A single decision can be overridden multiple times — full history returned.
    public List<Map<String, Object>> findByLogId(int logId) throws SQLException {
        String sql = "SELECT * FROM admin_overrides WHERE log_id = ? ORDER BY overridden_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Map<String, Object>> findByDateRange(
            LocalDateTime from, LocalDateTime to
    ) throws SQLException {

        String sql = """
            SELECT * FROM admin_overrides
            WHERE overridden_at BETWEEN ? AND ?
            ORDER BY overridden_at DESC
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Map<String, Object>> findByOriginalDecision(String originalDecision) throws SQLException {
        String sql = "SELECT * FROM admin_overrides WHERE original_decision = ? ORDER BY overridden_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, originalDecision);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("override_id",       rs.getInt("override_id"));
        row.put("log_id",            rs.getInt("log_id"));
        row.put("original_decision", rs.getString("original_decision"));
        row.put("new_decision",      rs.getString("new_decision"));
        row.put("reason",            rs.getString("reason"));
        row.put("overridden_at",     rs.getTimestamp("overridden_at").toLocalDateTime());
        return row;
    }

    private List<Map<String, Object>> queryList(String sql) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return collectRows(rs);
        }
    }

    private List<Map<String, Object>> collectRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        while (rs.next()) result.add(mapRow(rs));
        return result;
    }
}