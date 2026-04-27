package com.attendance;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DATABASE_URL = "jdbc:sqlite:" + System.getProperty("user.home") + "/attendance.db";
    private static final String TABLE_NAME = "attendance_logs";

    public DatabaseManager() {
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "SQLite JDBC driver not found", e);
        }
        initializeDatabase();
    }

    /**
     * Initialize the SQLite database and create table if it doesn't exist.
     */
    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement()) {

            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "employee_id TEXT NOT NULL, "
                    + "action TEXT NOT NULL, "
                    + "date TEXT NOT NULL, "
                    + "time TEXT NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ");";

            stmt.execute(createTableSQL);
            LOGGER.info("Database initialized successfully.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing database", e);
        }
    }

    /**
     * Insert a new attendance log into the database.
     */
    public void insertLog(String employeeId, String action, String date, String time) {
        String insertSQL = "INSERT INTO " + TABLE_NAME + " (employee_id, action, date, time) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, employeeId);
            pstmt.setString(2, action);
            pstmt.setString(3, date);
            pstmt.setString(4, time);

            pstmt.executeUpdate();
            LOGGER.info("Log inserted: " + employeeId + " | " + action + " | " + date + " | " + time);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting log", e);
        }
    }

    /**
     * Retrieve all attendance logs from the database.
     */
    public List<AttendanceLog> getAllLogs() {
        List<AttendanceLog> logs = new ArrayList<>();
        String selectSQL = "SELECT employee_id, action, date, time FROM " + TABLE_NAME + " ORDER BY date DESC, time DESC";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String employeeId = rs.getString("employee_id");
                String action = rs.getString("action");
                String date = rs.getString("date");
                String time = rs.getString("time");

                AttendanceLog log = new AttendanceLog(employeeId, action, date, time);
                logs.add(log);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving logs", e);
        }

        return logs;
    }

    /**
     * Retrieve logs filtered by date.
     */
    public List<AttendanceLog> getLogsByDate(String targetDate) {
        List<AttendanceLog> logs = new ArrayList<>();
        String selectSQL = "SELECT employee_id, action, date, time FROM " + TABLE_NAME + " WHERE date = ? ORDER BY time DESC";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, targetDate);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String employeeId = rs.getString("employee_id");
                String action = rs.getString("action");
                String date = rs.getString("date");
                String time = rs.getString("time");

                AttendanceLog log = new AttendanceLog(employeeId, action, date, time);
                logs.add(log);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving logs by date", e);
        }

        return logs;
    }

    /**
     * Retrieve logs for a specific employee.
     */
    public List<AttendanceLog> getLogsByEmployeeId(String employeeId) {
        List<AttendanceLog> logs = new ArrayList<>();
        String selectSQL = "SELECT employee_id, action, date, time FROM " + TABLE_NAME
                + " WHERE employee_id = ? ORDER BY date DESC, time DESC";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, employeeId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String empId = rs.getString("employee_id");
                String action = rs.getString("action");
                String date = rs.getString("date");
                String time = rs.getString("time");

                AttendanceLog log = new AttendanceLog(empId, action, date, time);
                logs.add(log);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving logs by employee", e);
        }

        return logs;
    }

    /**
     * Get the last action (LOGIN/LOGOUT) for a specific employee.
     */
    public String getLastAction(String employeeId) {
        String selectSQL = "SELECT action FROM " + TABLE_NAME
                + " WHERE employee_id = ? ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, employeeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("action");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving last action", e);
        }

        return null;
    }

    /**
     * Get all unique dates in the database.
     */
    public List<String> getUniqueDates() {
        List<String> dates = new ArrayList<>();
        String selectSQL = "SELECT DISTINCT date FROM " + TABLE_NAME + " WHERE date != 'N/A' ORDER BY date DESC";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                dates.add(rs.getString("date"));
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving unique dates", e);
        }

        return dates;
    }

    /**
     * Get all unique employee IDs in the database.
     */
    public List<String> getUniqueEmployeeIds() {
        List<String> employees = new ArrayList<>();
        String selectSQL = "SELECT DISTINCT employee_id FROM " + TABLE_NAME + " ORDER BY employee_id";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                employees.add(rs.getString("employee_id"));
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving unique employee IDs", e);
        }

        return employees;
    }

    /**
     * Check if employee exists in the database.
     */
    public boolean employeeExists(String employeeId) {
        String selectSQL = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE employee_id = ?";

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, employeeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking if employee exists", e);
        }

        return false;
    }

    /**
     * Count total logs in the database.
     */
    public int getTotalLogCount() {
        String selectSQL = "SELECT COUNT(*) FROM " + TABLE_NAME;

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting logs", e);
        }

        return 0;
    }

    /**
     * Clear all logs from the database.
     */
    public void clearAllLogs() {
        String deleteSQL = "DELETE FROM " + TABLE_NAME;

        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(deleteSQL);
            LOGGER.info("All logs cleared from database.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error clearing logs", e);
        }
    }
}
