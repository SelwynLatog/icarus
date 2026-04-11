package engine;

// Barcode generation and scanning via ZXing (Code128 format).
// Generate: student ID -> barcode image saved to assets/barcodes/
// Scan: barcode image -> student ID string for StudentDAO lookup

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class BarcodeEngine {

    private static final String BARCODE_DIR = "assets/barcodes/";

    public static Path generate(String studentId) throws WriterException, IOException {
        Code128Writer writer = new Code128Writer();
        BitMatrix matrix = writer.encode(studentId, BarcodeFormat.CODE_128, 400, 100);
        Path outPath = Path.of(BARCODE_DIR + studentId.replace("/", "-") + ".png");
        MatrixToImageWriter.writeToPath(matrix, "PNG", outPath);
        return outPath;
    }

    public static Optional<String> scan(String imagePath) {
        try {
            BufferedImage img = ImageIO.read(Path.of(imagePath).toFile());
            if (img == null) return Optional.empty();

            // TRY_HARDER improves detection on complex or low-contrast images
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13,
                BarcodeFormat.QR_CODE
            ));

            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap    = new BinaryBitmap(new HybridBinarizer(source));
            Result result          = new MultiFormatReader().decode(bitmap, hints);
            return Optional.of(result.getText());

        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            System.err.println("BarcodeEngine scan error: " + e.getMessage());
            return Optional.empty();
        }
    }
}