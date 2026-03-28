#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "=========================================="
echo "    FinSight CI/CD: Build and Run         "
echo "=========================================="

# Disable Jansi to avoid "Operation not permitted" on .jnilib.lck files on macOS
export MAVEN_OPTS="-Djansi.force=false -Dstyle.color=never"

echo ">>> [0/4] Cleaning up existing services..."
lsof -ti :8080 | xargs kill -9 2>/dev/null || true
lsof -ti :3000 | xargs kill -9 2>/dev/null || true

# 1. Build Backend
echo ""
echo ">>> [1/4] Building Backend (Spring Boot) & Setting up TrOCR..."
cd finsight-backend
mkdir -p tmp
# Use compile -DskipTests to match manual build success
if [ -f "mvnw" ]; then
    ./mvnw clean compile -DskipTests -Djava.io.tmpdir=$(pwd)/tmp
else
    mvn clean compile -DskipTests -Djava.io.tmpdir=$(pwd)/tmp
fi
# Remove the MockMaker file causing FileSystemException if it's redundant for Mockito 5
# rm -f src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker

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

# 2. Setup Frontend
echo ""
echo ">>> [2/4] Setting up Frontend (Next.js)..."
cd finsight-frontend
npm install
# npm run build # Skipped for DEV mode
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
# Add -Dspring-boot.run.fork=false if necessary, but backgrounding it with & is safer.
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.io.tmpdir=$(pwd)/tmp" > backend.log 2>&1 &
BACKEND_PID=$!

# Wait for backend to start (check port 8080)
echo ">>> Waiting for backend to initialize on port 8080..."
MAX_RETRIES=30
COUNT=0
while ! lsof -i :8080 >/dev/null && [ $COUNT -lt $MAX_RETRIES ]; do
    sleep 2
    COUNT=$((COUNT+1))
    if [ $((COUNT % 5)) -eq 0 ]; then
        echo "...still waiting ($COUNT/$MAX_RETRIES)"
    fi
done

if ! lsof -i :8080 >/dev/null; then
    echo "❌ Error: Backend failed to start. Check finsight-backend/backend.log"
    exit 1
fi
echo "✅ Backend successfully started on port 8080."

# --- CI/CD: Asynchronous Background Tests ---
echo ">>> [CI/CD] Triggering Background Unit Tests..."
REPORT_FILE="$(pwd)/full_test_report.log"
echo "--- FinSight CI/CD Test Report: $(date) ---" > "$REPORT_FILE"

(
    # Add a small delay to avoid resource conflicts during the initial app warmup
    sleep 10
    # 1. Run Backend Tests
    echo "--- 1. Backend Unit Tests ---" >> "$REPORT_FILE"
    # Use -o (offline) or skip re-resources if we can, but let's just ensure it's a separate run
    mvn test -Djava.io.tmpdir=$(pwd)/tmp -Djunit.jupiter.tempdir.parent.path=$(pwd)/tmp >> "$REPORT_FILE" 2>&1
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
echo ">>> [4/4] Starting Frontend on Port 3000 (DEV MODE)..."
cd finsight-frontend
npm run dev > frontend.log 2>&1 &
FRONTEND_PID=$!

# Wait for frontend to start (check port 3000)
echo ">>> Waiting for frontend to initialize on port 3000..."
COUNT=0
while ! lsof -i :3000 >/dev/null && [ $COUNT -lt 15 ]; do
    sleep 2
    COUNT=$((COUNT+1))
done

if ! lsof -i :3000 >/dev/null; then
    echo "❌ Error: Frontend failed to start. Check finsight-frontend/frontend.log"
    # If frontend fails, clean up backend too
    kill $BACKEND_PID 2>/dev/null || true
    exit 1
fi
echo "✅ Frontend successfully started on port 3000."
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
