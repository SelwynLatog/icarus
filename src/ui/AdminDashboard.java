package ui;

import service.ItemService;
import service.StudentService;
import ui.theme.IcarusTheme;
import javax.swing.*;
import java.awt.*;

// Phase 3 — Admin view. Decision log + overrides + analytics.
public class AdminDashboard extends JPanel {

    private final ItemService    itemService;
    private final StudentService studentService;
    private final AppFrame       frame;

    public AdminDashboard(ItemService itemService, StudentService studentService, AppFrame frame) {
        this.itemService    = itemService;
        this.studentService = studentService;
        this.frame          = frame;
        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout());

        JLabel wip = IcarusTheme.makeHeader("ADMIN DASHBOARD — BUILDING");
        wip.setHorizontalAlignment(SwingConstants.CENTER);
        add(wip, BorderLayout.CENTER);
    }
}