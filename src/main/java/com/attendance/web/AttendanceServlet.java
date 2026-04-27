package com.attendance.web;

import com.attendance.AttendanceLog;
import com.attendance.AttendanceLogAnalyzer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class AttendanceServlet extends HttpServlet {
    private static final String SESSION_AUTH_KEY = "authenticated";
    private static final String LOGIN_USERNAME = "admin";
    private static final String LOGIN_PASSWORD = "luffykapil";

    private AttendanceLogAnalyzer analyzer;

    @Override
    public void init() throws ServletException {
        analyzer = new AttendanceLogAnalyzer();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getParameter("operation");
        if (operation == null || operation.trim().isEmpty()) {
            operation = "logs";
        }

        if ("loginPage".equals(operation)) {
            renderLoginPage(resp, null);
            return;
        }

        if ("logout".equals(operation)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            resp.sendRedirect(req.getContextPath() + "/api/attendance?operation=loginPage");
            return;
        }

        if (!isAuthenticated(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(resp, "{\"success\": false, \"message\": \"Please login first\"}");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        String targetDate = req.getParameter("date");

        try {
            switch (operation) {
                case "logs":
                    out.print(logsToJson(analyzer.getFilteredLogs(targetDate)));
                    break;
                case "dates":
                    out.print(stringListToJson(analyzer.getUniqueDates()));
                    break;
                case "late":
                    out.print(logsToJson(analyzer.getLogsAfter9AM(targetDate)));
                    break;
                case "durations":
                    out.print(mapToJson(analyzer.calculateTotalDurations(targetDate)));
                    break;
                case "stats":
                    out.print(statsToJson(targetDate));
                    break;
                case "absent":
                    out.print(stringListToJson(analyzer.getAbsentEmployees(targetDate)));
                    break;
                case "employee":
                    String employeeId = req.getParameter("employeeId");
                    out.print(employeeSearchToJson(employeeId));
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"success\": false, \"message\": \"Unsupported operation\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"success\": false, \"message\": \"Internal Server Error\"}");
            e.printStackTrace();
        }
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getParameter("operation");
        if (operation == null || operation.trim().isEmpty()) {
            operation = "add";
        }

        if ("login".equals(operation)) {
            handleLogin(req, resp);
            return;
        }

        if (!isAuthenticated(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(resp, "{\"success\": false, \"message\": \"Please login first\"}");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            if ("add".equals(operation)) {
                String empId = req.getParameter("employeeId");
                String action = req.getParameter("action");

                if (empId == null || action == null || empId.trim().isEmpty() || action.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"success\": false, \"message\": \"Missing parameters\"}");
                    return;
                }

                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
                String date = now.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));

                analyzer.validateAndAddLog(empId.toUpperCase(), action.toUpperCase(), date, time);
                out.print("{\"success\": true, \"message\": \"Log added successfully!\"}");
            } else if ("sample".equals(operation)) {
                loadSampleData();
                out.print("{\"success\": true, \"message\": \"Sample data loaded successfully.\"}");
            } else if ("clear".equals(operation)) {
                analyzer.clearAllLogs();
                out.print("{\"success\": true, \"message\": \"All logs cleared.\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"success\": false, \"message\": \"Unsupported operation\"}");
            }
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String safeMsg = e.getMessage().replace("\"", "\\\"");
            out.print("{\"success\": false, \"message\": \"" + safeMsg + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"success\": false, \"message\": \"Internal Server Error\"}");
            e.printStackTrace();
        }
        out.flush();
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        boolean validCredentials = LOGIN_USERNAME.equals(username) && LOGIN_PASSWORD.equals(password);
        if (validCredentials) {
            HttpSession session = req.getSession(true);
            session.setAttribute(SESSION_AUTH_KEY, Boolean.TRUE);
            resp.sendRedirect(req.getContextPath() + "/index.html");
            return;
        }

        renderLoginPage(resp, "Invalid username or password");
    }

    private boolean isAuthenticated(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTH_KEY));
    }

    private void writeJson(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }

    private void renderLoginPage(HttpServletResponse resp, String errorMessage) throws IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter out = resp.getWriter();
        out.print("<!DOCTYPE html>");
        out.print("<html lang=\"en\"><head><meta charset=\"UTF-8\">");
        out.print("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.print("<title>Attendance Login</title>");
        out.print("<style>");
        out.print("body{font-family:Arial,sans-serif;background:#f3f4f6;margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;}");
        out.print(".card{background:#fff;padding:1.5rem;border-radius:10px;box-shadow:0 4px 16px rgba(0,0,0,0.08);width:min(92vw,360px);}");
        out.print("h2{margin-top:0;margin-bottom:1rem;color:#111827;}");
        out.print("label{display:block;margin-bottom:.35rem;color:#374151;font-size:.92rem;}");
        out.print("input{width:100%;padding:.6rem;border:1px solid #d1d5db;border-radius:6px;margin-bottom:.85rem;box-sizing:border-box;}");
        out.print("button{width:100%;padding:.65rem;background:#2563eb;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;}");
        out.print(".error{color:#dc2626;background:#fee2e2;border:1px solid #fecaca;padding:.55rem;border-radius:6px;margin-bottom:.8rem;font-size:.9rem;}");
        out.print("</style></head><body>");
        out.print("<div class=\"card\"><h2>Employee Attendance Login</h2>");
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            out.print("<div class=\"error\">" + escapeHtml(errorMessage) + "</div>");
        }
        out.print("<form method=\"post\" action=\"");
        out.print("?");
        out.print("\">");
        out.print("<input type=\"hidden\" name=\"operation\" value=\"login\">");
        out.print("<label for=\"username\">Username</label>");
        out.print("<input id=\"username\" type=\"text\" name=\"username\" placeholder=\"Enter username\" required>");
        out.print("<label for=\"password\">Password</label>");
        out.print("<input id=\"password\" type=\"password\" name=\"password\" placeholder=\"Enter password\" required>");
        out.print("<button type=\"submit\">Login</button>");
        out.print("</form></div></body></html>");
        out.flush();
    }

    private String statsToJson(String targetDate) {
        List<AttendanceLog> logs = analyzer.getFilteredLogs(targetDate);
        long logins = logs.stream().filter(l -> "LOGIN".equals(l.getAction())).count();
        long logouts = logs.stream().filter(l -> "LOGOUT".equals(l.getAction())).count();
        long uniqueEmployees = logs.stream().map(AttendanceLog::getEmployeeId).distinct().count();
        return "{"
            + "\"totalLogs\":" + logs.size() + ","
            + "\"logins\":" + logins + ","
            + "\"logouts\":" + logouts + ","
            + "\"uniqueEmployees\":" + uniqueEmployees
            + "}";
    }

    private String employeeSearchToJson(String employeeId) {
        if (employeeId == null || employeeId.trim().isEmpty()) {
            return "{\"success\": false, \"message\": \"Employee ID is required\"}";
        }

        String normalizedId = employeeId.trim().toUpperCase();
        if (!normalizedId.matches("EMP\\d{3}")) {
            return "{\"success\": false, \"message\": \"Employee ID format must be EMPXXX\"}";
        }

        if (!analyzer.employeeExists(normalizedId)) {
            return "{\"success\": false, \"message\": \"No records found for " + escapeJson(normalizedId) + "\"}";
        }

        List<AttendanceLog> logs = analyzer.getLogsByEmployeeId(normalizedId);
        long logins = logs.stream().filter(l -> "LOGIN".equals(l.getAction())).count();
        long logouts = logs.stream().filter(l -> "LOGOUT".equals(l.getAction())).count();

        return "{"
            + "\"success\": true,"
            + "\"employeeId\": \"" + escapeJson(normalizedId) + "\","
            + "\"totalDuration\": \"" + escapeJson(analyzer.getTotalDurationForEmployee(normalizedId)) + "\","
            + "\"logins\": " + logins + ","
            + "\"logouts\": " + logouts + ","
            + "\"logs\": " + logsToJson(logs)
            + "}";
    }

    private void loadSampleData() {
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
    }

    private String logsToJson(List<AttendanceLog> logs) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            AttendanceLog log = logs.get(i);
            json.append("{")
                .append("\"employeeId\":\"").append(escapeJson(log.getEmployeeId())).append("\",")
                .append("\"action\":\"").append(escapeJson(log.getAction())).append("\",")
                .append("\"date\":\"").append(escapeJson(log.getDate())).append("\",")
                .append("\"time\":\"").append(escapeJson(log.getTimeAsString())).append("\"")
                .append("}");
            if (i < logs.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String stringListToJson(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            json.append("\"").append(escapeJson(values.get(i))).append("\"");
            if (i < values.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String mapToJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            json.append("\"").append(escapeJson(entry.getKey())).append("\":")
                .append("\"").append(escapeJson(entry.getValue())).append("\"");
            if (index < data.size() - 1) {
                json.append(",");
            }
            index++;
        }
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
