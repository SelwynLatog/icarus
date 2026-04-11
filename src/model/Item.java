package model;

import enums.*;
import java.time.LocalDateTime;

// Immutable value object representing a physical item logged at the entrance.
// Use withStatus() and withQuantity() to produce updated copies.
public class Item {
    private final String itemName;
    private final String brand;
    private final PrimaryCategory primaryCategory;
    private final SecondaryCategory secondaryCategory;
    private final ItemFunction function;
    private final ConsumptionContext context;
    private final UsageType usageType;
    private final Replaceability replaceability;
    private final ItemStatus status;
    private final int quantity;
    private final LocalDateTime timestamp;
    private final String studentId;

    public Item(
            String itemName,
            String brand,
            PrimaryCategory primaryCategory,
            SecondaryCategory secondaryCategory,
            ItemFunction function,
            ConsumptionContext context,
            UsageType usageType,
            Replaceability replaceability,
            ItemStatus status,
            int quantity,
            LocalDateTime timestamp,
            String studentId
    ) {
        if (itemName == null || itemName.trim().isEmpty())
            throw new IllegalArgumentException("Item name cannot be null or empty");
        if (primaryCategory == null)
            throw new IllegalArgumentException("Primary category cannot be null");
        if (secondaryCategory == null)
            throw new IllegalArgumentException("Secondary category cannot be null");
        if (function == null)
            throw new IllegalArgumentException("Item function cannot be null");
        if (context == null)
            throw new IllegalArgumentException("Consumption context cannot be null");
        if (usageType == null)
            throw new IllegalArgumentException("Usage type cannot be null");
        if (replaceability == null)
            throw new IllegalArgumentException("Replaceability cannot be null");
        if (status == null)
            throw new IllegalArgumentException("Item status cannot be null");
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than 0, got: " + quantity);
        if (timestamp == null)
            throw new IllegalArgumentException("Timestamp cannot be null");

        this.itemName        = itemName;
        this.brand           = brand;
        this.primaryCategory = primaryCategory;
        this.secondaryCategory = secondaryCategory;
        this.function        = function;
        this.context         = context;
        this.usageType       = usageType;
        this.replaceability  = replaceability;
        this.status          = status;
        this.quantity        = quantity;
        this.timestamp       = timestamp;
        this.studentId       = studentId;
    }

    public String getItemName()              { return itemName;         }
    public String getBrand()                 { return brand;            }
    public PrimaryCategory getPrimaryCategory()     { return primaryCategory;   }
    public SecondaryCategory getSecondaryCategory() { return secondaryCategory; }
    public ItemFunction getFunction()        { return function;         }
    public ConsumptionContext getContext()   { return context;          }
    public UsageType getUsageType()          { return usageType;        }
    public Replaceability getReplaceability(){ return replaceability;   }
    public ItemStatus getStatus()            { return status;           }
    public int getQuantity()                 { return quantity;         }
    public LocalDateTime getTimestamp()      { return timestamp;        }
    public String getStudentId()             { return studentId;        }

    public Item withStatus(ItemStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Status cannot be null");
        return new Item(itemName, brand, primaryCategory, secondaryCategory,
                function, context, usageType, replaceability,
                newStatus, quantity, timestamp, studentId);
    }

    public Item withQuantity(int newQuantity) {
        if (newQuantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than 0, got: " + newQuantity);
        return new Item(itemName, brand, primaryCategory, secondaryCategory,
                function, context, usageType, replaceability,
                status, newQuantity, timestamp, studentId);
    }

    @Override
    public String toString() {
        return itemName + " [" +
                primaryCategory + " | " + secondaryCategory + " | " +
                function + " | " + context + " | " +
                usageType + " | " + replaceability + " | " +
                status + " | Qty: " + quantity + " | " +
                timestamp.toLocalDate() +
                (brand != null ? " | " + brand : "") +
                (studentId != null ? " | Student: " + studentId : "") + "]";
    }
}