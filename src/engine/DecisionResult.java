package engine;

// Immutable result produced by DecisionEngine for a single item evaluation.
// Contains the decision, risk breakdown, threat level, and guard instructions.

import enums.Decision;
import model.Item;

public class DecisionResult {

    private final Item item;
    private final Decision decision;
    private final String reason;
    private final RiskBreakdown breakdown; // null if item was not risk-scored
    private final ThreatLevel threatLevel;
    private final String actionRecommendation;
    private final boolean requiresImmediateAlert;

    public DecisionResult(
            Item item,
            Decision decision,
            String reason,
            RiskBreakdown breakdown,
            ThreatLevel threatLevel,
            String actionRecommendation,
            boolean requiresImmediateAlert
    ) {
        if (item == null)   throw new IllegalArgumentException("Item cannot be null");
        if (decision == null) throw new IllegalArgumentException("Decision cannot be null");
        if (reason == null || reason.trim().isEmpty())
            throw new IllegalArgumentException("Reason cannot be null or empty");
        if (threatLevel == null)
            throw new IllegalArgumentException("Threat level cannot be null");
        if (actionRecommendation == null)
            throw new IllegalArgumentException("Action recommendation cannot be null");

        this.item                   = item;
        this.decision               = decision;
        this.reason                 = reason;
        this.breakdown              = breakdown;
        this.threatLevel            = threatLevel;
        this.actionRecommendation   = actionRecommendation;
        this.requiresImmediateAlert = requiresImmediateAlert;
    }

    public Item getItem()                    { return item;                   }
    public Decision getDecision()            { return decision;               }
    public String getReason()                { return reason;                 }
    public RiskBreakdown getBreakdown()      { return breakdown;              }
    public ThreatLevel getThreatLevel()      { return threatLevel;            }
    public String getActionRecommendation()  { return actionRecommendation;   }
    public boolean requiresImmediateAlert()  { return requiresImmediateAlert; }

    public boolean hasRiskScore()           { return breakdown != null; }
    public int getRiskScore()               { return hasRiskScore() ? breakdown.getTotalScore() : -1; }
    public boolean isHardPolicyViolation()  { return threatLevel != ThreatLevel.NONE && !hasRiskScore(); }
    public boolean isPlasticPolicyViolation() { return hasRiskScore() && decision != Decision.ALLOW; }

    @Override
    public String toString() {
        return String.format("DecisionResult[item=%s, decision=%s, threat=%s, score=%s]",
                item.getItemName(), decision, threatLevel,
                hasRiskScore() ? breakdown.getTotalScore() : "N/A");
    }
}