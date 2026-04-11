package test;

import engine.CVEngine;
import engine.CVMatchResult;

/*
 * Manual CV test — put one image in assets/canteen/ and one in assets/prohibited/
 * then update the paths below to verify matching works before wiring into the UI.
 */
public class CVTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" ICARUS - CV Engine Test");
        System.out.println("========================================\n");

        CVEngine.loadLibrary();

        // Update these paths to actual images you have in your assets folders
        String[] testImages = {
            "assets/canteen/coca_cola.png",
            "assets/prohibited/cig_pack.jpg"
        };

        for (String path : testImages) {
            System.out.println("Scanning: " + path);
            CVMatchResult result = CVEngine.matchImage(path);
            System.out.println("  Result : " + result);
            System.out.println();
        }

        System.out.println("========================================");
        System.out.println(" CV test complete.");
        System.out.println("========================================");
    }
}