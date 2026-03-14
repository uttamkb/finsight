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

# Setup Python Venv for PaddleOCR if not exists
SCRIPTS_DIR="src/main/resources/scripts"
if [ ! -d "$SCRIPTS_DIR/venv" ]; then
    echo ">>> Setting up PaddleOCR Python Virtual Environment..."
    mkdir -p "$SCRIPTS_DIR"
    python3 -m venv "$SCRIPTS_DIR/venv"
    source "$SCRIPTS_DIR/venv/bin/activate"
    # Core vision + OCR stack (PaddleOCR replaces TrOCR/transformers)
    pip install --upgrade pip
    pip install paddlepaddle paddleocr Pillow opencv-python-headless pdfplumber pdf2image
else
    echo ">>> PaddleOCR Python Venv already exists."
    # Ensure paddleocr is installed even if venv existed from TrOCR era
    source "$SCRIPTS_DIR/venv/bin/activate"
    python3 -c "import paddleocr" 2>/dev/null || pip install paddlepaddle paddleocr
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

# --- CI/CD: Asynchronous Background Tests ---
echo ">>> [CI/CD] Triggering Background Unit Tests..."
REPORT_FILE="$(pwd)/full_test_report.log"
echo "--- FinSight CI/CD Test Report: $(date) ---" > "$REPORT_FILE"

(
    # 1. Run Backend Tests
    echo "--- 1. Backend Unit Tests ---" >> "$REPORT_FILE"
    mvn test -Dmaven.repo.local=$(pwd)/.m2_repo -Djava.io.tmpdir=$(pwd)/tmp -Djunit.jupiter.tempdir.parent.path=$(pwd)/tmp >> "$REPORT_FILE" 2>&1
    BACKEND_EXIT=$?
    
    if [ $BACKEND_EXIT -eq 0 ]; then
        echo "✅ Backend Tests Passed" >> "$REPORT_FILE"
    else
        echo "❌ Backend Tests Failed" >> "$REPORT_FILE"
    fi

    # 2. Run Frontend Tests
    echo -e "\n--- 2. Frontend Unit Tests ---" >> "$REPORT_FILE"
    cd ../finsight-frontend
    # Note: Using --run for non-interactive mode
    npm test >> "$REPORT_FILE" 2>&1
    FRONTEND_EXIT=$?
    
    if [ $FRONTEND_EXIT -eq 0 ]; then
        echo "✅ Frontend Tests Passed" >> "$REPORT_FILE"
    else
        echo "❌ Frontend Tests Failed" >> "$REPORT_FILE"
    fi

    # Final Result
    if [ $BACKEND_EXIT -eq 0 ] && [ $FRONTEND_EXIT -eq 0 ]; then
        echo -e "\n\n==========================================" >> "$REPORT_FILE"
        echo "🎉 [CI/CD] ALL SYSTEMS NOMINAL. ALL TESTS PASSED." >> "$REPORT_FILE"
        echo "==========================================" >> "$REPORT_FILE"
        echo ">>> ✅ [CI/CD] All Unit Tests PASSED. (Check finsight-backend/full_test_report.log)"
    else
        echo -e "\n\n==========================================" >> "$REPORT_FILE"
        echo "⚠️  [CI/CD] PIPELINE PARTIALLY FAILED." >> "$REPORT_FILE"
        echo "==========================================" >> "$REPORT_FILE"
        echo ">>> ⚠️  [CI/CD] Unit Tests COMPLETED WITH ERRORS. Check finsight-backend/full_test_report.log"
    fi
) &
TEST_PID=$!
# ---------------------------------------------

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
echo " ℹ️  Asynchronous Tests (PID: $TEST_PID) are running in the background."
echo "    Check 'finsight-backend/full_test_report.log' for results."
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
    echo "Wait, checking background tests..."
    kill $TEST_PID 2>/dev/null || true
    echo "Shutdown complete. Goodbye!"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Wait indefinitely, keeping the script alive so the trap can catch Ctrl+C
wait $BACKEND_PID $FRONTEND_PID
