import com.formdev.flatlaf.FlatDarkLaf;
import service.ItemService;
import service.StudentService;
import storage.dao.ItemDAO;
import storage.dao.StudentDAO;
import ui.AppFrame;
import ui.RoleMenu;
import ui.GuardDashboard;
import ui.AdminDashboard;
import engine.CVEngine;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {

        // Suppress libpng iCCP profile warnings from ImageIO
        java.util.logging.Logger.getLogger("javax.imageio").setLevel(java.util.logging.Level.OFF);
        
        // Load OpenCV native lib before anything else
        CVEngine.loadLibrary();

        // Install theme before any Swing component is created
        ui.theme.IcarusTheme.install();

        SwingUtilities.invokeLater(() -> {
            // Wire DAOs and services
            StudentDAO     studentDAO     = new StudentDAO();
            ItemDAO        itemDAO        = new ItemDAO();
            StudentService studentService = new StudentService(studentDAO, itemDAO);
            ItemService    itemService    = new ItemService(itemDAO, studentService);

            // Build root frame
            AppFrame frame = new AppFrame();

            // Build panels
            RoleMenu      roleMenu      = new RoleMenu(role -> {
                switch (role) {
                    case "SECURITY" -> frame.showCard(AppFrame.CARD_GUARD);
                    case "ADMIN"    -> frame.showCard(AppFrame.CARD_ADMIN);
                }
            });

            GuardDashboard  guardDash  = new GuardDashboard(itemService, studentService, frame);
            AdminDashboard  adminDash  = new AdminDashboard(itemService, studentService, frame);

            frame.addCard(roleMenu, AppFrame.CARD_ROLE);
            frame.addCard(guardDash, AppFrame.CARD_GUARD);
            frame.addCard(adminDash, AppFrame.CARD_ADMIN);

            frame.showCard(AppFrame.CARD_ROLE);
            frame.setVisible(true);
        });
    }
}