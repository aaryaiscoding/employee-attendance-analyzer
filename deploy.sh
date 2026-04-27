#!/bin/bash

# Employee Attendance Analyzer - Deploy Script

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
WAR_PATH="$PROJECT_DIR/target/attendance.war"
TOMCAT_DIR="$PROJECT_DIR/apache-tomcat-9.0.88"
WEBAPPS_DIR="$TOMCAT_DIR/webapps"
DEST_WAR="$WEBAPPS_DIR/attendance.war"

echo "=== Employee Attendance Analyzer Deploy Script ==="

if [ ! -d "$TOMCAT_DIR" ]; then
    echo "❌ Tomcat folder not found at: $TOMCAT_DIR"
    echo "Create the symlink first or update TOMCAT_DIR in deploy.sh"
    exit 1
fi

echo "1) Building WAR..."
"$PROJECT_DIR/build.sh"

if [ ! -f "$WAR_PATH" ]; then
    echo "❌ WAR not found after build: $WAR_PATH"
    exit 1
fi

echo "2) Deploying WAR to Tomcat webapps..."
cp "$WAR_PATH" "$DEST_WAR"
echo "✅ Deployed: $DEST_WAR"

echo "3) Restarting Tomcat service..."
brew services restart tomcat

echo ""
echo "✅ Deployment complete."
echo "Open: http://localhost:8080/attendance/"
