package test;

import storage.DBConnection;
import storage.dao.*;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Smoke test for Phase 0.
 * Verifies: JDBC connection, all 6 tables reachable, basic DAO reads work.
 * Run this before touching any engine or UI code.
 */
public class DBConnectionTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" ICARUS - Phase 0 Connection Test");
        System.out.println("========================================\n");

        testConnection();
        testStudentDAO();
        testItemDAO();
        testDecisionLogDAO();
        testAdminOverrideDAO();
        testCVMatchDAO();

        System.out.println("\n========================================");
        System.out.println(" All tests passed. Phase 0 complete.");
        System.out.println("========================================");

        DBConnection.close();
    }

    // ─── CONNECTION ───────────────────────────────────────────────────────────

    static void testConnection() {
        System.out.print("[1] JDBC connection................. ");
        try {
            Connection conn = DBConnection.get();
            if (conn != null && !conn.isClosed()) {
                System.out.println("PASS");
            } else {
                System.out.println("FAIL - connection is null or closed");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
            System.exit(1);
        }
    }

    // ─── STUDENT ──────────────────────────────────────────────────────────────

    static void testStudentDAO() {
        System.out.print("[2] StudentDAO.getAllStudents()...... ");
        try {
            StudentDAO dao = new StudentDAO();
            List students = dao.getAllStudents();
            System.out.println("PASS — " + students.size() + " student(s) found");

            if (!students.isEmpty()) {
                System.out.print("    StudentDAO.findById()............. ");
                var first = dao.getAllStudents().get(0);
                var found = dao.findStudentById(first.getStudentId());
                System.out.println(found.isPresent() ? "PASS" : "FAIL — not found by ID");
            }
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
        }
    }

    // ─── ITEM ─────────────────────────────────────────────────────────────────

    static void testItemDAO() {
        System.out.print("[3] ItemDAO.getAllItems()............ ");
        try {
            ItemDAO dao = new ItemDAO();
            List items = dao.getAllItems();
            System.out.println("PASS — " + items.size() + " item(s) found");
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
        }
    }

    // ─── DECISION LOG ─────────────────────────────────────────────────────────

    static void testDecisionLogDAO() {
        System.out.print("[4] DecisionLogDAO.getAll()......... ");
        try {
            DecisionLogDAO dao = new DecisionLogDAO();
            List<Map<String, Object>> logs = dao.getAll();
            System.out.println("PASS — " + logs.size() + " log(s) found");
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
        }
    }

    // ─── ADMIN OVERRIDE ───────────────────────────────────────────────────────

    static void testAdminOverrideDAO() {
        System.out.print("[5] AdminOverrideDAO.getAll()....... ");
        try {
            AdminOverrideDAO dao = new AdminOverrideDAO();
            List<Map<String, Object>> overrides = dao.getAll();
            System.out.println("PASS — " + overrides.size() + " override(s) found");
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
        }
    }

    // ─── CV MATCH ─────────────────────────────────────────────────────────────

    static void testCVMatchDAO() {
        System.out.print("[6] CVMatchDAO.getAll()............. ");
        try {
            CVMatchDAO dao = new CVMatchDAO();
            List<Map<String, Object>> matches = dao.getAll();
            System.out.println("PASS — " + matches.size() + " match(es) found");
        } catch (Exception e) {
            System.out.println("FAIL\n    " + e.getMessage());
        }
    }
}