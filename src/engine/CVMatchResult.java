package engine;

// Mutable during CVEngine scan loop, frozen once returned.
public class CVMatchResult {

    private String productName;
    private float  confidence;
    private String matchLabel; // APPROVED | REJECTED | NO_MATCH

    public CVMatchResult(String productName, float confidence, String matchLabel) {
        this.productName = productName;
        this.confidence  = confidence;
        this.matchLabel  = matchLabel;
    }

    // Called by CVEngine during directory scan to update the best result so far.
    void update(String productName, float confidence, String matchLabel) {
        this.productName = productName;
        this.confidence  = confidence;
        this.matchLabel  = matchLabel;
    }

    public String getProductName() { return productName; }
    public float  getConfidence()  { return confidence;  }
    public String getMatchLabel()  { return matchLabel;  }

    @Override
    public String toString() {
        return String.format("CVMatchResult[%s | %s | %.1f%%]",
                matchLabel, productName, confidence * 100);
    }
}