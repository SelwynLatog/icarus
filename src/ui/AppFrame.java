package ui;

// Root application window. Single JFrame, CardLayout swaps between role panels.
// Status bar is updated by any panel via setStatus() and setItemsHeld().

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import ui.theme.IcarusTheme;

public class AppFrame extends JFrame {

    public static final String CARD_ROLE  = "ROLE";
    public static final String CARD_GUARD = "GUARD";
    public static final String CARD_ADMIN = "ADMIN";

    private final JPanel     cardHost;
    private final CardLayout cardLayout;

    private final JLabel statusLeft   = makeStatusLabel("READY");
    private final JLabel statusCenter = makeStatusLabel("ITEMS HELD: 0");
    private final JLabel statusRight  = makeStatusLabel("");

    public AppFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setPreferredSize(new Dimension(1280, 800));
        getContentPane().setBackground(IcarusTheme.BG_FRAME);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildStatusBar(), BorderLayout.SOUTH);

        cardLayout = new CardLayout();
        cardHost   = new JPanel(cardLayout);
        cardHost.setBackground(IcarusTheme.BG_FRAME);
        add(cardHost, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        startClock();
    }

    public void addCard(JPanel panel, String name) { cardHost.add(panel, name); }
    public void showCard(String name)              { cardLayout.show(cardHost, name); }

    public void setStatus(String text)    { statusLeft.setText("STATUS: " + text.toUpperCase()); }
    public void setItemsHeld(int count)   { statusCenter.setText("ITEMS HELD: " + count); }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IcarusTheme.CHROME);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 28));

        JLabel title = new JLabel("  ICARUS");
        title.setFont(IcarusTheme.FONT_MONO_BOLD);
        title.setForeground(IcarusTheme.SUB_ACCENT);

        JLabel subtitle = new JLabel("ITEM COMPLIANCE AUDIT & REGULATORY UNIVERSITY SYSTEM  ");
        subtitle.setFont(IcarusTheme.FONT_MONO_SMALL);
        subtitle.setForeground(IcarusTheme.TEXT_LIGHT);

        bar.add(title,    BorderLayout.WEST);
        bar.add(subtitle, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IcarusTheme.CHROME);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, IcarusTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 22));

        JPanel left   = new JPanel(new FlowLayout(FlowLayout.LEFT,   8, 2));
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        JPanel right  = new JPanel(new FlowLayout(FlowLayout.RIGHT,  8, 2));
        left.setOpaque(false);
        center.setOpaque(false);
        right.setOpaque(false);

        left.add(sep()); left.add(statusLeft); left.add(sep());
        center.add(statusCenter);
        right.add(statusRight); right.add(sep());

        bar.add(left,   BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(right,  BorderLayout.EAST);
        return bar;
    }

    private JLabel sep() {
        JLabel s = new JLabel("|");
        s.setFont(IcarusTheme.FONT_MONO_SMALL);
        s.setForeground(IcarusTheme.BORDER);
        return s;
    }

    private JLabel makeStatusLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(IcarusTheme.FONT_MONO_SMALL);
        l.setForeground(IcarusTheme.CHROME_TEXT);
        return l;
    }

    private void startClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm:ss");
        Timer t = new Timer(1000, e -> statusRight.setText(LocalDateTime.now().format(fmt)));
        t.start();
    }
}