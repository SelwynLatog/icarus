package ui;

// Guard-facing security terminal.
// Three responsibilities: student lookup, item evaluation (manual or CV scan),
// and managing the held items queue. All actions feed into the decision log.

import engine.CVEngine;
import engine.CVMatchResult;
import engine.DecisionEngine;
import engine.DecisionResult;
import enums.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import model.Item;
import model.Student;
import service.ItemService;
import service.StudentService;
import ui.theme.IcarusTheme;

public class GuardDashboard extends JPanel {

    // Layout constants
    private static final int TOP_BAR_HEIGHT = 28;
    private static final int CV_IMAGE_HEIGHT = 220;
    private static final int STUDENT_PHOTO_HEIGHT = 220;
    private static final int STUDENT_PHOTO_WIDTH = 400;
    private static final int QUEUE_ROW_HEIGHT = 20;
    private static final float STUDENT_PANEL_WEIGHT = 0.28f;
    private static final float RIGHT_PANEL_WEIGHT = 0.72f;
    private static final float ENTRY_AREA_WEIGHT = 0.58f;
    private static final float RESULT_PANEL_WEIGHT = 0.42f;

    // CV product name keywords used to map a scan result to the right item category
    private static final String[] FIREARM_KEYWORDS = {
        "gun", "45", "firearm", "pistol", "rifle", "revolver",
        "glock", "handgun", "weapon", "beretta", "m16", "ar"
    };
    private static final String[] BLADE_KEYWORDS = { "knife", "blade", "sharp" };
    private static final String[] PLASTIC_KEYWORDS = { "plastic", "spork", "cup", "cellophane" };
    private static final String[] TOBACCO_KEYWORDS = { "cig", "vape", "tobacco" };
    private static final String[] ALCOHOL_KEYWORDS = { "alcohol", "beer", "wine", "liquor" };

    // Timestamp format used in the queue table
    private static final DateTimeFormatter QUEUE_TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    // Confidence floor for treating a canteen match as genuine
    private static final float CANTEEN_CONFIDENCE_THRESHOLD = 0.70f;

    // How many log lines to keep in memory during a CV scan
    private static final int CV_LOG_MAX_LINES = 60;

    // Pulse animation tick rate in milliseconds
    private static final int PULSE_TIMER_MS = 50;

    private final ItemService itemService;
    private final StudentService studentService;
    private final AppFrame frame;

    // Student info labels updated when a student is looked up
    private JLabel lblStudentName;
    private JLabel lblStudentCourse;
    private JLabel lblStudentYear;
    private JLabel lblStudentStatus;
    private JLabel lblStudentViolations;
    private JLabel lblStudentId;
    private JLabel lblStudentPhoto;

    // Evaluation result labels updated after every scan or manual entry
    private JLabel lblDecision;
    private JLabel lblReason;
    private JLabel lblAction;
    private JLabel lblThreat;
    private DecisionResult lastDecisionResult;

    // Queue table model and view
    private DefaultTableModel queueModel;
    private JTable queueTable;

    // CV scan state — tracks the current scan in progress and its visual state
    private JLabel lblCVResult;
    private JLabel lblCVConfidence;
    private String lastScannedImagePath;
    private CVMatchResult lastCVResult;
    private Item lastEvaluatedItem;
    private javax.swing.Timer cvPulseTimer;
    private float cvPulsePhase = 0f;

    // CV visual rendering state — shared between the painter and the scan worker
    private final List<String> cvLog = new ArrayList<>();
    private BufferedImage cvBaseImage = null;
    private List<float[]> cvKeypoints = null;
    private boolean cvScanning = false;
    private JPanel cvImagePanel;

    // Manual entry form fields
    private JTextField tfItemName;
    private JTextField tfBrand;
    private JTextField tfQuantity;
    private JComboBox<PrimaryCategory> cbPrimary;
    private JComboBox<SecondaryCategory> cbSecondary;
    private JComboBox<ItemFunction> cbFunction;
    private JComboBox<ConsumptionContext> cbContext;
    private JComboBox<UsageType> cbUsage;
    private JComboBox<Replaceability> cbReplace;

    // The student currently loaded in the left panel — null if no student is active
    private Student currentStudent;

    private final storage.dao.DecisionLogDAO decisionLogDAO = new storage.dao.DecisionLogDAO();
    private final storage.dao.ItemDAO itemDAO = new storage.dao.ItemDAO();

    public GuardDashboard(ItemService itemService, StudentService studentService, AppFrame frame) {
        this.itemService = itemService;
        this.studentService = studentService;
        this.frame = frame;

        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        refreshQueue();

        // When guard clicks a row in the queue, load that item's logged decision into the result panel
        queueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) doLoadFromQueue();
        });
    }

    // Builds the thin dark bar at the top showing the terminal title and back button
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IcarusTheme.BG_PANEL_ALT);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, TOP_BAR_HEIGHT));

        JLabel label = new JLabel("  SECURITY TERMINAL");
        label.setFont(IcarusTheme.FONT_MONO_BOLD);
        label.setForeground(IcarusTheme.ACCENT);

        JButton btnBack = IcarusTheme.makeButton("ROLE SELECT");
        btnBack.setPreferredSize(new Dimension(160, 24));
        btnBack.addActionListener(e -> frame.showCard(AppFrame.CARD_ROLE));

        bar.add(label, BorderLayout.WEST);
        bar.add(btnBack, BorderLayout.EAST);
        return bar;
    }

    // Splits the screen into the student panel on the left and the entry/queue area on the right
    private JPanel buildContent() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(IcarusTheme.BG_FRAME);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 4, 4, 4);
        c.weighty = 1.0;

        c.gridx = 0; c.gridy = 0; c.weightx = STUDENT_PANEL_WEIGHT;
        root.add(buildStudentPanel(), c);

        c.gridx = 1; c.weightx = RIGHT_PANEL_WEIGHT;
        root.add(buildRightColumn(), c);

        return root;
    }

    // Builds the left column: barcode scan button, manual ID entry, student info labels, and ID photo
    private JPanel buildStudentPanel() {
        JPanel outer = IcarusTheme.makePanelWithHeader("STUDENT SCAN");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(IcarusTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JButton btnBarcode = IcarusTheme.makeButton("SCAN BARCODE");
        btnBarcode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        btnBarcode.addActionListener(e -> doBarcodeScana());

        JPanel manualRow = new JPanel(new BorderLayout(4, 0));
        manualRow.setOpaque(false);
        manualRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JTextField tfManualId = IcarusTheme.makeTextField();
        JButton btnManual = IcarusTheme.makeButton("LOOKUP");
        btnManual.setPreferredSize(new Dimension(80, 26));
        btnManual.addActionListener(e -> doStudentLookup(tfManualId.getText().trim()));
        manualRow.add(tfManualId, BorderLayout.CENTER);
        manualRow.add(btnManual, BorderLayout.EAST);

        JSeparator sep = new JSeparator();
        sep.setForeground(IcarusTheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        lblStudentId = makeCardRow("ID", "---");
        lblStudentName = makeCardRow("NAME", "---");
        lblStudentCourse = makeCardRow("COURSE", "---");
        lblStudentYear = makeCardRow("YEAR", "---");
        lblStudentStatus = makeCardRow("STATUS", "---");
        lblStudentViolations = makeCardRow("VIOLATIONS", "---");

        panel.add(btnBarcode);
        panel.add(Box.createVerticalStrut(6));
        panel.add(IcarusTheme.makeDimLabel("OR ENTER ID MANUALLY:"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(manualRow);
        panel.add(Box.createVerticalStrut(10));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(10));
        panel.add(lblStudentId);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblStudentName);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblStudentCourse);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblStudentYear);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblStudentStatus);
        panel.add(Box.createVerticalStrut(3));
        panel.add(lblStudentViolations);

        lblStudentPhoto = new JLabel("NO ID ON FILE", SwingConstants.CENTER);
        lblStudentPhoto.setFont(IcarusTheme.FONT_MONO_SMALL);
        lblStudentPhoto.setForeground(IcarusTheme.TEXT_DIM);
        lblStudentPhoto.setOpaque(false);
        lblStudentPhoto.setBorder(null);
        lblStudentPhoto.setHorizontalAlignment(SwingConstants.LEFT);
        lblStudentPhoto.setVerticalAlignment(SwingConstants.CENTER);
        lblStudentPhoto.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblStudentPhoto.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        lblStudentPhoto.setPreferredSize(new Dimension(0, STUDENT_PHOTO_HEIGHT));

        panel.add(Box.createVerticalStrut(2));
        panel.add(lblStudentPhoto);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    // Creates a single student info row label in the format "FIELD:       value"
    private JLabel makeCardRow(String field, String value) {
        JLabel l = new JLabel(String.format("%-12s %s", field + ":", value));
        l.setFont(IcarusTheme.FONT_MONO_SMALL);
        l.setForeground(IcarusTheme.TEXT_PRIMARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // Stacks the entry area (tabs + result panel) above the held items queue
    private JPanel buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 4, 0);

        c.gridy = 0; c.weighty = 0.55;
        col.add(buildEntryArea(), c);

        c.gridy = 1; c.weighty = 0.45;
        col.add(buildQueuePanel(), c);

        return col;
    }

    // Puts the manual/CV tabs side by side with the evaluation result panel
    private JPanel buildEntryArea() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        c.insets = new Insets(0, 0, 0, 4);

        c.gridx = 0; c.weightx = ENTRY_AREA_WEIGHT;
        root.add(buildEntryTabs(), c);

        c.gridx = 1; c.weightx = RESULT_PANEL_WEIGHT;
        c.insets = new Insets(0, 0, 0, 0);
        root.add(buildResultPanel(), c);

        return root;
    }

    // Creates the tabbed pane containing the manual entry form and the CV scan panel
    private JTabbedPane buildEntryTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(IcarusTheme.FONT_MONO_BOLD);
        tabs.setBackground(IcarusTheme.BG_PANEL);
        tabs.setForeground(IcarusTheme.TEXT_PRIMARY);
        tabs.addTab("MANUAL ENTRY", buildManualTab());
        tabs.addTab("CV SCAN", buildCVTab());
        return tabs;
    }

    // Builds the manual entry form with all item classification dropdowns and an evaluate button
    private JPanel buildManualTab() {
        JPanel panel = IcarusTheme.makePanel(null);
        panel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 6, 3, 6);
        c.weightx = 1.0;

        tfItemName = IcarusTheme.makeTextField();
        tfBrand = IcarusTheme.makeTextField();
        tfQuantity = IcarusTheme.makeTextField();
        tfQuantity.setText("1");

        cbPrimary = new JComboBox<>(PrimaryCategory.values());
        cbSecondary = new JComboBox<>(SecondaryCategory.values());
        cbFunction = new JComboBox<>(ItemFunction.values());
        cbContext = new JComboBox<>(ConsumptionContext.values());
        cbUsage = new JComboBox<>(UsageType.values());
        cbReplace = new JComboBox<>(Replaceability.values());

        styleCombo(cbPrimary); styleCombo(cbSecondary);
        styleCombo(cbFunction); styleCombo(cbContext);
        styleCombo(cbUsage); styleCombo(cbReplace);

        int row = 0;
        addFormRow(panel, c, row++, "ITEM NAME", tfItemName);
        addFormRow(panel, c, row++, "BRAND", tfBrand);
        addFormRow(panel, c, row++, "QUANTITY", tfQuantity);
        addFormRow(panel, c, row++, "CATEGORY", cbPrimary);
        addFormRow(panel, c, row++, "SUB-CATEGORY", cbSecondary);
        addFormRow(panel, c, row++, "FUNCTION", cbFunction);
        addFormRow(panel, c, row++, "CONTEXT", cbContext);
        addFormRow(panel, c, row++, "USAGE TYPE", cbUsage);
        addFormRow(panel, c, row++, "REPLACEABILITY", cbReplace);

        JButton btnEval = IcarusTheme.makeButton("EVALUATE");
        btnEval.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        btnEval.addActionListener(e -> doManualEvaluate());

        c.gridy = row; c.gridx = 0; c.gridwidth = 2;
        c.insets = new Insets(8, 6, 6, 6);
        panel.add(btnEval, c);

        c.gridy = row + 1; c.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), c);

        return panel;
    }

    // Adds a label-field pair as one row in the manual entry form grid
    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row; c.gridwidth = 1; c.weighty = 0;
        c.gridx = 0; c.weightx = 0.35;
        panel.add(IcarusTheme.makeDimLabel(label), c);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(field, c);
    }

    // Applies the consistent theme styling to a combo box
    private <T> void styleCombo(JComboBox<T> cb) {
        cb.setFont(IcarusTheme.FONT_MONO_SMALL);
        cb.setBackground(IcarusTheme.BG_PANEL_ALT);
        cb.setForeground(IcarusTheme.TEXT_PRIMARY);
    }

    // Builds the CV scan tab with the image preview panel, scan result labels, and action buttons.
    // The image panel does custom painting: shows the loaded image, pulses ORB keypoints during
    // scanning, overlays the compiler log while the engine is running, and shows a click hint
    // after a successful match.
    private JPanel buildCVTab() {
        JPanel panel = IcarusTheme.makePanel(null);
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        cvImagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int pw = getWidth();
                int ph = getHeight();

                g2.setColor(new Color(0x1A1A14));
                g2.fillRect(0, 0, pw, ph);

                if (cvBaseImage != null) {
                    double aspect = (double) cvBaseImage.getWidth() / cvBaseImage.getHeight();
                    int scaledW = pw;
                    int scaledH = (int) (pw / aspect);
                    if (scaledH > ph) { scaledH = ph; scaledW = (int) (ph * aspect); }
                    int ox = (pw - scaledW) / 2;
                    int oy = (ph - scaledH) / 2;
                    g2.drawImage(cvBaseImage, ox, oy, scaledW, scaledH, null);

                    // While scanning, draw pulsing white circles on the keypoints found so far
                    if (cvScanning && cvKeypoints != null && !cvKeypoints.isEmpty()) {
                        double scaleX = (double) scaledW / cvBaseImage.getWidth();
                        double scaleY = (double) scaledH / cvBaseImage.getHeight();
                        float pulse = 0.5f + 0.5f * (float) Math.sin(cvPulsePhase);

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
                    FontMetrics fm = g2.getFontMetrics();
                    int lineH = fm.getHeight();
                    int maxLines = (ph - 8) / lineH;
                    int start = Math.max(0, cvLog.size() - maxLines);

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
                    String hint = "CLICK FOR ANALYSIS";
                    g2.setColor(new Color(0xFDFFDE));
                    g2.drawString(hint, pw - fm.stringWidth(hint) - 8, ph - 6);
                }
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

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        JButton btnPick = IcarusTheme.makeButton("SELECT IMAGE");
        JButton btnScan = IcarusTheme.makeButton("RUN CV SCAN");
        btnPick.addActionListener(e -> doCVPick());
        btnScan.addActionListener(e -> doCVEvaluate());
        btnRow.add(btnPick);
        btnRow.add(btnScan);

        panel.add(imgHolder, BorderLayout.CENTER);
        panel.add(resultBlock, BorderLayout.NORTH);
        panel.add(btnRow, BorderLayout.SOUTH);

        return panel;
    }

    // Returns the appropriate text color for a given CV engine log line.
    // Errors are red, new best matches are bright green, completion is white,
    // good match counts are muted, everything else is off-white.
    private Color cvLogColor(String line) {
        if (line.startsWith("ERROR")) return new Color(0xCC4444);
        if (line.contains("NEW BEST") || line.startsWith("BEST MATCH")) return new Color(0x88FF44);
        if (line.startsWith("CLASSIFICATION")) return new Color(0xFFFFFF);
        if (line.startsWith("    GOOD")) return new Color(0x889977);
        return new Color(0xCCCCBB);
    }

    // Builds the evaluation result panel showing the decision, threat level, reason, and action steps.
    // The LOG DECISION button at the bottom saves the result to the database.
    private JPanel buildResultPanel() {
        JPanel outer = IcarusTheme.makePanelWithHeader("EVALUATION RESULT");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(IcarusTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        lblDecision = new JLabel("---");
        lblDecision.setFont(new Font("GohuFont 11 NerdFont Mono", Font.BOLD, 22));
        lblDecision.setForeground(IcarusTheme.TEXT_DIM);
        lblDecision.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblThreat = IcarusTheme.makeDimLabel("THREAT: ---");
        lblThreat.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setForeground(IcarusTheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        lblReason = new JLabel("<html>---</html>");
        lblReason.setFont(IcarusTheme.FONT_MONO_SMALL);
        lblReason.setForeground(IcarusTheme.TEXT_PRIMARY);
        lblReason.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblAction = new JLabel("<html>---</html>");
        lblAction.setFont(IcarusTheme.FONT_MONO_SMALL);
        lblAction.setForeground(IcarusTheme.ACCENT);
        lblAction.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnLog = IcarusTheme.makeButton("LOG DECISION");
        btnLog.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        btnLog.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLog.addActionListener(e -> doLogDecision());

        panel.add(pad(lblDecision));
        panel.add(Box.createVerticalStrut(4));
        panel.add(pad(lblThreat));
        panel.add(Box.createVerticalStrut(8));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(8));
        panel.add(pad(lblReason));
        panel.add(Box.createVerticalStrut(6));
        panel.add(pad(lblAction));
        panel.add(Box.createVerticalGlue());
        panel.add(pad(btnLog));
        panel.add(Box.createVerticalStrut(4));

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    // Wraps a component so it stays left-aligned and full-width with consistent side padding
    private JPanel pad(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
        p.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // Builds the held items queue table showing only items belonging to the currently loaded student
    private JPanel buildQueuePanel() {
        JPanel outer = IcarusTheme.makePanelWithHeader("HELD ITEMS QUEUE");

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(IcarusTheme.BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        String[] cols = { "ID", "ITEM NAME", "BRAND", "CATEGORY", "STUDENT", "QTY", "TIMESTAMP" };
        queueModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        queueTable = new JTable(queueModel);
        queueTable.setFont(IcarusTheme.FONT_MONO_SMALL);
        queueTable.setRowHeight(QUEUE_ROW_HEIGHT);
        queueTable.getTableHeader().setFont(IcarusTheme.FONT_MONO_BOLD);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.setShowGrid(true);
        queueTable.setGridColor(IcarusTheme.BORDER);

        int[] widths = { 40, 160, 100, 140, 100, 40, 130 };
        for (int i = 0; i < widths.length; i++)
            queueTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane scroll = new JScrollPane(queueTable);
        scroll.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        scroll.getViewport().setBackground(IcarusTheme.BG_PANEL);

        JButton btnRefresh = IcarusTheme.makeButton("REFRESH");
        btnRefresh.addActionListener(e -> refreshQueue());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.setOpaque(false);
        btnRow.add(btnRefresh);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    // Opens a file picker for barcode images, decodes the barcode, and loads the matching student
    private void doBarcodeScana() {
        JFileChooser fc = new JFileChooser("assets/barcodes");
        fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Optional<String> id = engine.BarcodeEngine.scan(fc.getSelectedFile().getAbsolutePath());
        if (id.isEmpty()) {
            showError("No barcode detected in selected image.");
            return;
        }
        doStudentLookup(id.get());
    }

    // Finds a student by ID and populates the left panel. Clears the panel if not found.
    private void doStudentLookup(String studentId) {
        if (studentId.isBlank()) return;
        Optional<Student> opt = studentService.findStudentById(studentId);
        if (opt.isEmpty()) {
            showError("Student not found: " + studentId);
            clearStudentCard();
            return;
        }
        currentStudent = opt.get();
        populateStudentCard(currentStudent);
        refreshQueue();
        frame.setStatus("STUDENT LOADED: " + currentStudent.getFullName());
    }

    // Fills in all the student info labels and loads the ID photo. Shows a red alert if suspended.
    private void populateStudentCard(Student s) {
        int violations = studentService.findStudentById(s.getStudentId())
                .map(st -> st.getItemIds().size()).orElse(0);

        lblStudentId.setText(fmt("ID", s.getStudentId()));
        lblStudentName.setText(fmt("NAME", s.getFullName()));
        lblStudentCourse.setText(fmt("COURSE", s.getCourse()));
        lblStudentYear.setText(fmt("YEAR", String.valueOf(s.getYear())));
        lblStudentViolations.setText(fmt("VIOLATIONS", String.valueOf(violations)));
        SwingUtilities.invokeLater(() -> loadStudentPhoto(s.getStudentId()));

        lblStudentStatus.setText(fmt("STATUS", s.getStatus().name()));
        lblStudentStatus.setForeground(switch (s.getStatus()) {
            case SUSPENDED -> IcarusTheme.STATUS_DISALLOW;
            case ENROLLED -> IcarusTheme.STATUS_ALLOW;
            default -> IcarusTheme.TEXT_DIM;
        });

        if (s.getStatus() == StudentStatus.SUSPENDED)
            frame.setStatus("ALERT: SUSPENDED STUDENT  " + s.getFullName().toUpperCase());
    }

    // Resets all student info labels to dashes and clears the photo. Called when lookup fails.
    private void clearStudentCard() {
        currentStudent = null;
        lblStudentId.setText(fmt("ID", "---"));
        lblStudentName.setText(fmt("NAME", "---"));
        lblStudentCourse.setText(fmt("COURSE", "---"));
        lblStudentYear.setText(fmt("YEAR", "---"));
        lblStudentStatus.setText(fmt("STATUS", "---"));
        lblStudentStatus.setForeground(IcarusTheme.TEXT_PRIMARY);
        lblStudentViolations.setText(fmt("VIOLATIONS", "---"));
        lblStudentPhoto.setIcon(null);
        lblStudentPhoto.setText("NO ID ON FILE");
    }

    // Loads the student ID card image from assets/students/ and scales it to fit the photo box.
    // Falls back to a text placeholder if the file is missing or unreadable.
    private void loadStudentPhoto(String studentId) {
        String path = "assets/students/" + studentId + "_id.png";
        File f = new File(path);
        if (!f.exists()) {
            lblStudentPhoto.setIcon(null);
            lblStudentPhoto.setText("NO ID ON FILE");
            return;
        }
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { lblStudentPhoto.setText("NO ID ON FILE"); return; }

            int w = STUDENT_PHOTO_WIDTH;
            int h = STUDENT_PHOTO_HEIGHT;
            double imgAspect = (double) img.getWidth() / img.getHeight();
            int scaledW = w;
            int scaledH = (int) (w / imgAspect);
            if (scaledH > h) { scaledH = h; scaledW = (int) (h * imgAspect); }

            lblStudentPhoto.setIcon(new ImageIcon(img.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH)));
            lblStudentPhoto.setText("");
        } catch (Exception e) {
            lblStudentPhoto.setText("NO ID ON FILE");
        }
    }

    // Formats a student info label line with consistent field width padding
    private String fmt(String field, String value) {
        return String.format("%-12s %s", field + ":", value);
    }

    // Reads the manual entry form, builds an Item, runs it through the decision engine,
    // and shows the result. Does not save to the database yet — that happens on LOG DECISION.
    private void doManualEvaluate() {
        String name = tfItemName.getText().trim();
        if (name.isBlank()) { showError("Item name required."); return; }

        int qty;
        try {
            qty = Integer.parseInt(tfQuantity.getText().trim());
        } catch (NumberFormatException e) {
            showError("Quantity must be a number.");
            return;
        }

        String studentId = currentStudent != null ? currentStudent.getStudentId() : null;

        try {
            Item item = new Item(
                name,
                tfBrand.getText().trim().isEmpty() ? null : tfBrand.getText().trim(),
                (PrimaryCategory) cbPrimary.getSelectedItem(),
                (SecondaryCategory) cbSecondary.getSelectedItem(),
                (ItemFunction) cbFunction.getSelectedItem(),
                (ConsumptionContext) cbContext.getSelectedItem(),
                (UsageType) cbUsage.getSelectedItem(),
                (Replaceability) cbReplace.getSelectedItem(),
                ItemStatus.HELD, qty, LocalDateTime.now(), studentId
            );

            DecisionResult result = DecisionEngine.evaluate(item);
            lastEvaluatedItem = item;
            lastDecisionResult = result;
            displayResult(result);
            frame.setStatus("EVALUATION COMPLETE: " + result.getDecision().name());
        } catch (Exception ex) {
            showError("Evaluation error: " + ex.getMessage());
        }
    }

    // Opens a file picker for the item image and loads it into the CV panel preview.
    // Does not run the scan — the guard must press RUN CV SCAN separately.
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
            cvScanning = false;
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
    private void doCVEvaluate() {
        if (lastScannedImagePath == null) {
            showError("No image selected. Use SELECT IMAGE first.");
            return;
        }

        cvLog.clear();
        cvKeypoints = null;
        cvScanning = true;
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
        String studentId = currentStudent != null ? currentStudent.getStudentId() : null;

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
                cvScanning = false;
                if (cvPulseTimer != null) { cvPulseTimer.stop(); cvPulseTimer = null; }

                try { cvBaseImage = ImageIO.read(new File(imagePath)); } catch (Exception ignored) {}
                cvImagePanel.repaint();

                lblCVResult.setText("SCAN RESULT: " + lastCVResult.getMatchLabel());
                lblCVConfidence.setText("CONFIDENCE: " + String.format("%.1f%%", lastCVResult.getConfidence() * 100));

                PrimaryCategory primary;
                SecondaryCategory secondary;

                switch (lastCVResult.getMatchLabel()) {
                    case "APPROVED" -> {
                        if (lastCVResult.getConfidence() < CANTEEN_CONFIDENCE_THRESHOLD) {
                            primary = PrimaryCategory.ALLOWED;
                            secondary = SecondaryCategory.FOOD_CONTAINER;
                        } else {
                            primary = PrimaryCategory.CANTEEN_PRODUCT;
                            secondary = SecondaryCategory.APPROVED_FOOD;
                        }
                    }
                    case "REJECTED" -> {
                        String pName = lastCVResult.getProductName().toLowerCase();
                        if (nameContainsAny(pName, TOBACCO_KEYWORDS)) {
                            primary = PrimaryCategory.TOBACCO;
                            secondary = SecondaryCategory.SMOKING_PRODUCT;
                        } else if (nameContainsAny(pName, FIREARM_KEYWORDS)) {
                            primary = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.FIREARM;
                        } else if (nameContainsAny(pName, BLADE_KEYWORDS)) {
                            primary = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.SHARP_OBJECT;
                        } else if (nameContainsAny(pName, PLASTIC_KEYWORDS)) {
                            primary = PrimaryCategory.SINGLE_USE_PLASTIC;
                            secondary = SecondaryCategory.PACKAGING;
                        } else if (nameContainsAny(pName, ALCOHOL_KEYWORDS)) {
                            primary = PrimaryCategory.ALCOHOL;
                            secondary = SecondaryCategory.ALCOHOLIC_BEVERAGE;
                        } else {
                            // Unknown prohibited item — default to weapon for maximum safety
                            primary = PrimaryCategory.WEAPON;
                            secondary = SecondaryCategory.SHARP_OBJECT;
                        }
                    }
                    default -> {
                        primary = PrimaryCategory.ALLOWED;
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
                    lastEvaluatedItem = item;
                    lastDecisionResult = result;
                    displayResult(result);
                    frame.setStatus("CV SCAN COMPLETE: " + lastCVResult.getMatchLabel());
                } catch (Exception ex) {
                    showError("CV evaluation error: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    // Returns true if the given text contains any of the provided keywords.
    // Used to map a CV-matched product name to an item category.
    private boolean nameContainsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // Updates the evaluation result panel with the decision, threat level, reason, and action text.
    // Color-codes the decision label: green for allow, red for disallow, amber for conditional.
    private void displayResult(DecisionResult result) {
        String dec = result.getDecision().name();
        lblDecision.setText(dec);
        lblDecision.setForeground(switch (result.getDecision()) {
            case ALLOW -> IcarusTheme.STATUS_ALLOW;
            case DISALLOW -> IcarusTheme.STATUS_DISALLOW;
            case CONDITIONAL -> IcarusTheme.STATUS_CONDITIONAL;
        });
        lblThreat.setText("THREAT: " + result.getThreatLevel().name());
        lblReason.setText("<html>" + result.getReason().replace("\n", "<br>") + "</html>");
        lblAction.setText("<html>" + result.getActionRecommendation().replace("\n", "<br>") + "</html>");
    }

    // Saves the current evaluation result to the database.
    // First saves the item, then the decision log, then releases the item if the decision is ALLOW.
    // Clears the in-memory state afterward so the guard cannot double-log.
    private void doLogDecision() {
        if (lastDecisionResult == null || lastEvaluatedItem == null) {
            showError("No evaluation to log. Run an evaluation first.");
            return;
        }

        try {
            int itemId = itemDAO.addItem(lastEvaluatedItem);

            decisionLogDAO.logDecision(
                itemId,
                lastDecisionResult.getDecision().name(),
                lastDecisionResult.getRiskScore(),
                lastDecisionResult.getThreatLevel().name(),
                lastDecisionResult.getReason(),
                lastDecisionResult.getActionRecommendation(),
                lastDecisionResult.requiresImmediateAlert(),
                lastScannedImagePath
            );

            if (lastDecisionResult.getDecision() == enums.Decision.ALLOW)
                itemDAO.updateItemStatus(itemId, enums.ItemStatus.RELEASED);

            frame.setStatus("DECISION LOGGED - ITEM ID: " + itemId);
            lastDecisionResult = null;
            lastEvaluatedItem = null;
            refreshQueue();

        } catch (Exception e) {
            showError("Failed to log decision: " + e.getMessage());
        }
    }

    // Reloads the queue table from the database. Only shows HELD items for the current student.
    // If no student is loaded, the queue stays empty.
    private void refreshQueue() {
        queueModel.setRowCount(0);
        try {
            List<storage.dao.ItemEntry> entries = itemService.getAllItemsWithIds();
            int held = 0;
            for (storage.dao.ItemEntry entry : entries) {
                Item item = entry.getItem();
                if (item.getStatus() != ItemStatus.HELD) continue;
                if (currentStudent == null) continue;
                if (!currentStudent.getStudentId().equals(item.getStudentId())) continue;
                held++;
                queueModel.addRow(new Object[]{
                    entry.getId(),
                    item.getItemName(),
                    item.getBrand() != null ? item.getBrand() : "-",
                    item.getPrimaryCategory().name(),
                    item.getStudentId() != null ? item.getStudentId() : "-",
                    item.getQuantity(),
                    item.getTimestamp().format(QUEUE_TIME_FMT)
                });
            }
            frame.setItemsHeld(held);
        } catch (Exception e) {
            System.err.println("Queue refresh error: " + e.getMessage());
        }
    }

    // Triggered when the guard clicks a row in the queue.
    // Fetches the saved decision log for that item and populates the result panel and CV image.
    private void doLoadFromQueue() {
        int row = queueTable.getSelectedRow();
        if (row < 0) return;

        int itemId = (int) queueModel.getValueAt(row, 0);

        try {
            List<Map<String, Object>> logs = decisionLogDAO.findByItemId(itemId);
            if (logs.isEmpty()) {
                frame.setStatus("NO DECISION LOG FOR ITEM ID: " + itemId);
                return;
            }

            Map<String, Object> log = logs.get(0);
            String decision = (String) log.get("decision");
            String threat = (String) log.get("threat_level");
            String reason = (String) log.get("reason");
            String actionRec = (String) log.get("action_rec");
            String imagePath = (String) log.get("image_path");

            lblDecision.setText(decision);
            lblDecision.setForeground(switch (decision) {
                case "ALLOW" -> IcarusTheme.STATUS_ALLOW;
                case "DISALLOW" -> IcarusTheme.STATUS_DISALLOW;
                case "CONDITIONAL" -> IcarusTheme.STATUS_CONDITIONAL;
                default -> IcarusTheme.TEXT_DIM;
            });
            lblThreat.setText("THREAT: " + threat);
            lblReason.setText("<html>" + reason.replace("\n", "<br>") + "</html>");
            lblAction.setText("<html>" + actionRec.replace("\n", "<br>") + "</html>");

            if (imagePath != null && !imagePath.isBlank()) {
                try {
                    File imgFile = new File(imagePath);
                    if (imgFile.exists()) {
                        BufferedImage img = ImageIO.read(imgFile);
                        if (img != null) {
                            cvBaseImage = img;
                            cvKeypoints = null;
                            cvScanning = false;
                            cvImagePanel.repaint();
                        }
                    } else {
                        cvBaseImage = null;
                        cvKeypoints = null;
                        cvImagePanel.repaint();
                    }
                } catch (Exception ex) {
                    cvBaseImage = null;
                    cvKeypoints = null;
                    cvImagePanel.repaint();
                }
                lblCVResult.setText("SCAN RESULT: " + decision);
                lblCVConfidence.setText("CONFIDENCE: logged");
                SwingUtilities.invokeLater(() -> switchToCVTab(GuardDashboard.this));
            } else {
                cvBaseImage = null;
                cvKeypoints = null;
                cvLog.clear();
                cvScanning = false;
                cvImagePanel.repaint();
                lblCVResult.setText("SCAN RESULT: ---");
                lblCVConfidence.setText("CONFIDENCE: ---");
            }

            frame.setStatus("LOADED LOG FOR ITEM ID: " + itemId + "  " + decision + " | " + threat);

        } catch (Exception ex) {
            showError("Failed to load decision log: " + ex.getMessage());
        }
    }

    // Walks the component tree looking for the CV SCAN tab and selects it.
    // Used after loading a logged CV result so the image panel becomes visible.
    private void switchToCVTab(Component comp) {
        if (comp instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if ("CV SCAN".equals(tabs.getTitleAt(i))) {
                    tabs.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) switchToCVTab(child);
        }
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

        titleBar.add(title, BorderLayout.WEST);
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

        root.add(titleBar, BorderLayout.NORTH);
        root.add(imageArea, BorderLayout.CENTER);
        root.add(logScroll, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    // Shows a modal error dialog with the given message
    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}