package engine;

// ORB-based feature matching engine.
// Compares a query image against reference assets in assets/canteen/ and assets/prohibited/.
// Prohibited scanned first - weapons must not lose to canteen background matches.
// Lowe's ratio test handles angle/lighting variance.

import model.Item;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class CVEngine {

    private static final String CANTEEN_DIR       = "assets/canteen/";
    private static final String PROHIBITED_DIR    = "assets/prohibited/";
    private static final float  RATIO_THRESHOLD   = 0.75f;
    private static final int MIN_GOOD_MATCHES     = 10;
    private static final int MIN_GOOD_MATCHES_WEAPON = 5;

    private static final java.util.Set<String> WEAPON_KEYWORDS = java.util.Set.of(
        "45", "gun", "knife", "blade", "weapon", "sharp", "firearm",
        "pistol", "rifle", "revolver", "glock", "handgun", "beretta",
        "45 angle2", "45 angle3", "45 bag", "gun bag"
    );

    public static void loadLibrary() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static CVMatchResult match(Item item) {
        return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
    }

    public static CVMatchResult matchImage(String queryImagePath) {
        return matchImage(queryImagePath, null);
    }

    public static CVMatchResult matchImage(String queryImagePath, Consumer<String> progress) {
        emit(progress, "LOADING IMAGE: " + new File(queryImagePath).getName());

        Mat query = Imgcodecs.imread(queryImagePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (query.empty()) {
            emit(progress, "ERROR: Could not read image file.");
            return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        }

        ORB orb = ORB.create();
        MatOfKeyPoint queryKP   = new MatOfKeyPoint();
        Mat           queryDesc = new Mat();

        orb.detectAndCompute(query, new Mat(), queryKP, queryDesc);

        int qkpCount = (int) queryKP.total();
        emit(progress, "KEYPOINTS DETECTED: " + qkpCount);

        if (queryDesc.empty()) {
            emit(progress, "ERROR: No descriptors found. Image may be too uniform.");
            return new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        }

        CVMatchResult best            = new CVMatchResult("UNKNOWN", 0.0f, "NO_MATCH");
        int           bestGoodMatches = 0;

        // Prohibited first - weapons must not lose to canteen background texture matches
        emit(progress, "SCANNING PROHIBITED REFERENCES...");
        bestGoodMatches = scanDirectory(
            PROHIBITED_DIR, "REJECTED", orb, queryDesc, best, bestGoodMatches, progress
        );

        emit(progress, "SCANNING CANTEEN REFERENCES...");
        bestGoodMatches = scanDirectory(
            CANTEEN_DIR, "APPROVED", orb, queryDesc, best, bestGoodMatches, progress
        );

        emit(progress, "BEST MATCH: " + best.getProductName().toUpperCase()
            + " [" + best.getMatchLabel() + "]"
            + String.format(" %.1f%%", best.getConfidence() * 100));
        emit(progress, "CLASSIFICATION COMPLETE.");

        return best;
    }

    private static int scanDirectory(
            String dir,
            String label,
            ORB orb,
            Mat queryDesc,
            CVMatchResult best,
            int bestGoodMatches,
            Consumer<String> progress
    ) {
        File folder = new File(dir);
        File[] refs = folder.listFiles((f, name) ->
                name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        if (refs == null || refs.length == 0) {
            emit(progress, "  NO REFERENCES IN: " + dir);
            return bestGoodMatches;
        }

        BFMatcher matcher = BFMatcher.create(Core.NORM_HAMMING, false);

        for (File ref : refs) {
            String refName = ref.getName()
                    .replaceAll("\\.[^.]+$", "")
                    .replace("_", " ");

            boolean isWeapon = WEAPON_KEYWORDS.stream()
                    .anyMatch(kw -> refName.toLowerCase().contains(kw));
            int minMatches = isWeapon ? MIN_GOOD_MATCHES_WEAPON : MIN_GOOD_MATCHES;

            emit(progress, "  MATCHING: " + refName.toUpperCase()
                + (isWeapon ? " [WEAPON]" : ""));

            Mat refImg = Imgcodecs.imread(ref.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
            if (refImg.empty()) continue;

            MatOfKeyPoint refKP   = new MatOfKeyPoint();
            Mat           refDesc = new Mat();
            orb.detectAndCompute(refImg, new Mat(), refKP, refDesc);
            if (refDesc.empty()) continue;

            List<MatOfDMatch> knnMatches = new ArrayList<>();
            matcher.knnMatch(queryDesc, refDesc, knnMatches, 2);

            int goodMatches = 0;
            for (MatOfDMatch pair : knnMatches) {
                DMatch[] m = pair.toArray();
                if (m.length == 2 && m[0].distance < RATIO_THRESHOLD * m[1].distance)
                    goodMatches++;
            }

            emit(progress, "    GOOD MATCHES: " + goodMatches
                + " / " + knnMatches.size()
                + " (min: " + minMatches + ")");

            if (goodMatches > bestGoodMatches && goodMatches >= minMatches) {
                bestGoodMatches = goodMatches;

                float confidenceCeil = isWeapon ? 15.0f : 20.0f;
                float confidence     = Math.min(1.0f, goodMatches / confidenceCeil);

                best.update(refName, confidence, label);

                KeyPoint[]    kpArray = refKP.toArray();
                List<float[]> kpList  = new ArrayList<>();
                for (KeyPoint kp : kpArray)
                    kpList.add(new float[]{ (float) kp.pt.x, (float) kp.pt.y, kp.size });
                best.setKeypoints(kpList);

                emit(progress, "    NEW BEST: " + refName.toUpperCase()
                    + String.format(" (%.1f%%)", confidence * 100));
            }
        }
        return bestGoodMatches;
    }

    private static void emit(Consumer<String> progress, String msg) {
        if (progress != null) progress.accept(msg);
    }
}