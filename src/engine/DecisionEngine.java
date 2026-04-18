package engine;

// Main DSS orchestrator. Three evaluation paths:
//   1. Hard policy violation -> ThreatClassifier -> DISALLOW
//   2. Single-use plastic    -> RiskEvaluator    -> scored decision
//   3. Canteen product       -> CVEngine         -> match decision
//   Everything else          -> ALLOW (out of scope)

import enums.Decision;
import enums.PrimaryCategory;
import java.util.Optional;
import model.Item;

public class DecisionEngine {

    private static final int ALLOW_THRESHOLD       = 30;
    private static final int CONDITIONAL_THRESHOLD = 70;

    public static DecisionResult evaluate(Item item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");

        Optional<String> policyViolation = PolicyGate.checkHardPolicy(item);
        if (policyViolation.isPresent()) {
            return handleHardPolicyViolation(item, policyViolation.get());
        }

        if (item.getPrimaryCategory() == PrimaryCategory.SINGLE_USE_PLASTIC) {
            return handlePlasticItem(item);
        }

        if (item.getPrimaryCategory() == PrimaryCategory.CANTEEN_PRODUCT) {
            return handleCanteenItem(item);
        }

        return handleOutOfScopeItem(item);
    }

    // Called from UI when a CV scan result is already available.
    public static DecisionResult evaluateWithCVResult(Item item, CVMatchResult cvResult) {
        Optional<String> policyViolation = PolicyGate.checkHardPolicy(item);
        if (policyViolation.isPresent()) {
            return handleHardPolicyViolation(item, policyViolation.get());
        }

        if (item.getPrimaryCategory() == PrimaryCategory.CANTEEN_PRODUCT) {
            return handleCanteenItemCV(item, cvResult);
        }

        return evaluate(item);
    }

    private static DecisionResult handleHardPolicyViolation(Item item, String reason) {
        ThreatLevel threat   = ThreatClassifier.classify(item);
        String actionRec     = ActionResolver.getActionRecommendation(threat, item);
        boolean requireAlert = ActionResolver.requiresImmediateAlert(threat);

        return new DecisionResult(item, Decision.DISALLOW, reason,
                null, threat, actionRec, requireAlert);
    }

    private static DecisionResult handlePlasticItem(Item item) {
        RiskBreakdown breakdown = RiskEvaluator.evaluate(item);
        int score               = breakdown.getTotalScore();
        Decision decision       = mapScoreToDecision(score);
        String reason           = generatePlasticReason(decision, score);
        String actionRec        = ActionResolver.getPlasticPolicyAction(decision, score);

        return new DecisionResult(item, decision, reason,
                breakdown, ThreatLevel.NONE, actionRec, false);
    }

    // Used when no CV result is available — holds for manual verification.
    private static DecisionResult handleCanteenItem(Item item) {
        return new DecisionResult(item, Decision.CONDITIONAL,
                "CV scan inconclusive — no confident match found.",
                null, ThreatLevel.NONE,
                "Hold item. Request manual admin verification.",
                false);
    }

    private static DecisionResult handleCanteenItemCV(Item item, CVMatchResult matchResult) {
        Decision decision;
        String reason;
        String actionRec;

        switch (matchResult.getMatchLabel()) {
            case "APPROVED" -> {
                decision  = Decision.DISALLOW;
                reason    = "Canteen product detected: " + matchResult.getProductName().toUpperCase() +
                            " (" + String.format("%.1f", matchResult.getConfidence() * 100) + "% match). " +
                            "Product is already available for purchase inside school premises.";
                actionRec = "Item not permitted through gate.\n" +
                            "Inform student this product is sold inside the school canteen.\n" +
                            "Item may be left at entrance for retrieval after school hours.";
            }
            case "REJECTED" -> {
                decision  = Decision.DISALLOW;
                reason    = "CV match: product not on approved canteen list.";
                actionRec = "Confiscate item. Log and notify admin.";
            }
            default -> {
                decision  = Decision.CONDITIONAL;
                reason    = "CV scan inconclusive - no confident match found.";
                actionRec = "Hold item. Request manual admin verification.";
            }
        }

        return new DecisionResult(item, decision, reason,
                null, ThreatLevel.NONE, actionRec, false);
    }

    private static DecisionResult handleOutOfScopeItem(Item item) {
        return new DecisionResult(item, Decision.ALLOW,
                "Item not within scope of plastic policy",
                null, ThreatLevel.NONE,
                "No hard policy violation. Item permitted.",
                false);
    }

    private static Decision mapScoreToDecision(int score) {
        if (score <= ALLOW_THRESHOLD)       return Decision.ALLOW;
        if (score <= CONDITIONAL_THRESHOLD) return Decision.CONDITIONAL;
        return Decision.DISALLOW;
    }

    private static String generatePlasticReason(Decision decision, int score) {
        return switch (decision) {
            case ALLOW       -> String.format("Within acceptable risk parameters (score: %d - %d)", score, ALLOW_THRESHOLD);
            case CONDITIONAL -> String.format("Moderate policy concern (score: %d)", score);
            case DISALLOW    -> String.format("Exceeds plastic policy threshold (score: %d - %d)", score, CONDITIONAL_THRESHOLD + 1);
        };
    }
}