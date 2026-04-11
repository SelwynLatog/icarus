package storage.dao;

// Persists CV scan results to cv_matches table — every scan logged, win or no-match.

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import storage.DBConnection;

public class CVMatchDAO {

    // product_id is nullable — null if no match was found.
    public int logMatch(
            int itemId,
            Integer productId,
            float confidence,
            String matchResult
    ) throws SQLException {

        String sql = """
            INSERT INTO cv_matches
              (item_id, product_id, confidence, match_result, matched_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, itemId);
            if (productId != null) ps.setInt(2, productId);
            else                   ps.setNull(2, Types.INTEGER);
            ps.setFloat(3, confidence);
            ps.setString(4, matchResult);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert succeeded but no match_id returned.");
            }
        }
    }

    public Optional<Map<String, Object>> findById(int matchId) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE match_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Map<String, Object>> getAll() throws SQLException {
        return queryList("SELECT * FROM cv_matches ORDER BY matched_at DESC");
    }

    // An item can be scanned more than once — full history returned.
    public List<Map<String, Object>> findByItemId(int itemId) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE item_id = ? ORDER BY matched_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Map<String, Object>> findByProductId(int productId) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE product_id = ? ORDER BY matched_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    // Expected matchResult values: APPROVED | REJECTED | NO_MATCH
    public List<Map<String, Object>> findByMatchResult(String matchResult) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE match_result = ? ORDER BY matched_at DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, matchResult);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public Optional<Map<String, Object>> findLatestByItemId(int itemId) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE item_id = ? ORDER BY matched_at DESC LIMIT 1";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Map<String, Object>> findByMinConfidence(float minConfidence) throws SQLException {
        String sql = "SELECT * FROM cv_matches WHERE confidence >= ? ORDER BY confidence DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setFloat(1, minConfidence);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("match_id",    rs.getInt("match_id"));
        row.put("item_id",     rs.getInt("item_id"));
        int productId = rs.getInt("product_id");
        row.put("product_id",  rs.wasNull() ? null : productId);
        row.put("confidence",  rs.getFloat("confidence"));
        row.put("match_result", rs.getString("match_result"));
        row.put("matched_at",  rs.getTimestamp("matched_at").toLocalDateTime());
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