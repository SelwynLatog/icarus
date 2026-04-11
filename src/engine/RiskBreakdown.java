package engine;

// Complete risk analysis for an evaluated item.
// Stores all scoring factors for explainability — admins can see exactly what drove the score.

import java.util.*;
import java.util.stream.Collectors;

public class RiskBreakdown {

    private final List<RiskFactor> factors;
    private final int totalScore;

    public RiskBreakdown(List<RiskFactor> factors, int totalScore) {
        if (factors == null || factors.isEmpty())
            throw new IllegalArgumentException("Factors list cannot be null or empty");

        int calculated = factors.stream().mapToInt(RiskFactor::getContribution).sum();
        if (calculated != totalScore)
            throw new IllegalArgumentException(
                String.format("Total score mismatch: provided %d, calculated %d", totalScore, calculated));

        this.factors    = new ArrayList<>(factors);
        this.totalScore = totalScore;
    }

    public List<RiskFactor> getAllFactors() {
        return Collections.unmodifiableList(factors);
    }

    public int getTotalScore() { return totalScore; }

    public List<RiskFactor> getPositiveContributors() {
        return factors.stream()
                .filter(RiskFactor::isPositiveContribution)
                .sorted(Comparator.comparingInt(RiskFactor::getContribution).reversed())
                .collect(Collectors.toList());
    }

    public List<RiskFactor> getNegativeContributors() {
        return factors.stream()
                .filter(RiskFactor::isNegativeContribution)
                .sorted(Comparator.comparingInt(RiskFactor::getAbsoluteImpact).reversed())
                .collect(Collectors.toList());
    }

    public List<RiskFactor> getTopContributors(int n) {
        if (n <= 0) throw new IllegalArgumentException("N must be positive, got: " + n);
        return getPositiveContributors().stream().limit(n).collect(Collectors.toList());
    }

    public Optional<RiskFactor> getLargestMitigatingFactor() {
        return getNegativeContributors().stream().findFirst();
    }

    public String generateExplanation() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Risk Score: %d\n\n", totalScore));

        List<RiskFactor> top = getTopContributors(3);
        if (!top.isEmpty()) {
            sb.append("Primary Risk Drivers:\n");
            for (RiskFactor f : top) {
                sb.append(String.format("  %s (%s): %+d\n", f.getFactorName(), f.getFactorValue(), f.getContribution()));
                sb.append(String.format("    %s\n", f.getDescription()));
            }
            sb.append("\n");
        }

        Optional<RiskFactor> mitigator = getLargestMitigatingFactor();
        if (mitigator.isPresent()) {
            RiskFactor f = mitigator.get();
            sb.append("Mitigating Factor:\n");
            sb.append(String.format("  %s (%s): %+d\n", f.getFactorName(), f.getFactorValue(), f.getContribution()));
            sb.append(String.format("    %s\n\n", f.getDescription()));
        } else {
            sb.append("No mitigating factors identified.\n\n");
        }

        if (totalScore >= 71)
            sb.append("Item significantly exceeds the policy threshold and should be disallowed.");
        else if (totalScore >= 31)
            sb.append("Item shows moderate policy concern and requires conditional review.");
        else
            sb.append("Item falls within acceptable risk parameters.");

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("RiskBreakdown[totalScore=%d, factors=%d]", totalScore, factors.size());
    }
}