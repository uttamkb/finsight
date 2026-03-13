# Product Specification: FinSight (Sowparnika Sanvi)

## 1. Product Vision
**FinSight** is an AI-powered financial management and auditing platform designed to automate the painful process of reconciling bank statements with physical/digital receipts. It bridges the gap between bank records and actual spending through high-accuracy OCR, intelligent similarity scoring, and a human-in-the-loop dispute resolution workflow.

## 2. Core Modules & Features

### 2.1. Automated Data Ingestion
*   **Google Drive Sync:** Direct integration with GDrive to fetch receipts automatically.
*   **Bank Statement Upload:** Support for PDF and CSV formats, including specialized parsing for major Indian banks (e.g., ICICI).
*   **Content Hashing:** Prevents duplicate processing by tracking MD5 checksums of files.

### 2.2. Intelligent OCR Engine (PaddleOCR + Gemini)
*   **3-Mode Routing:**
    - **Low-Cost:** Fast local processing with PaddleOCR.
    - **Hybrid:** Local processing with a 75% confidence threshold; triggers Gemini AI fallback for low-confidence fields.
    - **High-Accuracy:** Direct processing through Gemini 2.0 Flash for maximum precision.
*   **Self-Learning Pipeline:** Harvesting "Golden Samples" from user corrections to fine-tune local models and keywords.

### 2.3. Reconciliation Engine
*   **100-Point Similarity Scoring:** Matches bank transactions to receipts based on:
    - Amount (50 pts)
    - Date Proximity (30 pts)
    - Vendor Name Similarity (20 pts - Levenshtein Distance)
*   **Discrepancy UI:** A dedicated interface to review and manually resolve AI-flagged mismatches (e.g., Amount Mismatch, No Receipt Found).

### 2.4. Dashboard & Analytics
*   **Financial Summary:** Monthly Income vs. Expense trends.
*   **Vendor Analytics:** Top vendors by volume and transaction frequency.
*   **Anomaly Detection:** Real-time flagging of duplicates or fraudulent entries.
*   **System Health:** Monitoring OCR performance and sync logs.

## 3. Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Backend** | Java 17 / Spring Boot 3.x |
| **Frontend** | Next.js 14 / TypeScript / Tailwind CSS |
| **Database** | SQLite (Development) / LibSQL (Cloud Ready) |
| **OCR/Vision** | Python 3.12 / PaddleOCR / OpenCV |
| **AI/LLM** | Google Gemini 2.0 Flash (Vertex AI / AI Studio) |
| **Security** | Spring Security / Service Account Integration |

## 4. User Personas
1.  **Individual User:** Wants to track personal spend and ensure all bank debits have corresponding receipts.
2.  **Apartment Association Auditor:** (e.g., Sowparnika Sanvi Phase 1) Needs to reconcile vendor payments against bank withdrawals and detect anomalies.
3.  **Small Business Owner:** Needs a high-level dashboard of revenue vs. burn rate with automated categorization.

## 5. Deployment Roadmap
*   **Phase 1 (Local):** Single-machine deployment using bundled Python venv and SQLite.
*   **Phase 2 (Cloud):** Dockerized microservices deployed on Railway/Fly.io/Oracle Cloud with Turso DB.
*   **Phase 3 (SaaS):** Multi-tenant architecture with specialized AI workers and tiered OCR pricing.
