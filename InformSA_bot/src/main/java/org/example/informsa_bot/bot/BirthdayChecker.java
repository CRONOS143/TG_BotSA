package org.example.informsa_bot.bot;



import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BirthdayChecker {

    private final ExcelEmployeeService sheetService; // <-- Добавьте это поле

    public BirthdayChecker(ExcelEmployeeService sheetService) {
        this.sheetService = sheetService;
    }

    public List<Employee> getTodayBirthdayEmployees() {
        List<Employee> birthdayEmployees = new ArrayList<>();
        List<Employee> employees = sheetService.getEmployees();

        LocalDate today = LocalDate.now();
        int day = today.getDayOfMonth();
        int month = today.getMonthValue();

        for (Employee emp : employees) {
            LocalDate dob = emp.getBirthDate();
            if (dob != null && dob.getDayOfMonth() == day && dob.getMonthValue() == month) {
                birthdayEmployees.add(emp);
            }
        }
        return birthdayEmployees;
    }

}