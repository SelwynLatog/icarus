package engine;

import model.Item;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.*;

/*
 * ORB-based feature matching engine.
 * Compares a query image against reference assets in assets/canteen/ and assets/prohibited/.
 * Canteen scanned first — approved products take priority over prohibited.
 * Lowe's ratio test handles angle/lighting variance.
 */
public class CVEngine {

    private static final String CANTEEN_DIR    = "assets/canteen/";
    private static final String PROHIBITED_DIR = "assets/prohibited/";

    private static final float RATIO_THRESHOLD = 0.75f;
    private static final int   MIN_GOOD_MATCHES = 10;

    public static void loadLibrary() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // Stub — item path not provided yet, called by DecisionEngine canteen path
    public static CVMatchResult match(Item item) {
        return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
    }

    // Full match against a specific image file — called from UI scan panel
    public static CVMatchResult matchImage(String queryImagePath) {
        Mat query = Imgcodecs.imread(queryImagePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (query.empty()) {
            return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        }

        ORB orb = ORB.create();
        MatOfKeyPoint queryKP   = new MatOfKeyPoint();
        Mat           queryDesc = new Mat();
        orb.detectAndCompute(query, new Mat(), queryKP, queryDesc);

        if (queryDesc.empty()) {
            return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        }

        CVMatchResult best          = new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        int           bestGoodMatches = 0;

        // Canteen first — approved takes priority
        bestGoodMatches = scanDirectory(
            CANTEEN_DIR, "APPROVED", orb, queryDesc, best, bestGoodMatches
        );

        // Prohibited second
        scanDirectory(
            PROHIBITED_DIR, "REJECTED", orb, queryDesc, best, bestGoodMatches
        );

        return best;
    }

    private static int scanDirectory(
            String dir,
            String label,
            ORB orb,
            Mat queryDesc,
            CVMatchResult best,
            int bestGoodMatches
    ) {
        File folder = new File(dir);
        File[] refs = folder.listFiles((f, name) ->
                name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));

        if (refs == null || refs.length == 0) return bestGoodMatches;

        BFMatcher matcher = BFMatcher.create(Core.NORM_HAMMING, false);

        for (File ref : refs) {
            Mat refImg = Imgcodecs.imread(ref.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
            if (refImg.empty()) continue;

            MatOfKeyPoint refKP   = new MatOfKeyPoint();
            Mat           refDesc = new Mat();
            orb.detectAndCompute(refImg, new Mat(), refKP, refDesc);
            if (refDesc.empty()) continue;

            List<MatOfDMatch> knnMatches = new ArrayList<>();
            matcher.knnMatch(queryDesc, refDesc, knnMatches, 2);

            // Lowe's ratio test
            int goodMatches = 0;
            for (MatOfDMatch pair : knnMatches) {
                DMatch[] m = pair.toArray();
                if (m.length == 2 && m[0].distance < RATIO_THRESHOLD * m[1].distance) {
                    goodMatches++;
                }
            }

            if (goodMatches > bestGoodMatches && goodMatches >= MIN_GOOD_MATCHES) {
                bestGoodMatches = goodMatches;
                String productName = ref.getName()
                        .replaceAll("\\.[^.]+$", "")
                        .replace("_", " ");
                float confidence = Math.min(1.0f, goodMatches / 20.0f);
                best.update(productName, confidence, label);
            }
        }

        return bestGoodMatches;
    }
}