package ui;

// Role selection screen shown on launch.
// Guard or Admin — click to proceed to the respective dashboard.

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import ui.theme.IcarusTheme;

public class RoleMenu extends JPanel {

    public interface RoleListener {
        void onRoleSelected(String role);
    }

    private static final String[] ROLES = { "SECURITY", "ADMIN" };
    private static final String[] DESCS = {
        "ITEM SCANNING / STUDENT LOOKUP / GATE DECISIONS",
        "DECISION LOG / OVERRIDES / ANALYTICS / REGISTRY"
    };

    private final RoleListener listener;
    private int hoveredIndex = -1;

    public RoleMenu(RoleListener listener) {
        this.listener = listener;
        setBackground(IcarusTheme.BG_FRAME);
        setLayout(new GridBagLayout());
        add(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(IcarusTheme.BG_FRAME);
        root.setOpaque(false);

        JLabel title = new JLabel("ICARUS");
        title.setFont(new Font("GohuFont 11 NerdFont Mono", Font.BOLD, 36));
        title.setForeground(IcarusTheme.ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("ITEM COMPLIANCE AUDIT & REGULATORY UNIVERSITY SYSTEM");
        sub.setFont(IcarusTheme.FONT_MONO_SMALL);
        sub.setForeground(IcarusTheme.TEXT_DIM);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setForeground(IcarusTheme.BORDER);
        sep.setMaximumSize(new Dimension(480, 1));

        JLabel prompt = IcarusTheme.makeDimLabel("SELECT OPERATOR ROLE:");
        prompt.setAlignmentX(Component.CENTER_ALIGNMENT);

        root.add(Box.createVerticalStrut(12));
        root.add(title);
        root.add(Box.createVerticalStrut(6));
        root.add(sub);
        root.add(Box.createVerticalStrut(20));
        root.add(sep);
        root.add(Box.createVerticalStrut(20));
        root.add(prompt);
        root.add(Box.createVerticalStrut(14));

        for (int i = 0; i < ROLES.length; i++) {
            root.add(buildRoleRow(i));
            root.add(Box.createVerticalStrut(8));
        }

        return root;
    }

    private JPanel buildRoleRow(int index) {
        JPanel row = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(hoveredIndex == index ? IcarusTheme.BG_SELECTION : IcarusTheme.BG_PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
                // Left accent bar changes color on hover
                g.setColor(hoveredIndex == index ? IcarusTheme.ACCENT : IcarusTheme.BORDER);
                g.fillRect(0, 0, 3, getHeight());
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(IcarusTheme.BORDER, 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        row.setMaximumSize(new Dimension(480, 64));
        row.setPreferredSize(new Dimension(480, 64));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel name = new JLabel(ROLES[index]);
        name.setFont(IcarusTheme.FONT_MONO_TITLE);
        name.setForeground(IcarusTheme.TEXT_HEADER);

        JLabel desc = new JLabel(DESCS[index]);
        desc.setFont(IcarusTheme.FONT_MONO_SMALL);
        desc.setForeground(IcarusTheme.TEXT_DIM);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(name);
        text.add(Box.createVerticalStrut(3));
        text.add(desc);

        JLabel arrow = new JLabel(">");
        arrow.setFont(IcarusTheme.FONT_MONO_BOLD);
        arrow.setForeground(IcarusTheme.ACCENT);

        row.add(text,  BorderLayout.CENTER);
        row.add(arrow, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hoveredIndex = index; row.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hoveredIndex = -1;    row.repaint(); }
            @Override public void mouseClicked(MouseEvent e) { listener.onRoleSelected(ROLES[index]); }
        });

        return row;
    }
}