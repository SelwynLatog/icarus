package storage.dao;

// MySQL-backed student persistence.
// itemIds are derived from the items table — DB is the source of truth, not the Student object.

import enums.StudentStatus;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import model.Student;
import storage.DBConnection;

public class StudentDAO {

    public void addStudent(Student student) throws SQLException {
        String sql = """
            INSERT INTO students
            (student_id, first_name, middle_name, last_name, course, year, status, enrollment_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, student.getStudentId());
            ps.setString(2, student.getFirstName());
            ps.setString(3, student.getMiddleName());
            ps.setString(4, student.getLastName());
            ps.setString(5, student.getCourse());
            ps.setInt(6, student.getYear());
            ps.setString(7, student.getStatus().name());
            ps.setDate(8, Date.valueOf(student.getEnrollmentDate()));
            ps.executeUpdate();
        }
    }

    public boolean updateStudent(Student student) throws SQLException {
        String sql = """
            UPDATE students
            SET first_name = ?, middle_name = ?, last_name = ?,
                course = ?, year = ?, status = ?, enrollment_date = ?
            WHERE student_id = ?
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, student.getFirstName());
            ps.setString(2, student.getMiddleName());
            ps.setString(3, student.getLastName());
            ps.setString(4, student.getCourse());
            ps.setInt(5, student.getYear());
            ps.setString(6, student.getStatus().name());
            ps.setDate(7, Date.valueOf(student.getEnrollmentDate()));
            ps.setString(8, student.getStudentId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean removeStudent(String studentId) throws SQLException {
        String sql = "DELETE FROM students WHERE student_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Student> findStudentById(String studentId) throws SQLException {
        String sql = "SELECT * FROM students WHERE student_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public List<Student> getAllStudents() throws SQLException {
        String sql = "SELECT * FROM students ORDER BY student_id ASC";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return collectRows(rs);
        }
    }

    public List<Student> findStudentsByStatus(StudentStatus status) throws SQLException {
        String sql = "SELECT * FROM students WHERE status = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Student> findStudentsByCourse(String course) throws SQLException {
        String sql = "SELECT * FROM students WHERE LOWER(course) = LOWER(?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, course);
            try (ResultSet rs = ps.executeQuery()) {
                return collectRows(rs);
            }
        }
    }

    public List<Student> findStudentsWithViolations() throws SQLException {
        String sql = """
            SELECT DISTINCT s.*
            FROM students s
            INNER JOIN items i ON s.student_id = i.student_id
            """;

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return collectRows(rs);
        }
    }

    public List<Integer> getItemIdsForStudent(String studentId) throws SQLException {
        String sql = "SELECT item_id FROM items WHERE student_id = ? ORDER BY timestamp ASC";
        List<Integer> ids = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("item_id"));
            }
        }
        return ids;
    }

    // Links by updating items.student_id rather than a join table.
    public boolean linkItemToStudent(String studentId, int itemId) throws SQLException {
        String sql = "UPDATE items SET student_id = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    // Unlinks by nulling items.student_id — item record is preserved.
    public boolean unlinkItemFromStudent(String studentId, int itemId) throws SQLException {
        String sql = "UPDATE items SET student_id = NULL WHERE item_id = ? AND student_id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            ps.setString(2, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    // itemIds starts empty — call getItemIdsForStudent() separately if needed.
    private Student mapRow(ResultSet rs) throws SQLException {
        return new Student(
            rs.getString("student_id"),
            rs.getString("first_name"),
            rs.getString("middle_name"),
            rs.getString("last_name"),
            rs.getString("course"),
            rs.getInt("year"),
            StudentStatus.valueOf(rs.getString("status")),
            new ArrayList<>(),
            rs.getDate("enrollment_date").toLocalDate()
        );
    }

    private List<Student> collectRows(ResultSet rs) throws SQLException {
        List<Student> result = new ArrayList<>();
        while (rs.next()) result.add(mapRow(rs));
        return result;
    }
}