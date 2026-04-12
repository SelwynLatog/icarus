package ui;

// Admin terminal — Decision Log, Student Registry, Overrides, Analytics.
// Convoluted and monolithic as well. Will modularize everything after

import engine.ThreatLevel;
import enums.*;
import model.Item;
import model.Student;
import service.ItemService;
import service.StudentService;
import storage.dao.AdminOverrideDAO;
import storage.dao.DecisionLogDAO;
import storage.dao.ItemDAO;
import storage.dao.ItemEntry;
import storage.dao.StudentDAO;
import ui.theme.IcarusTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminDashboard extends JPanel {

    private final ItemService    itemService;
    private final StudentService studentService;
    private final AppFrame       frame;

    private final DecisionLogDAO   decisionLogDAO   = new DecisionLogDAO();
    private final AdminOverrideDAO adminOverrideDAO = new AdminOverrideDAO();
    private final StudentDAO       studentDAO       = new StudentDAO();
    private final ItemDAO          itemDAO          = new ItemDAO();

    // Log tab
    private DefaultTableModel logModel;
    private JTable            logTable;

    // Registry tab
    private DefaultTableModel registryModel;
    private JTable            registryTable;
    private JTextField        tfRegId, tfRegFirst, tfRegMiddle, tfRegLast, tfRegCourse, tfRegYear;
    private JComboBox<StudentStatus> cbRegStatus;

    // Override tab
    private DefaultTableModel overrideLogModel;
    private JTable            overrideLogTable;
    private JComboBox<String> cbNewDecision;
    private JTextField        tfOverrideReason;
    private JLabel            lblSelectedLog;

    // Analytics panel refs
    private JPanel analyticsPanel;

    // Selected log entry for override
    private int selectedLogId   = -1;
    private int selectedItemId  = -1;

    public AdminDashboard(ItemService itemService, StudentService studentService, AppFrame frame) {
        this.itemService    = itemService;
        this.studentService = studentService;
        this.frame          = frame;

        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildTabArea(),   BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    // Top bar
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IcarusTheme.BG_PANEL_ALT);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 28));

        JLabel label = new JLabel("  ADMIN TERMINAL");
        label.setFont(IcarusTheme.FONT_MONO_BOLD);
        label.setForeground(IcarusTheme.ACCENT);

        JButton btnBack = IcarusTheme.makeButton("ROLE SELECT");
        btnBack.setPreferredSize(new Dimension(160, 24));
        btnBack.addActionListener(e -> frame.showCard(AppFrame.CARD_ROLE));

        bar.add(label,   BorderLayout.WEST);
        bar.add(btnBack, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setBackground(IcarusTheme.CHROME);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 32));

        String[] tabs = { "DECISION LOG", "STUDENT REGISTRY", "OVERRIDES", "ANALYTICS" };
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            JButton btn = new JButton(tabs[i]) {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(IcarusTheme.CHROME);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setFont(getFont());
                    g.setColor(IcarusTheme.CHROME_TEXT);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
                @Override protected void paintBorder(Graphics g) {
                    g.setColor(IcarusTheme.BORDER);
                    g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                }
            };
            btn.setFont(IcarusTheme.FONT_MONO_BOLD);
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setPreferredSize(new Dimension(180, 32));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> switchTab(idx));
            bar.add(btn);
        }
        return bar;
    }

    // Card layout content area
    private JPanel mainContent;
    private CardLayout contentLayout;

    private JPanel buildTabArea() {
        contentLayout = new CardLayout();
        mainContent   = new JPanel(contentLayout);
        mainContent.setBackground(IcarusTheme.BG_FRAME);

        mainContent.add(buildLogTab(),      "LOG");
        mainContent.add(buildRegistryTab(), "REGISTRY");
        mainContent.add(buildOverrideTab(), "OVERRIDES");
        mainContent.add(buildAnalyticsTab(),"ANALYTICS");

        return mainContent;
    }

    private void switchTab(int idx) {
        String[] cards = { "LOG", "REGISTRY", "OVERRIDES", "ANALYTICS" };
        contentLayout.show(mainContent, cards[idx]);
        if (idx == 0) refreshLog();
        if (idx == 1) refreshRegistry();
        if (idx == 2) refreshOverrideLog();
        if (idx == 3) refreshAnalytics();
    }

    // DECISION LOG TAB
    private JPanel buildLogTab() {
        JPanel outer = IcarusTheme.makePanelWithHeader("DECISION LOG");

        String[] cols = { "LOG ID", "ITEM ID", "ITEM NAME", "STUDENT", "DECISION", "THREAT", "RISK", "TIMESTAMP" };
        logModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        logTable = new JTable(logModel);
        logTable.setFont(IcarusTheme.FONT_MONO_SMALL);
        logTable.setRowHeight(20);
        logTable.getTableHeader().setFont(IcarusTheme.FONT_MONO_BOLD);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setShowGrid(true);
        logTable.setGridColor(IcarusTheme.BORDER);

        int[] widths = { 60, 60, 160, 110, 100, 90, 60, 130 };
        for (int i = 0; i < widths.length; i++)
            logTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane scroll = new JScrollPane(logTable);
        scroll.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        scroll.getViewport().setBackground(IcarusTheme.BG_PANEL);

        // Detail panel below table
        JPanel detail = new JPanel(new GridBagLayout());
        detail.setBackground(IcarusTheme.BG_PANEL);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        detail.setPreferredSize(new Dimension(0, 80));

        JLabel lblReason = IcarusTheme.makeDimLabel("Select a log entry to view reason and action.");
        JLabel lblAction = IcarusTheme.makeDimLabel("");
        lblReason.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblAction.setAlignmentX(Component.LEFT_ALIGNMENT);

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = logTable.getSelectedRow();
            if (row < 0) return;
            int logId = (int) logModel.getValueAt(row, 0);
            selectedLogId  = logId;
            selectedItemId = (int) logModel.getValueAt(row, 1);
            try {
                decisionLogDAO.findById(logId).ifPresent(log -> {
                    lblReason.setText("<html><b>REASON:</b> " + log.get("reason") + "</html>");
                    lblAction.setText("<html><b>ACTION:</b> " + log.get("action_rec") + "</html>");
                });
            } catch (Exception ex) {
                lblReason.setText("Error loading log details.");
            }
        });

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; gc.gridx = 0;
        gc.gridy = 0; detail.add(lblReason, gc);
        gc.gridy = 1; detail.add(lblAction, gc);

        JButton btnRefresh = IcarusTheme.makeButton("REFRESH");
        btnRefresh.addActionListener(e -> refreshLog());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btnRow.setOpaque(false);
        btnRow.add(btnRefresh);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        content.add(scroll,  BorderLayout.CENTER);
        content.add(detail,  BorderLayout.SOUTH);

        outer.add(content,  BorderLayout.CENTER);
        outer.add(btnRow,   BorderLayout.SOUTH);

        refreshLog();
        return outer;
    }

    private void refreshLog() {
        logModel.setRowCount(0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        try {
            List<Map<String, Object>> logs = decisionLogDAO.getAll();
            for (Map<String, Object> log : logs) {
                int itemId = (int) log.get("item_id");
                String itemName = "-";
                String studentId = "-";
                try {
                    Optional<Item> item = itemDAO.findItemById(itemId);
                    if (item.isPresent()) {
                        itemName  = item.get().getItemName();
                        studentId = item.get().getStudentId() != null ? item.get().getStudentId() : "-";
                    }
                } catch (Exception ignored) {}

                logModel.addRow(new Object[]{
                    log.get("log_id"),
                    itemId,
                    itemName,
                    studentId,
                    log.get("decision"),
                    log.get("threat_level"),
                    log.get("risk_score"),
                    ((LocalDateTime) log.get("evaluated_at")).format(fmt)
                });
            }
        } catch (Exception e) {
            showError("Failed to load decision log: " + e.getMessage());
        }
    }

    // STUDENT REGISTRY TAB
    // May or may not remove registry feature but for cleaner demo & progress report
    // to our prof I will keep this for now
    private JPanel buildRegistryTab() {
        JPanel outer = IcarusTheme.makePanelWithHeader("STUDENT REGISTRY");

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH; c.weightx = 1.0; c.insets = new Insets(0, 0, 4, 0);

        // Student table
        String[] cols = { "ID", "NAME", "COURSE", "YEAR", "STATUS", "VIOLATIONS" };
        registryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        registryTable = new JTable(registryModel);
        registryTable.setFont(IcarusTheme.FONT_MONO_SMALL);
        registryTable.setRowHeight(20);
        registryTable.getTableHeader().setFont(IcarusTheme.FONT_MONO_BOLD);
        registryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        registryTable.setShowGrid(true);
        registryTable.setGridColor(IcarusTheme.BORDER);

        int[] widths = { 100, 200, 160, 50, 90, 80 };
        for (int i = 0; i < widths.length; i++)
            registryTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane scroll = new JScrollPane(registryTable);
        scroll.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        scroll.getViewport().setBackground(IcarusTheme.BG_PANEL);

        c.gridy = 0; c.weighty = 0.6;
        content.add(scroll, c);

        // Registration form
        JPanel form = IcarusTheme.makePanelWithHeader("REGISTER NEW STUDENT");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fields.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL; fc.insets = new Insets(3, 4, 3, 4);

        tfRegId     = IcarusTheme.makeTextField();
        tfRegFirst  = IcarusTheme.makeTextField();
        tfRegMiddle = IcarusTheme.makeTextField();
        tfRegLast   = IcarusTheme.makeTextField();
        tfRegCourse = IcarusTheme.makeTextField();
        tfRegYear   = IcarusTheme.makeTextField();
        cbRegStatus = new JComboBox<>(StudentStatus.values());
        cbRegStatus.setFont(IcarusTheme.FONT_MONO_SMALL);
        cbRegStatus.setBackground(IcarusTheme.BG_PANEL_ALT);

        int fr = 0;
        addRegRow(fields, fc, fr++, "STUDENT ID",   tfRegId);
        addRegRow(fields, fc, fr++, "FIRST NAME",   tfRegFirst);
        addRegRow(fields, fc, fr++, "MIDDLE NAME",  tfRegMiddle);
        addRegRow(fields, fc, fr++, "LAST NAME",    tfRegLast);
        addRegRow(fields, fc, fr++, "COURSE",       tfRegCourse);
        addRegRow(fields, fc, fr++, "YEAR",         tfRegYear);
        addRegRow(fields, fc, fr++, "STATUS",       cbRegStatus);

        JButton btnRegister = IcarusTheme.makeButton("REGISTER");
        JButton btnSuspend  = IcarusTheme.makeButton("SUSPEND");
        JButton btnRemove   = IcarusTheme.makeButton("REMOVE");

        btnRegister.addActionListener(e -> doRegisterStudent());
        btnSuspend.addActionListener(e  -> doSuspendStudent());
        btnRemove.addActionListener(e   -> doRemoveStudent());

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        btnRow.add(btnRegister);
        btnRow.add(btnSuspend);
        btnRow.add(btnRemove);

        form.add(fields,  BorderLayout.CENTER);
        form.add(btnRow,  BorderLayout.SOUTH);

        c.gridy = 1; c.weighty = 0.4;
        content.add(form, c);

        outer.add(content, BorderLayout.CENTER);

        refreshRegistry();
        return outer;
    }

    private void addRegRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row; c.gridwidth = 1;
        c.gridx = 0; c.weightx = 0.3;
        panel.add(IcarusTheme.makeDimLabel(label), c);
        c.gridx = 1; c.weightx = 0.7;
        panel.add(field, c);
    }

    private void refreshRegistry() {
        registryModel.setRowCount(0);
        try {
            List<Student> students = studentService.getAllStudents();
            for (Student s : students) {
                int violations = itemDAO.findItemsByStudentId(s.getStudentId()).size();
                registryModel.addRow(new Object[]{
                    s.getStudentId(),
                    s.getFullName(),
                    s.getCourse(),
                    s.getYear() == 0 ? "-" : String.valueOf(s.getYear()),
                    s.getStatus().name(),
                    violations
                });
            }
        } catch (Exception e) {
            showError("Failed to load registry: " + e.getMessage());
        }
    }

    private void doRegisterStudent() {
        String id     = tfRegId.getText().trim();
        String first  = tfRegFirst.getText().trim();
        String middle = tfRegMiddle.getText().trim();
        String last   = tfRegLast.getText().trim();
        String course = tfRegCourse.getText().trim();
        String yearTx = tfRegYear.getText().trim();

        if (id.isBlank() || first.isBlank() || last.isBlank() || course.isBlank() || yearTx.isBlank()) {
            showError("All fields except Middle Name are required.");
            return;
        }

        int year;
        try { year = Integer.parseInt(yearTx); }
        catch (NumberFormatException e) { showError("Year must be a number."); return; }

        StudentStatus status = (StudentStatus) cbRegStatus.getSelectedItem();

        try {
            studentService.registerNewStudent(id, first,
                middle.isBlank() ? null : middle,
                last, course, year, status);

            // Generate barcode for the new student
            engine.BarcodeEngine.generate(id);

            clearRegForm();
            refreshRegistry();
            frame.setStatus("STUDENT REGISTERED: " + id);
        } catch (Exception e) {
            showError("Registration failed: " + e.getMessage());
        }
    }

    private void doSuspendStudent() {
        int row = registryTable.getSelectedRow();
        if (row < 0) { showError("Select a student first."); return; }
        String studentId = (String) registryModel.getValueAt(row, 0);
        try {
            studentService.updateStudentStatus(studentId, StudentStatus.SUSPENDED);
            refreshRegistry();
            frame.setStatus("STUDENT SUSPENDED: " + studentId);
        } catch (Exception e) {
            showError("Suspend failed: " + e.getMessage());
        }
    }

    private void doRemoveStudent() {
        int row = registryTable.getSelectedRow();
        if (row < 0) { showError("Select a student first."); return; }
        String studentId = (String) registryModel.getValueAt(row, 0);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove student " + studentId + "? This cannot be undone.",
            "CONFIRM REMOVAL", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            studentService.removeStudent(studentId);
            refreshRegistry();
            frame.setStatus("STUDENT REMOVED: " + studentId);
        } catch (Exception e) {
            showError("Removal failed: " + e.getMessage());
        }
    }

    private void clearRegForm() {
        tfRegId.setText("");
        tfRegFirst.setText("");
        tfRegMiddle.setText("");
        tfRegLast.setText("");
        tfRegCourse.setText("");
        tfRegYear.setText("");
        cbRegStatus.setSelectedIndex(0);
    }

    // OVERRIDE TAB
    private JPanel buildOverrideTab() {
        JPanel outer = IcarusTheme.makePanelWithHeader("ADMIN OVERRIDES");

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH; c.weightx = 1.0; c.insets = new Insets(0, 0, 4, 0);

        // Log table for selecting entry to override
        String[] cols = { "LOG ID", "ITEM ID", "ITEM NAME", "STUDENT", "DECISION", "THREAT", "TIMESTAMP" };
        overrideLogModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        overrideLogTable = new JTable(overrideLogModel);
        overrideLogTable.setFont(IcarusTheme.FONT_MONO_SMALL);
        overrideLogTable.setRowHeight(20);
        overrideLogTable.getTableHeader().setFont(IcarusTheme.FONT_MONO_BOLD);
        overrideLogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        overrideLogTable.setShowGrid(true);
        overrideLogTable.setGridColor(IcarusTheme.BORDER);

        int[] widths = { 60, 60, 160, 110, 100, 90, 130 };
        for (int i = 0; i < widths.length; i++)
            overrideLogTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        overrideLogTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = overrideLogTable.getSelectedRow();
            if (row < 0) return;
            selectedLogId  = (int) overrideLogModel.getValueAt(row, 0);
            selectedItemId = (int) overrideLogModel.getValueAt(row, 1);
            String currentDecision = (String) overrideLogModel.getValueAt(row, 4);
            lblSelectedLog.setText("SELECTED: LOG #" + selectedLogId
                + " | CURRENT: " + currentDecision
                + " | ITEM ID: " + selectedItemId);
        });

        JScrollPane scroll = new JScrollPane(overrideLogTable);
        scroll.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        scroll.getViewport().setBackground(IcarusTheme.BG_PANEL);

        c.gridy = 0; c.weighty = 0.6;
        content.add(scroll, c);

        // Override form
        JPanel form = IcarusTheme.makePanelWithHeader("OVERRIDE DECISION");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fields.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL; fc.insets = new Insets(4, 4, 4, 4);

        lblSelectedLog = IcarusTheme.makeDimLabel("No log entry selected.");
        cbNewDecision  = new JComboBox<>(new String[]{ "ALLOW", "CONDITIONAL", "DISALLOW" });
        cbNewDecision.setFont(IcarusTheme.FONT_MONO_SMALL);
        cbNewDecision.setBackground(IcarusTheme.BG_PANEL_ALT);
        tfOverrideReason = IcarusTheme.makeTextField();

        fc.gridy = 0; fc.gridx = 0; fc.gridwidth = 2; fc.weightx = 1;
        fields.add(lblSelectedLog, fc);
        fc.gridwidth = 1;
        fc.gridy = 1; fc.gridx = 0; fc.weightx = 0.3;
        fields.add(IcarusTheme.makeDimLabel("NEW DECISION"), fc);
        fc.gridx = 1; fc.weightx = 0.7;
        fields.add(cbNewDecision, fc);
        fc.gridy = 2; fc.gridx = 0; fc.weightx = 0.3;
        fields.add(IcarusTheme.makeDimLabel("REASON"), fc);
        fc.gridx = 1; fc.weightx = 0.7;
        fields.add(tfOverrideReason, fc);

        JButton btnOverride = IcarusTheme.makeButton("OVERRIDE");
        btnOverride.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        btnOverride.addActionListener(e -> doOverride());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btnRow.setOpaque(false);
        btnRow.add(IcarusTheme.makeButton("REFRESH"));
        btnRow.getComponent(0);
        ((JButton) btnRow.getComponent(0)).addActionListener(e -> refreshOverrideLog());
        btnRow.add(btnOverride);

        form.add(fields,  BorderLayout.CENTER);
        form.add(btnRow,  BorderLayout.SOUTH);

        c.gridy = 1; c.weighty = 0.4;
        content.add(form, c);

        outer.add(content, BorderLayout.CENTER);
        return outer;
    }

    private void refreshOverrideLog() {
        overrideLogModel.setRowCount(0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        try {
            List<Map<String, Object>> logs = decisionLogDAO.getAll();
            for (Map<String, Object> log : logs) {
                int itemId = (int) log.get("item_id");
                String itemName  = "-";
                String studentId = "-";
                try {
                    Optional<Item> item = itemDAO.findItemById(itemId);
                    if (item.isPresent()) {
                        itemName  = item.get().getItemName();
                        studentId = item.get().getStudentId() != null ? item.get().getStudentId() : "-";
                    }
                } catch (Exception ignored) {}

                overrideLogModel.addRow(new Object[]{
                    log.get("log_id"),
                    itemId,
                    itemName,
                    studentId,
                    log.get("decision"),
                    log.get("threat_level"),
                    ((LocalDateTime) log.get("evaluated_at")).format(fmt)
                });
            }
        } catch (Exception e) {
            showError("Failed to load override log: " + e.getMessage());
        }
    }

    private void doOverride() {
        if (selectedLogId < 0) { showError("Select a log entry first."); return; }
        String reason = tfOverrideReason.getText().trim();
        if (reason.isBlank()) { showError("Reason is required."); return; }

        String originalDecision;
        try {
            originalDecision = (String) decisionLogDAO.findById(selectedLogId)
                .map(m -> m.get("decision")).orElse("UNKNOWN");
        } catch (Exception e) {
            showError("Could not fetch original decision."); return;
        }

        String newDecision = (String) cbNewDecision.getSelectedItem();

        try {
            adminOverrideDAO.recordOverride(selectedLogId, originalDecision, newDecision, reason);

            // Update item status to match new decision
            ItemStatus newStatus = switch (newDecision) {
                case "ALLOW" -> ItemStatus.RELEASED;
                default      -> ItemStatus.HELD;
            };
            itemDAO.updateItemStatus(selectedItemId, newStatus);

            tfOverrideReason.setText("");
            selectedLogId  = -1;
            selectedItemId = -1;
            lblSelectedLog.setText("No log entry selected.");
            refreshOverrideLog();
            frame.setStatus("OVERRIDE RECORDED — LOG #" + selectedLogId);
        } catch (Exception e) {
            showError("Override failed: " + e.getMessage());
        }
    }

    // ANALYTICS TAB
    private JPanel buildAnalyticsTab() {
        analyticsPanel = new JPanel(new GridBagLayout());
        analyticsPanel.setBackground(IcarusTheme.BG_FRAME);
        analyticsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        refreshAnalytics();
        return analyticsPanel;
    }

    private void refreshAnalytics() {
        analyticsPanel.removeAll();

        try {
            List<Map<String, Object>> logs     = decisionLogDAO.getAll();
            List<Item>                items    = itemDAO.getAllItems();
            List<Student>             students = studentDAO.getAllStudents();

            // Compute counts
            int totalItems   = items.size();
            int totalHeld    = 0, totalReleased = 0;
            int totalAllow   = 0, totalDisallow = 0, totalConditional = 0;
            int criticalCount = 0, highCount = 0, mediumCount = 0, lowCount = 0;
            Map<String, Integer> categoryCount = new java.util.LinkedHashMap<>();

            for (Item item : items) {
                if (item.getStatus() == ItemStatus.HELD)     totalHeld++;
                if (item.getStatus() == ItemStatus.RELEASED) totalReleased++;
                String cat = item.getPrimaryCategory().name();
                categoryCount.merge(cat, 1, Integer::sum);
            }

            for (Map<String, Object> log : logs) {
                String dec = (String) log.get("decision");
                String thr = (String) log.get("threat_level");
                if ("ALLOW".equals(dec))       totalAllow++;
                if ("DISALLOW".equals(dec))    totalDisallow++;
                if ("CONDITIONAL".equals(dec)) totalConditional++;
                if ("CRITICAL".equals(thr)) criticalCount++;
                if ("HIGH".equals(thr))     highCount++;
                if ("MEDIUM".equals(thr))   mediumCount++;
                if ("LOW".equals(thr))      lowCount++;
            }

            // Top violators
            Map<String, Integer> violatorMap = new java.util.LinkedHashMap<>();
            for (Student s : students) {
                try {
                    int v = itemDAO.findItemsByStudentId(s.getStudentId()).size();
                    if (v > 0) violatorMap.put(s.getFullName() + " (" + s.getStudentId() + ")", v);
                } catch (Exception ignored) {}
            }

            GridBagConstraints c = new GridBagConstraints();
            c.fill    = GridBagConstraints.BOTH;
            c.insets  = new Insets(4, 4, 4, 4);
            c.weighty = 1.0;

            // Row 0 — stat cards
            c.gridy = 0; c.weighty = 0.15;

            c.gridx = 0; c.weightx = 0.25;
            analyticsPanel.add(buildStatCard("TOTAL ITEMS",   String.valueOf(totalItems),   IcarusTheme.TEXT_PRIMARY), c);
            c.gridx = 1;
            analyticsPanel.add(buildStatCard("HELD",          String.valueOf(totalHeld),    IcarusTheme.STATUS_DISALLOW), c);
            c.gridx = 2;
            analyticsPanel.add(buildStatCard("RELEASED",      String.valueOf(totalReleased),IcarusTheme.STATUS_ALLOW), c);
            c.gridx = 3;
            analyticsPanel.add(buildStatCard("DISALLOWED",    String.valueOf(totalDisallow),IcarusTheme.STATUS_DISALLOW), c);

            // Row 1 — category bar chart + threat breakdown
            c.gridy = 1; c.weighty = 0.5;

            c.gridx = 0; c.gridwidth = 2; c.weightx = 0.6;
            analyticsPanel.add(buildBarChart("ITEMS BY CATEGORY", categoryCount, IcarusTheme.ACCENT), c);

            c.gridx = 2; c.gridwidth = 2; c.weightx = 0.4;
            Map<String, Integer> threatMap = new java.util.LinkedHashMap<>();
            threatMap.put("CRITICAL", criticalCount);
            threatMap.put("HIGH",     highCount);
            threatMap.put("MEDIUM",   mediumCount);
            threatMap.put("LOW",      lowCount);
            analyticsPanel.add(buildBarChart("THREAT BREAKDOWN", threatMap, IcarusTheme.STATUS_DISALLOW), c);

            // Row 2 — decision breakdown + top violators
            c.gridy = 2; c.weighty = 0.35;

            c.gridx = 0; c.gridwidth = 2; c.weightx = 0.4;
            Map<String, Integer> decisionMap = new java.util.LinkedHashMap<>();
            decisionMap.put("ALLOW",       totalAllow);
            decisionMap.put("CONDITIONAL", totalConditional);
            decisionMap.put("DISALLOW",    totalDisallow);
            analyticsPanel.add(buildBarChart("DECISIONS", decisionMap, IcarusTheme.STATUS_CONDITIONAL), c);

            c.gridx = 2; c.gridwidth = 2; c.weightx = 0.6;
            analyticsPanel.add(buildViolatorsTable(violatorMap), c);

        } catch (Exception e) {
            JLabel err = IcarusTheme.makeDimLabel("Analytics load error: " + e.getMessage());
            analyticsPanel.add(err);
        }

        analyticsPanel.revalidate();
        analyticsPanel.repaint();
    }

    private JPanel buildStatCard(String label, String value, Color valueColor) {
        JPanel card = IcarusTheme.makePanelWithHeader(label);
        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font("GohuFont 11 NerdFont Mono", Font.BOLD, 28));
        val.setForeground(valueColor);

        inner.add(val, BorderLayout.CENTER);
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    // Java2D horizontal bar chart
    private JPanel buildBarChart(String title, Map<String, Integer> data, Color barColor) {
        JPanel outer = IcarusTheme.makePanelWithHeader(title);

        JPanel chart = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (data.isEmpty()) return;

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int maxVal = data.values().stream().mapToInt(Integer::intValue).max().orElse(1);
                int n      = data.size();
                int w      = getWidth()  - 16;
                int h      = getHeight() - 8;
                int barH   = Math.max(4, (h / n) - 6);
                int y      = 4;

                g2.setFont(IcarusTheme.FONT_MONO_SMALL);
                FontMetrics fm = g2.getFontMetrics();

                int labelW = data.keySet().stream()
                        .mapToInt(fm::stringWidth).max().orElse(80) + 8;

                for (Map.Entry<String, Integer> entry : data.entrySet()) {
                    String key = entry.getKey();
                    int    val = entry.getValue();

                    // Label
                    g2.setColor(IcarusTheme.TEXT_DIM);
                    g2.drawString(key, 8, y + barH / 2 + fm.getAscent() / 2);

                    // Bar
                    int barW = maxVal == 0 ? 0 : (int) ((double) val / maxVal * (w - labelW - 40));
                    g2.setColor(barColor);
                    g2.fillRect(labelW, y, barW, barH);

                    // Border
                    g2.setColor(IcarusTheme.BORDER);
                    g2.drawRect(labelW, y, w - labelW - 40, barH);

                    // Value
                    g2.setColor(IcarusTheme.TEXT_PRIMARY);
                    g2.drawString(String.valueOf(val), labelW + barW + 4, y + barH / 2 + fm.getAscent() / 2);

                    y += barH + 6;
                }
            }
        };
        chart.setBackground(IcarusTheme.BG_PANEL);
        outer.add(chart, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildViolatorsTable(Map<String, Integer> violatorMap) {
        JPanel outer = IcarusTheme.makePanelWithHeader("TOP VIOLATORS");

        String[] cols = { "STUDENT", "VIOLATIONS" };
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        violatorMap.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> model.addRow(new Object[]{ e.getKey(), e.getValue() }));

        JTable table = new JTable(model);
        table.setFont(IcarusTheme.FONT_MONO_SMALL);
        table.setRowHeight(20);
        table.getTableHeader().setFont(IcarusTheme.FONT_MONO_BOLD);
        table.setShowGrid(true);
        table.setGridColor(IcarusTheme.BORDER);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(IcarusTheme.BORDER));
        scroll.getViewport().setBackground(IcarusTheme.BG_PANEL);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}