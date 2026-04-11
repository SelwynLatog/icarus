package storage.dao;

import enums.*;
import java.sql.*;
import java.util.*;
import java.util.function.Predicate;
import model.Item;
import storage.DBConnection;
/**
 * MySQL-backed replacement for ItemLog.
 * Same method signatures as ItemLog — services don't need to change.
 */
public class ItemDAO {

    //WRITE

    /**
     * Inserts a new item into the DB.
     * Returns the auto-generated item_id, mirrors ItemLog.addItem().
     */
    public int addItem(Item item) throws SQLException {
        String sql = """
            INSERT INTO items
              (student_id, item_name, brand, primary_category, secondary_category,
               item_function, consumption_context, usage_type, replaceability, status, quantity, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, item.getStudentId());
            ps.setString(2, item.getItemName());
            ps.setString(3, item.getBrand());
            ps.setString(4, item.getPrimaryCategory().name());
            ps.setString(5, item.getSecondaryCategory().name());
            ps.setString(6, item.getFunction().name());
            ps.setString(7, item.getContext().name());
            ps.setString(8, item.getUsageType().name());
            ps.setString(9, item.getReplaceability().name());
            ps.setString(10, item.getStatus().name());
            ps.setInt(11, item.getQuantity());
            ps.setTimestamp(12, Timestamp.valueOf(item.getTimestamp()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert succeeded but no generated key returned.");
            }
        }
    }

    /**
     * Updates just the status column for a given item_id.
     * Mirrors ItemLog.updateItemStatus().
     */
    public boolean updateItemStatus(int id, ItemStatus newStatus) throws SQLException {
        String sql = "UPDATE items SET status = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Hard-deletes an item by ID.
     * Mirrors ItemLog.removeItemById().
     */
    public boolean removeItemById(int id) throws SQLException {
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // READ

    public Optional<Item> findItemById(int id) throws SQLException {
        String sql = "SELECT * FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Item> getAllItems() throws SQLException {
        String sql = "SELECT * FROM items ORDER BY timestamp DESC";
        return queryList(sql);
    }

    public List<ItemEntry> getAllItemsWithIds() throws SQLException {
        String sql = "SELECT * FROM items ORDER BY item_id ASC";
        List<ItemEntry> entries = new ArrayList<>();

        try (Connection conn = DBConnection.get();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("item_id");
                entries.add(new ItemEntry(id, mapRow(rs)));
            }
        }
        return entries;
    }

    public List<Item> findItemsByCategory(PrimaryCategory category) throws SQLException {
        String sql = "SELECT * FROM items WHERE primary_category = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category.name());
            return collectResults(ps);
        }
    }

    public List<Item> findItemsByStatus(ItemStatus status) throws SQLException {
        String sql = "SELECT * FROM items WHERE status = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            return collectResults(ps);
        }
    }

    public List<Item> findItemsByBrand(String brand) throws SQLException {
        String sql = "SELECT * FROM items WHERE LOWER(brand) = LOWER(?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brand);
            return collectResults(ps);
        }
    }

    public List<Item> findItemsByStudentId(String studentId) throws SQLException {
        String sql = "SELECT * FROM items WHERE student_id = ? ORDER BY timestamp DESC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            return collectResults(ps);
        }
    }

    /**
     * In-memory predicate filter — loads all items then filters.
     * Mirrors ItemLog.findItemsBy(). Use specific finders for performance.
     */
    public List<Item> findItemsBy(Predicate<Item> criteria) throws SQLException {
        return getAllItems().stream().filter(criteria).toList();
    }

    //MAPPING

    /**
     * Maps a single ResultSet row to an Item.
     * Central mapping point — all reads go through here.
     */
    private Item mapRow(ResultSet rs) throws SQLException {
        return new Item(
            rs.getString("item_name"),
            rs.getString("brand"),
            PrimaryCategory.valueOf(rs.getString("primary_category")),
            SecondaryCategory.valueOf(rs.getString("secondary_category")),
            ItemFunction.valueOf(rs.getString("item_function")),
            ConsumptionContext.valueOf(rs.getString("consumption_context")),
            UsageType.valueOf(rs.getString("usage_type")),
            Replaceability.valueOf(rs.getString("replaceability")),
            ItemStatus.valueOf(rs.getString("status")),
            rs.getInt("quantity"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            rs.getString("student_id")
        );
    }

    private List<Item> queryList(String sql) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return collectRows(rs);
        }
    }

    private List<Item> collectResults(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return collectRows(rs);
        }
    }

    private List<Item> collectRows(ResultSet rs) throws SQLException {
        List<Item> result = new ArrayList<>();
        while (rs.next()) result.add(mapRow(rs));
        return result;
    }
}