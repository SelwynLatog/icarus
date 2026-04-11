package service;

// Business logic and validation layer for student operations.
// Orchestrates StudentDAO and ItemDAO for linking items to students.

import enums.StudentStatus;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import model.Student;
import storage.dao.ItemDAO;
import storage.dao.StudentDAO;

public class StudentService {

    private final StudentDAO studentDAO;
    private final ItemDAO itemDAO;

    public StudentService(StudentDAO studentDAO, ItemDAO itemDAO) {
        if (studentDAO == null) throw new IllegalArgumentException("StudentDAO cannot be null");
        if (itemDAO == null)    throw new IllegalArgumentException("ItemDAO cannot be null");
        this.studentDAO = studentDAO;
        this.itemDAO    = itemDAO;
    }

    public void registerNewStudent(
            String studentId,
            String firstName,
            String middleName,
            String lastName,
            String course,
            int year,
            StudentStatus status
    ) {
        if (studentId == null || studentId.isBlank()) throw new IllegalArgumentException("Student ID required");
        if (firstName == null || firstName.isBlank())  throw new IllegalArgumentException("First name required");
        if (lastName == null || lastName.isBlank())    throw new IllegalArgumentException("Last name required");
        if (course == null || course.isBlank())        throw new IllegalArgumentException("Course required");
        if (status == null)                            throw new IllegalArgumentException("Status required");

        Student student = new Student(studentId, firstName, middleName, lastName,
                course, year, status, List.of(), LocalDate.now());

        try {
            studentDAO.addStudent(student);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register student: " + e.getMessage(), e);
        }
    }

    public boolean linkItemToStudent(String studentId, int itemId) {
        try {
            if (itemDAO.findItemById(itemId).isEmpty()) return false;
            return studentDAO.linkItemToStudent(studentId, itemId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to link item to student: " + e.getMessage(), e);
        }
    }

    public boolean unlinkItemFromStudent(String studentId, int itemId) {
        try {
            return studentDAO.unlinkItemFromStudent(studentId, itemId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unlink item: " + e.getMessage(), e);
        }
    }

    public boolean updateStudentStatus(String studentId, StudentStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Status cannot be null");
        try {
            Optional<Student> found = studentDAO.findStudentById(studentId);
            if (found.isEmpty()) return false;
            return studentDAO.updateStudent(found.get().withStatus(newStatus));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update student status: " + e.getMessage(), e);
        }
    }

    public Optional<Student> findStudentById(String studentId) {
        try {
            return studentDAO.findStudentById(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find student: " + e.getMessage(), e);
        }
    }

    public List<Student> getAllStudents() {
        try {
            return studentDAO.getAllStudents();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get students: " + e.getMessage(), e);
        }
    }

    public List<Student> findStudentsByStatus(StudentStatus status) {
        try {
            return studentDAO.findStudentsByStatus(status);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find students by status: " + e.getMessage(), e);
        }
    }

    public List<Student> findStudentsByCourse(String course) {
        try {
            return studentDAO.findStudentsByCourse(course);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find students by course: " + e.getMessage(), e);
        }
    }

    public List<Student> findStudentsWithViolations() {
        try {
            return studentDAO.findStudentsWithViolations();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find students with violations: " + e.getMessage(), e);
        }
    }

    public boolean removeStudent(String studentId) {
        try {
            return studentDAO.removeStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove student: " + e.getMessage(), e);
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
    }

    private void requireNonNull(Object value, String fieldName) {
        if (value == null)
            throw new IllegalArgumentException(fieldName + " cannot be null");
    }
}