package org.example.informsa_bot.bot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.informsa_bot.Config.Config;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.example.informsa_bot.Config.ExcelFileLoader.getExcelInputStream;

public class ExcelEmployeeService {
    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");





    private static final List<Integer> DEPARTMENT_HEADER_ROWS = Arrays.asList(
            4, 23, 30, 39, 44, 52, 68, 75, 84, 105, 113, 117, 121, 126, 132, 136, 140, 145,
            149, 153, 156, 159, 168, 179, 185, 189, 196
    ); // 1-based номера строк с названиями отделов

    private final List<Employee> employees = new ArrayList<>();

    public ExcelEmployeeService() {
        String path = Config.getContactFilePath();
        loadEmployeesFromExcel(path);
    }

    private void loadEmployeesFromExcel(String path) {
        employees.clear();
        try (InputStream is = getExcelInputStream("Contact.xlsx");) {
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheetAt(0);

            // Получаем названия отделов по строкам, объединяя текст из столбцов B до I
            Map<Integer, String> departmentsMap = new HashMap<>();
            for (int rowNum : DEPARTMENT_HEADER_ROWS) {
                Row row = sheet.getRow(rowNum - 1); // zero-based
                if (row != null) {
                    StringBuilder deptName = new StringBuilder();

                    int startCol = (rowNum == 4) ? 0 : 1; // для 4-й строки (A4:I4) с 0, для остальных B–I с 1
                    int endCol = 8; // столбец I - индекс 8

                    for (int c = startCol; c <= endCol; c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null && cell.getCellType() == CellType.STRING) {
                            String cellText = cell.getStringCellValue().trim();
                            if (!cellText.isEmpty()) {
                                if (deptName.length() > 0) deptName.append(" ");
                                deptName.append(cellText);
                            }
                        }
                    }
                    if (deptName.length() > 0) {
                        departmentsMap.put(rowNum, deptName.toString());
                    }

                }
            }


            List<Integer> sortedDeptRows = new ArrayList<>(departmentsMap.keySet());
            Collections.sort(sortedDeptRows);

            // Считываем сотрудников с 5 по 193 строки (1-based)
            for (int rowNum = 5; rowNum <= 193; rowNum++) {
                Row row = sheet.getRow(rowNum - 1);
                if (row == null) continue;

                Cell fioCell = row.getCell(1);      // B (индекс 1)
                Cell postCell = row.getCell(2);      // C (индекс 1)
                Cell mobPhoneCell = row.getCell(5); // F (индекс 5)
                Cell tgPhoneCell = row.getCell(6);  // G (индекс 6)
                Cell mailCell = row.getCell(7);     // H (индекс 7)
                Cell dobCell = row.getCell(8);      // I (индекс 8)

                if (fioCell == null || fioCell.getCellType() == CellType.BLANK) continue;
                if (dobCell == null || dobCell.getCellType() == CellType.BLANK) continue;

                String fio = fioCell.getStringCellValue().trim();
                String mobPhone = mobPhoneCell != null ? mobPhoneCell.toString().trim() : "";
                String post = postCell != null ? postCell.toString().trim() : "";
                String mail = mailCell != null ? mailCell.toString().trim() : "";
                String tgPhone = tgPhoneCell != null ? tgPhoneCell.toString().trim() : "";

                LocalDate dob;
                if (dobCell.getCellType() == CellType.NUMERIC) {
                    dob = dobCell.getLocalDateTimeCellValue().toLocalDate();
                } else {
                    String dobStr = dobCell.getStringCellValue().trim();
                    dob = LocalDate.parse(dobStr, DOB_FORMATTER);
                }

                // Определяем отдел сотрудника по строке
                String department = getDepartmentForRow(rowNum, sortedDeptRows, departmentsMap);

                Employee employee = new Employee(fio, dob, department, post, mobPhone, mail, tgPhone);
                employees.add(employee);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String getDepartmentForRow(int rowNum, List<Integer> sortedDeptRows, Map<Integer, String> departmentsMap) {
        String currentDept = "Без відділу";
        for (int i = 0; i < sortedDeptRows.size(); i++) {
            int deptRow = sortedDeptRows.get(i);
            int nextDeptRow = (i + 1 < sortedDeptRows.size()) ? sortedDeptRows.get(i + 1) : Integer.MAX_VALUE;

            if (rowNum > deptRow && rowNum < nextDeptRow) {
                currentDept = departmentsMap.get(deptRow);
                break;
            }
        }
        return currentDept;
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public List<Employee> findEmployeesByNameOrDepartment(String query) {
        String q = query.toLowerCase();
        List<Employee> results = new ArrayList<>();
        for (Employee e : employees) {
            if (e.getFullName().toLowerCase().contains(q) || e.getDepartment().toLowerCase().contains(q)) {
                results.add(e);
            }
        }
        return results;
    }


    public List<Employee> getTodayBirthdayEmployees() {
        LocalDate today = LocalDate.now();
        List<Employee> birthdayEmployees = new ArrayList<>();
        for (Employee e : employees) {
            if (e.getBirthDate().getMonth() == today.getMonth() &&
                    e.getBirthDate().getDayOfMonth() == today.getDayOfMonth()) {
                birthdayEmployees.add(e);
            }
        }
        return birthdayEmployees;
    }
}
