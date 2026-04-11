package test;

import enums.*;
import service.ItemService;
import service.StudentService;
import storage.dao.ItemDAO;
import storage.dao.StudentDAO;

public class TestLoader {

    public static void main(String[] args) {
        StudentDAO studentDAO = new StudentDAO();
        ItemDAO itemDAO       = new ItemDAO();
        StudentService studentService = new StudentService(studentDAO, itemDAO);
        ItemService itemService       = new ItemService(itemDAO, studentService);

        addSampleStudents(studentService);
        addSampleItems(itemService);

        System.out.println("Test data loaded.");
    }

    public static void addSampleStudents(StudentService studentService) {
        try {
            studentService.registerNewStudent("2024-00001", "Selwyn",   "Autentico", "Latog",       "Computer Science",       2, StudentStatus.ENROLLED);
            studentService.registerNewStudent("2024-00002", "Pharell",  "Dreamybull","Brown",        "Information Technology", 2, StudentStatus.ENROLLED);
            studentService.registerNewStudent("2023-00003", "Patrick",  "Sigma",     "Bateman",      "Computer Engineering",   3, StudentStatus.ENROLLED);
            studentService.registerNewStudent("2023-00004", "Jon",      "Dwight",    "Jones",        "Sports Science",         3, StudentStatus.ENROLLED);
            studentService.registerNewStudent("2023-00005", "Socrates", null,        "Sophroniscus", "Philosophy",             3, StudentStatus.ENROLLED);
            studentService.registerNewStudent("VISITOR-001","Shrek",    "Terror",    "Swampson",     "N/A",                    0, StudentStatus.OUTSIDER);
        } catch (Exception e) {
            System.err.println("Sample students: " + e.getMessage());
        }
    }

    public static void addSampleItems(ItemService itemService) {
        try {
            itemService.registerNewItem("2026-1001", "Plastic Water Bottle", "Nestle",
                    PrimaryCategory.SINGLE_USE_PLASTIC, SecondaryCategory.BEVERAGE_CONTAINER,
                    ItemFunction.CONTAINER, ConsumptionContext.BEVERAGE, UsageType.SINGLE_USE, Replaceability.HIGH, 1);

            itemService.registerNewItem("2026-1002", "Styrofoam Lunch Box", "Generic",
                    PrimaryCategory.SINGLE_USE_PLASTIC, SecondaryCategory.FOOD_CONTAINER,
                    ItemFunction.CONTAINER, ConsumptionContext.TAKEOUT, UsageType.SINGLE_USE, Replaceability.HIGH, 1);

            itemService.registerNewItem("2026-1003", "Cigarette Pack", "Marlboro",
                    PrimaryCategory.TOBACCO, SecondaryCategory.SMOKING_PRODUCT,
                    ItemFunction.CONSUMABLE, ConsumptionContext.PERSONAL_USE, UsageType.SINGLE_USE, Replaceability.MEDIUM, 1);

            itemService.registerNewItem("2026-0505", "Vape Pen", "Juul",
                    PrimaryCategory.TOBACCO, SecondaryCategory.ELECTRONIC_SMOKING,
                    ItemFunction.CONSUMABLE, ConsumptionContext.PERSONAL_USE, UsageType.REUSABLE, Replaceability.MEDIUM, 1);

            itemService.registerNewItem("V-2026-001", "Pocket Knife", "Swiss Army",
                    PrimaryCategory.WEAPON, SecondaryCategory.SHARP_OBJECT,
                    ItemFunction.TOOL, ConsumptionContext.PERSONAL_USE, UsageType.REUSABLE, Replaceability.LOW, 1);

            itemService.registerNewItem("2026-1004", "Beer Can", "San Miguel",
                    PrimaryCategory.ALCOHOL, SecondaryCategory.ALCOHOLIC_BEVERAGE,
                    ItemFunction.CONSUMABLE, ConsumptionContext.BEVERAGE, UsageType.SINGLE_USE, Replaceability.HIGH, 6);
        } catch (Exception e) {
            System.err.println("Sample items: " + e.getMessage());
        }
    }
}