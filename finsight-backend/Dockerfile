# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Python and dependencies for TrOCR
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Copy scripts and model weights cache (if exists)
COPY src/main/resources/scripts /app/scripts

# Setup Python environment inside container
RUN python3 -m venv /app/scripts/venv && \
    /app/scripts/venv/bin/pip install torch transformers Pillow opencv-python-headless

# Port for backend
EXPOSE 8080

# Environment variables for Python pathing
ENV PYTHON_EXECUTABLE=/app/scripts/venv/bin/python3
ENV OCR_SCRIPT_PATH=/app/scripts/trocr_processor.py
ENV HF_HOME=/app/scripts/hf_cache
ENV TRANSFORMERS_CACHE=/app/scripts/hf_cache

ENTRYPOINT ["java", "-jar", "app.jar"]
