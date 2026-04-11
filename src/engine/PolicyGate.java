package engine;

// First checkpoint in the decision pipeline.
// Enforces absolute campus policy rules with zero tolerance.
// Items that pass go to risk evaluation. Items that fail are immediately disallowed.

import enums.*;
import java.util.Optional;
import model.Item;

public class PolicyGate {

    // Returns a violation reason if the item breaks a hard policy, empty if it passes.
    public static Optional<String> checkHardPolicy(Item item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");

        PrimaryCategory primary     = item.getPrimaryCategory();
        SecondaryCategory secondary = item.getSecondaryCategory();

        switch (primary) {
            case WEAPON               -> { return Optional.of("Weapons prohibited under campus safety policy."); }
            case ALCOHOL              -> { return Optional.of("Alcoholic beverages prohibited on campus premises."); }
            case TOBACCO              -> { return Optional.of("Tobacco products prohibited under campus health policy."); }
            case PROHIBITED_SUBSTANCE -> { return Optional.of("Prohibited substances not allowed on campus."); }
            default                   -> {}
        }

        switch (secondary) {
            case FIREARM            -> { return Optional.of("Firearms prohibited under campus safety policy."); }
            case ILLEGAL_SUBSTANCE  -> { return Optional.of("Illegal substances prohibited by law."); }
            case SHARP_OBJECT       -> { return Optional.of("Sharp objects prohibited under campus safety policy."); }
            case SMOKING_PRODUCT,
                 ELECTRONIC_SMOKING -> { return Optional.of("Smoking products prohibited on campus premises."); }
            case ALCOHOLIC_BEVERAGE -> { return Optional.of("Alcoholic beverages prohibited on campus premises."); }
            case CHEMICAL_SUBSTANCE -> { return Optional.of("Unregulated chemical substances prohibited on campus."); }
            default                 -> {}
        }

        return Optional.empty();
    }
}