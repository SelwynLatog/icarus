package ui;

// CV scan tab panel.
// Handles image selection, ORB scan worker, YOLO result mapping, webcam lifecycle,
// and the analysis overlay dialog. Fires onEvaluated when a result is ready.

import engine.CVEngine;
import engine.CVMatchResult;
import engine.CVSocketClient;
import engine.DecisionEngine;
import engine.DecisionResult;
import enums.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import model.Item;
import ui.theme.IcarusTheme;

public class CVScanPanel extends JPanel {

    // CV product name keywords used to map a scan result to the right item category
    private static final String[] FIREARM_KEYWORDS = {
        "gun", "45", "firearm", "pistol", "rifle", "revolver",
        "glock", "handgun", "weapon", "beretta", "m16", "ar"
    };
    private static final String[] BLADE_KEYWORDS   = { "knife", "blade", "sharp" };
    private static final String[] PLASTIC_KEYWORDS = { "plastic", "spork", "cup", "cellophane" };
    private static final String[] TOBACCO_KEYWORDS = { "cig", "vape", "tobacco" };
    private static final String[] ALCOHOL_KEYWORDS = { "alcohol", "beer", "wine", "liquor" };

    // Confidence floor for treating a canteen match as genuine
    private static final float CANTEEN_CONFIDENCE_THRESHOLD = 0.70f;

    // How many log lines to keep in memory during a CV scan
    private static final int CV_LOG_MAX_LINES = 60;

    // Pulse animation tick rate in milliseconds
    private static final int PULSE_TIMER_MS = 50;

    private static final int CV_IMAGE_HEIGHT = 220;

    private final AppFrame frame;

    // Called after a successful evaluation with (item, result)
    private final BiConsumer<Item, DecisionResult> onEvaluated;

    // Supplies the current student ID - may return null
    private final Supplier<String> studentIdSupplier;

    // Shared scan state - written here, read by GuardDashboard for logging
    private String        lastScannedImagePath;
    private CVMatchResult lastCVResult;

    // CV visual rendering state
    private final List<String> cvLog      = new ArrayList<>();
    private BufferedImage      cvBaseImage = null;
    private List<float[]>      cvKeypoints = null;
    private boolean            cvScanning  = false;

    // Pulse animation
    private javax.swing.Timer cvPulseTimer = null;
    private float             cvPulsePhase = 0f;

    // Result labels shown below the image panel
    private JLabel lblCVResult;
    private JLabel lblCVConfidence;

    // The custom-painted image panel - repainted by the pulse timer and scan worker
    private JPanel cvImagePanel;

    // Webcam state
    private Process        cvServerProcess  = null;
    private CVSocketClient cvSocketClient   = null;
    private JButton        btnStartWebcam   = null;
    private JButton        btnStopWebcam    = null;
    private JButton        btnCaptureWebcam = null;

    public CVScanPanel(AppFrame frame,
                       BiConsumer<Item, DecisionResult> onEvaluated,
                       Supplier<String> studentIdSupplier) {
        this.frame             = frame;
        this.onEvaluated       = onEvaluated;
        this.studentIdSupplier = studentIdSupplier;
        setLayout(new BorderLayout());
        setOpaque(false);
        add(buildCVTab(), BorderLayout.CENTER);
    }

    // --- Getters for GuardDashboard to read scan state when logging ---

    public String getLastScannedImagePath() { return lastScannedImagePath; }
    public CVMatchResult getLastCVResult()  { return lastCVResult; }

    // Called by GuardDashboard when loading a logged entry from the queue
    public void showLoggedImage(BufferedImage img, String decision) {
        cvBaseImage = img;
        cvKeypoints = null;
        cvScanning  = false;
        cvImagePanel.repaint();
        lblCVResult.setText("SCAN RESULT: " + decision);
        lblCVConfidence.setText("CONFIDENCE: logged");
    }

    // Clears the image panel state (e.g. when queue entry has no image)
    public void clearImage() {
        cvBaseImage = null;
        cvKeypoints = null;
        cvLog.clear();
        cvScanning  = false;
        cvImagePanel.repaint();
        lblCVResult.setText("SCAN RESULT: ---");
        lblCVConfidence.setText("CONFIDENCE: ---");
    }

    // Builds the CV scan tab: image preview, result labels, and action buttons
    private JPanel buildCVTab() {
        JPanel panel = IcarusTheme.makePanel(null);
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        cvImagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintCVPanel((Graphics2D) g);
            }
        };

        cvImagePanel.setBackground(new Color(0x1A1A14));
        cvImagePanel.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        cvImagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cvImagePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!cvScanning && cvBaseImage != null && lastCVResult != null
                        && !"NO_MATCH".equals(lastCVResult.getMatchLabel())) {
                    showCVAnalysisOverlay();
                }
            }
        });

        JPanel imgHolder = new JPanel(new BorderLayout());
        imgHolder.setOpaque(false);
        imgHolder.setPreferredSize(new Dimension(0, CV_IMAGE_HEIGHT));
        imgHolder.setMinimumSize(new Dimension(0, CV_IMAGE_HEIGHT));
        imgHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, CV_IMAGE_HEIGHT));
        imgHolder.add(cvImagePanel, BorderLayout.CENTER);

        JPanel resultBlock = new JPanel();
        resultBlock.setLayout(new BoxLayout(resultBlock, BoxLayout.Y_AXIS));
        resultBlock.setOpaque(false);

        lblCVResult = new JLabel("SCAN RESULT:");
        lblCVResult.setFont(IcarusTheme.FONT_MONO_HEADER);
        lblCVResult.setForeground(IcarusTheme.TEXT_DIM);
        lblCVResult.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblCVConfidence = new JLabel("CONFIDENCE:");
        lblCVConfidence.setFont(IcarusTheme.FONT_MONO_BASE);
        lblCVConfidence.setForeground(IcarusTheme.TEXT_DIM);
        lblCVConfidence.setAlignmentX(Component.LEFT_ALIGNMENT);

        resultBlock.add(lblCVResult);
        resultBlock.add(Box.createVerticalStrut(4));
        resultBlock.add(lblCVConfidence);

        JPanel btnRow = new JPanel(new GridLayout(2, 2, 6, 4));
        btnRow.setOpaque(false);
        JButton btnPick = IcarusTheme.makeButton("SELECT IMAGE");
        JButton btnScan = IcarusTheme.makeButton("RUN CV SCAN");
        btnPick.addActionListener(e -> doCVPick());
        btnScan.addActionListener(e -> doCVEvaluate());

        btnStartWebcam   = IcarusTheme.makeButton("START WEBCAM");
        btnStopWebcam    = IcarusTheme.makeButton("STOP WEBCAM");
        btnCaptureWebcam = IcarusTheme.makeButton("CAPTURE & SCAN");
        btnStopWebcam.setEnabled(false);
        btnCaptureWebcam.setEnabled(false);

        btnStartWebcam.addActionListener(e -> doStartWebcam());
        btnStopWebcam.addActionListener(e -> doStopWebcam());
        btnCaptureWebcam.addActionListener(e -> doCaptureAndScan());

        btnRow.add(btnPick);
        btnRow.add(btnScan);
        btnRow.add(btnStartWebcam);
        btnRow.add(btnStopWebcam);

        // Capture button gets its own full-width row
        JPanel captureRow = new JPanel(new GridLayout(1, 1));
        captureRow.setOpaque(false);
        captureRow.add(btnCaptureWebcam);

        JPanel southBlock = new JPanel();
        southBlock.setLayout(new BoxLayout(southBlock, BoxLayout.Y_AXIS));
        southBlock.setOpaque(false);
        southBlock.add(btnRow);
        southBlock.add(Box.createVerticalStrut(4));
        southBlock.add(captureRow);

        panel.add(imgHolder,    BorderLayout.CENTER);
        panel.add(resultBlock,  BorderLayout.NORTH);
        panel.add(southBlock,   BorderLayout.SOUTH);

        return panel;
    }

    // Custom painter for the CV image panel.
    // Shows the loaded image, pulses ORB keypoints during scanning, overlays the
    // engine log while running, and shows a click hint after a successful match.
    private void paintCVPanel(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int pw = cvImagePanel.getWidth();
        int ph = cvImagePanel.getHeight();

        g2.setColor(new Color(0x1A1A14));
        g2.fillRect(0, 0, pw, ph);

        if (cvBaseImage != null) {
            double aspect  = (double) cvBaseImage.getWidth() / cvBaseImage.getHeight();
            int scaledW    = pw;
            int scaledH    = (int) (pw / aspect);
            if (scaledH > ph) { scaledH = ph; scaledW = (int) (ph * aspect); }
            int ox = (pw - scaledW) / 2;
            int oy = (ph - scaledH) / 2;
            g2.drawImage(cvBaseImage, ox, oy, scaledW, scaledH, null);

            // While scanning, draw pulsing white circles on the keypoints found so far
            if (cvScanning && cvKeypoints != null && !cvKeypoints.isEmpty()) {
                double scaleX = (double) scaledW / cvBaseImage.getWidth();
                double scaleY = (double) scaledH / cvBaseImage.getHeight();
                float pulse   = 0.5f + 0.5f * (float) Math.sin(cvPulsePhase);

                for (float[] kp : cvKeypoints) {
                    int kx = ox + (int) (kp[0] * scaleX);
                    int ky = oy + (int) (kp[1] * scaleY);
                    int ks = Math.max(2, (int) (kp[2] * scaleX * 0.4f * (0.7f + 0.3f * pulse)));

                    Composite old = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f + 0.3f * pulse));
                    g2.setColor(new Color(0xFFFFFF));
                    g2.fillOval(kx - ks - 1, ky - ks - 1, (ks + 1) * 2, (ks + 1) * 2);
                    g2.setComposite(old);

                    g2.setColor(new Color(0xFFFFFF));
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawOval(kx - ks, ky - ks, ks * 2, ks * 2);
                    g2.drawLine(kx - 2, ky, kx + 2, ky);
                    g2.drawLine(kx, ky - 2, kx, ky + 2);
                }
            }

            if (cvScanning) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g2.setColor(new Color(0x1A1A14));
                g2.fillRect(ox, oy, scaledW, scaledH);
                g2.setComposite(old);
            }
        } else {
            g2.setColor(IcarusTheme.TEXT_DIM);
            g2.setFont(IcarusTheme.FONT_MONO_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            String msg = cvScanning ? "INITIALIZING..." : "NO IMAGE SELECTED";
            g2.drawString(msg, (pw - fm.stringWidth(msg)) / 2, ph / 2 + fm.getAscent() / 2);
        }

        // While scanning, draw the engine log as scrolling text over a dark overlay
        if (cvScanning && !cvLog.isEmpty()) {
            Font logFont = new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 11);
            g2.setFont(logFont);
            FontMetrics fm   = g2.getFontMetrics();
            int lineH        = fm.getHeight();
            int maxLines     = (ph - 8) / lineH;
            int start        = Math.max(0, cvLog.size() - maxLines);

            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            g2.setColor(new Color(0x0A0A08));
            g2.fillRect(0, 0, pw, ph);
            g2.setComposite(old);

            int ty = 8 + fm.getAscent();
            for (int i = start; i < cvLog.size(); i++) {
                g2.setColor(cvLogColor(cvLog.get(i)));
                g2.drawString(cvLog.get(i), 8, ty);
                ty += lineH;
            }
        }

        // After a successful match, show a small hint that the image is clickable
        if (!cvScanning && cvBaseImage != null && lastCVResult != null
                && !"NO_MATCH".equals(lastCVResult.getMatchLabel())) {
            Font hintFont = new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 11);
            g2.setFont(hintFont);
            FontMetrics fm = g2.getFontMetrics();
            String hint    = "CLICK FOR ANALYSIS";
            g2.setColor(new Color(0xFDFFDE));
            g2.drawString(hint, pw - fm.stringWidth(hint) - 8, ph - 6);
        }
    }

    // Returns the appropriate text color for a given CV engine log line
    private Color cvLogColor(String line) {
        if (line.startsWith("ERROR"))                                    return new Color(0xCC4444);
        if (line.contains("NEW BEST") || line.startsWith("BEST MATCH")) return new Color(0x88FF44);
        if (line.startsWith("CLASSIFICATION"))                           return new Color(0xFFFFFF);
        if (line.startsWith("    GOOD"))                                 return new Color(0x889977);
        return new Color(0xCCCCBB);
    }

    // Opens a file picker for the item image and loads it into the preview.
    // Does not run the scan - the guard must press RUN CV SCAN separately.
    private void doCVPick() {
        JFileChooser fc = new JFileChooser("assets");
        fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        lastScannedImagePath = f.getAbsolutePath();

        try {
            cvBaseImage = ImageIO.read(f);
            cvKeypoints = null;
            cvLog.clear();
            cvScanning  = false;
            cvImagePanel.repaint();
        } catch (Exception ex) {
            cvBaseImage = null;
        }

        lblCVResult.setText("SCAN RESULT: IMAGE LOADED - PRESS RUN CV SCAN");
        frame.setStatus("IMAGE SELECTED: " + f.getName());
    }

    // Runs the CV engine on the selected image in a background thread so the UI stays responsive.
    // Streams engine log lines into the image panel as they arrive, animates keypoints,
    // then on completion maps the matched product name to item categories and evaluates.
    void doCVEvaluate() {
        if (lastScannedImagePath == null) {
            showError("No image selected. Use SELECT IMAGE first.");
            return;
        }

        cvLog.clear();
        cvKeypoints  = null;
        cvScanning   = true;
        cvPulsePhase = 0f;
        cvImagePanel.repaint();
        lblCVResult.setText("SCAN RESULT: PROCESSING...");
        lblCVConfidence.setText("CONFIDENCE: ---");

        if (cvPulseTimer != null) cvPulseTimer.stop();
        cvPulseTimer = new javax.swing.Timer(PULSE_TIMER_MS, ev -> {
            cvPulsePhase += 0.18f;
            if (cvPulsePhase > (float) (Math.PI * 2)) cvPulsePhase = 0f;
            cvImagePanel.repaint();
        });
        cvPulseTimer.start();

        String imagePath = lastScannedImagePath;
        String studentId = studentIdSupplier.get();

        SwingWorker<CVMatchResult, String> worker = new SwingWorker<>() {
            @Override
            protected CVMatchResult doInBackground() {
                return CVEngine.matchImage(imagePath, line -> publish(line));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    cvLog.add(line);
                    if (cvLog.size() > CV_LOG_MAX_LINES) cvLog.remove(0);
                }
                cvImagePanel.repaint();
            }

            @Override
            protected void done() {
                try {
                    lastCVResult = get();
                } catch (Exception ex) {
                    showError("CV scan error: " + ex.getMessage());
                    cvScanning = false;
                    if (cvPulseTimer != null) { cvPulseTimer.stop(); cvPulseTimer = null; }
                    cvImagePanel.repaint();
                    return;
                }

                cvKeypoints = lastCVResult.getKeypoints();
                cvScanning  = false;
                if (cvPulseTimer != null) { cvPulseTimer.stop(); cvPulseTimer = null; }

                try { cvBaseImage = ImageIO.read(new File(imagePath)); } catch (Exception ignored) {}
                cvImagePanel.repaint();

                lblCVResult.setText("SCAN RESULT: " + lastCVResult.getMatchLabel());
                lblCVConfidence.setText("CONFIDENCE: " + String.format("%.1f%%", lastCVResult.getConfidence() * 100));

                PrimaryCategory   primary;
                SecondaryCategory secondary;

                switch (lastCVResult.getMatchLabel()) {
                    case "APPROVED" -> {
                        if (lastCVResult.getConfidence() < CANTEEN_CONFIDENCE_THRESHOLD) {
                            primary   = PrimaryCategory.ALLOWED;
                            secondary = SecondaryCategory.FOOD_CONTAINER;
                        } else {
                            primary   = PrimaryCategory.CANTEEN_PRODUCT;
                            secondary = SecondaryCategory.APPROVED_FOOD;
                        }
                    }
                    case "REJECTED" -> {
                        String pName = lastCVResult.getProductName().toLowerCase();
                        if (nameContainsAny(pName, TOBACCO_KEYWORDS)) {
                            primary   = PrimaryCategory.TOBACCO;
                            secondary = SecondaryCategory.SMOKING_PRODUCT;
                        } else if (nameContainsAny(pName, FIREARM_KEYWORDS)) {
                            primary   = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.FIREARM;
                        } else if (nameContainsAny(pName, BLADE_KEYWORDS)) {
                            primary   = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.SHARP_OBJECT;
                        } else if (nameContainsAny(pName, PLASTIC_KEYWORDS)) {
                            primary   = PrimaryCategory.SINGLE_USE_PLASTIC;
                            secondary = SecondaryCategory.PACKAGING;
                        } else if (nameContainsAny(pName, ALCOHOL_KEYWORDS)) {
                            primary   = PrimaryCategory.ALCOHOL;
                            secondary = SecondaryCategory.ALCOHOLIC_BEVERAGE;
                        } else {
                            // Unknown prohibited item - default to weapon for maximum safety
                            primary   = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.SHARP_OBJECT;
                        }
                    }
                    default -> {
                        primary   = PrimaryCategory.ALLOWED;
                        secondary = SecondaryCategory.FOOD_CONTAINER;
                    }
                }

                try {
                    Item item = new Item(
                        lastCVResult.getProductName(), null,
                        primary, secondary,
                        ItemFunction.CONSUMABLE, ConsumptionContext.FOOD,
                        UsageType.SINGLE_USE, Replaceability.HIGH,
                        ItemStatus.HELD, 1, LocalDateTime.now(), studentId
                    );

                    DecisionResult result = DecisionEngine.evaluateWithCVResult(item, lastCVResult);
                    onEvaluated.accept(item, result);
                    frame.setStatus("CV SCAN COMPLETE: " + lastCVResult.getMatchLabel());
                } catch (Exception ex) {
                    showError("CV evaluation error: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    // Returns true if the given text contains any of the provided keywords
    private boolean nameContainsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // Maps a YOLO label directly to Item categories and runs it through DecisionEngine.
    // Completely bypasses ORB - no CVEngine.matchImage() call needed.
    private void handleYoloResult(CVSocketClient.CaptureResult capture) {
        String label = capture.yoloLabel;   // already lowercased by CVSocketClient
        float  conf  = capture.yoloConf;

        lblCVResult.setText("SCAN RESULT: YOLO - " + label.toUpperCase());
        lblCVConfidence.setText(String.format("CONFIDENCE: %.1f%%", conf * 100));

        PrimaryCategory    primary;
        SecondaryCategory  secondary;
        ItemFunction       function;
        ConsumptionContext context;
        UsageType          usage;
        Replaceability     replace;

        switch (label) {
            case "knife", "scissors" -> {
                primary   = PrimaryCategory.WEAPON;
                secondary = SecondaryCategory.SHARP_OBJECT;
                function  = ItemFunction.TOOL;
                context   = ConsumptionContext.UNKNOWN;
                usage     = UsageType.OTHER;
                replace   = Replaceability.LOW;
            }
            case "bottle" -> {
                // Water bottle, soda bottle — outside plastic, single use
                primary   = PrimaryCategory.SINGLE_USE_PLASTIC;
                secondary = SecondaryCategory.BEVERAGE_CONTAINER;
                function  = ItemFunction.CONTAINER;
                context   = ConsumptionContext.BEVERAGE;
                usage     = UsageType.SINGLE_USE;
                replace   = Replaceability.HIGH;
            }
            case "cup", "wine glass" -> {
                // Plastic cup brought from outside
                primary   = PrimaryCategory.SINGLE_USE_PLASTIC;
                secondary = SecondaryCategory.BEVERAGE_CONTAINER;
                function  = ItemFunction.CONTAINER;
                context   = ConsumptionContext.SCHOOL_USE;
                usage     = UsageType.SINGLE_USE;
                replace   = Replaceability.HIGH;
            }
            case "fork", "spoon" -> {
                // Plastic utensils — high risk, easily replaceable
                primary   = PrimaryCategory.SINGLE_USE_PLASTIC;
                secondary = SecondaryCategory.FOOD_ACCESSORY;
                function  = ItemFunction.UTENSIL;
                context   = ConsumptionContext.FOOD;
                usage     = UsageType.SINGLE_USE;
                replace   = Replaceability.HIGH;
            }
            default -> {
                // YOLO flagged something in RELEVANT_LABELS but no specific rule
                // ALLOWED + FOOD_CONTAINER = lowest risk path through DecisionEngine
                primary   = PrimaryCategory.ALLOWED;
                secondary = SecondaryCategory.FOOD_CONTAINER;
                function  = ItemFunction.OTHER;
                context   = ConsumptionContext.UNKNOWN;
                usage     = UsageType.OTHER;
                replace   = Replaceability.LOW;
            }
        }

        String studentId = studentIdSupplier.get();

        try {
            Item item = new Item(
                label, null,
                primary, secondary,
                function, context,
                usage, replace,
                ItemStatus.HELD, 1,
                LocalDateTime.now(), studentId
            );

            // Build a CVMatchResult so DecisionEngine.evaluateWithCVResult() works correctly
            String matchLabel;
            if (primary == PrimaryCategory.WEAPON  ||
                primary == PrimaryCategory.TOBACCO  ||
                primary == PrimaryCategory.ALCOHOL) {
                matchLabel = "REJECTED";
            } else if (primary == PrimaryCategory.CANTEEN_PRODUCT) {
                matchLabel = "APPROVED";
            } else {
                matchLabel = "NO_MATCH";
            }

            CVMatchResult cvResult = new CVMatchResult(label, conf, matchLabel);
            lastCVResult = cvResult;

            DecisionResult result = DecisionEngine.evaluateWithCVResult(item, cvResult);
            onEvaluated.accept(item, result);
            frame.setStatus("YOLO SCAN COMPLETE: " + label.toUpperCase()
                + String.format(" (%.0f%%)", conf * 100));

        } catch (Exception ex) {
            showError("YOLO evaluation error: " + ex.getMessage());
        }
    }

    // Launches cv_server.py as a subprocess and connects the socket client
    private void doStartWebcam() {
        if (cvServerProcess != null && cvServerProcess.isAlive()) {
            showError("Webcam already running.");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python", "cv_server.py");
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);
            cvServerProcess = pb.start();

            // Drain the Python process stdout so it doesn't block
            Thread drain = new Thread(() -> {
                try (var br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(cvServerProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        System.out.println("[cv_server] " + line);
                } catch (Exception ignored) {}
            });
            drain.setDaemon(true);
            drain.start();

            // Start connection attempt in a background thread to keep UI responsive
            new Thread(() -> {
                int maxAttempts = 60; // 60 seconds patience
                int attempts    = 0;
                boolean connected = false;

                cvSocketClient = new CVSocketClient();

                while (attempts < maxAttempts && !connected) {
                    try {
                        cvSocketClient.connect();
                        connected = true;
                    } catch (Exception e) {
                        attempts++;
                        final int currentSec = attempts;
                        SwingUtilities.invokeLater(() ->
                            frame.setStatus("WAITING FOR WEBCAM... " + currentSec + "s")
                        );
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    }
                }

                if (connected) {
                    SwingUtilities.invokeLater(() -> {
                        btnStartWebcam.setEnabled(false);
                        btnStopWebcam.setEnabled(true);
                        btnCaptureWebcam.setEnabled(true);
                        frame.setStatus("WEBCAM ONLINE");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        showError("CV Server failed to start after 1 minute. Check if cv_server.py has errors.");
                        doStopWebcam();
                    });
                }
            }).start();

        } catch (Exception ex) {
            showError("Failed to launch process: " + ex.getMessage());
            doStopWebcam();
        }
    }

    // Sends STOP to cv_server.py and cleans up the process
    private void doStopWebcam() {
        if (cvSocketClient != null) {
            cvSocketClient.disconnect();
            cvSocketClient = null;
        }
        if (cvServerProcess != null) {
            cvServerProcess.destroyForcibly();
            cvServerProcess = null;
        }
        btnStartWebcam.setEnabled(true);
        btnStopWebcam.setEnabled(false);
        btnCaptureWebcam.setEnabled(false);
        frame.setStatus("WEBCAM OFFLINE");
    }

    // Tells cv_server.py to grab the current frame, then feeds the saved path
    // into the existing doCVEvaluate() flow - no changes to CV or decision engine needed.
    private void doCaptureAndScan() {
        if (cvSocketClient == null || !cvSocketClient.isConnected()) {
            showError("Webcam not connected. Press START WEBCAM first.");
            return;
        }
        frame.setStatus("CAPTURING FRAME...");
        SwingWorker<CVSocketClient.CaptureResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CVSocketClient.CaptureResult doInBackground() throws Exception {
                return cvSocketClient.sendCapture();
            }
            @Override
            protected void done() {
                try {
                    CVSocketClient.CaptureResult capture = get();
                    lastScannedImagePath = capture.imagePath;

                    // Load captured frame into CV preview panel
                    try {
                        cvBaseImage = ImageIO.read(new File(capture.imagePath));
                        cvKeypoints = null;
                        cvLog.clear();
                        cvScanning  = false;
                        cvImagePanel.repaint();
                    } catch (Exception ignored) {}

                    if (capture.hasYoloHit()) {
                        // YOLO hit - map label directly to categories
                        handleYoloResult(capture);
                    } else {
                        // YOLO found nothing - webcam path is YOLO only, do not fall through to ORB
                        lblCVResult.setText("SCAN RESULT: NO ITEM DETECTED");
                        lblCVConfidence.setText("CONFIDENCE: ---");
                        frame.setStatus("CAPTURE COMPLETE: NO ITEM DETECTED");
                    }

                } catch (Exception ex) {
                    showError("Capture failed: " + ex.getMessage());
                    frame.setStatus("CAPTURE FAILED");
                }
            }
        };
        worker.execute();
    }

    // Opens a full-screen dark modal showing the scanned image side by side with the ORB keypoint
    // overlay, plus the complete engine log from the scan. Only available after a successful match.
    private void showCVAnalysisOverlay() {
        JDialog dialog = new JDialog(
            SwingUtilities.getWindowAncestor(this) instanceof Frame f ? f : null,
            "CV ANALYSIS", true);
        dialog.setBackground(new Color(0x1A1A14));
        dialog.setUndecorated(true);
        dialog.setSize(900, 620);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(0x1A1A14));
        root.setBorder(BorderFactory.createLineBorder(new Color(0x4A6A10), 1));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(0x0A0A08));
        titleBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel title = new JLabel("CV ANALYSIS - " + lastCVResult.getProductName().toUpperCase()
            + "  [" + lastCVResult.getMatchLabel() + "]"
            + String.format("  %.1f%% CONFIDENCE", lastCVResult.getConfidence() * 100));
        title.setFont(IcarusTheme.FONT_MONO_BOLD);
        title.setForeground(new Color(0xCCCCBB));

        JButton btnClose = new JButton("CLOSE");
        btnClose.setFont(IcarusTheme.FONT_MONO_SMALL);
        btnClose.setForeground(new Color(0xCCCCBB));
        btnClose.setBackground(new Color(0x2A2A1E));
        btnClose.setBorder(BorderFactory.createLineBorder(new Color(0x4A6A10)));
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dialog.dispose());

        titleBar.add(title,    BorderLayout.WEST);
        titleBar.add(btnClose, BorderLayout.EAST);

        JPanel imageArea = new JPanel(new GridLayout(1, 2, 4, 0));
        imageArea.setBackground(new Color(0x1A1A14));
        imageArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        // Left panel shows the clean original image
        JPanel cleanPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(0x0A0A08));
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (cvBaseImage != null) {
                    int pw = getWidth() - 16;
                    int ph = getHeight() - 24;
                    double aspect = (double) cvBaseImage.getWidth() / cvBaseImage.getHeight();
                    int sw = pw, sh = (int) (pw / aspect);
                    if (sh > ph) { sh = ph; sw = (int) (ph * aspect); }
                    g2.drawImage(cvBaseImage, 8 + (pw - sw) / 2, 8, sw, sh, null);
                }
                g2.setColor(new Color(0x667755));
                g2.setFont(new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 11));
                g2.drawString("ORIGINAL", 8, getHeight() - 6);
            }
        };
        cleanPanel.setBackground(new Color(0x0A0A08));
        cleanPanel.setBorder(BorderFactory.createLineBorder(new Color(0x3A3A2A)));

        // Right panel shows the same image with ORB keypoints drawn on it
        JPanel kpPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x0A0A08));
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (cvBaseImage != null) {
                    int pw = getWidth() - 16;
                    int ph = getHeight() - 24;
                    double aspect = (double) cvBaseImage.getWidth() / cvBaseImage.getHeight();
                    int sw = pw, sh = (int) (pw / aspect);
                    if (sh > ph) { sh = ph; sw = (int) (ph * aspect); }
                    int ox = 8 + (pw - sw) / 2;
                    int oy = 8;
                    g2.drawImage(cvBaseImage, ox, oy, sw, sh, null);

                    if (cvKeypoints != null) {
                        double scaleX = (double) sw / cvBaseImage.getWidth();
                        double scaleY = (double) sh / cvBaseImage.getHeight();
                        for (float[] kp : cvKeypoints) {
                            int kx = ox + (int) (kp[0] * scaleX);
                            int ky = oy + (int) (kp[1] * scaleY);
                            int ks = Math.max(2, (int) (kp[2] * scaleX * 0.4f));

                            Composite old = g2.getComposite();
                            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
                            g2.setColor(new Color(0x4A6A10));
                            g2.fillOval(kx - ks - 1, ky - ks - 1, (ks + 1) * 2, (ks + 1) * 2);
                            g2.setComposite(old);

                            g2.setColor(new Color(0x7AAA30));
                            g2.setStroke(new BasicStroke(1.0f));
                            g2.drawOval(kx - ks, ky - ks, ks * 2, ks * 2);
                            g2.drawLine(kx - 2, ky, kx + 2, ky);
                            g2.drawLine(kx, ky - 2, kx, ky + 2);
                        }
                    }
                }
                g2.setColor(new Color(0xCCCCBB));
                g2.setFont(new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 11));
                g2.drawString("ORB KEYPOINTS (" + (cvKeypoints != null ? cvKeypoints.size() : 0) + ")",
                    8, getHeight() - 6);
            }
        };
        kpPanel.setBackground(new Color(0x0A0A08));
        kpPanel.setBorder(BorderFactory.createLineBorder(new Color(0x4A6A10)));

        imageArea.add(cleanPanel);
        imageArea.add(kpPanel);

        JTextArea logArea = new JTextArea();
        logArea.setFont(new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 11));
        logArea.setForeground(new Color(0xCCCCBB));
        logArea.setBackground(new Color(0x0A0A08));
        logArea.setEditable(false);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        logArea.setLineWrap(false);
        for (String line : cvLog) logArea.append(line + "\n");

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBackground(new Color(0x0A0A08));
        logScroll.getViewport().setBackground(new Color(0x0A0A08));
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x3A3A2A)));
        logScroll.setPreferredSize(new Dimension(0, 160));

        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = logScroll.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });

        root.add(titleBar,  BorderLayout.NORTH);
        root.add(imageArea, BorderLayout.CENTER);
        root.add(logScroll, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}