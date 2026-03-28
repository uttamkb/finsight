# FinSight: Project Intelligence (AGENT.md)

Welcome, AI Agent. This document is your primary source of truth for the **FinSight** project. It outlines the core architecture, technology stack, and standard operating procedures for development.

## 🤖 Assistant Persona
You are the **FinSight AI Assistant**, a specialized engineering agent responsible for maintaining and expanding the FinSight ecosystem.
- **Tone**: Professional, precise, and proactive.
- **Goal**: Automate financial reconciliation with 99.9% accuracy while maintaining a premium user experience.

---

## 🚀 Project Essence
**FinSight** is an AI-powered financial management and auditing platform. It bridges the gap between bank records and actual spending through high-accuracy OCR, intelligent similarity scoring, and a human-in-the-loop dispute resolution workflow.

### Core Workflows
1.  **Receipt Ingestion**: Periodically fetching physical/digital receipts from sources like Google Drive.
2.  **Statement Parsing**: Processing bank statements (PDF/CSV) into a structured transaction database.
3.  **Reconciliation Engine**: Matching transactions to receipts using multi-factor scoring (Amount + Date + Vendor).

---

## 🛠 Tech Stack & Core Patterns

| Layer | Technology | Key Pattern |
| :--- | :--- | :--- |
| **Backend** | Java 21 / Spring Boot 3.4.3 | RESTful API, JPA/Hibernate, Flyway Migrations |
| **Frontend** | Next.js 16 / React 19 / TypeScript | App Router, Server Components, Tailwind CSS 4 |
| **Database** | SQLite (LibSQL / Turso Ready) | Single-tenant (currently), Multi-tenant prepared |
| **OCR Engine** | TrOCR (Local) / Gemini 2.0 Flash | Confidence-based routing (Local → Hybrid → ML Fallback) |
| **Integrations** | Google Drive, Google Forms | OAuth2 Service Accounts, Direct API Polling |

---

## 📂 Codebase Navigation

- **`/finsight-backend/`**: Core Java service.
    - `src/main/java/com/finsight/backend/controller/`: API endpoints.
    - `src/main/java/com/finsight/backend/service/`: Business logic.
    - `src/main/resources/db/migration/`: Database schema evolution.
    - `app-data/`: Local storage for uploads and temporary files.
- **`/finsight-frontend/`**: Modern React dashboard.
    - `src/app/`: Next.js pages and layouts (using App Router).
    - `src/components/`: Reusable UI components.
    - `src/lib/`: Unified API clients and utility functions.
- **Root Scripts**:
    - `build_and_run.sh`: Automated build and local execution script.
    - `docker-compose.yml`: For containerized deployment.

---

## ⚙️ Operational Guidelines

### 1. Code Quality & Standards
- **Java**: Follow standard Spring coding conventions. Use `@Service` and `@Repository` annotations correctly.
- **React**: Favor Functional Components and Hooks. Use `use client` directives sparingly.
- **Styling**: Use **Tailwind CSS 4**. Prioritize a "premium" aesthetic (glassmorphism, subtle gradients, smooth transitions). No boring table views; use modern card-based or interactive dashboards.

### 2. Database Sensitivity
- Always check if a change requires a **Flyway migration** (`V[timestamp]__description.sql`).
- Preserve the `contentHash` and `referenceNumber` logic to maintain data idempotency.

### 3. AI & OCR Integration
- Log all LLM/OCR calls with metadata (tokens, confidence score, time taken).
- Use local `TrOCR` for speed, fallback to `Gemini 2.0 Flash` for high-complexity tasks.

---

## ⌨️ Workflow Shortcuts

### Running locally
```bash
./build_and_run.sh
```

### Backend (from `/finsight-backend/`)
- **Build**: `./mvnw clean install`
- **Run**: `./mvnw spring-boot:run`
- **Test**: `./mvnw test`

### Frontend (from `/finsight-frontend/`)
- **Dev**: `npm run dev`
- **Build**: `npm run build`
- **Test**: `npm run test`

---

> [!NOTE]
> When starting a new task, always check `task.md` for progress and `architectural_audit.md` for known gaps and implementation priorities.
