package engine;

// Classifies threat level based on item category, independent of risk scoring.
// Threat level determines response urgency and whether alerts are triggered.

import enums.*;
import model.Item;

public class ThreatClassifier {

    public static ThreatLevel classify(Item item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");

        PrimaryCategory primary     = item.getPrimaryCategory();
        SecondaryCategory secondary = item.getSecondaryCategory();

        if (secondary == SecondaryCategory.FIREARM ||
            secondary == SecondaryCategory.ILLEGAL_SUBSTANCE)
            return ThreatLevel.CRITICAL;

        if (primary == PrimaryCategory.WEAPON ||
            secondary == SecondaryCategory.SHARP_OBJECT ||
            secondary == SecondaryCategory.CHEMICAL_SUBSTANCE)
            return ThreatLevel.HIGH;

        if (primary == PrimaryCategory.ALCOHOL ||
            primary == PrimaryCategory.PROHIBITED_SUBSTANCE ||
            secondary == SecondaryCategory.ALCOHOLIC_BEVERAGE)
            return ThreatLevel.MEDIUM;

        if (primary == PrimaryCategory.TOBACCO ||
            secondary == SecondaryCategory.SMOKING_PRODUCT ||
            secondary == SecondaryCategory.ELECTRONIC_SMOKING)
            return ThreatLevel.LOW;

        return ThreatLevel.NONE;
    }
}