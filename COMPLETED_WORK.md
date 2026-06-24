# PhysioSync (PhysioLink) - Project Status Report

Based on the current state of the repository, here is a detailed breakdown of what has been completed across the different implementation phases of the project.

## Phase 1: Infrastructure & Scaffolding ✅ (Completed)
- **Mono-repo Structure**: The project is structured with `frontend`, `services` (Spring Boot), `ml-service` (Python), and `infra` (Docker/Ansible) directories.
- **Docker Compose Setup**: Both `docker-compose.yml` and `docker-compose.dev.yml` are set up to orchestrate the application stack.
- **Database Schema (`infra/db/init.sql`)**: Fully implemented PostgreSQL schema including:
  - `users` table with roles (PATIENT, PHYSIO).
  - `patients`, `exercises`, `routines`, and a join table `routine_exercises`.
  - `session_logs` for tracking patient progress (reps, angle accuracy).
  - `recovery_plans` to store ML-generated plans.
  - `appointments` table for scheduling.
  - Initial seed data for a physio and a patient, along with 5 standard exercises.

## Phase 2: Spring Boot Microservices ✅ (Fully Implemented)
- **`api-gateway`**: `JwtAuthFilter` (reactive WebFlux) validates Bearer tokens and injects `X-User-Id` / `X-User-Role` headers downstream.
- **`patient-service`**: Full auth flow — `User`/`Patient` JPA entities, `BCryptPasswordEncoder`, JWT issuance (`JwtUtil`), `AuthController` (register/login), `PatientController` (profiles).
- **`exercise-service`**: `Exercise`, `Routine`, `SessionLog` (JSONB repAngles), `RecoveryPlan` (JSONB) entities; full CRUD controllers; Kafka `SessionEventProducer` publishes `session.completed` after every session.
- **`appointment-service`**: `Appointment` FSM (SCHEDULED → CONFIRMED → COMPLETED / CANCELLED); `ReminderService` scheduled job with Redis deduplication for email reminders; `EmailService` via JavaMailSender.
- **`video-service`**: WebSocket signaling handler for WebRTC (offer/answer/ICE candidate routing); `RecordingService` generates MinIO pre-signed upload/download URLs; `VideoController` exposes ICE config and recording URL endpoints.
- All services use a shared `GatewayHeaderFilter` pattern to read gateway-injected headers and populate Spring Security context.

## Phase 3: ML Service ✅ (Implemented)
- **FastAPI Foundation**: The FastAPI application is fully built (`ml-service/app/main.py`) with CORS middleware and lifecycle hooks.
- **Pose Estimation Module**: Implemented MediaPipe-based pose estimation, joint angle scoring (`scorer.py`), and a high-throughput `/ws/pose` WebSocket handler.
- **LangChain RAG Pipeline**: Implemented a Retrieval-Augmented Generation pipeline (`rag_chain.py`, `plan_generator.py`) to fetch medical context and dynamically generate recovery plans via the `/api/ask` endpoint.
- **Event Driven Processing**: A Kafka consumer is implemented (`consumer.py`) to listen for backend events.

## Phase 4: Frontend ✅ (Fully Implemented)
- **Tech Stack**: React 18, TypeScript, Vite, TailwindCSS with Inter + JetBrains Mono fonts.
- **API Services**: `authService.ts`, `patientService.ts`, `exerciseService.ts`, `appointmentService.ts` using a shared Axios instance with JWT interceptor.
- **Custom Hooks**: `useAuth` (JWT + localStorage), `useWebSocket` (auto-reconnecting ML pose stream), `useAsync` (loading/error state).
- **UI Components**: `Button`, `Card`, `Input`, `Badge`, `Spinner`, `Modal`, `Toast` (notification system with context).
- **Layout**: `Header` with role-aware navigation and logout.
- **Pages**:
  - `LoginPage` — glassmorphism design, demo credentials shown.
  - `RegisterPage` — role selection (PATIENT / PHYSIO).
  - `PatientDashboard` — stats, active routines, progress line chart, upcoming appointments, latest recovery plan.
  - `SessionPage` — live webcam, Canvas 2D pose overlay, real-time HUD (rep count, accuracy, angle), session log auto-save.
  - `PhysioDashboard` — patient list with search, session history, recovery plans, assign routine modal.
- **App.tsx**: Full routing with `PrivateRoute` guards by role, `ToastProvider` wrapping the tree.

## Phase 5: WebRTC Video Consultation ✅ (Implemented)
- **`VideoConsultationPage`**: Full WebRTC peer connection using `RTCPeerConnection`.
  - Joins the signaling WebSocket room on load.
  - Creates/responds to offers, exchanges ICE candidates.
  - Fetches STUN/TURN config from `video-service /api/video/ice-config`.
  - Mic and camera toggle controls; hang-up button.

## Phase 6: DevOps ✅ (Scaffolded & Ready)
- `infra/jenkins/Jenkinsfile`: Full CI/CD pipeline (test → build → push → deploy via Ansible).
- `infra/ansible/`: Playbooks for server provisioning and Docker Compose deployment.
- `infra/docker-compose.yml`: Full stack orchestration ready to `docker compose up --build`.

## Phase 7: Polish ✅ (Implemented)
- **Toast Notifications**: Global `ToastProvider` with success/error/info toasts and auto-dismiss.
- **Loading States**: `Spinner` component used throughout; `useAsync` hook for consistent API loading states.
- **Error Handling**: Axios 401 interceptor redirects to login; `@ExceptionHandler` in Spring controllers returns structured error JSON.
- **Responsive Layout**: TailwindCSS grid/flex layouts work across mobile and desktop.

---

## 🔴 What's Missing / Remaining

The following items are **not yet implemented** or are **stubbed/incomplete** as of the last commit. Prioritized by impact.

### 🔴 P1 — High Impact (Blockers)

#### 1. RAG Knowledge Base is Empty
- `ml-service/knowledge_base/` contains only a `.gitkeep` placeholder — **no actual clinical documents**.
- The `embedder.py` script needs source PDFs/TXTs to build ChromaDB vectors.
- **Impact**: The `/api/ask` recovery plan generation endpoint returns no meaningful results without embedded documents.
- **Fix**: Add physiotherapy clinical guidelines (PDF or TXT) to `knowledge_base/` and run `python -m app.langchain.embedder`.

#### 2. Missing `sessionService.ts`
- `README.md` references `sessionService.ts` in `frontend/src/services/`, but the file does not exist.
- Session-related API calls (e.g., saving a session log, fetching session history) have no dedicated service layer.
- **Fix**: Create `frontend/src/services/sessionService.ts` with endpoints for `POST /api/exercises/sessions` and `GET /api/exercises/sessions/{patientId}`.

### 🟡 P2 — Medium Impact (Missing Features)

#### 3. Missing Frontend Pages
- **Patient Appointment Booking Page**: Patients have no UI to book appointments — only the physio-side view exists.
- **Patient Session History Detail Page**: `PhysioDashboard` lists sessions but there is no dedicated drilldown page per patient/session.

#### 4. Docker Build Not Validated End-to-End
- Individual Spring Boot service `Dockerfile`s exist but `docker compose up --build` has not been tested against the full stack.
- The Jenkins pipeline (`Jenkinsfile`) has never actually run against CI — no green build badge.
- **Fix**: Run `docker compose -f infra/docker-compose.yml up --build` locally and resolve any image build or networking issues.

### 🟢 P3 — Lower Impact (Quality / Ops)

#### 5. Zero Tests Across the Entire Stack
- No unit tests in any Spring Boot service (`src/test/` directories are empty stubs).
- No `pytest` tests for the ML service (pose scorer, RAG chain).
- No frontend tests (no Vitest/Jest configuration).
- **Fix**: At minimum, add auth flow tests for `patient-service` and angle scoring tests for `ml-service/app/pose/scorer.py`.

#### 6. `.env.example` Not Documented
- `.env.example` lists variables but provides no descriptions or instructions for which secrets are mandatory to run the stack.
- Required secrets include: Gemini API key, JWT secret, mail credentials (host/port/user/pass), MinIO access/secret keys.
- **Fix**: Annotate each variable in `.env.example` with an inline comment explaining its purpose and where to obtain it.

---

## 📋 Remaining Work Summary

| # | Item | Priority | Effort |
|---|------|----------|--------|
| 1 | Add clinical docs to RAG knowledge base + re-embed | 🔴 High | Low |
| 2 | Create `sessionService.ts` | 🔴 High | Low |
| 3 | Patient appointment booking page | 🟡 Medium | Medium |
| 4 | Patient session history detail page | 🟡 Medium | Medium |
| 5 | Validate full Docker Compose build | 🟡 Medium | Medium |
| 6 | Write unit tests (backend + ML + frontend) | 🟢 Low | High |
| 7 | Annotate `.env.example` with descriptions | 🟢 Low | Low |
