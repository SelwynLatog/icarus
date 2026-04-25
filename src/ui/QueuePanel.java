package ui;

// Held items queue panel.
// Shows only HELD items belonging to the currently loaded student.
// Notifies GuardDashboard when the guard selects a row via onRowSelected.

import enums.ItemStatus;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import model.Item;
import service.ItemService;
import storage.dao.ItemEntry;
import ui.theme.IcarusTheme;

public class QueuePanel extends JPanel {

    private static final int QUEUE_ROW_HEIGHT = 20;
    private static final DateTimeFormatter QUEUE_TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private final ItemService itemService;
    private final AppFrame frame;

    // Called when the guard clicks a row - passes the item ID
    private final Consumer<Integer> onRowSelected;

    // Supplies the currently loaded student ID - may return null
    private final java.util.function.Supplier<String> studentIdSupplier;

    private DefaultTableModel queueModel;
    private JTable queueTable;

    public QueuePanel(ItemService itemService,
                      AppFrame frame,
                      Consumer<Integer> onRowSelected,
                      java.util.function.Supplier<String> studentIdSupplier) {
        this.itemService       = itemService;
        this.frame             = frame;
        this.onRowSelected     = onRowSelected;
        this.studentIdSupplier = studentIdSupplier;
        setLayout(new BorderLayout());
        setOpaque(false);
        add(buildQueuePanel(), BorderLayout.CENTER);

        queueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = queueTable.getSelectedRow();
                if (row >= 0) onRowSelected.accept((int) queueModel.getValueAt(row, 0));
            }
        });
    }

    // Builds the held items queue table
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
        btnRefresh.addActionListener(e -> refresh());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.setOpaque(false);
        btnRow.add(btnRefresh);

        panel.add(scroll,  BorderLayout.CENTER);
        panel.add(btnRow,  BorderLayout.SOUTH);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    // Reloads the queue table from the database.
    // Only shows HELD items for the current student. If no student is loaded, stays empty.
    public void refresh() {
        queueModel.setRowCount(0);
        String currentStudentId = studentIdSupplier.get();
        try {
            List<ItemEntry> entries = itemService.getAllItemsWithIds();
            int held = 0;
            for (ItemEntry entry : entries) {
                Item item = entry.getItem();
                if (item.getStatus() != ItemStatus.HELD) continue;
                if (currentStudentId == null) continue;
                if (!currentStudentId.equals(item.getStudentId())) continue;
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
}