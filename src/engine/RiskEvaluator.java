package engine;

// Risk scoring rubric for single-use plastic items.
// Only processes SINGLE_USE_PLASTIC — other categories go through PolicyGate.
//
// Score ranges:
//   0-30   low risk, item may proceed
//   31-70  moderate, flag for review
//   71+    policy violation, disallow

import enums.*;
import java.util.*;
import model.Item;

public class RiskEvaluator {

    private static final int BASE_RISK = 12;

    // Usage type
    private static final int USAGE_SINGLE_USE = 30;
    private static final int USAGE_OTHER       = 18;
    private static final int USAGE_REUSABLE    = -20;

    // Replaceability — HIGH means easy to replace, so penalty is higher
    private static final int REPLACE_HIGH   = 25;
    private static final int REPLACE_MEDIUM = 13;
    private static final int REPLACE_LOW    = 4;

    // Secondary category
    private static final int SECONDARY_FOOD_ACCESSORY    = 18;
    private static final int SECONDARY_BEVERAGE_CONTAINER = 14;
    private static final int SECONDARY_FOOD_CONTAINER    = 10;
    private static final int SECONDARY_PACKAGING         = 4;
    private static final int SECONDARY_OTHER             = 8;

    // Function
    private static final int FUNCTION_UTENSIL   = 12;
    private static final int FUNCTION_CONTAINER = 8;
    private static final int FUNCTION_PACKAGING = 4;
    private static final int FUNCTION_TOOL      = 2;
    private static final int FUNCTION_OTHER     = 6;

    // Consumption context
    private static final int CONTEXT_SCHOOL_USE   = 13;
    private static final int CONTEXT_TAKEOUT      = 9;
    private static final int CONTEXT_FOOD         = 7;
    private static final int CONTEXT_BEVERAGE     = 7;
    private static final int CONTEXT_PERSONAL_USE = 4;
    private static final int CONTEXT_UNKNOWN      = 12;

    // Multiple items of the same type indicate bulk or systematic violation.
    // Bonus scales with quantity, capped at +20.
    private static int calculateQuantityBonus(int quantity) {
        if (quantity <= 1) return 0;
        return Math.min((quantity - 1) * 2, 20);
    }

    public static RiskBreakdown evaluate(Item item) {
        if (item == null)
            throw new IllegalArgumentException("Item cannot be null");
        if (item.getPrimaryCategory() != PrimaryCategory.SINGLE_USE_PLASTIC)
            throw new IllegalArgumentException(
                "RiskEvaluator only evaluates SINGLE_USE_PLASTIC items. Got: "
                + item.getPrimaryCategory());

        List<RiskFactor> factors = new ArrayList<>();

        factors.add(new RiskFactor(
            "Base Risk", "SINGLE_USE_PLASTIC", BASE_RISK,
            "All plastic items carry inherent environmental policy risk"
        ));

        factors.add(evaluateUsageType(item));
        factors.add(evaluateReplaceability(item));
        factors.add(evaluateSecondaryCategory(item));
        factors.add(evaluateFunction(item));
        factors.add(evaluateConsumptionContext(item));

        int quantityBonus = calculateQuantityBonus(item.getQuantity());
        if (quantityBonus > 0) {
            factors.add(new RiskFactor(
                "Quantity", String.valueOf(item.getQuantity()), quantityBonus,
                String.format("Multiple items detected (%d units) - bulk violation", item.getQuantity())
            ));
        }

        int totalScore = factors.stream().mapToInt(RiskFactor::getContribution).sum();
        return new RiskBreakdown(factors, totalScore);
    }

    private static RiskFactor evaluateUsageType(Item item) {
        return switch (item.getUsageType()) {
            case SINGLE_USE -> new RiskFactor("Usage Type", "SINGLE_USE", USAGE_SINGLE_USE,
                    "Item is designed for single-use and disposal");
            case REUSABLE   -> new RiskFactor("Usage Type", "REUSABLE", USAGE_REUSABLE,
                    "Item can be reused, reducing environmental impact");
            case OTHER      -> new RiskFactor("Usage Type", "OTHER", USAGE_OTHER,
                    "Item has uncertain reusability profile");
        };
    }

    private static RiskFactor evaluateReplaceability(Item item) {
        return switch (item.getReplaceability()) {
            case HIGH   -> new RiskFactor("Replaceability", "HIGH", REPLACE_HIGH,
                    "Eco-friendly alternatives readily available");
            case MEDIUM -> new RiskFactor("Replaceability", "MEDIUM", REPLACE_MEDIUM,
                    "Alternatives available but may require adjustment");
            case LOW    -> new RiskFactor("Replaceability", "LOW", REPLACE_LOW,
                    "Limited alternatives available, minimal penalty applied");
        };
    }

    private static RiskFactor evaluateSecondaryCategory(Item item) {
        SecondaryCategory secondary = item.getSecondaryCategory();
        int contribution;
        String description;

        switch (secondary) {
            case FOOD_ACCESSORY      -> { contribution = SECONDARY_FOOD_ACCESSORY;     description = "Classified as food-related accessory"; }
            case BEVERAGE_CONTAINER  -> { contribution = SECONDARY_BEVERAGE_CONTAINER; description = "Classified as beverage container"; }
            case FOOD_CONTAINER      -> { contribution = SECONDARY_FOOD_CONTAINER;     description = "Classified as food storage container"; }
            case PACKAGING           -> { contribution = SECONDARY_PACKAGING;          description = "Classified as packaging material"; }
            default                  -> { contribution = SECONDARY_OTHER;              description = "Item category has standard policy impact"; }
        }

        return new RiskFactor("Secondary Category", secondary.toString(), contribution, description);
    }

    private static RiskFactor evaluateFunction(Item item) {
        ItemFunction function = item.getFunction();
        int contribution;
        String description;

        switch (function) {
            case UTENSIL    -> { contribution = FUNCTION_UTENSIL;   description = "Item serves as eating utensil"; }
            case CONTAINER  -> { contribution = FUNCTION_CONTAINER; description = "Item functions as storage or transport container"; }
            case PACKAGING  -> { contribution = FUNCTION_PACKAGING; description = "Item serves packaging or wrapping purpose"; }
            case TOOL       -> { contribution = FUNCTION_TOOL;      description = "Item functions as utility tool"; }
            default         -> { contribution = FUNCTION_OTHER;     description = "Item has general functional purpose"; }
        }

        return new RiskFactor("Function", function.toString(), contribution, description);
    }

    private static RiskFactor evaluateConsumptionContext(Item item) {
        ConsumptionContext context = item.getContext();
        int contribution;
        String description;

        switch (context) {
            case SCHOOL_USE   -> { contribution = CONTEXT_SCHOOL_USE;   description = "Item intended for use within campus premises"; }
            case TAKEOUT      -> { contribution = CONTEXT_TAKEOUT;      description = "Item associated with takeout food service"; }
            case FOOD         -> { contribution = CONTEXT_FOOD;         description = "Item used in food consumption context"; }
            case BEVERAGE     -> { contribution = CONTEXT_BEVERAGE;     description = "Item used in beverage consumption context"; }
            case PERSONAL_USE -> { contribution = CONTEXT_PERSONAL_USE; description = "Item for general personal use"; }
            case UNKNOWN      -> { contribution = CONTEXT_UNKNOWN;      description = "Context unclear - treated as high risk"; }
            default           -> throw new IllegalStateException("Unknown context: " + context);
        }

        return new RiskFactor("Consumption Context", context.toString(), contribution, description);
    }
}