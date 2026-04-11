package engine;

// A single scored factor contributing to an item's total risk score.
// Captures what was evaluated, what value was found, its score impact, and why.
public class RiskFactor {

    private final String factorName;
    private final String factorValue;
    private final int contribution;
    private final String description;

    public RiskFactor(String factorName, String factorValue, int contribution, String description) {
        if (factorName == null || factorName.trim().isEmpty())
            throw new IllegalArgumentException("Factor name cannot be null or empty");
        if (factorValue == null || factorValue.trim().isEmpty())
            throw new IllegalArgumentException("Factor value cannot be null or empty");
        if (description == null || description.trim().isEmpty())
            throw new IllegalArgumentException("Description cannot be null or empty");

        this.factorName  = factorName;
        this.factorValue = factorValue;
        this.contribution = contribution;
        this.description = description;
    }

    public String getFactorName()  { return factorName;   }
    public String getFactorValue() { return factorValue;  }
    public int getContribution()   { return contribution; }
    public String getDescription() { return description;  }

    public boolean isPositiveContribution() { return contribution > 0;  }
    public boolean isNegativeContribution() { return contribution < 0;  }
    public int getAbsoluteImpact()          { return Math.abs(contribution); }

    @Override
    public String toString() {
        return String.format("[%s: %s] %s%d - %s",
            factorName, factorValue,
            (contribution >= 0 ? "+" : ""),
            contribution, description);
    }
}