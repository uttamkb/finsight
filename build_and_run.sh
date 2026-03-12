#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "=========================================="
echo "    FinSight CI/CD: Build and Run         "
echo "=========================================="

echo ">>> [0/4] Cleaning up existing services..."
lsof -ti :8080 | xargs kill -9 2>/dev/null || true
lsof -ti :3000 | xargs kill -9 2>/dev/null || true

# 1. Build Backend
echo ""
echo ">>> [1/4] Building Backend (Spring Boot) & Setting up TrOCR..."
cd finsight-backend
mkdir -p .m2_repo /tmp
if [ -f "mvnw" ]; then
    ./mvnw clean compile -DskipTests -Dmaven.repo.local=$(pwd)/.m2_repo -Djava.io.tmpdir=/tmp
else
    mvn clean compile -DskipTests -Dmaven.repo.local=$(pwd)/.m2_repo -Djava.io.tmpdir=/tmp
fi

# Setup Python Venv for TrOCR if not exists
SCRIPTS_DIR="src/main/resources/scripts"
if [ ! -d "$SCRIPTS_DIR/venv" ]; then
    echo ">>> Setting up TrOCR Python Virtual Environment..."
    mkdir -p "$SCRIPTS_DIR"
    python3 -m venv "$SCRIPTS_DIR/venv"
    source "$SCRIPTS_DIR/venv/bin/activate"
    pip install torch transformers Pillow opencv-python-headless
else
    echo ">>> TrOCR Python Venv already exists."
fi
cd ..

# 2. Build Frontend
echo ""
echo ">>> [2/4] Building Frontend (Next.js)..."
cd finsight-frontend
npm install
npm run build
cd ..

echo ""
echo "=========================================="
echo "    Starting Application Services...      "
echo "=========================================="

# 3. Start Backend
echo ""
echo ">>> [3/4] Starting Backend on Port 8080..."
cd finsight-backend
mkdir -p tmp
export TMPDIR=$(pwd)/tmp
# Run backend directly using Maven to avoid JAR assembly permission issues
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.io.tmpdir=$(pwd)/tmp" -Dmaven.repo.local=$(pwd)/.m2_repo > backend.log 2>&1 &
BACKEND_PID=$!
cd ..

# Wait a few seconds for the backend to initialize before starting the frontend
sleep 5

# 4. Start Frontend
echo ""
echo ">>> [4/4] Starting Frontend on Port 3000..."
cd finsight-frontend
npm start &
FRONTEND_PID=$!
cd ..

echo ""
echo "========================================================"
echo " ✅ FinSight is now fully compiled and running! "
echo ""
echo " 🟢 Backend API:  http://localhost:8080"
echo " 🟢 Frontend UI:  http://localhost:3000"
echo ""
echo " NOTE: Press Ctrl+C to safely shut down both services."
echo "========================================================"

# Trap SIGINT (Ctrl+C) and SIGTERM to clean up background processes gracefully
cleanup() {
    echo ""
    echo "Shutting down FinSight services..."
    echo "Killing Frontend (PID: $FRONTEND_PID)..."
    kill $FRONTEND_PID 2>/dev/null || true
    echo "Killing Backend (PID: $BACKEND_PID)..."
    kill $BACKEND_PID 2>/dev/null || true
    echo "Shutdown complete. Goodbye!"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Wait indefinitely, keeping the script alive so the trap can catch Ctrl+C
wait $BACKEND_PID $FRONTEND_PID
