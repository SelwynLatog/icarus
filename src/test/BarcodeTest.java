package test;

import engine.BarcodeEngine;
import java.nio.file.Path;
import java.util.Optional;

public class BarcodeTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" ICARUS - Barcode Engine Test");
        System.out.println("========================================\n");

        String[] demoIds = {
            "2024-00001", "2024-00002", "2023-00003",
            "2023-00004", "2023-00005", "VISITOR-001"
        };

        // Generate barcodes for all demo student IDs
        System.out.println("[ Generating barcodes ]");
        for (String id : demoIds) {
            try {
                Path out = BarcodeEngine.generate(id);
                System.out.println("  GEN  " + id + " → " + out);
            } catch (Exception e) {
                System.out.println("  FAIL " + id + " → " + e.getMessage());
            }
        }

        // Scan them back and verify round-trip
        System.out.println("\n[ Scanning barcodes back ]");
        for (String id : demoIds) {
            String path = "assets/barcodes/" + id.replace("/", "-") + ".png";
            Optional<String> result = BarcodeEngine.scan(path);
            if (result.isPresent() && result.get().equals(id)) {
                System.out.println("  PASS " + id);
            } else {
                System.out.println("  FAIL " + id + " → got: " + result.orElse("nothing"));
            }
        }

        System.out.println("\n========================================");
        System.out.println(" Barcode test complete.");
        System.out.println("========================================");
    }
}