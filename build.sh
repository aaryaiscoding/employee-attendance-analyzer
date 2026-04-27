#!/bin/bash

# Employee Attendance Analyzer - Build Script

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
CLASS_DIR="$PROJECT_DIR/target/classes"
WEBAPP_SRC_DIR="$PROJECT_DIR/src/main/webapp"
WEBAPP_TARGET_DIR="$PROJECT_DIR/target/webapp"
WEB_INF_DIR="$WEBAPP_TARGET_DIR/WEB-INF"
LIB_DIR="$PROJECT_DIR/lib"

echo "=== Employee Attendance Analyzer Build Script ==="

# Create output and dependency directories
mkdir -p "$CLASS_DIR"
mkdir -p "$LIB_DIR"
mkdir -p "$WEB_INF_DIR/classes"
mkdir -p "$WEB_INF_DIR/lib"

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

# Download Jakarta Servlet API for compile-time only
SERVLET_JAR="$LIB_DIR/jakarta.servlet-api-6.0.0.jar"
if [ ! -f "$SERVLET_JAR" ]; then
    echo "Downloading Jakarta Servlet API..."
    curl -L -o "$SERVLET_JAR" "https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ Jakarta Servlet API downloaded successfully!"
    else
        echo "⚠️  Could not download Jakarta Servlet API automatically. Please place it in $LIB_DIR"
        exit 1
    fi
fi

# Build classpath
CP="$JDBC_JAR:$SERVLET_JAR"

# Clean previous build output
rm -rf "$CLASS_DIR" "$WEBAPP_TARGET_DIR"
mkdir -p "$CLASS_DIR" "$WEB_INF_DIR/classes" "$WEB_INF_DIR/lib"

# Compile all Java source files
echo "Compiling Java source files..."
javac -cp "$CP" -d "$CLASS_DIR" $(find "$SRC_DIR" -name "*.java")

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Compilation successful!"

# Assemble WAR structure
echo "Assembling WAR structure..."
cp -R "$WEBAPP_SRC_DIR/"* "$WEBAPP_TARGET_DIR/"
cp -R "$CLASS_DIR/"* "$WEB_INF_DIR/classes/"
cp "$JDBC_JAR" "$WEB_INF_DIR/lib/"

WAR_PATH="$PROJECT_DIR/target/attendance.war"
rm -f "$WAR_PATH"
(
    cd "$WEBAPP_TARGET_DIR" && jar -cf "$WAR_PATH" .
)

if [ $? -eq 0 ]; then
    echo "✅ WAR created successfully!"
    echo "WAR file: $WAR_PATH"
else
    echo "❌ WAR packaging failed!"
    exit 1
fi

echo ""
echo "Next steps:"
echo "  1) Copy target/attendance.war to your Tomcat webapps/ folder"
echo "  2) Start Tomcat and open http://localhost:8080/attendance/"
echo ""