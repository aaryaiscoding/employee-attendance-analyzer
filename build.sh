#!/bin/bash

# Employee Attendance Analyzer - Build Script

PROJECT_DIR="$(dirname "$0")"
SRC_DIR="$PROJECT_DIR/src/main/java"
CLASS_DIR="$PROJECT_DIR/target/classes"
LIB_DIR="$PROJECT_DIR/lib"

echo "=== Employee Attendance Analyzer Build Script ==="

# Create target and lib directories
mkdir -p "$CLASS_DIR"
mkdir -p "$LIB_DIR"

# Download SQLite JDBC driver if not present
JDBC_JAR="$LIB_DIR/sqlite-jdbc-3.41.2.2.jar"
if [ ! -f "$JDBC_JAR" ]; then
    echo "Downloading SQLite JDBC driver..."
    curl -L -o "$JDBC_JAR" "https://github.com/xerial/sqlite-jdbc/releases/download/3.41.2.2/sqlite-jdbc-3.41.2.2.jar" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ SQLite JDBC driver downloaded successfully!"
    else
        echo "⚠️  Could not download JDBC driver automatically. Attempting offline..."
        # Create a placeholder that will be handled by Maven or manual download
        echo "Please download sqlite-jdbc JAR and place it in $LIB_DIR"
    fi
fi

# Compile all Java files with JDBC driver in classpath
echo "Compiling Java source files..."
if [ -f "$JDBC_JAR" ]; then
    javac -cp "$JDBC_JAR" -d "$CLASS_DIR" "$SRC_DIR/com/attendance/"*.java
    cp "$JDBC_JAR" "$CLASS_DIR"
else
    javac -d "$CLASS_DIR" "$SRC_DIR/com/attendance/"*.java
fi

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "Available commands:"
    echo "  ./run.sh gui       - Launch the Java Swing GUI interface (RECOMMENDED)"
    echo "  ./run.sh analyzer  - Run the console-based attendance log analyzer"
    echo ""
else
    echo "❌ Compilation failed!"
    exit 1
fi