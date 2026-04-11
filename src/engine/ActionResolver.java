package engine;

// Translates threat levels and decisions into actionable guard instructions.

import enums.Decision;
import model.Item;

public class ActionResolver {

    public static String getActionRecommendation(ThreatLevel level, Item item) {
        if (level == null) throw new IllegalArgumentException("Threat level cannot be null");
        if (item == null)  throw new IllegalArgumentException("Item cannot be null");

        return switch (level) {
            case CRITICAL -> "CRITICAL ALERT - CRITICAL SECURITY PROTOCOL REQUIRED\n" +
                             "ACTION: Do not allow entry\n" +
                             "1. Secure item immediately\n" +
                             "2. Contact provincial police immediately\n" +
                             "3. Detain individual for verification\n" +
                             "4. File incident report and secure item as evidence\n" +
                             "FOLLOW-UP: Contact student affairs within 24 hours";

            case HIGH     -> "HIGH THREAT - IMMEDIATE ACTION REQUIRED\n" +
                             "ACTION: Confiscate and hold\n" +
                             "1. Confiscate item (do not return)\n" +
                             "2. Log student ID and details\n" +
                             "3. Consider student disciplinary action immediately\n" +
                             "FOLLOW-UP: Routine Processing";

            case MEDIUM   -> "POLICY VIOLATION - CONFISCATION REQUIRED\n" +
                             "ACTION: Confiscate item\n" +
                             "1. Inform student of violation\n" +
                             "2. Confiscate item (issue receipt)\n" +
                             "3. Log violation details\n" +
                             "FOLLOW-UP: Routine Processing";

            case LOW      -> "HEALTH POLICY VIOLATION\n" +
                             "ACTION: Confiscate and warn\n" +
                             "1. Confiscate item\n" +
                             "2. Issue verbal warning\n" +
                             "3. Log for records\n" +
                             "FOLLOW-UP: No further action required";

            case NONE     -> "";
        };
    }

    public static String getPlasticPolicyAction(Decision decision, int riskScore) {
        if (decision == null) throw new IllegalArgumentException("Decision cannot be null");

        return switch (decision) {
            case ALLOW -> "RECOMMENDED ACTION:\n" +
                          "Allow item on campus\n" +
                          "No confiscation required\n" +
                          "Item meets policy standards";

            case CONDITIONAL, DISALLOW -> "RECOMMENDED ACTION:\n" +
                          "Confiscate item and issue receipt\n" +
                          "Issue verbal warning about single-use plastics\n" +
                          "Log violation for student records";
        };
    }

    public static boolean requiresImmediateAlert(ThreatLevel level) {
        return level == ThreatLevel.CRITICAL || level == ThreatLevel.HIGH;
    }

    public static String getStatusLabel(ThreatLevel level) {
        return switch (level) {
            case CRITICAL -> "EMERGENCY";
            case HIGH     -> "ALERT";
            case MEDIUM,
                 LOW      -> "HOLD";
            case NONE     -> "PROCEED";
        };
    }
}