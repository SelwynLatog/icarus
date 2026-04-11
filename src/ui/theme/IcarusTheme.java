package ui.theme;

// Central theme registry — all colors, fonts, borders, and component factories.
// Every UI component pulls from here. Nothing hardcoded elsewhere.
// Palette: PC-98 / A-Train 90s Japanese workstation aesthetic.

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class IcarusTheme {

    // Workspace backgrounds
    public static final Color BG_FRAME       = new Color(0xF9FADA);
    public static final Color BG_PANEL       = new Color(0XFDFFDE);
    public static final Color BG_PANEL_ALT   = new Color(0xe4e6c1);
    public static final Color BG_SELECTION   = new Color(0xb1b396);
    public static final Color BG_BUTTON      = new Color(0xb1b396);

    // Structural chrome
    public static final Color BORDER         = new Color(0x5A5A3A);
    public static final Color BORDER_BRIGHT  = new Color(0x3A3A22);

    // Accent
    public static final Color ACCENT         = new Color(0x4A6A10);
    public static final Color SUB_ACCENT     = new Color(0xdbe1cf);

    // Text
    public static final Color TEXT_PRIMARY   = new Color(0x1E1E0E);
    public static final Color TEXT_DIM       = new Color(0x6A6A4A);
    public static final Color TEXT_HEADER    = new Color(0x0E0E06);
    public static final Color TEXT_ACCENT    = new Color(0x2A4A08);
    public static final Color TEXT_LIGHT     = new Color(0XFDFFDE);

    // Status
    public static final Color STATUS_ALLOW       = new Color(0x2A5A2A);
    public static final Color STATUS_DISALLOW    = new Color(0x8A2010);
    public static final Color STATUS_CONDITIONAL = new Color(0x8A5A08);

    // Bevel
    public static final Color BEVEL_LIGHT    = new Color(0xE8E4CC);
    public static final Color BEVEL_DARK     = new Color(0x6A6A4A);

    // Panel header chrome
    public static final Color CHROME          = new Color(0x3A3A24);
    public static final Color CHROME_TEXT     = new Color(0xF0EDD0);
    public static final Color CHROME_TEXT_DIM = new Color(0xA8A480);

    // Fonts
    public static final Font FONT_MONO_SMALL  = new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 14);
    public static final Font FONT_MONO_BASE   = new Font("GohuFont 11 Nerd Font Mono", Font.PLAIN, 16);
    public static final Font FONT_MONO_BOLD   = new Font("GohuFont 11 Nerd Font Mono", Font.BOLD,  16);
    public static final Font FONT_MONO_HEADER = new Font("GohuFont 11 Nerd Font Mono", Font.BOLD,  17);
    public static final Font FONT_MONO_TITLE  = new Font("GohuFont 11 Nerd Font Mono", Font.BOLD,  17);

    // Borders
    public static Border borderBright() {
        return BorderFactory.createLineBorder(BORDER_BRIGHT, 1);
    }

    public static Border borderBevel() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 0, 0, BEVEL_LIGHT),
            BorderFactory.createMatteBorder(0, 0, 1, 1, BEVEL_DARK)
        );
    }

    public static Border shadowBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BEVEL_DARK,  2),
                BorderFactory.createLineBorder(BEVEL_LIGHT, 1)
            ),
            BorderFactory.createLineBorder(BORDER, 1)
        );
    }

    // borderTitled kept for API compatibility — title param unused, shadow applied.
    public static Border borderTitled(String title) {
        return shadowBorder();
    }

    public static Border borderFlat() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BEVEL_DARK, 1),
            BorderFactory.createLineBorder(BORDER, 1)
        );
    }

    // Component factories
    public static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_MONO_BASE);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    public static JLabel makeHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_MONO_HEADER);
        l.setForeground(TEXT_HEADER);
        return l;
    }

    public static JLabel makeDimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_MONO_SMALL);
        l.setForeground(TEXT_DIM);
        return l;
    }

    public static JButton makeButton(String text) {
        JButton b = new JButton(text.toUpperCase()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(getModel().isPressed() ? BG_SELECTION : BG_BUTTON);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setFont(getFont());
                g2.setColor(getModel().isPressed() ? CHROME_TEXT : TEXT_PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }

            @Override
            protected void paintBorder(Graphics g) {
                g.setColor(BEVEL_LIGHT);
                g.drawLine(0, 0, getWidth() - 1, 0);
                g.drawLine(0, 0, 0, getHeight() - 1);
                g.setColor(BEVEL_DARK);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                g.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
            }
        };
        b.setFont(FONT_MONO_BOLD);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(140, 28));
        return b;
    }

    public static JTextField makeTextField() {
        JTextField tf = new JTextField();
        tf.setFont(FONT_MONO_BASE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(BG_PANEL);
        tf.setCaretColor(ACCENT);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        return tf;
    }

    public static JTextArea makeTextArea() {
        JTextArea ta = new JTextArea();
        ta.setFont(FONT_MONO_SMALL);
        ta.setForeground(TEXT_PRIMARY);
        ta.setBackground(BG_PANEL);
        ta.setCaretColor(ACCENT);
        ta.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }

    public static JPanel makePanel(String title) {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        if (title != null) p.setBorder(borderTitled(title));
        else               p.setBorder(borderFlat());
        return p;
    }

    public static JPanel makePanelWithHeader(String title) {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(BG_PANEL);
        outer.setBorder(shadowBorder());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CHROME);
        header.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JLabel lbl = new JLabel(title.toUpperCase());
        lbl.setFont(FONT_MONO_BOLD);
        lbl.setForeground(CHROME_TEXT);
        header.add(lbl, BorderLayout.WEST);

        outer.add(header, BorderLayout.NORTH);
        return outer;
    }

    public static void install() {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("FlatLaf unavailable, using system L&F");
        }

        UIManager.put("Panel.background",             BG_PANEL);
        UIManager.put("RootPane.background",          BG_FRAME);
        UIManager.put("ScrollPane.background",        BG_PANEL);
        UIManager.put("Viewport.background",          BG_PANEL);
        UIManager.put("Table.background",             BG_PANEL);
        UIManager.put("Table.alternateRowColor",      BG_PANEL_ALT);
        UIManager.put("Table.foreground",             TEXT_PRIMARY);
        UIManager.put("Table.gridColor",              BORDER);
        UIManager.put("Table.selectionBackground",    BG_SELECTION);
        UIManager.put("Table.selectionForeground",    CHROME_TEXT);
        UIManager.put("TableHeader.background",       CHROME);
        UIManager.put("TableHeader.foreground",       CHROME_TEXT);
        UIManager.put("TableHeader.font",             FONT_MONO_BOLD);
        UIManager.put("ScrollBar.background",         BG_PANEL_ALT);
        UIManager.put("ScrollBar.thumb",              BORDER);
        UIManager.put("ScrollBar.track",              BG_PANEL);
        UIManager.put("Label.foreground",             TEXT_PRIMARY);
        UIManager.put("Label.font",                   FONT_MONO_BASE);
        UIManager.put("TextField.background",         BG_PANEL);
        UIManager.put("TextField.foreground",         TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground",    ACCENT);
        UIManager.put("ComboBox.background",          BG_PANEL);
        UIManager.put("ComboBox.foreground",          TEXT_PRIMARY);
        UIManager.put("List.background",              BG_PANEL);
        UIManager.put("List.foreground",              TEXT_PRIMARY);
        UIManager.put("List.selectionBackground",     BG_SELECTION);
        UIManager.put("List.selectionForeground",     CHROME_TEXT);
        UIManager.put("ToolTip.background",           BG_PANEL_ALT);
        UIManager.put("ToolTip.foreground",           TEXT_PRIMARY);
        UIManager.put("ToolTip.border",               borderFlat());
        UIManager.put("SplitPane.background",         BG_FRAME);
        UIManager.put("SplitPane.dividerSize",        4);
        UIManager.put("TabbedPane.background",        BG_PANEL);
        UIManager.put("TabbedPane.foreground",        TEXT_PRIMARY);
        UIManager.put("TabbedPane.selected",          BG_FRAME);
        UIManager.put("TabbedPane.selectedForeground", TEXT_HEADER);
        UIManager.put("TabbedPane.underlineColor",    ACCENT);
    }
}