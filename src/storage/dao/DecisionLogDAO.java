package storage.dao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import storage.DBConnection;

/**
 * Handles all reads and writes to the decision_log table.
 * Every decision the engine makes gets persisted here.
 * Admin overrides reference these log entries.
 */
public class DecisionLogDAO {

    // WRITE

    /**
     * Persists a decision result to the log.
     * Returns the auto-generated log_id.
     */
    public int logDecision(
            int itemId,
            String decision,
            int riskScore,
            String threatLevel,
            String reason,
            String actionRec,
            boolean alertTriggered,
            String imagePath
    ) throws SQLException {

        String sql = """
            INSERT INTO decision_log
              (item_id, decision, risk_score, threat_level, reason,
               action_rec, image_path, alert_triggered, evaluated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, itemId);
            ps.setString(2, decision);
            ps.setInt(3, riskScore);
            ps.setString(4, threatLevel);
            ps.setString(5, reason);
            ps.setString(6, actionRec);
            ps.setString(7, imagePath);
            ps.setBoolean(8, alertTriggered);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert succeeded but no log_id returned.");
            }
        }
    }

    // READ

    public Optional<Map<String, Object>> findById(int logId) throws SQLException {
        String sql = "SELECT * FROM decision_log WHERE log_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Map<String, Object>> getAll() throws SQLException {
        String sql = "SELECT * FROM decision_log ORDER BY evaluated_at DESC";
        return queryList(sql);
    }

    public List<Map<String, Object>> findByItemId(int itemId) throws SQLException {
        String sql = "SELECT * FROM decision_log WHERE item_id = ? ORDER BY evaluated_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Map<String, Object>> findByDecision(String decision) throws SQLException {
        String sql = "SELECT * FROM decision_log WHERE decision = ? ORDER BY evaluated_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, decision);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Map<String, Object>> findByAlertTriggered(boolean triggered) throws SQLException {
        String sql = "SELECT * FROM decision_log WHERE alert_triggered = ? ORDER BY evaluated_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, triggered);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    /**
     * Time-range query — backbone of the analytics/reporting layer.
     * Used by ItemAnalytics for weekly, monthly, bi-annual reports.
     */
    public List<Map<String, Object>> findByDateRange(
            LocalDateTime from,
            LocalDateTime to
    ) throws SQLException {

        String sql = """
            SELECT * FROM decision_log
            WHERE evaluated_at BETWEEN ? AND ?
            ORDER BY evaluated_at DESC
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

    // MAPPING

    /**
     * Maps a row to a plain Map — no model class needed for log entries.
     * UI and analytics layers read these as key-value pairs.
     */
    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("log_id",          rs.getInt("log_id"));
        row.put("item_id",         rs.getInt("item_id"));
        row.put("decision",        rs.getString("decision"));
        row.put("risk_score",      rs.getInt("risk_score"));
        row.put("threat_level",    rs.getString("threat_level"));
        row.put("reason",          rs.getString("reason"));
        row.put("action_rec",      rs.getString("action_rec"));
        row.put("image_path",      rs.getString("image_path"));
        row.put("alert_triggered", rs.getBoolean("alert_triggered"));
        row.put("evaluated_at",    rs.getTimestamp("evaluated_at").toLocalDateTime());
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