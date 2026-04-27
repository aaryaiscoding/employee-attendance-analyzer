#!/bin/bash

# Employee Attendance Analyzer - Run Script

PROJECT_DIR="$(dirname "$0")"
CLASS_DIR="$PROJECT_DIR/target/classes"
LIB_DIR="$PROJECT_DIR/lib"

if [ ! -d "$CLASS_DIR" ]; then
    echo "Classes not found. Run ./build.sh first."
    exit 1
fi

# Build classpath with JDBC driver
CLASSPATH="$CLASS_DIR"
if [ -f "$CLASS_DIR/sqlite-jdbc-3.41.2.2.jar" ]; then
    CLASSPATH="$CLASSPATH:$CLASS_DIR/sqlite-jdbc-3.41.2.2.jar"
elif [ -f "$LIB_DIR/sqlite-jdbc-3.41.2.2.jar" ]; then
    CLASSPATH="$CLASSPATH:$LIB_DIR/sqlite-jdbc-3.41.2.2.jar"
fi

case "$1" in
    "gui")
        echo "Launching Swing GUI..."
        echo ""
        java -cp "$CLASSPATH" com.attendance.AttendanceGUI
        ;;
    "analyzer")
        echo "Running AttendanceLogAnalyzer..."
        echo ""
        java -cp "$CLASSPATH" com.attendance.AttendanceLogAnalyzer
        ;;
    "war")
        echo "Web deployment is now external to this project."
        echo "Build the WAR with ./build.sh, then deploy:"
        echo "  $PROJECT_DIR/target/attendance.war"
        echo "Copy this file into your Tomcat webapps/ folder and start Tomcat."
        ;;
    *)
        echo "Usage: $0 {gui|analyzer|war}"
        echo ""
        echo "  gui       - Launch the Java Swing GUI interface (RECOMMENDED)"
        echo "  analyzer  - Run the console-based attendance log analyzer"
        echo "  war       - Show deployment instructions for Tomcat WAR deployment"
        exit 1
        ;;
esac