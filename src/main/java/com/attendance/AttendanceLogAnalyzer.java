package com.attendance;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AttendanceLogAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(AttendanceLogAnalyzer.class.getName());

    // Store valid employees to detect absences
    public static final List<String> ALL_EMPLOYEES = Arrays.asList("EMP101", "EMP102", "EMP103", "EMP104", "EMP105");

    private DatabaseManager dbManager;

    public AttendanceLogAnalyzer() {
        this.dbManager = new DatabaseManager();
    }

    public void parseAndAddLog(String logEntry) {
        try {
            String[] parts = logEntry.split(" \\| ");
            String employeeId, action, date, timeStr;

            if (parts.length == 4) {
                // New format: EMP101 | LOGIN | 29-Mar-2026 | 09:05 AM
                employeeId = parts[0].trim();
                action = parts[1].trim();
                date = parts[2].trim();
                timeStr = parts[3].trim();
            } else if (parts.length == 3) {
                // Legacy format: EMP101 | LOGIN | 09:05 AM (no date)
                employeeId = parts[0].trim();
                action = parts[1].trim();
                date = "N/A";
                timeStr = parts[2].trim();
            } else {
                LOGGER.warning("Invalid log format: " + logEntry);
                return;
            }

            // Validate the log
            AttendanceLog log = new AttendanceLog(employeeId, action, date, timeStr);

            // Insert into database
            dbManager.insertLog(employeeId, action, date, timeStr);

        } catch (DateTimeParseException e) {
            LOGGER.log(Level.WARNING, "Invalid time format in log entry: " + logEntry, e);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Invalid argument in log entry: " + logEntry, e);
        }
    }

    /**
     * Validates business rules and adds a log entry.
     * Throws IllegalArgumentException if validation fails.
     * This is the primary method for interactive (GUI/console) entry.
     * 
     * Validation rules:
     *   - Employee ID must match EMPXXX format
     *   - Cannot LOGIN if already logged in
     *   - Cannot LOGOUT if not logged in
     */
    public void validateAndAddLog(String employeeId, String action, String date, String timeStr) {
        // Validate Employee ID format
        if (employeeId == null || employeeId.isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required!");
        }
        if (!employeeId.matches("EMP\\d{3}")) {
            throw new IllegalArgumentException("Employee ID format must be EMPXXX (e.g., EMP101)");
        }

        // Sanitize inputs — reject special characters that could break parsing
        if (containsUnsafeChars(action) || containsUnsafeChars(date) || containsUnsafeChars(timeStr)) {
            throw new IllegalArgumentException("Input contains invalid special characters.");
        }

        // Validate action
        if (!"LOGIN".equals(action) && !"LOGOUT".equals(action)) {
            throw new IllegalArgumentException("Action must be LOGIN or LOGOUT.");
        }

        // Validate login/logout sequencing
        String lastAction = dbManager.getLastAction(employeeId);

        if ("LOGIN".equals(action) && "LOGIN".equals(lastAction)) {
            throw new IllegalArgumentException(
                "Employee " + employeeId + " is already logged in. Please logout first.");
        }

        if ("LOGOUT".equals(action) && !"LOGIN".equals(lastAction)) {
            String reason = lastAction == null
                ? "Employee " + employeeId + " is not logged in. Cannot logout."
                : "Employee " + employeeId + " is already logged out. Please login first.";
            throw new IllegalArgumentException(reason);
        }

        // Validation passed — add the log
        dbManager.insertLog(employeeId, action, date, timeStr);
    }

    /**
     * Checks if a string contains characters that could break file I/O or parsing.
     */
    private boolean containsUnsafeChars(String input) {
        if (input == null) return false;
        // Reject pipe (breaks TXT parsing), quotes (breaks JSON), newlines, tabs
        return input.contains("|") || input.contains("\"") ||
               input.contains("\n") || input.contains("\r") || input.contains("\t");
    }

    /**
     * Calculate total duration of work for each employee, filtered by date.
     * Overtime (>8h) and Shortfalls (<8h) are visibly flagged.
     */
    public Map<String, String> calculateTotalDurations(String targetDate) {
        Map<String, String> durationReport = new LinkedHashMap<>();

        List<AttendanceLog> allLogs = "All Dates".equals(targetDate) || targetDate == null
            ? dbManager.getAllLogs()
            : dbManager.getLogsByDate(targetDate);

        // Group logs by employee
        Map<String, List<AttendanceLog>> logsPerEmployee = allLogs.stream()
            .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        logsPerEmployee.forEach((empId, logs) -> {
            long totalMinutes = 0;
            AttendanceLog lastLogin = null;

            // Sort logs chronologically
            List<AttendanceLog> sortedLogs = logs.stream()
                .sorted(Comparator.comparing(AttendanceLog::getTime))
                .collect(Collectors.toList());

            for (AttendanceLog log : sortedLogs) {
                if ("LOGIN".equals(log.getAction())) {
                    lastLogin = log;
                } else if ("LOGOUT".equals(log.getAction()) && lastLogin != null) {
                    totalMinutes += Duration.between(lastLogin.getTime(), log.getTime()).toMinutes();
                    lastLogin = null; // Reset for next session
                }
            }

            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            String resultStr = String.format("%d hours, %d minutes", hours, minutes);

            // Calculate overtime/undertime (assume 8 hours = 480 mins)
            long diff = totalMinutes - 480;
            if (diff > 0) {
                resultStr += "  [OVERTIME +" + diff + " mins]";
            } else if (diff < 0) {
                resultStr += "  [SHORTFALL " + diff + " mins]";
            }

            durationReport.put(empId, resultStr);
        });

        return durationReport;
    }

    public Map<String, String> calculateTotalDurations() {
        return calculateTotalDurations("All Dates");
    }

    /**
     * Extract a list of distinct dates present in the logs.
     */
    public List<String> getUniqueDates() {
        return dbManager.getUniqueDates();
    }

    /**
     * Find employees who did not log in on a specific date.
     */
    public List<String> getAbsentEmployees(String targetDate) {
        if (targetDate == null || "All Dates".equals(targetDate)) {
            return new ArrayList<>();
        }

        Set<String> presentIds = dbManager.getLogsByDate(targetDate).stream()
                .map(AttendanceLog::getEmployeeId)
                .collect(Collectors.toSet());

        return ALL_EMPLOYEES.stream()
                .filter(emp -> !presentIds.contains(emp))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve logs optionally filtered by date.
     */
    public List<AttendanceLog> getFilteredLogs(String targetDate) {
        if (targetDate == null || "All Dates".equals(targetDate)) {
            return getAllLogs();
        }
        return dbManager.getLogsByDate(targetDate);
    }

    public List<AttendanceLog> getLogsAfter9AM(String targetDate) {
        return getFilteredLogs(targetDate).stream()
            .filter(AttendanceLog::isLoginAfter9AM)
            .collect(Collectors.toList());
    }

    public List<AttendanceLog> getAllLogs() {
        return dbManager.getAllLogs();
    }

    public List<AttendanceLog> getLogsAfter9AM() {
        return getLogsAfter9AM("All Dates");
    }

    /**
     * Filter logs by specific employee ID
     * Returns all attendance records for a given employee
     */
    public List<AttendanceLog> getLogsByEmployeeId(String employeeId) {
        return dbManager.getLogsByEmployeeId(employeeId);
    }

    /**
     * Get total working hours for a specific employee
     * Returns a formatted string with hours and minutes
     */
    public String getTotalDurationForEmployee(String employeeId) {
        List<AttendanceLog> logs = dbManager.getLogsByEmployeeId(employeeId);
        if (logs == null || logs.isEmpty()) {
            return "No data available";
        }

        long totalMinutes = 0;
        AttendanceLog lastLogin = null;

        // Sort logs chronologically
        List<AttendanceLog> sortedLogs = logs.stream()
            .sorted(Comparator.comparing(AttendanceLog::getTime))
            .collect(Collectors.toList());

        for (AttendanceLog log : sortedLogs) {
            if ("LOGIN".equals(log.getAction())) {
                lastLogin = log;
            } else if ("LOGOUT".equals(log.getAction()) && lastLogin != null) {
                totalMinutes += Duration.between(lastLogin.getTime(), log.getTime()).toMinutes();
                lastLogin = null;
            }
        }

        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%d hours, %d minutes", hours, minutes);
    }

    /**
     * Get the last action (LOGIN/LOGOUT) for a specific employee.
     * Returns null if the employee has no records.
     */
    public String getLastAction(String employeeId) {
        return dbManager.getLastAction(employeeId);
    }

    /**
     * Check if employee exists in the system
     */
    public boolean employeeExists(String employeeId) {
        return dbManager.employeeExists(employeeId);
    }

    // --- DATABASE OPERATIONS ---

    public void clearAllLogs() {
        dbManager.clearAllLogs();
    }

    // --- FILE I/O OPERATIONS ---

    public void saveToFile(String filename, String format, List<AttendanceLog> logsToSave) throws IOException {
        List<AttendanceLog> flatLogs = logsToSave;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            if ("JSON".equalsIgnoreCase(format)) {
                bw.write("[\n");
                for (int i = 0; i < flatLogs.size(); i++) {
                    AttendanceLog log = flatLogs.get(i);
                    bw.write("  {\n");
                    bw.write("    \"employeeId\": \"" + escapeJsonString(log.getEmployeeId()) + "\",\n");
                    bw.write("    \"action\": \"" + escapeJsonString(log.getAction()) + "\",\n");
                    bw.write("    \"date\": \"" + escapeJsonString(log.getDate()) + "\",\n");
                    bw.write("    \"time\": \"" + escapeJsonString(log.getTimeAsString()) + "\"\n");
                    bw.write("  }");
                    if (i < flatLogs.size() - 1) bw.write(",");
                    bw.write("\n");
                }
                bw.write("]\n");
            } else if ("CSV".equalsIgnoreCase(format)) {
                bw.write("Employee ID,Action,Date,Time\n");
                for (AttendanceLog log : flatLogs) {
                    bw.write(log.getEmployeeId() + "," + log.getAction() + "," + log.getDate() + "," + log.getTimeAsString() + "\n");
                }
            } else {
                for (AttendanceLog log : flatLogs) {
                    bw.write(log.getEmployeeId() + " | " + log.getAction() + " | " + log.getDate() + " | " + log.getTimeAsString() + "\n");
                }
            }
        }
    }

    public void saveToFile(String filename, String format) throws IOException {
        saveToFile(filename, format, getAllLogs());
    }

    /**
     * Escapes special characters for safe JSON string output.
     */
    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }


    public static void main(String[] args) {
        AttendanceLogAnalyzer analyzer = new AttendanceLogAnalyzer();
        Scanner scanner = new Scanner(System.in);

        boolean running = true;
        while (running) {
            System.out.println("=== Employee Attendance Log Analyzer (Console) ===");
            System.out.println("1. Display All Logs");
            System.out.println("2. Add New Log Entry");
            System.out.println("3. Find Late Logins (>9AM)");
            System.out.println("4. Calculate Durations");
            System.out.println("5. Show Statistics");
            System.out.println("6. Search Employee");
            System.out.println("7. Load Sample Data (for testing)");
            System.out.println("8. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            System.out.println();

            switch (choice) {
                case "1":
                    System.out.println("--- All Attendance Logs ---");
                    analyzer.getAllLogs().forEach(System.out::println);
                    break;

                case "2":
                    try {
                        System.out.print("Employee ID (EMPXXX): ");
                        String empId = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Action (LOGIN/LOGOUT): ");
                        String action = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Date (e.g. 29-Mar-2026): ");
                        String date = scanner.nextLine().trim();
                        System.out.print("Time (e.g. 09:05 AM): ");
                        String time = scanner.nextLine().trim().toUpperCase();
                        analyzer.validateAndAddLog(empId, action, date, time);
                        System.out.println("Log added successfully!");
                    } catch (IllegalArgumentException e) {
                        System.out.println("Validation Error: " + e.getMessage());
                    }
                    break;

                case "3":
                    System.out.println("--- Late Logins (after 9:00 AM) ---");
                    List<AttendanceLog> lateLogs = analyzer.getLogsAfter9AM();
                    if (lateLogs.isEmpty()) {
                        System.out.println("No late logins found.");
                    } else {
                        lateLogs.forEach(System.out::println);
                    }
                    break;

                case "4":
                    System.out.println("--- Total Session Durations ---");
                    Map<String, String> durations = analyzer.calculateTotalDurations();
                    durations.forEach((id, dur) ->
                        System.out.printf("Employee ID: %s | Total Time: %s%n", id, dur));
                    break;

                case "5":
                    System.out.println("--- Statistics ---");
                    List<AttendanceLog> all = analyzer.getAllLogs();
                    long logins = all.stream().filter(l -> "LOGIN".equals(l.getAction())).count();
                    long logouts = all.stream().filter(l -> "LOGOUT".equals(l.getAction())).count();
                    long unique = all.stream().map(AttendanceLog::getEmployeeId).distinct().count();
                    System.out.println("Total Logs: " + all.size());
                    System.out.println("Logins: " + logins);
                    System.out.println("Logouts: " + logouts);
                    System.out.println("Unique Employees: " + unique);
                    break;

                case "6":
                    System.out.print("Enter Employee ID: ");
                    String searchId = scanner.nextLine().trim().toUpperCase();
                    if (!analyzer.employeeExists(searchId)) {
                        System.out.println("No records found for " + searchId);
                    } else {
                        List<AttendanceLog> empLogs = analyzer.getLogsByEmployeeId(searchId);
                        empLogs.forEach(System.out::println);
                        System.out.println("Total Working Time: " + analyzer.getTotalDurationForEmployee(searchId));
                    }
                    break;

                case "7":
                    // Load sample data for testing
                    String d1 = "14-Feb-2026";
                    String d2 = "03-Mar-2026";
                    String d3 = "22-Apr-2026";
                    String d4 = "10-May-2026";
                    String d5 = "01-Jun-2026";

                    String[] sampleLogs = {
                        "EMP101 | LOGIN | " + d1 + " | 09:05 AM",
                        "EMP101 | LOGOUT | " + d1 + " | 05:30 PM",
                        "EMP102 | LOGIN | " + d2 + " | 08:45 AM",
                        "EMP102 | LOGOUT | " + d2 + " | 06:00 PM",
                        "EMP103 | LOGIN | " + d3 + " | 09:15 AM",
                        "EMP103 | LOGOUT | " + d3 + " | 05:45 PM",
                        "EMP104 | LOGIN | " + d4 + " | 08:30 AM",
                        "EMP104 | LOGOUT | " + d4 + " | 04:30 PM",
                        "EMP105 | LOGIN | " + d5 + " | 09:30 AM",
                        "EMP105 | LOGOUT | " + d5 + " | 06:15 PM"
                    };
                    for (String log : sampleLogs) {
                        analyzer.parseAndAddLog(log);
                    }
                    System.out.println("Sample data loaded (" + sampleLogs.length + " entries).\n");
                    break;

                case "8":
                    running = false;
                    System.out.println("Goodbye!");
                    break;

                default:
                    System.out.println("Invalid option. Please choose 1-8.");
            }
            System.out.println();
        }
        scanner.close();
    }
}