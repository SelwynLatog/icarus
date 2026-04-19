package engine;

import java.util.List;
import java.util.ArrayList;

// Mutable during CVEngine scan loop, frozen once returned.
public class CVMatchResult {

    private String productName;
    private float  confidence;
    private String matchLabel; // APPROVED | REJECTED | NO_MATCH

    // ORB keypoints of the best matching reference image — (x, y, size)
    private List<float[]> keypoints = new ArrayList<>();

    public CVMatchResult(String productName, float confidence, String matchLabel) {
        this.productName = productName;
        this.confidence  = confidence;
        this.matchLabel  = matchLabel;
    }

    void update(String productName, float confidence, String matchLabel) {
        this.productName = productName;
        this.confidence  = confidence;
        this.matchLabel  = matchLabel;
    }

    void setKeypoints(List<float[]> keypoints) {
        this.keypoints = keypoints != null ? keypoints : new ArrayList<>();
    }

    public String     getProductName() { return productName; }
    public float      getConfidence()  { return confidence;  }
    public String     getMatchLabel()  { return matchLabel;  }
    public List<float[]> getKeypoints() { return keypoints; }

    @Override
    public String toString() {
        return String.format("CVMatchResult[%s | %s | %.1f%%]",
                matchLabel, productName, confidence * 100);
    }
}