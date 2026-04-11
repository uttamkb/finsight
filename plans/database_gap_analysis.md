# Database Migration Gap Analysis: SQLite to Turso/LibSQL

## 1. Executive Summary
The FinSight project currently utilizes **SQLite** as its primary database engine for the `local` profile. While SQLite is excellent for development and single-user scenarios, the project's architecture (supporting a `cloud` profile with Turso) suggests a transition toward a distributed, network-accessible database is planned. This report analyzes the gaps between the current file-based approach and a managed database service.

## 2. Current State vs. Target State

| Feature | Current State (SQLite) | Target State (Turso/LibSQL) | Gap/Impact |
| :--- | :--- | :--- | :--- |
| **Storage Model** | Local file (`finsight.db`) | Managed Cloud/Edge Database | **High**: Moves from local disk dependency to network-based access. |
| **Concurrency** | Single-writer (Database-level locking) | High concurrency (Multi-user/Multi-connection) | **Critical**: Essential for scaling to multiple users/instances. |
| **Scalability** | Vertical (Limited by local disk/CPU) | Horizontal/Edge (Distributed) | **High**: Enables global/edge deployment. |
| **Data Integrity** | ACID compliant (Local) | ACID compliant (Distributed/Managed) | **Low**: Both provide strong consistency. |
| **Availability** | Dependent on local file/volume persistence | High availability via managed service | **Medium**: Reduces risk of data loss due to local disk failure. |
| **Accessibility** | Restricted to the host machine/container | Accessible via HTTP/JDBC from anywhere | **High**: Enables decoupled frontend/backend/edge architectures. |

## 3. Detailed Gap Analysis

### 3.1 Concurrency & Multi-tenancy
*   **Gap**: SQLite's locking mechanism can lead to `SQLITE_BUSY` errors during high-frequency write operations (e.g., during heavy receipt ingestion or automated sync runs).
*   **Impact**: As the number of `tenant_id` entries grows and concurrent sync processes (via `DriveSyncService`) increase, the file-based approach will become a bottleneck.

###   3.2 Deployment & Scalability
*   **Gap**: The current setup relies on a local file. In a containerized (Docker) or serverless environment, the database file must be persisted via volumes, which is difficult to scale across multiple nodes.
*   **Impact**: Moving to Turso allows the backend to be stateless, making it much easier to deploy on platforms like AWS, Google Cloud, or Vercel/Edge functions.

### 3.3 Data Persistence & Disaster Recovery
*   **Gap**: Backing up a SQLite file requires manual file-system level snapshots or `VACUUM INTO` commands.
*   **Impact**: Turso provides managed backups, point-in-time recovery, and replication out of the box, significantly reducing the operational burden of the `BackupController`.

## 4. Migration Impact Assessment

### 4.1 Technical Effort
*   **Low**: The codebase is already prepared for this transition. The `application.yml` contains a `cloud` profile specifically for Turso.
*   **Flyway**: Database migrations are already managed via Flyway, ensuring schema consistency across both environments.

### 4.2 Infrastructure & Cost
*   **Medium**: Transitioning to Turso introduces a dependency on an external service and requires managing `TURSO_AUTH_TOKEN`.
*   **Cost**: While Turso has a generous free tier, costs will scale with the number of database operations and storage usage.

### 4.3 Operational Complexity
*   **Low**: The primary complexity shift is from managing local file backups to managing cloud credentials and network latency/connectivity.

## 5. Recommendation
**Proceed with the migration to Turso/LibSQL for any production or multi-user environment.**

The current architecture is already "migration-ready." The transition will unlock the ability to scale the `ReconciliationEngine` and `DriveSyncService` across distributed environments, which is critical for the long-term vision of FinSight as a multi-tenant platform.
