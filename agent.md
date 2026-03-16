# FinSight: Agent Context

This document provides essential context and guidelines for AI agents working on the FinSight project.

## 🚀 Project Overview
**FinSight** is an AI-powered financial management platform designed to automate the reconciliation of bank statements with physical/digital receipts. It uses high-accuracy OCR (TrOCR + Gemini) and intelligent similarity matching.

## 🛠 Tech Stack
| Component | Technology |
| :--- | :--- |
| **Backend** | Java 21 / Spring Boot 3.4.3 |
| **Frontend** | Next.js 16.1.6 / React 19 / Tailwind CSS 4 |
| **Database** | SQLite (LibSQL / Turso ready) |
| **OCR Engine** | TrOCR (Local) + Google Gemini 2.0 Flash (Fallback) |
| **Integrations** | Google Drive (Receipt Ingestion) |

## 📂 Project Structure
- `/finsight-backend/`: Core API, OCR processing logic, and database migrations (Flyway).
- `/finsight-frontend/`: Next.js application for dashboard and reconciliation UI.
- `build_and_run.sh`: Main script to build and start both backend and frontend.
- `docker-compose.yml`: Configuration for containerized deployment.
- `ProductSpec.md`: Detailed product vision and feature requirements.

## ⚙️ Development Workflows
### Local Development
To start the entire system (Backend, Frontend, AI Engine):
```bash
./build_and_run.sh
```

### Backend Commands
- Build: `./mvnw clean install` (inside `/finsight-backend/`)
- Run: `./mvnw spring-boot:run`

### Frontend Commands
- Dev: `npm run dev` (inside `/finsight-frontend/`)
- Build: `npm run build`

## 🤖 Agent Best Practices
- **Atomic Edits**: Focus on one component at a time (e.g., Backend vs. Frontend).
- **SQLite Sensitivity**: When modifying JPA entities, ensure Flyway migrations are updated or the database is compatible.
- **AI Logging**: Monitor `backend.log` for OCR processing and Gemini API interactions.
- **Premium UI**: Frontend changes should prioritize a premium, modern aesthetic using the defined Tailwind/CSS system.
