package ui;

// Guard-facing security terminal.
// Handles student lookup, manual/CV item evaluation, decision logging, and held items queue.
// Quite convoluted and monolithic. Will clean it once UI is finalized
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

    private final ItemService    itemService;
    private final StudentService studentService;
    private final AppFrame       frame;

    // Student panel
    private JLabel lblStudentName;
    private JLabel lblStudentCourse;
    private JLabel lblStudentYear;
    private JLabel lblStudentStatus;
    private JLabel lblStudentViolations;
    private JLabel lblStudentId;
    private JLabel lblStudentPhoto;

    // Evaluation result panel
    private JLabel lblDecision;
    private JLabel lblReason;
    private JLabel lblAction;
    private JLabel lblThreat;
    private DecisionResult lastDecisionResult;

    // Queue table
    private DefaultTableModel queueModel;
    private JTable queueTable;

    // CV scan state
    private JLabel lblCVImage;
    private JLabel lblCVResult;
    private JLabel lblCVConfidence;
    private String lastScannedImagePath;
    private CVMatchResult lastCVResult;
    private Item lastEvaluatedItem;

    // Manual entry form fields
    private JTextField tfItemName;
    private JTextField tfBrand;
    private JTextField tfQuantity;
    private JComboBox<PrimaryCategory>    cbPrimary;
    private JComboBox<SecondaryCategory>  cbSecondary;
    private JComboBox<ItemFunction>       cbFunction;
    private JComboBox<ConsumptionContext> cbContext;
    private JComboBox<UsageType>          cbUsage;
    private JComboBox<Replaceability>     cbReplace;

    private Student currentStudent;

    private final storage.dao.DecisionLogDAO decisionLogDAO = new storage.dao.DecisionLogDAO();
    private final storage.dao.ItemDAO        itemDAO        = new storage.dao.ItemDAO();

    public GuardDashboard(ItemService itemService, StudentService studentService, AppFrame frame) {
        this.itemService    = itemService;
        this.studentService = studentService;
        this.frame          = frame;

        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),  BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        refreshQueue();

        queueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) doLoadFromQueue();
        });
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IcarusTheme.BG_PANEL_ALT);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 28));

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

    private JPanel buildContent() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(IcarusTheme.BG_FRAME);

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.BOTH;
        c.insets  = new Insets(4, 4, 4, 4);
        c.weighty = 1.0;

        c.gridx = 0; c.gridy = 0; c.weightx = 0.28;
        root.add(buildStudentPanel(), c);

        c.gridx = 1; c.weightx = 0.72;
        root.add(buildRightColumn(), c);

        return root;
    }

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
        manualRow.add(btnManual,  BorderLayout.EAST);

        JSeparator sep = new JSeparator();
        sep.setForeground(IcarusTheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        lblStudentId         = makeCardRow("ID",         "---");
        lblStudentName       = makeCardRow("NAME",       "---");
        lblStudentCourse     = makeCardRow("COURSE",     "---");
        lblStudentYear       = makeCardRow("YEAR",       "---");
        lblStudentStatus     = makeCardRow("STATUS",     "---");
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
        lblStudentPhoto.setPreferredSize(new Dimension(0, 220));

        panel.add(Box.createVerticalStrut(2));
        panel.add(lblStudentPhoto);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    private JLabel makeCardRow(String field, String value) {
        JLabel l = new JLabel(String.format("%-12s %s", field + ":", value));
        l.setFont(IcarusTheme.FONT_MONO_SMALL);
        l.setForeground(IcarusTheme.TEXT_PRIMARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.insets  = new Insets(0, 0, 4, 0);

        c.gridy = 0; c.weighty = 0.55;
        col.add(buildEntryArea(), c);

        c.gridy = 1; c.weighty = 0.45;
        col.add(buildQueuePanel(), c);

        return col;
    }

    private JPanel buildEntryArea() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        c.insets  = new Insets(0, 0, 0, 4);

        c.gridx = 0; c.weightx = 0.58;
        root.add(buildEntryTabs(), c);

        c.gridx = 1; c.weightx = 0.42;
        c.insets = new Insets(0, 0, 0, 0);
        root.add(buildResultPanel(), c);

        return root;
    }

    private JTabbedPane buildEntryTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(IcarusTheme.FONT_MONO_BOLD);
        tabs.setBackground(IcarusTheme.BG_PANEL);
        tabs.setForeground(IcarusTheme.TEXT_PRIMARY);
        tabs.addTab("MANUAL ENTRY", buildManualTab());
        tabs.addTab("CV SCAN",      buildCVTab());
        return tabs;
    }

    private JPanel buildManualTab() {
        JPanel panel = IcarusTheme.makePanel(null);
        panel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.HORIZONTAL;
        c.insets  = new Insets(3, 6, 3, 6);
        c.weightx = 1.0;

        tfItemName = IcarusTheme.makeTextField();
        tfBrand    = IcarusTheme.makeTextField();
        tfQuantity = IcarusTheme.makeTextField();
        tfQuantity.setText("1");

        cbPrimary   = new JComboBox<>(PrimaryCategory.values());
        cbSecondary = new JComboBox<>(SecondaryCategory.values());
        cbFunction  = new JComboBox<>(ItemFunction.values());
        cbContext   = new JComboBox<>(ConsumptionContext.values());
        cbUsage     = new JComboBox<>(UsageType.values());
        cbReplace   = new JComboBox<>(Replaceability.values());

        styleCombo(cbPrimary);   styleCombo(cbSecondary);
        styleCombo(cbFunction);  styleCombo(cbContext);
        styleCombo(cbUsage);     styleCombo(cbReplace);

        int row = 0;
        addFormRow(panel, c, row++, "ITEM NAME",      tfItemName);
        addFormRow(panel, c, row++, "BRAND",          tfBrand);
        addFormRow(panel, c, row++, "QUANTITY",       tfQuantity);
        addFormRow(panel, c, row++, "CATEGORY",       cbPrimary);
        addFormRow(panel, c, row++, "SUB-CATEGORY",   cbSecondary);
        addFormRow(panel, c, row++, "FUNCTION",       cbFunction);
        addFormRow(panel, c, row++, "CONTEXT",        cbContext);
        addFormRow(panel, c, row++, "USAGE TYPE",     cbUsage);
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

    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row; c.gridwidth = 1; c.weighty = 0;
        c.gridx = 0; c.weightx = 0.35;
        panel.add(IcarusTheme.makeDimLabel(label), c);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(field, c);
    }

    private <T> void styleCombo(JComboBox<T> cb) {
        cb.setFont(IcarusTheme.FONT_MONO_SMALL);
        cb.setBackground(IcarusTheme.BG_PANEL_ALT);
        cb.setForeground(IcarusTheme.TEXT_PRIMARY);
    }

    private JPanel buildCVTab() {
        JPanel panel = IcarusTheme.makePanel(null);
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        lblCVImage = new JLabel("NO IMAGE SELECTED", SwingConstants.CENTER);
        lblCVImage.setFont(IcarusTheme.FONT_MONO_BOLD);
        lblCVImage.setForeground(IcarusTheme.TEXT_DIM);
        lblCVImage.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        lblCVImage.setOpaque(true);
        lblCVImage.setBackground(IcarusTheme.BG_PANEL_ALT);
        lblCVImage.setHorizontalAlignment(SwingConstants.CENTER);
        lblCVImage.setVerticalAlignment(SwingConstants.CENTER);

        // Fixed-height holder prevents image from pushing the queue panel down.
        JPanel imgHolder = new JPanel(new BorderLayout());
        imgHolder.setOpaque(false);
        imgHolder.setPreferredSize(new Dimension(0, 220));
        imgHolder.setMinimumSize(new Dimension(0, 220));
        imgHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        imgHolder.add(lblCVImage, BorderLayout.CENTER);

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

        panel.add(imgHolder,   BorderLayout.CENTER);
        panel.add(resultBlock, BorderLayout.NORTH);
        panel.add(btnRow,      BorderLayout.SOUTH);

        return panel;
    }

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

    // Wraps a component in a left-aligned full-width panel with horizontal padding.
    private JPanel pad(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
        p.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

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
        queueTable.setRowHeight(20);
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

        panel.add(scroll,  BorderLayout.CENTER);
        panel.add(btnRow,  BorderLayout.SOUTH);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

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

    private void populateStudentCard(Student s) {
        int violations = studentService.findStudentById(s.getStudentId())
                .map(st -> st.getItemIds().size()).orElse(0);

        lblStudentId.setText(fmt("ID",         s.getStudentId()));
        lblStudentName.setText(fmt("NAME",      s.getFullName()));
        lblStudentCourse.setText(fmt("COURSE",  s.getCourse()));
        lblStudentYear.setText(fmt("YEAR",      String.valueOf(s.getYear())));
        lblStudentViolations.setText(fmt("VIOLATIONS", String.valueOf(violations)));
        SwingUtilities.invokeLater(() -> loadStudentPhoto(s.getStudentId()));

        lblStudentStatus.setText(fmt("STATUS", s.getStatus().name()));
        lblStudentStatus.setForeground(switch (s.getStatus()) {
            case SUSPENDED -> IcarusTheme.STATUS_DISALLOW;
            case ENROLLED  -> IcarusTheme.STATUS_ALLOW;
            default        -> IcarusTheme.TEXT_DIM;
        });

        if (s.getStatus() == StudentStatus.SUSPENDED)
            frame.setStatus("ALERT: SUSPENDED STUDENT  " + s.getFullName().toUpperCase());
    }

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

            int w = 400;
            int h = 220;
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

    private String fmt(String field, String value) {
        return String.format("%-12s %s", field + ":", value);
    }

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
                (PrimaryCategory)    cbPrimary.getSelectedItem(),
                (SecondaryCategory)  cbSecondary.getSelectedItem(),
                (ItemFunction)       cbFunction.getSelectedItem(),
                (ConsumptionContext) cbContext.getSelectedItem(),
                (UsageType)          cbUsage.getSelectedItem(),
                (Replaceability)     cbReplace.getSelectedItem(),
                ItemStatus.HELD, qty, LocalDateTime.now(), studentId
            );

            DecisionResult result = DecisionEngine.evaluate(item);
            lastEvaluatedItem  = item;
            lastDecisionResult = result;
            displayResult(result);
            frame.setStatus("EVALUATION COMPLETE: " + result.getDecision().name());
        } catch (Exception ex) {
            showError("Evaluation error: " + ex.getMessage());
        }
    }

    private void doCVPick() {
        JFileChooser fc = new JFileChooser("assets");
        fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        lastScannedImagePath = f.getAbsolutePath();

        try {
            BufferedImage img = ImageIO.read(f);
            if (img != null) {
                Image scaled = img.getScaledInstance(
                    lblCVImage.getWidth() > 0 ? lblCVImage.getWidth() : 200,
                    130, Image.SCALE_SMOOTH);
                lblCVImage.setIcon(new ImageIcon(scaled));
                lblCVImage.setText("");
            }
        } catch (Exception ex) {
            lblCVImage.setText(f.getName());
        }

        lblCVResult.setText("SCAN RESULT: IMAGE LOADED — PRESS RUN CV SCAN");
        frame.setStatus("IMAGE SELECTED: " + f.getName());
    }

    private void doCVEvaluate() {
        if (lastScannedImagePath == null) {
            showError("No image selected. Use SELECT IMAGE first.");
            return;
        }

        lastCVResult = CVEngine.matchImage(lastScannedImagePath);
        lblCVResult.setText("SCAN RESULT: " + lastCVResult.getMatchLabel());
        lblCVConfidence.setText("CONFIDENCE: " + String.format("%.1f%%", lastCVResult.getConfidence() * 100));

        String studentId = currentStudent != null ? currentStudent.getStudentId() : null;
        PrimaryCategory primary;
        SecondaryCategory secondary;

        switch (lastCVResult.getMatchLabel()) {
            case "APPROVED" -> {
                // Low-confidence canteen matches fall through as out-of-scope to avoid false approvals.
                if (lastCVResult.getConfidence() < 0.70f) {
                    primary   = PrimaryCategory.ALLOWED;
                    secondary = SecondaryCategory.FOOD_CONTAINER;
                } else {
                    primary   = PrimaryCategory.CANTEEN_PRODUCT;
                    secondary = SecondaryCategory.APPROVED_FOOD;
                }
            }
            case "REJECTED" -> {
                //"What in the fuck is this abomination?"
                // Yes i know. Im sorry.
                String pName = lastCVResult.getProductName().toLowerCase();
                if (pName.contains("cig") || pName.contains("vape") || pName.contains("tobacco")) {
                    primary   = PrimaryCategory.TOBACCO;
                    secondary = SecondaryCategory.SMOKING_PRODUCT;
                } else if (pName.contains("gun") || pName.contains("45") ||
                        pName.contains("firearm") || pName.contains("pistol") ||
                        pName.contains("rifle") || pName.contains("revolver")) {
                    primary   = PrimaryCategory.WEAPON;
                    secondary = SecondaryCategory.FIREARM;
                }  else if (pName.contains("knife") || pName.contains("gun") ||
                           pName.contains("blade") || pName.contains("45") ||
                           pName.contains("weapon") || pName.contains("sharp")) {
                    primary   = PrimaryCategory.WEAPON;
                    secondary = SecondaryCategory.SHARP_OBJECT;
                } else if (pName.contains("plastic") || pName.contains("spork") ||
                           pName.contains("cup") || pName.contains("cellophane")) {
                    primary   = PrimaryCategory.SINGLE_USE_PLASTIC;
                    secondary = SecondaryCategory.PACKAGING;
                } else if (pName.contains("alcohol") || pName.contains("beer") ||
                           pName.contains("wine") || pName.contains("liquor")) {
                    primary   = PrimaryCategory.ALCOHOL;
                    secondary = SecondaryCategory.ALCOHOLIC_BEVERAGE;
                } else {
                    // Unknown prohibited item — default to weapon path for strictness.
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
            lastEvaluatedItem  = item;
            lastDecisionResult = result;
            displayResult(result);
            frame.setStatus("CV SCAN COMPLETE: " + lastCVResult.getMatchLabel());
        } catch (Exception ex) {
            showError("CV evaluation error: " + ex.getMessage());
        }
    }

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

            frame.setStatus("DECISION LOGGED — ITEM ID: " + itemId);

            lastDecisionResult = null;
            lastEvaluatedItem  = null;

            refreshQueue();

        } catch (Exception e) {
            showError("Failed to log decision: " + e.getMessage());
        }
    }

    private void refreshQueue() {
        queueModel.setRowCount(0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
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
                    item.getTimestamp().format(fmt)
                });
            }
            frame.setItemsHeld(held);
        } catch (Exception e) {
            System.err.println("Queue refresh error: " + e.getMessage());
        }
    }

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
                            int w = lblCVImage.getWidth()  > 0 ? lblCVImage.getWidth()  : 300;
                            int h = lblCVImage.getHeight() > 0 ? lblCVImage.getHeight() : 220;
                            double aspect = (double) img.getWidth() / img.getHeight();
                            int scaledW = w;
                            int scaledH = (int) (w / aspect);
                            if (scaledH > h) { scaledH = h; scaledW = (int) (h * aspect); }
                            lblCVImage.setIcon(new ImageIcon(img.getScaledInstance(
                                    scaledW, scaledH, Image.SCALE_SMOOTH)));
                            lblCVImage.setText("");
                        }
                    } else {
                        lblCVImage.setIcon(null);
                        lblCVImage.setText("IMAGE FILE MISSING");
                    }
                } catch (Exception ex) {
                    lblCVImage.setIcon(null);
                    lblCVImage.setText("IMAGE LOAD ERROR");
                }
                lblCVResult.setText("SCAN RESULT: " + decision);
                lblCVConfidence.setText("CONFIDENCE: logged");
                SwingUtilities.invokeLater(() -> switchToCVTab(GuardDashboard.this));
            } else {
                lblCVImage.setIcon(null);
                lblCVImage.setText("NO IMAGE (MANUAL ENTRY)");
                lblCVResult.setText("SCAN RESULT: ---");
                lblCVConfidence.setText("CONFIDENCE: ---");
            }

            frame.setStatus("LOADED LOG FOR ITEM ID: " + itemId + " — " + decision + " | " + threat);

        } catch (Exception ex) {
            showError("Failed to load decision log: " + ex.getMessage());
        }
    }

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

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}