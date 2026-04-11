package model;

import enums.StudentStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Immutable value object representing a student or campus visitor.
// Use withStatus(), withAddedItem(), withRemovedItem() for updates.
public class Student {

    private final String studentId;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final String course;
    private final int year;
    private final StudentStatus status;
    private final List<Integer> itemIds;
    private final LocalDate enrollmentDate;

    public Student(
            String studentId,
            String firstName,
            String middleName,
            String lastName,
            String course,
            int year,
            StudentStatus status,
            List<Integer> itemIds,
            LocalDate enrollmentDate
    ) {
        if (studentId == null || studentId.trim().isEmpty())
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        if (firstName == null || firstName.trim().isEmpty())
            throw new IllegalArgumentException("First name cannot be null or empty");
        if (lastName == null || lastName.trim().isEmpty())
            throw new IllegalArgumentException("Last name cannot be null or empty");
        if (course == null || course.trim().isEmpty())
            throw new IllegalArgumentException("Course cannot be null or empty");
        if (status == StudentStatus.OUTSIDER) {
            if (year != 0)
                throw new IllegalArgumentException("Outsiders must have year = 0");
        } else {
            if (year < 1 || year > 6)
                throw new IllegalArgumentException("Year must be 1-6 for students, got: " + year);
        }
        if (status == null)       throw new IllegalArgumentException("Status cannot be null");
        if (itemIds == null)      throw new IllegalArgumentException("Item IDs list cannot be null");
        if (enrollmentDate == null) throw new IllegalArgumentException("Enrollment date cannot be null");

        this.studentId      = studentId.trim();
        this.firstName      = firstName.trim();
        this.middleName     = (middleName != null && !middleName.trim().isEmpty())
                              ? middleName.trim() : null;
        this.lastName       = lastName.trim();
        this.course         = course.trim();
        this.year           = year;
        this.status         = status;
        this.itemIds        = new ArrayList<>(itemIds);
        this.enrollmentDate = enrollmentDate;
    }

    public String getStudentId()         { return studentId;      }
    public String getFirstName()         { return firstName;      }
    public String getMiddleName()        { return middleName;     }
    public String getLastName()          { return lastName;       }
    public String getCourse()            { return course;         }
    public int getYear()                 { return year;           }
    public StudentStatus getStatus()     { return status;         }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }

    public List<Integer> getItemIds() {
        return Collections.unmodifiableList(itemIds);
    }

    // "Last, First M." format used across all display panels
    public String getFullName() {
        String mi = (middleName != null) ? " " + middleName.charAt(0) + "." : "";
        return lastName + ", " + firstName + mi;
    }

    public int getViolationCount()  { return itemIds.size();    }
    public boolean hasViolations()  { return !itemIds.isEmpty(); }
    public boolean isEnrolled()     { return status == StudentStatus.ENROLLED || status == StudentStatus.SUSPENDED; }
    public boolean isSuspended()    { return status == StudentStatus.SUSPENDED; }

    public Student withStatus(StudentStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Status cannot be null");
        return new Student(studentId, firstName, middleName, lastName,
                course, year, newStatus, itemIds, enrollmentDate);
    }

    public Student withAddedItem(int itemId) {
        List<Integer> updated = new ArrayList<>(itemIds);
        updated.add(itemId);
        return new Student(studentId, firstName, middleName, lastName,
                course, year, status, updated, enrollmentDate);
    }

    public Student withRemovedItem(int itemId) {
        List<Integer> updated = new ArrayList<>(itemIds);
        updated.remove(Integer.valueOf(itemId));
        return new Student(studentId, firstName, middleName, lastName,
                course, year, status, updated, enrollmentDate);
    }

    @Override
    public String toString() {
        return String.format("Student[id=%s, name=%s, course=%s, year=%d, status=%s]",
                studentId, getFullName(), course, year, status);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return studentId.equals(((Student) obj).studentId);
    }

    @Override
    public int hashCode() { return studentId.hashCode(); }
}