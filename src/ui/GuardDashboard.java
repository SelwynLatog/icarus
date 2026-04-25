package ui;

// Guard-facing security terminal.
// Orchestrates the four sub-panels: StudentPanel, ManualEntryPanel, CVScanPanel, QueuePanel.
// Owns shared state (currentStudent, lastDecisionResult, lastEvaluatedItem) and all
// cross-panel actions (displayResult, doLogDecision, doLoadFromQueue).

import engine.DecisionResult;
import enums.Decision;
import enums.ItemStatus;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import model.Item;
import model.Student;
import service.ItemService;
import service.StudentService;
import storage.dao.DecisionLogDAO;
import storage.dao.ItemDAO;
import ui.theme.IcarusTheme;

public class GuardDashboard extends JPanel {

    private static final int TOP_BAR_HEIGHT      = 28;
    private static final float STUDENT_PANEL_WEIGHT = 0.28f;
    private static final float RIGHT_PANEL_WEIGHT   = 0.72f;
    private static final float ENTRY_AREA_WEIGHT    = 0.58f;
    private static final float RESULT_PANEL_WEIGHT  = 0.42f;

    private final ItemService    itemService;
    private final StudentService studentService;
    private final AppFrame       frame;

    private final DecisionLogDAO decisionLogDAO = new DecisionLogDAO();
    private final ItemDAO        itemDAO        = new ItemDAO();

    // Shared state - read by sub-panels via suppliers, written here via callbacks
    private Student        currentStudent     = null;
    private DecisionResult lastDecisionResult = null;
    private Item           lastEvaluatedItem  = null;

    // Sub-panels
    private StudentPanel     studentPanel;
    private CVScanPanel      cvScanPanel;
    private QueuePanel       queuePanel;

    // Evaluation result labels - owned here, updated by displayResult()
    private JLabel lblDecision;
    private JLabel lblReason;
    private JLabel lblAction;
    private JLabel lblThreat;

    public GuardDashboard(ItemService itemService, StudentService studentService, AppFrame frame) {
        this.itemService    = itemService;
        this.studentService = studentService;
        this.frame          = frame;

        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),  BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        queuePanel.refresh();
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

        bar.add(label,   BorderLayout.WEST);
        bar.add(btnBack, BorderLayout.EAST);
        return bar;
    }

    // Splits the screen into the student panel on the left and the entry/queue area on the right
    private JPanel buildContent() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(IcarusTheme.BG_FRAME);

        GridBagConstraints c = new GridBagConstraints();
        c.fill   = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 4, 4, 4);
        c.weighty = 1.0;

        studentPanel = new StudentPanel(studentService, frame, this::onStudentLoaded);

        c.gridx = 0; c.gridy = 0; c.weightx = STUDENT_PANEL_WEIGHT;
        root.add(studentPanel, c);

        c.gridx = 1; c.weightx = RIGHT_PANEL_WEIGHT;
        root.add(buildRightColumn(), c);

        return root;
    }

    // Stacks the entry area (tabs + result panel) above the held items queue
    private JPanel buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.insets  = new Insets(0, 0, 4, 0);

        queuePanel = new QueuePanel(
            itemService, frame,
            this::doLoadFromQueue,
            () -> currentStudent != null ? currentStudent.getStudentId() : null
        );

        c.gridy = 0; c.weighty = 0.55;
        col.add(buildEntryArea(), c);

        c.gridy = 1; c.weighty = 0.45;
        col.add(queuePanel, c);

        return col;
    }

    // Puts the manual/CV tabs side by side with the evaluation result panel
    private JPanel buildEntryArea() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        c.insets  = new Insets(0, 0, 0, 4);

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

        ManualEntryPanel manualPanel = new ManualEntryPanel(
            frame,
            (item, result) -> { lastEvaluatedItem = item; lastDecisionResult = result; displayResult(result); },
            () -> currentStudent != null ? currentStudent.getStudentId() : null
        );

        cvScanPanel = new CVScanPanel(
            frame,
            (item, result) -> { lastEvaluatedItem = item; lastDecisionResult = result; displayResult(result); },
            () -> currentStudent != null ? currentStudent.getStudentId() : null
        );

        tabs.addTab("MANUAL ENTRY", manualPanel);
        tabs.addTab("CV SCAN",      cvScanPanel);
        return tabs;
    }

    // Builds the evaluation result panel showing decision, threat level, reason, and action steps.
    // The LOG DECISION button saves the result to the database.
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

    // Fired by StudentPanel when a student is loaded (non-null) or cleared (null)
    private void onStudentLoaded(Student student) {
        currentStudent = student;
        queuePanel.refresh();
    }

    // Updates the evaluation result panel with the decision, threat level, reason, and action text.
    // Color-codes the decision label: green for allow, red for disallow, amber for conditional.
    private void displayResult(DecisionResult result) {
        String dec = result.getDecision().name();
        lblDecision.setText(dec);
        lblDecision.setForeground(switch (result.getDecision()) {
            case ALLOW       -> IcarusTheme.STATUS_ALLOW;
            case DISALLOW    -> IcarusTheme.STATUS_DISALLOW;
            case CONDITIONAL -> IcarusTheme.STATUS_CONDITIONAL;
        });
        lblThreat.setText("THREAT: " + result.getThreatLevel().name());
        lblReason.setText("<html>" + result.getReason().replace("\n", "<br>") + "</html>");
        lblAction.setText("<html>" + result.getActionRecommendation().replace("\n", "<br>") + "</html>");
    }

    // Saves the current evaluation result to the database.
    // First saves the item, then the decision log, then releases the item if decision is ALLOW.
    // Clears in-memory state afterward so the guard cannot double-log.
    private void doLogDecision() {
        if (lastDecisionResult == null || lastEvaluatedItem == null) {
            JOptionPane.showMessageDialog(this, "No evaluation to log. Run an evaluation first.",
                "ERROR", JOptionPane.ERROR_MESSAGE);
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
                cvScanPanel.getLastScannedImagePath()
            );

            if (lastDecisionResult.getDecision() == Decision.ALLOW)
                itemDAO.updateItemStatus(itemId, ItemStatus.RELEASED);

            frame.setStatus("DECISION LOGGED - ITEM ID: " + itemId);
            lastDecisionResult = null;
            lastEvaluatedItem  = null;
            queuePanel.refresh();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to log decision: " + e.getMessage(),
                "ERROR", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Triggered when the guard clicks a row in the queue.
    // Fetches the saved decision log for that item and populates the result panel and CV image.
    private void doLoadFromQueue(int itemId) {
        try {
            List<Map<String, Object>> logs = decisionLogDAO.findByItemId(itemId);
            if (logs.isEmpty()) {
                frame.setStatus("NO DECISION LOG FOR ITEM ID: " + itemId);
                return;
            }

            Map<String, Object> log = logs.get(0);
            String decision  = (String) log.get("decision");
            String threat    = (String) log.get("threat_level");
            String reason    = (String) log.get("reason");
            String actionRec = (String) log.get("action_rec");
            String imagePath = (String) log.get("image_path");

            lblDecision.setText(decision);
            lblDecision.setForeground(switch (decision) {
                case "ALLOW"       -> IcarusTheme.STATUS_ALLOW;
                case "DISALLOW"    -> IcarusTheme.STATUS_DISALLOW;
                case "CONDITIONAL" -> IcarusTheme.STATUS_CONDITIONAL;
                default            -> IcarusTheme.TEXT_DIM;
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
                            cvScanPanel.showLoggedImage(img, decision);
                        } else {
                            cvScanPanel.clearImage();
                        }
                    } else {
                        cvScanPanel.clearImage();
                    }
                } catch (Exception ex) {
                    cvScanPanel.clearImage();
                }
                SwingUtilities.invokeLater(() -> switchToCVTab(this));
            } else {
                cvScanPanel.clearImage();
            }

            frame.setStatus("LOADED LOG FOR ITEM ID: " + itemId + "  " + decision + " | " + threat);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load decision log: " + ex.getMessage(),
                "ERROR", JOptionPane.ERROR_MESSAGE);
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
}