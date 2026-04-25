package ui;

// Left-column student card.
// Handles barcode scan, manual ID lookup, info display, and ID photo.
// Notifies GuardDashboard of the loaded student via the onStudentLoaded callback.

import engine.BarcodeEngine;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import model.Student;
import service.StudentService;
import ui.theme.IcarusTheme;

public class StudentPanel extends JPanel {

    private static final int STUDENT_PHOTO_HEIGHT = 220;
    private static final int STUDENT_PHOTO_WIDTH  = 400;

    private final StudentService studentService;
    private final AppFrame frame;

    // Called whenever a student is successfully loaded or cleared (null = cleared)
    private final Consumer<Student> onStudentLoaded;

    private JLabel lblStudentId;
    private JLabel lblStudentName;
    private JLabel lblStudentCourse;
    private JLabel lblStudentYear;
    private JLabel lblStudentStatus;
    private JLabel lblStudentViolations;
    private JLabel lblStudentPhoto;

    public StudentPanel(StudentService studentService, AppFrame frame, Consumer<Student> onStudentLoaded) {
        this.studentService  = studentService;
        this.frame           = frame;
        this.onStudentLoaded = onStudentLoaded;
        setLayout(new BorderLayout());
        setOpaque(false);
        add(buildStudentPanel(), BorderLayout.CENTER);
    }

    // Builds the barcode/manual lookup controls and the student info card below them
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

    // Opens a file picker for barcode images, decodes the barcode, and loads the matching student
    private void doBarcodeScana() {
        JFileChooser fc = new JFileChooser("assets/barcodes");
        fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Optional<String> id = BarcodeEngine.scan(fc.getSelectedFile().getAbsolutePath());
        if (id.isEmpty()) {
            showError("No barcode detected in selected image.");
            return;
        }
        doStudentLookup(id.get());
    }

    // Finds a student by ID and populates the left panel. Clears the panel if not found.
    public void doStudentLookup(String studentId) {
        if (studentId.isBlank()) return;
        Optional<Student> opt = studentService.findStudentById(studentId);
        if (opt.isEmpty()) {
            showError("Student not found: " + studentId);
            clearStudentCard();
            return;
        }
        Student student = opt.get();
        populateStudentCard(student);
        onStudentLoaded.accept(student);
        frame.setStatus("STUDENT LOADED: " + student.getFullName());
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
            case ENROLLED  -> IcarusTheme.STATUS_ALLOW;
            default        -> IcarusTheme.TEXT_DIM;
        });

        if (s.getStatus() == enums.StudentStatus.SUSPENDED)
            frame.setStatus("ALERT: SUSPENDED STUDENT  " + s.getFullName().toUpperCase());
    }

    // Resets all student info labels to dashes and clears the photo. Called when lookup fails.
    public void clearStudentCard() {
        onStudentLoaded.accept(null);
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

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
    }
}