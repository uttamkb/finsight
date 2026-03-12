# FinSight: Apartment Finance Management

FinSight is a modern financial management application designed to bridge physical receipts and digital finance tracking.

## 🚀 Getting Started

### Option 1: Local Development (Manual)
Run the project locally on your machine using the provided setup script. This script automatically handles the Java build, Next.js build, and the **TrOCR Neural Engine** setup.

```bash
./build_and_run.sh
```

### Option 2: Docker Deployment (Recommended for Cloud)
Optimized for **Oracle Cloud ARM** or any high-RAM server. This runs both the backend (with the AI engine) and the frontend in containers.

```bash
docker-compose up -d --build
```

## 🛠 Tech Stack
- **Backend**: Spring Boot 3 + Java 21
- **Frontend**: Next.js 15 + React
- **AI Engine**: TrOCR (Transformer-based Optical Character Recognition)
- **Database**: LibSQL (Local or Turso managed)
- **Automation**: Google Drive Ingestion Engine

## 📄 Documentation
- [Phase 2 Walkthrough](brain/7cb0d88f-1565-4f0d-9cf2-8238943bfb50/walkthrough.md)
- [Deployment Strategy](brain/7cb0d88f-1565-4f0d-9cf2-8238943bfb50/deployment_plan.md)
- [Docker Implementation](brain/7cb0d88f-1565-4f0d-9cf2-8238943bfb50/docker_implementation_plan.md)
