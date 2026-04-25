package ui;

// Manual item entry form.
// Guard fills in item details, hits EVALUATE, and the result is passed back
// to GuardDashboard via the onEvaluated callback.

import engine.DecisionEngine;
import engine.DecisionResult;
import enums.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import javax.swing.*;
import model.Item;
import service.StudentService;
import ui.theme.IcarusTheme;

public class ManualEntryPanel extends JPanel {

    private final AppFrame frame;

    // Called after a successful evaluation with (item, result)
    private final BiConsumer<Item, DecisionResult> onEvaluated;

    // Supplier for the currently loaded student ID - may return null
    private final java.util.function.Supplier<String> studentIdSupplier;

    private JTextField tfItemName;
    private JTextField tfBrand;
    private JTextField tfQuantity;
    private JComboBox<PrimaryCategory>    cbPrimary;
    private JComboBox<SecondaryCategory>  cbSecondary;
    private JComboBox<ItemFunction>       cbFunction;
    private JComboBox<ConsumptionContext> cbContext;
    private JComboBox<UsageType>          cbUsage;
    private JComboBox<Replaceability>     cbReplace;

    public ManualEntryPanel(AppFrame frame,
                            BiConsumer<Item, DecisionResult> onEvaluated,
                            java.util.function.Supplier<String> studentIdSupplier) {
        this.frame             = frame;
        this.onEvaluated       = onEvaluated;
        this.studentIdSupplier = studentIdSupplier;
        setLayout(new BorderLayout());
        setOpaque(false);
        add(buildManualTab(), BorderLayout.CENTER);
    }

    // Builds the form with all item classification dropdowns and an evaluate button
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

    // Adds a label-field pair as one row in the form grid
    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row; c.gridwidth = 1; c.weighty = 0;
        c.gridx = 0; c.weightx = 0.35;
        panel.add(IcarusTheme.makeDimLabel(label), c);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(field, c);
    }

    // Applies consistent theme styling to a combo box
    private <T> void styleCombo(JComboBox<T> cb) {
        cb.setFont(IcarusTheme.FONT_MONO_SMALL);
        cb.setBackground(IcarusTheme.BG_PANEL_ALT);
        cb.setForeground(IcarusTheme.TEXT_PRIMARY);
    }

    // Reads the form, builds an Item, runs it through the decision engine,
    // and fires onEvaluated. Does not save to the database.
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

        String studentId = studentIdSupplier.get();

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
            onEvaluated.accept(item, result);
            frame.setStatus("EVALUATION COMPLETE: " + result.getDecision().name());
        } catch (Exception ex) {
            showError("Evaluation error: " + ex.getMessage());
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}