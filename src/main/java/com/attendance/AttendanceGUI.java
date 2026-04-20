package com.attendance;

import java.awt.*;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class AttendanceGUI extends JFrame {

    private AttendanceLogAnalyzer analyzer;
    private JTextField empIdField, timeField, dateField;
    private JComboBox<String> dateFilterCombo;
    private boolean isUpdatingFilter = false;
    private JTable logTable;
    private DefaultTableModel tableModel;
    private JTextArea outputArea;
    private JLabel statusLabel;

    private static final String[] COLUMN_NAMES = { "Employee ID", "Action", "Date", "Time" };
    private static final Color PRIMARY_COLOR = new Color(70, 130, 180);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color ERROR_COLOR = new Color(231, 76, 60);

    public AttendanceGUI() {
        analyzer = new AttendanceLogAnalyzer();
        initializeGUI();
        setLocationRelativeTo(null);
    }

    private void initializeGUI() {
        setTitle("Employee Attendance Log Analyzer - Swing GUI");
        setSize(1200, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createHeaderPanel());
        mainPanel.add(createInputPanel());

        JPanel centerArea = new JPanel(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createTablePanel(),
                createOutputPanel());
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.3);
        centerArea.add(splitPane, BorderLayout.CENTER);
        centerArea.setPreferredSize(new Dimension(1200, 550));
        mainPanel.add(centerArea);

        JPanel buttonArea = createButtonPanel();
        buttonArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        mainPanel.add(buttonArea);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        refreshTable();
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("Employee Attendance Log Analyzer", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        header.add(titleLabel, BorderLayout.CENTER);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterPanel.setOpaque(false);
        JLabel filterLabel = new JLabel("View Date: ");
        filterLabel.setForeground(Color.WHITE);
        filterLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dateFilterCombo = new JComboBox<>(new String[]{"All Dates"});
        dateFilterCombo.setPreferredSize(new Dimension(140, 25));
        dateFilterCombo.addActionListener(e -> {
            if (!isUpdatingFilter) {
                refreshTable();
            }
        });
        filterPanel.add(filterLabel);
        filterPanel.add(dateFilterCombo);

        header.add(filterPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createInputPanel() {
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(PRIMARY_COLOR, 2),
                "Add New Attendance Log",
                0, 0, new Font("Arial", Font.BOLD, 14), PRIMARY_COLOR));
        inputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Employee ID:"), gbc);
        gbc.gridx = 1;
        empIdField = new JTextField(15);
        empIdField.setToolTipText("Format: EMPXXX (e.g., EMP101)");
        inputPanel.add(empIdField, gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Action:"), gbc);
        gbc.gridx = 3;
        String[] actions = { "LOGIN", "LOGOUT" };
        JComboBox<String> actionCombo = new JComboBox<>(actions);
        actionCombo.addActionListener(e -> captureCurrentDateTime());
        inputPanel.add(actionCombo, gbc);

        // Row 2
        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Date:"), gbc);
        gbc.gridx = 1;
        dateField = new JTextField(15);
        dateField.setEditable(false);
        dateField.setBackground(Color.WHITE);
        inputPanel.add(dateField, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Time:"), gbc);
        gbc.gridx = 3;
        timeField = new JTextField(15);
        timeField.setEditable(false);
        timeField.setBackground(Color.WHITE);
        inputPanel.add(timeField, gbc);

        gbc.gridx = 4;
        gbc.gridy = 1;
        JButton captureTimeButton = createStyledButton("Refresh", PRIMARY_COLOR);
        captureTimeButton.setPreferredSize(new Dimension(100, 30));
        captureTimeButton.addActionListener(e -> captureCurrentDateTime());
        inputPanel.add(captureTimeButton, gbc);

        gbc.gridx = 5;
        gbc.gridy = 1;
        JButton addButton = createStyledButton("Add Log", PRIMARY_COLOR);
        addButton.setPreferredSize(new Dimension(100, 30));
        addButton.addActionListener(e -> addLog((String) actionCombo.getSelectedItem()));
        inputPanel.add(addButton, gbc);

        captureCurrentDateTime();
        return inputPanel;
    }

    private JScrollPane createTablePanel() {
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        logTable = new JTable(tableModel);
        logTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTable.setRowHeight(25);
        logTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        logTable.getTableHeader().setBackground(PRIMARY_COLOR);
        logTable.getTableHeader().setForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Attendance Logs (TreeMap Storage)"));
        return scrollPane;
    }

    private JScrollPane createOutputPanel() {
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        outputArea.setBackground(new Color(245, 245, 245));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Analysis Output"));
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setPreferredSize(new Dimension(1200, 180));

        JPanel buttonPanel = new JPanel(new GridLayout(4, 3, 15, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));

        JButton displayAllBtn = createStyledButton("Display All Logs", PRIMARY_COLOR);
        JButton lateLoginBtn = createStyledButton("Find Late Logins (>9AM)", PRIMARY_COLOR);
        JButton durationBtn = createStyledButton("Calculate Durations", PRIMARY_COLOR);

        JButton searchEmployeeBtn = createStyledButton("Search Employee", PRIMARY_COLOR);
        JButton sortBtn = createStyledButton("Sort by Employee ID", PRIMARY_COLOR);
        JButton statsBtn = createStyledButton("Show Statistics", PRIMARY_COLOR);

        JButton absentBtn = createStyledButton("Show Absentees", PRIMARY_COLOR);
        JButton saveBtn = createStyledButton("Save to File", PRIMARY_COLOR);

        displayAllBtn.addActionListener(e -> displayAllLogs());
        lateLoginBtn.addActionListener(e -> findLateLogins());
        durationBtn.addActionListener(e -> calculateDurations());
        searchEmployeeBtn.addActionListener(e -> searchEmployee());
        sortBtn.addActionListener(e -> sortByEmployeeId());
        statsBtn.addActionListener(e -> showStatistics());
        absentBtn.addActionListener(e -> showAbsentees());
        saveBtn.addActionListener(e -> saveToFile(false));

        buttonPanel.add(displayAllBtn);
        buttonPanel.add(lateLoginBtn);
        buttonPanel.add(durationBtn);
        buttonPanel.add(searchEmployeeBtn);
        buttonPanel.add(sortBtn);
        buttonPanel.add(statsBtn);
        buttonPanel.add(absentBtn);
        buttonPanel.add(saveBtn);

        statusLabel = new JLabel("Ready", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 11));

        outerPanel.add(buttonPanel, BorderLayout.NORTH);
        outerPanel.add(statusLabel, BorderLayout.SOUTH);

        return outerPanel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Inter", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBackground(bgColor);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bgColor.darker()),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private void captureCurrentDateTime() {
        try {
            ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            dateField.setText(istDateTime.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)));
            timeField.setText(istDateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)).toUpperCase());
            updateStatus("Refreshed current date and time", PRIMARY_COLOR);
        } catch (java.time.DateTimeException e) {
            showError("Date Error", e.getMessage());
        }
    }

    private void addLog(String action) {
        try {
            String empId = empIdField.getText().trim();
            String date = dateField.getText().trim();
            String time = timeField.getText().trim();

            analyzer.validateAndAddLog(empId, action, date, time);

            refreshDateFilter();
            refreshTable();
            empIdField.setText("");
            updateStatus("Log added successfully!", SUCCESS_COLOR);

        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    this, e.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            updateStatus("Error: " + e.getMessage(), ERROR_COLOR);
        } catch (Exception e) {
            showError("Input Error", e.getMessage());
        }
    }

    private String getTargetDate() {
        if (dateFilterCombo == null || dateFilterCombo.getSelectedItem() == null) return "All Dates";
        return (String) dateFilterCombo.getSelectedItem();
    }

    private void refreshDateFilter() {
        if (isUpdatingFilter || dateFilterCombo == null) return;
        isUpdatingFilter = true;
        
        String currentSelection = (String) dateFilterCombo.getSelectedItem();
        dateFilterCombo.removeAllItems();
        dateFilterCombo.addItem("All Dates");
        for (String date : analyzer.getUniqueDates()) {
            dateFilterCombo.addItem(date);
        }
        
        if (currentSelection != null) {
            boolean found = false;
            for (int i = 0; i < dateFilterCombo.getItemCount(); i++) {
                if (dateFilterCombo.getItemAt(i).equals(currentSelection)) {
                    dateFilterCombo.setSelectedIndex(i);
                    found = true; break;
                }
            }
            if (!found) dateFilterCombo.setSelectedIndex(0);
        }
        isUpdatingFilter = false;
    }

    private void refreshTable() {
        if (isUpdatingFilter) return;
        tableModel.setRowCount(0);
        
        String targetDate = getTargetDate();
        List<AttendanceLog> logs = analyzer.getFilteredLogs(targetDate);
        for (AttendanceLog log : logs) {
            tableModel.addRow(new Object[] { log.getEmployeeId(), log.getAction(), log.getDate(), log.getTimeAsString() });
        }
    }

    private void calculateDurations() {
        outputArea.setText("=== TOTAL SESSION DURATIONS [" + getTargetDate() + "] ===\n");
        Map<String, String> durations = analyzer.calculateTotalDurations(getTargetDate());

        if (durations.isEmpty()) {
            outputArea.append("No session data available for this date.\n");
        } else {
            durations.forEach((empId, timeStr) -> {
                outputArea.append(String.format("Employee ID: %s | Total Time: %s\n", empId, timeStr));
            });
        }
        updateStatus("Durations calculated", SUCCESS_COLOR);
    }

    private void displayAllLogs() {
        outputArea.setText("=== ATTENDANCE LOGS [" + getTargetDate() + "] ===\n");
        List<AttendanceLog> logs = analyzer.getFilteredLogs(getTargetDate());
        if (logs.isEmpty()) {
            outputArea.append("No logs found.\n");
        } else {
            logs.forEach(log -> outputArea.append(log.toString() + "\n"));
        }
        updateStatus("Displayed logs for " + getTargetDate(), PRIMARY_COLOR);
    }

    private void sortByEmployeeId() {
        // TreeMap already keeps logs sorted by Employee ID
        refreshTable();
        outputArea.setText("=== LOGS SORTED BY EMPLOYEE ID [" + getTargetDate() + "] ===\n");
        analyzer.getFilteredLogs(getTargetDate()).forEach(log -> outputArea.append(log.toString() + "\n"));
        updateStatus("Logs sorted by Employee ID", new Color(142, 68, 173));
    }

    private void findLateLogins() {
        outputArea.setText("=== LATE LOGINS (>9AM) [" + getTargetDate() + "] ===\n");
        List<AttendanceLog> lateLogs = analyzer.getLogsAfter9AM(getTargetDate());
        if (lateLogs.isEmpty()) {
            outputArea.append("No late logins found for this date.\n");
        } else {
            lateLogs.forEach(log -> outputArea.append(log.toString() + "\n"));
        }
        updateStatus("Found " + lateLogs.size() + " late logins", new Color(243, 156, 18));
    }

    private void showStatistics() {
        outputArea.setText("=== STATISTICS [" + getTargetDate() + "] ===\n");
        List<AttendanceLog> filteredLogs = analyzer.getFilteredLogs(getTargetDate());
        outputArea.append("Total Logs: " + filteredLogs.size() + "\n");

        long logins = filteredLogs.stream().filter(l -> l.getAction().equals("LOGIN")).count();
        long logouts = filteredLogs.stream().filter(l -> l.getAction().equals("LOGOUT")).count();

        outputArea.append("Logins: " + logins + "\n");
        outputArea.append("Logouts: " + logouts + "\n");

        long uniqueEmployees = filteredLogs.stream()
                .map(l -> l.getEmployeeId())
                .distinct()
                .count();
        outputArea.append("Unique Employees: " + uniqueEmployees + "\n");

        updateStatus("Stats generated", PRIMARY_COLOR);
    }

    private void showAbsentees() {
        outputArea.setText("=== ABSENT EMPLOYEES [" + getTargetDate() + "] ===\n");
        if ("All Dates".equals(getTargetDate())) {
            outputArea.append("Please select a specific date from the dropdown to check absences.\n");
        } else {
            List<String> absentees = analyzer.getAbsentEmployees(getTargetDate());
            if (absentees.isEmpty()) {
                outputArea.append("All known employees were present!\n");
            } else {
                for (String emp : absentees) {
                    outputArea.append("Employee ID: " + emp + "\n");
                }
            }
        }
        updateStatus("Absence check complete", new Color(192, 57, 43));
    }

    /**
     * NEW: Search and display all records for a specific employee
     * Shows attendance logs and total working hours
     */
    private void searchEmployee() {
        try {
            // Get employee ID from user
            String empId = JOptionPane.showInputDialog(
                    this,
                    "Enter Employee ID (e.g., EMP101):",
                    "Search Employee",
                    JOptionPane.QUESTION_MESSAGE);

            // Handle cancel or empty input
            if (empId == null || empId.trim().isEmpty()) {
                return;
            }

            empId = empId.trim().toUpperCase();

            // Validate format
            if (!empId.matches("EMP\\d{3}")) {
                showError("Invalid Format", "Employee ID must be in format EMPXXX (e.g., EMP101)");
                return;
            }

            // Check if employee exists
            if (!analyzer.employeeExists(empId)) {
                outputArea.setText("=== SEARCH RESULTS ===\n\n");
                outputArea.append("❌ No records found for Employee ID: " + empId + "\n\n");
                outputArea.append("This employee does not exist in the system.\n");
                outputArea.append("Please verify the Employee ID and try again.\n");
                updateStatus("Employee not found: " + empId, ERROR_COLOR);
                return;
            }

            // Get employee's logs
            List<AttendanceLog> employeeLogs = analyzer.getLogsByEmployeeId(empId);

            // Display results
            outputArea.setText("=== EMPLOYEE ATTENDANCE RECORDS ===\n");
            outputArea.append("Employee ID: " + empId + "\n");
            outputArea.append("Total Records: " + employeeLogs.size() + "\n");
            outputArea.append("=".repeat(50) + "\n\n");

            if (employeeLogs.isEmpty()) {
                outputArea.append("No attendance records found.\n");
            } else {
                // Display all logs chronologically
                outputArea.append("Attendance Activity (Chronological Order):\n");
                outputArea.append("-".repeat(50) + "\n");

                for (AttendanceLog log : employeeLogs) {
                    String icon = "LOGIN".equals(log.getAction()) ? "→ IN " : "← OUT";
                    outputArea.append(String.format("%s  %s | %s | %s\n",
                            icon, log.getAction(), log.getDate(), log.getTimeAsString()));
                }

                // Calculate and display total working hours
                outputArea.append("\n" + "=".repeat(50) + "\n");
                String totalTime = analyzer.getTotalDurationForEmployee(empId);
                outputArea.append("Total Working Time: " + totalTime + "\n");

                // Count logins and logouts
                long logins = employeeLogs.stream()
                        .filter(log -> "LOGIN".equals(log.getAction()))
                        .count();
                long logouts = employeeLogs.stream()
                        .filter(log -> "LOGOUT".equals(log.getAction()))
                        .count();

                outputArea.append("\nSession Summary:\n");
                outputArea.append("  • Total Logins: " + logins + "\n");
                outputArea.append("  • Total Logouts: " + logouts + "\n");

                if (logins > logouts) {
                    outputArea.append("  ⚠ Currently logged in (incomplete session)\n");
                } else if (logins == logouts) {
                    outputArea.append("  ✓ All sessions completed\n");
                }
            }

            updateStatus("Displaying records for " + empId + " (" + employeeLogs.size() + " records)", SUCCESS_COLOR);

        } catch (Exception e) {
            showError("Search Error", "Error searching employee: " + e.getMessage());
        }
    }

    // --- FILE OPERATIONS ---

    private void saveToFile(boolean saveAs) {
        try {
            String[] formats = { "TXT", "CSV", "JSON" };
            String format = (String) JOptionPane.showInputDialog(this, "Select format:", "Format",
                    JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);
            if (format == null)
                return;

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new java.io.File("attendance." + format.toLowerCase()));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                // Export only what is visible in the table to allow smart exports
                List<AttendanceLog> activeLogs = new java.util.ArrayList<>();
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    activeLogs.add(new AttendanceLog(
                        (String) tableModel.getValueAt(i, 0),
                        (String) tableModel.getValueAt(i, 1),
                        (String) tableModel.getValueAt(i, 2),
                        (String) tableModel.getValueAt(i, 3)
                    ));
                }
                
                analyzer.saveToFile(fileChooser.getSelectedFile().getAbsolutePath(), format, activeLogs);
                updateStatus("Saved " + activeLogs.size() + " logs successfully", SUCCESS_COLOR);
            }
        } catch (IOException e) {
            showError("Save Error", e.getMessage());
        }
    }

    private void loadSampleData() {
        // Use a diverse set of dates to test the filtering
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
        refreshDateFilter();
        refreshTable();
        updateStatus("Sample data loaded", SUCCESS_COLOR);
    }

    private void updateStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + title, ERROR_COLOR);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new AttendanceGUI().setVisible(true));
    }
}