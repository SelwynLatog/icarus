package service;

// Business logic and validation layer for item operations.
// Delegates storage to ItemDAO, links items to students via StudentService.

import enums.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import model.Item;
import storage.dao.ItemDAO;

public class ItemService {

    private final ItemDAO itemDAO;
    private final StudentService studentService;

    public ItemService(ItemDAO itemDAO, StudentService studentService) {
        if (itemDAO == null)        throw new IllegalArgumentException("ItemDAO cannot be null");
        if (studentService == null) throw new IllegalArgumentException("StudentService cannot be null");
        this.itemDAO        = itemDAO;
        this.studentService = studentService;
    }

    public int registerNewItem(
            String studentId,
            String itemName,
            String brand,
            PrimaryCategory primaryCategory,
            SecondaryCategory secondaryCategory,
            ItemFunction function,
            ConsumptionContext context,
            UsageType usageType,
            Replaceability replaceability,
            int quantity
    ) {
        validateItemDetails(itemName, primaryCategory, secondaryCategory,
                function, context, usageType, replaceability, quantity);

        Item item = new Item(itemName, brand, primaryCategory, secondaryCategory,
                function, context, usageType, replaceability,
                ItemStatus.HELD, quantity, LocalDateTime.now(), studentId);

        try {
            int itemId = itemDAO.addItem(item);
            if (studentId != null && !studentId.trim().isEmpty()) {
                boolean linked = studentService.linkItemToStudent(studentId, itemId);
                if (!linked)
                    System.err.println("Warning: Could not link item " + itemId + " to student " + studentId);
            }
            return itemId;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register item: " + e.getMessage(), e);
        }
    }

    public boolean releaseItem(int id) {
        try {
            return itemDAO.updateItemStatus(id, ItemStatus.RELEASED);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to release item: " + e.getMessage(), e);
        }
    }

    public Optional<Item> findItemById(int id) {
        try {
            return itemDAO.findItemById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find item: " + e.getMessage(), e);
        }
    }

    public List<Item> getAllItems() {
        try {
            return itemDAO.getAllItems();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get items: " + e.getMessage(), e);
        }
    }

    public List<storage.dao.ItemEntry> getAllItemsWithIds() {
        try {
            return itemDAO.getAllItemsWithIds();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get items with IDs: " + e.getMessage(), e);
        }
    }

    public List<Item> findItemsByCategory(PrimaryCategory category) {
        try {
            return itemDAO.findItemsByCategory(category);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find items by category: " + e.getMessage(), e);
        }
    }

    public List<Item> findItemsByStatus(ItemStatus status) {
        try {
            return itemDAO.findItemsByStatus(status);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find items by status: " + e.getMessage(), e);
        }
    }

    public List<Item> findItemsByBrand(String brand) {
        try {
            return itemDAO.findItemsByBrand(brand);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find items by brand: " + e.getMessage(), e);
        }
    }

    public boolean removeItemById(int id) {
        try {
            return itemDAO.removeItemById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove item: " + e.getMessage(), e);
        }
    }

    private void validateItemDetails(
            String itemName, PrimaryCategory primaryCategory,
            SecondaryCategory secondaryCategory, ItemFunction function,
            ConsumptionContext context, UsageType usageType,
            Replaceability replaceability, int quantity
    ) {
        requireNonBlank(itemName, "Item name");
        requireNonNull(primaryCategory,   "Primary category");
        requireNonNull(secondaryCategory, "Secondary category");
        requireNonNull(function,          "Item function");
        requireNonNull(context,           "Consumption context");
        requireNonNull(usageType,         "Usage type");
        requireNonNull(replaceability,    "Replaceability");
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be > 0, got: " + quantity);
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
    }

    private void requireNonNull(Object value, String fieldName) {
        if (value == null)
            throw new IllegalArgumentException(fieldName + " cannot be null");
    }
}