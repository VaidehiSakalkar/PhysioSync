# PhysioSync (PhysioLink) — Complete Technical Guide

> A full-stack, AI-powered remote physiotherapy platform. Patients do real-time exercise sessions with pose tracking, physiotherapists manage recovery plans, and an AI backend generates personalised rehab programmes.

---

## Table of Contents

1. [What This Project Does](#1-what-this-project-does)
2. [High-Level Architecture](#2-high-level-architecture)
3. [How a Request Flows Through the System](#3-how-a-request-flows-through-the-system)
4. [Directory Structure](#4-directory-structure)
5. [Service-by-Service Breakdown](#5-service-by-service-breakdown)
   - [API Gateway](#51-api-gateway)
   - [Patient Service](#52-patient-service)
   - [Exercise Service](#53-exercise-service)
   - [Appointment Service](#54-appointment-service)
   - [Video Service](#55-video-service)
   - [ML Service](#56-ml-service-python--fastapi)
   - [Frontend](#57-frontend-react--typescript)
6. [Infrastructure Components](#6-infrastructure-components)
7. [Database Schema](#7-database-schema)
8. [Key Workflows — End-to-End](#8-key-workflows--end-to-end)
   - [Login & JWT Auth](#81-login--jwt-auth)
   - [Live Exercise Session (Pose Tracking)](#82-live-exercise-session-pose-tracking)
   - [AI Recovery Plan Generation](#83-ai-recovery-plan-generation)
   - [Video Consultation](#84-video-consultation)
   - [Appointment Booking](#85-appointment-booking)
9. [Prerequisites](#9-prerequisites)
10. [Running Locally (Dev Mode)](#10-running-locally-dev-mode)
11. [Environment Variables Reference](#11-environment-variables-reference)
12. [Demo Credentials](#12-demo-credentials)
13. [Running Tests](#13-running-tests)
14. [Docker Compose (Full Stack)](#14-docker-compose-full-stack)
15. [Where to Start Reading the Code](#15-where-to-start-reading-the-code)

---

## 1. What This Project Does

PhysioSync is a **remote physiotherapy platform** with two kinds of users:

| Role | What they do |
|------|-------------|
| **Patient** | Logs in, selects an exercise from their assigned routine, opens their webcam, and does the exercise while the camera tracks their joint angles in real time and counts reps. After each session the AI generates a personalised recovery plan. They can also book video appointments with their physio. |
| **Physiotherapist** | Logs in, views their patient list, reviews session history, creates exercise routines for patients, updates appointment statuses, and joins video consultations. |

The core tech trick: **the browser captures webcam frames, sends them as base64 JPEG images over a WebSocket to a Python server, which runs Google MediaPipe to detect 33 body landmarks, calculates joint angles, and streams back real-time feedback** — all at ~10 frames per second.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        BROWSER (React SPA)                       │
│  LoginPage · PatientDashboard · SessionPage · PhysioDashboard   │
│  AppointmentBookingPage · SessionHistoryPage · VideoPage         │
└───────────────┬──────────────────────────────┬──────────────────┘
                │ REST (Axios, port 8080)        │ WebSocket (pose/video)
                ▼                               ▼
┌──────────────────────┐            ┌───────────────────────────┐
│   API GATEWAY        │            │  ML SERVICE (Python 8000)  │
│ Spring Cloud Gateway │            │  FastAPI + MediaPipe        │
│ - JWT validation     │            │  /ws/pose  (pose tracking)  │
│ - Injects X-User-Id  │            │  /api/ask  (RAG Q&A)        │
│ - Routes to services │            │  ChromaDB vector store      │
└──┬───────────────────┘            └────────────┬──────────────┘
   │                                             │ Kafka events
   ├─► patient-service   (port 8081)             │
   ├─► exercise-service  (port 8082)  ◄──────────┘
   ├─► appointment-service (port 8083)
   └─► video-service     (port 8084)

All Spring Boot services share:
  PostgreSQL ──► single shared database (physiolink)
  Redis      ──► reminder deduplication + session caching
  Kafka      ──► async event bus (session.completed topic)
  MinIO      ──► S3-compatible object store for video recordings
```

**The key insight**: The API Gateway is the *only* service exposed to the internet. Every REST call from the browser goes to port 8080 (gateway), which validates the JWT and then forwards the request to the appropriate downstream service. The downstream services trust the gateway-injected headers and never see the raw JWT.

---

## 3. How a Request Flows Through the System

Here is what happens when a patient clicks "Start Session":

```
Browser (SessionPage.tsx)
  │
  │  1. Opens WebSocket directly to ML service port 8000 (bypasses gateway)
  │     ws://localhost:8000/ws/pose?exercise_id=<uuid>
  │
  │  2. Every 100ms: captures a webcam frame → draws to canvas → toDataURL('jpeg')
  │     → sends JSON: { "frame": "<base64 jpeg string>" }
  │
ML Service (ws_handler.py)
  │
  │  3. Decodes base64 → numpy array → cv2.imdecode
  │  4. Runs MediaPipe Pose → gets 33 body landmarks (x,y,z per point)
  │  5. Picks the 3 landmarks for the target joint (e.g. HIP, KNEE, ANKLE for knee)
  │  6. Computes angle using dot product formula
  │  7. Compares to exercise target range → calculates accuracy 0.0–1.0
  │  8. Tracks reps (hysteresis: angle enters range → exits range = 1 rep)
  │  9. Sends back: { angle, accuracy, feedback, repCount, landmarks }
  │
Browser (SessionPage.tsx)
  │
  │ 10. Draws green/amber/red dots at each landmark position on a canvas overlay
  │ 11. Updates HUD: rep counter, accuracy bar, live angle reading, feedback text
  │
  │ [Patient clicks "End Session"]
  │
  │ 12. Calls exerciseService.logSession() → POST /api/exercises/sessions (via gateway)
  │
API Gateway → Exercise Service
  │
  │ 13. Saves session_log row in PostgreSQL
  │ 14. Publishes "session.completed" event to Kafka
  │
ML Service Kafka Consumer (background thread)
  │
  │ 15. Consumes session.completed event
  │ 16. Fetches last 4 sessions from exercise-service
  │ 17. Sends condition + session history to Google Gemini Pro via LangChain
  │ 18. Gemini returns a 4-week rehab plan as JSON
  │ 19. Stores the plan in PostgreSQL via exercise-service
  │
Browser (PatientDashboard.tsx)
  │
  │ 20. Next time patient opens dashboard, latest recovery plan is shown
```

---

## 4. Directory Structure

```
PhysioSync/
├── frontend/                    # React 18 + TypeScript + Vite + TailwindCSS
│   └── src/
│       ├── pages/
│       │   ├── auth/            # LoginPage, RegisterPage
│       │   ├── patient/         # PatientDashboard, SessionPage,
│       │   │                    # AppointmentBookingPage, SessionHistoryPage
│       │   ├── physio/          # PhysioDashboard
│       │   └── video/           # VideoConsultationPage
│       ├── services/            # API layer (Axios wrappers)
│       │   ├── api.ts           # shared Axios instance with JWT interceptor
│       │   ├── authService.ts
│       │   ├── patientService.ts
│       │   ├── exerciseService.ts
│       │   ├── sessionService.ts    ← NEW (added in latest work)
│       │   └── appointmentService.ts
│       ├── hooks/
│       │   ├── useAuth.ts       # JWT read/write from localStorage
│       │   └── useWebSocket.ts  # auto-reconnecting ML pose WebSocket
│       └── components/
│           ├── layout/Header.tsx
│           └── ui/              # Button, Card, Badge, Input, Modal,
│                                # Spinner, Toast
│
├── services/                    # Spring Boot microservices (Java 21)
│   ├── api-gateway/             # Spring Cloud Gateway (reactive/WebFlux)
│   ├── patient-service/         # Auth + user profiles (port 8081)
│   ├── exercise-service/        # Exercises, routines, sessions, plans (port 8082)
│   ├── appointment-service/     # Appointments + email reminders (port 8083)
│   └── video-service/           # WebRTC signalling + MinIO recordings (port 8084)
│
├── ml-service/                  # Python 3.11 FastAPI
│   ├── app/
│   │   ├── main.py              # FastAPI app, startup hooks
│   │   ├── pose/
│   │   │   ├── estimator.py     # MediaPipe wrapper — decodes frame, returns landmarks
│   │   │   ├── scorer.py        # Angle maths + rep counter logic
│   │   │   └── ws_handler.py    # WebSocket session loop
│   │   ├── langchain/
│   │   │   ├── embedder.py      # One-off script: embed knowledge_base/ into ChromaDB
│   │   │   ├── rag_chain.py     # RAG: ChromaDB retriever + Gemini LLM → answer
│   │   │   └── plan_generator.py # LangChain LLMChain → 4-week rehab plan JSON
│   │   └── kafka/
│   │       └── consumer.py      # Listens to session.completed → triggers plan gen
│   ├── knowledge_base/
│   │   └── physio_clinical_guidelines.txt  ← NEW (evidence-based physio guidelines)
│   └── tests/
│       └── test_scorer.py       ← NEW (pytest tests for angle scoring)
│
├── infra/
│   ├── docker-compose.yml       # Full production stack
│   ├── docker-compose.dev.yml   # Infra only (postgres, redis, kafka, minio, chroma)
│   ├── db/
│   │   └── init.sql             # Schema + seed data (runs automatically)
│   ├── ansible/                 # Server provisioning playbooks
│   └── jenkins/Jenkinsfile      # CI/CD pipeline definition
│
├── .env.example                 # All environment variables documented
└── COMPLETED_WORK.md            # Detailed project status log
```

---

## 5. Service-by-Service Breakdown

### 5.1 API Gateway

**Location**: `services/api-gateway/`  
**Port**: 8080 (the only port the browser talks to for REST)  
**Technology**: Spring Cloud Gateway (reactive, non-blocking WebFlux)

**What it does**:
1. Every incoming request must have `Authorization: Bearer <JWT>` (except `/api/auth/**`)
2. `JwtAuthFilter.java` validates the token signature using the shared `JWT_SECRET`
3. If valid, it extracts the user's UUID and role from the JWT claims
4. It **mutates the request** by injecting two headers: `X-User-Id` and `X-User-Role`
5. The request is then forwarded to the correct downstream service based on path:
   - `/api/patients/**` → patient-service:8081
   - `/api/exercises/**` → exercise-service:8082
   - `/api/appointments/**` → appointment-service:8083
   - `/api/video/**` → video-service:8084

**Why this pattern matters**: Downstream services don't need to validate JWTs. They just read `X-User-Id` and `X-User-Role` headers (injected by the gateway). This is handled by `GatewayHeaderFilter.java` inside each service.

```java
// Every downstream service reads the user identity like this:
String userId = request.getHeader("X-User-Id");
String role   = request.getHeader("X-User-Role");
// These were injected by the gateway after JWT validation.
```

---

### 5.2 Patient Service

**Location**: `services/patient-service/`  
**Port**: 8081  
**Owns**: `users` table, `patients` table

**Key files**:
| File | What it does |
|------|-------------|
| `AuthController.java` | `POST /api/auth/register` and `POST /api/auth/login` — the only public endpoints |
| `AuthService.java` | Hashes passwords with BCrypt, issues JWTs via `JwtUtil` |
| `JwtUtil.java` | Builds JWT with `sub=userId`, `role=PATIENT\|PHYSIO`, signs with HMAC-SHA256 |
| `PatientController.java` | `GET /api/patients/me`, `PUT /api/patients/me`, `GET /api/physios/patients` |
| `UserService.java` | Business logic for profile lookups |

**JWT structure** (what's inside the token):
```
Header: { alg: "HS256" }
Payload: {
  sub: "uuid-of-user",
  role: "PATIENT",    ← or "PHYSIO"
  iat: 1234567890,
  exp: 1234654290
}
```

**Why patient-service issues JWTs**: It's the only service that owns the `users` table and passwords. All other services are consumers of the token, not issuers.

---

### 5.3 Exercise Service

**Location**: `services/exercise-service/`  
**Port**: 8082  
**Owns**: `exercises`, `routines`, `routine_exercises`, `session_logs`, `recovery_plans` tables

**Key endpoints**:
| Endpoint | Who calls it | Purpose |
|----------|-------------|---------|
| `GET /api/exercises` | Browser | List all exercises (optionally filter by joint) |
| `GET /api/exercises/{id}` | ML Service | Fetch exercise config (target angles) for scoring |
| `GET /api/exercises/routines/my` | Patient browser | Patient's assigned routines |
| `POST /api/exercises/routines` | Physio browser | Physio assigns a routine to a patient |
| `POST /api/exercises/sessions` | Browser (after session) | Save a completed session log |
| `GET /api/exercises/sessions/my/recent` | Browser | Patient's recent sessions |
| `GET /api/exercises/sessions/patient/{id}` | Physio browser | Physio views a patient's sessions |
| `GET /api/exercises/plans/my` | Patient browser | Patient's AI-generated recovery plans |

**Kafka publishing**: After `POST /api/exercises/sessions`, the exercise service publishes a `session.completed` Kafka event. The ML service consumes this event and generates a recovery plan asynchronously. The browser never waits for the plan — it gets the plan next time it loads the dashboard.

---

### 5.4 Appointment Service

**Location**: `services/appointment-service/`  
**Port**: 8083  
**Owns**: `appointments` table

**Appointment lifecycle (state machine)**:
```
SCHEDULED → CONFIRMED → COMPLETED
           ↘ CANCELLED
```

**Notable features**:

- **`ReminderService.java`**: A `@Scheduled` Spring job runs every 15 minutes. It queries appointments scheduled within the next 24 hours, checks Redis to see if a reminder was already sent (using key `reminder:sent:<appointment-id>`), and emails the patient. Redis acts as a deduplication store with a TTL — so even if the job runs 100 times, each patient only gets one email per appointment.

- **`EmailService.java`**: Uses Spring's `JavaMailSender` configured against `MAIL_HOST`/`MAIL_PORT`. In development this points to **MailHog** (a fake SMTP server with a web UI at `http://localhost:8025`).

- **`videoRoomId`**: When an appointment is created, a random UUID is generated as `video_room_id`. Both the patient and physio can navigate to `/video/<videoRoomId>` to join the WebRTC call.

---

### 5.5 Video Service

**Location**: `services/video-service/`  
**Port**: 8084

**What it does**:
1. Provides a WebSocket signalling server for WebRTC peer-to-peer video calls
2. Routes WebRTC offer/answer/ICE candidates between patient and physio in the same room
3. Exposes `GET /api/video/ice-config` — returns STUN/TURN server configuration
4. Uses MinIO pre-signed URLs for session recording upload/download

**How WebRTC works in this context**:
```
Patient Browser                    Video Service (WS)         Physio Browser
     │                                    │                         │
     │──── join room (roomId) ───────────►│                         │
     │                                    │◄──── join room ─────────│
     │                                    │                         │
     │──── RTCPeerConnection offer ──────►│──── forward offer ─────►│
     │                                    │                         │
     │◄─── RTCPeerConnection answer ──────│◄──── answer ────────────│
     │                                    │                         │
     │◄──────── ICE candidates ───────────┼──────────────────────── │
     │                                    │                         │
     │◄═══════ Direct P2P video stream ═══════════════════════════►│
```
Once ICE negotiation completes, video/audio flows **directly browser-to-browser** — not through the server. The video service only handles the initial handshake messages.

---

### 5.6 ML Service (Python + FastAPI)

**Location**: `ml-service/`  
**Port**: 8000  
**Technology**: Python 3.11, FastAPI, MediaPipe, LangChain, ChromaDB, Kafka

This is the most interesting service. It has three independent subsystems:

#### A. Real-time Pose Estimation (`/ws/pose`)

The WebSocket handler (`ws_handler.py`) runs this loop for each connected patient:

```python
# Simplified flow inside handle_session():
exercise = fetch_exercise_config(exercise_id)   # GET from exercise-service

while True:
    raw = await websocket.receive_text()         # {"frame": "<base64 jpeg>"}
    frame_result = process_frame(raw["frame"])   # MediaPipe: 33 landmarks
    
    if not frame_result["detected"]:
        send({"detected": False, "repCount": rep_count})
        continue
    
    score = score_frame(frame_result["landmarks"], exercise)
    # score = {angle, accuracy, feedback, inRange}
    
    # Hysteresis rep counter:
    # A "rep" = angle enters target range, then exits
    if score["inRange"] and not in_rep:
        in_rep = True
    elif not score["inRange"] and in_rep:
        in_rep = False
        rep_count += 1   # ← counts a completed rep
    
    send({**score, "repCount": rep_count, "landmarks": ...})
```

**How joint angles are calculated** (`scorer.py`):

For a knee exercise, we take three landmarks: HIP (23), KNEE (25), ANKLE (27).

```
         HIP (a)
          │
          │ vector ba
          │
        KNEE (b) ←── angle measured HERE
          │
          │ vector bc
          │
        ANKLE (c)
```

```python
ba = a - b   # vector from knee to hip
bc = c - b   # vector from knee to ankle
cosine = dot(ba, bc) / (|ba| × |bc|)
angle = degrees(arccos(cosine))
```

A knee squat target might be `70°–110°`. When the patient's knee angle is in that range, accuracy = 1.0 and the system counts "in rep". When they straighten (angle goes back above 110°), that counts as one completed rep.

#### B. RAG Q&A (`/api/ask`)

**RAG = Retrieval Augmented Generation**. Instead of relying purely on the LLM's training data, we:

1. First **embed** (pre-process) a knowledge base of physiotherapy clinical guidelines into ChromaDB (a vector database) — this is a one-time setup step
2. When a patient asks a question, we **retrieve** the most relevant text chunks from ChromaDB
3. We feed those chunks + the question to **Google Gemini Pro** as context
4. Gemini answers using the retrieved evidence rather than hallucinating

```
Patient question: "How many reps should I do for my knee?"
        │
        ▼
ChromaDB: find 5 most similar text chunks from clinical_guidelines.txt
        │
        ▼
Gemini: "Based on the guidelines, early knee rehab: 3×15 reps at sub-maximal load..."
```

#### C. Recovery Plan Generation (Kafka consumer)

When a session completes, the ML service gets notified via Kafka. It then:
1. Fetches the patient's last 4 session logs (avg accuracy per exercise)
2. Sends this + their medical condition to Gemini with a structured prompt
3. Gemini returns a JSON 4-week progressive rehab plan
4. The plan is saved to PostgreSQL via exercise-service

---

### 5.7 Frontend (React + TypeScript)

**Location**: `frontend/`  
**Technology**: React 18, TypeScript, Vite, TailwindCSS, React Router, Chart.js, Lucide icons

#### Routing (App.tsx)

```
/login               → LoginPage
/register            → RegisterPage
/patient             → PatientDashboard      (PATIENT only)
/patient/session/:id → SessionPage           (PATIENT only)
/patient/appointments→ AppointmentBookingPage (PATIENT only)
/patient/history     → SessionHistoryPage    (PATIENT only)
/physio              → PhysioDashboard       (PHYSIO only)
/video/:roomId       → VideoConsultationPage (any role)
```

`PrivateRoute` reads `physiolink_token` and `physiolink_role` from `localStorage`. If the token is missing or the role doesn't match, it redirects to `/login`.

#### API Layer (`services/`)

All REST calls go through `api.ts` — a shared Axios instance:
```typescript
// api.ts — adds JWT to every request automatically
api.interceptors.request.use(config => {
    const token = localStorage.getItem('physiolink_token')
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
})

// On 401: clear auth and redirect to login
api.interceptors.response.use(null, error => {
    if (error.response?.status === 401) {
        localStorage.clear()
        window.location.href = '/login'
    }
})
```

#### WebSocket Hook (`hooks/useWebSocket.ts`)

The `useWebSocket` hook manages the connection to the ML pose service. It:
- Opens `ws://ML_URL/ws/pose?exercise_id=<id>` when `sessionActive` becomes true
- Automatically reconnects if the connection drops
- Calls `onFrame(data)` with every message received from the ML service

---

## 6. Infrastructure Components

| Component | Docker Image | Port | Purpose |
|-----------|-------------|------|---------|
| **PostgreSQL** | `postgres:15-alpine` | 5432 | Single shared database for all services |
| **Redis** | `redis:7-alpine` | 6379 | Appointment reminder deduplication |
| **Zookeeper** | `confluentinc/cp-zookeeper:7.5.0` | 2181 | Kafka coordination |
| **Kafka** | `confluentinc/cp-kafka:7.5.0` | 9092 | Event bus (`session.completed` topic) |
| **MinIO** | `minio/minio:latest` | 9000/9001 | S3-compatible file storage for exercise videos and recordings |
| **ChromaDB** | `chromadb/chroma:latest` | 8001 (dev) | Vector database for RAG |
| **MailHog** | `mailhog/mailhog:latest` | 1025/8025 | Dev-only fake SMTP with web UI |

---

## 7. Database Schema

All tables live in the `physiolink` PostgreSQL database. Here's the entity relationship:

```
users (id, email, password_hash, role, name, phone, ...)
  │
  ├──► patients (id→users.id, medical_history, assigned_physio_id→users.id)
  │                              │
  │                              └── "which physio manages this patient"
  │
  ├──► exercises (id, name, target_joint, target_angle_min, target_angle_max,
  │               target_reps, target_sets, ...)
  │
  ├──► routines (id, patient_id→users, physio_id→users, name, active)
  │         │
  │         └──► routine_exercises (routine_id, exercise_id, order_index)
  │                                 [many-to-many join table]
  │
  ├──► session_logs (id, patient_id, exercise_id, reps_completed,
  │                  avg_angle_accuracy, rep_angles JSONB, duration_seconds)
  │                  │
  │                  └── rep_angles stores the per-rep angle readings as JSON array
  │                      e.g. [87.3, 91.2, 88.5, ...]
  │
  ├──► recovery_plans (id, patient_id, plan_json JSONB, progression_notes,
  │                    red_flags JSONB, session_log_id)
  │                    │
  │                    └── plan_json has the 4-week plan from Gemini
  │
  └──► appointments (id, patient_id, physio_id, scheduled_at, duration_minutes,
                     status, notes, video_room_id)
```

**Seed data** (auto-inserted at first DB startup):
- **Physio**: `physio@physiolink.dev` / `password`
- **Patient**: `patient@physiolink.dev` / `password`
- Patient is pre-assigned to the physio
- 5 exercises pre-loaded (Squat, Knee Extension, Shoulder Raise, Arm Curl, Hip Abduction)

---

## 8. Key Workflows — End-to-End

### 8.1 Login & JWT Auth

```
1. User submits email + password on LoginPage
2. authService.ts → POST /api/auth/login (gateway passes this through unauthenticated)
3. patient-service: finds user by email, BCrypt.matches(password, hash)
4. JwtUtil.generateToken(userId, role) → compact JWT string
5. Response: { token, userId, name, role }
6. useAuth.ts: saves all 4 values to localStorage
7. Browser navigates to /patient or /physio based on role
8. All future Axios calls include: Authorization: Bearer <token>
```

### 8.2 Live Exercise Session (Pose Tracking)

```
1. Patient opens PatientDashboard → clicks "Start" next to an exercise
2. Browser navigates to /patient/session/<exerciseId>
3. SessionPage loads exercise config (name, target joint, target angles)
4. Patient clicks "Start Session":
   a. getUserMedia() — requests webcam permission
   b. useWebSocket opens ws://localhost:8000/ws/pose?exercise_id=<id>
   c. setInterval every 100ms:
      - draw webcam frame to hidden canvas
      - canvas.toDataURL('image/jpeg', 0.6) → base64
      - sendFrame(base64) → WebSocket message to ML service
5. ML service processes each frame:
   - Decode base64 → image → MediaPipe landmarks
   - Calculate joint angle for target joint
   - Score accuracy vs target range
   - Count reps via hysteresis
   - Return { angle, accuracy, feedback, repCount, landmarks }
6. SessionPage receives data:
   - Updates rep counter (big number display)
   - Updates accuracy percentage + progress bar
   - Draws coloured dots at landmark positions on canvas overlay
   - Shows live feedback text ("Great form!" / "Bend more — target 70°–110°, current 145°")
7. Patient clicks "End Session":
   - Stops webcam + WebSocket
   - Calculates average accuracy from history
   - POST /api/exercises/sessions → saves to DB
   - Toast notification: "Session saved! 12 reps — 84% accuracy"
   - Redirects to /patient after 2 seconds
```

### 8.3 AI Recovery Plan Generation

```
[Runs asynchronously after session.completed event]

1. Exercise service publishes to Kafka:
   { topic: "session.completed", value: { patientId, sessionLogId, condition } }

2. ML service Kafka consumer (background thread) wakes up:
   a. GET /api/exercises/sessions/<patientId>?limit=4  → last 4 sessions
   b. GET /api/exercises/routines/<patientId>           → current exercises

3. LangChain LLMChain sends prompt to Gemini Pro:
   "You are an expert physiotherapist. Patient condition: Left knee ACL reconstruction.
    Last sessions: [{avgAccuracy: 0.82, exercise: "Squat"}, ...]
    Current exercises: [...]
    Return ONLY valid JSON: {weeklyPlan: [...], progressionNotes: "...", redFlags: [...]}"

4. Gemini returns structured 4-week plan JSON

5. ML service → PUT /api/exercises/plans/<patientId> → stored in recovery_plans table

6. Next time patient opens PatientDashboard:
   GET /api/exercises/plans/my → fetches and displays the plan
```

### 8.4 Video Consultation

```
1. Physio books/confirms an appointment → it gets a videoRoomId UUID
2. Both patient and physio navigate to /video/<videoRoomId>
3. VideoConsultationPage:
   a. GET /api/video/ice-config → gets STUN server address
   b. new RTCPeerConnection({ iceServers: [...] })
   c. Opens WebSocket to video-service: ws://localhost:8084/ws/signal/<roomId>
4. First to join sends "offer" SDP via WebSocket
5. Second to join receives offer → creates answer → sends back
6. ICE candidates are exchanged via WebSocket relay
7. Once negotiation completes: direct P2P video/audio stream established
8. Mic/camera toggle buttons mute tracks without closing the connection
```

### 8.5 Appointment Booking

```
1. Patient navigates to /patient/appointments
2. AppointmentBookingPage loads:
   - GET /api/patients/me → checks assignedPhysioId
   - GET /api/appointments/my → loads existing appointments
3. Patient clicks "Book Appointment":
   - Date/time picker (minimum 30 min from now)
   - Duration: 30 / 45 / 60 min
   - Optional notes
4. Confirm → POST /api/appointments with:
   { physioId, scheduledAt, durationMinutes, notes }
5. Appointment created with status=SCHEDULED, videoRoomId=random-uuid
6. ReminderService runs every 15 min:
   - Finds appointments in next 24 hours
   - Checks Redis: "reminder:sent:<apptId>" — if exists, skip
   - Sends email, sets Redis key with 25hr TTL
7. Physio opens PhysioDashboard → can CONFIRM or CANCEL appointment
8. On appointment time: both parties open /video/<videoRoomId>
```

---

## 9. Prerequisites

Make sure you have all of these installed before running:

| Tool | Version | Why |
|------|---------|-----|
| **Java** | 21 (LTS) | Spring Boot services |
| **Maven** | 3.9+ | Build Spring Boot services |
| **Python** | 3.11 | ML service |
| **Node.js** | 18+ | Frontend (Vite dev server) |
| **Docker Desktop** | Latest | Run infrastructure (postgres, redis, etc.) |
| **Git** | Any | Clone the repo |

Check your versions:
```bash
java -version      # should say "21"
mvn -v
python3 --version  # should say "3.11.x"
node -v            # should say "18.x" or higher
docker -v
```

---

## 10. Running Locally (Dev Mode)

This is the recommended way to develop. You run infrastructure in Docker and application services locally for fast hot-reload.

### Step 1: Clone & configure

```bash
git clone <repo-url>
cd PhysioSync

# Copy env file and fill in your Gemini API key
cp .env.example .env
# Edit .env — the only mandatory change is GEMINI_API_KEY
```

### Step 2: Start infrastructure containers

```bash
# From project root
docker compose -f infra/docker-compose.dev.yml up -d
```

This starts: PostgreSQL, Redis, Zookeeper, Kafka, MinIO, ChromaDB, MailHog.

Wait ~30 seconds, then verify:
```bash
docker ps          # all 7 containers should be "Up"
docker logs physiolink-postgres  # should end with "database system is ready"
```

The database schema and seed data are applied automatically on first start from `infra/db/init.sql`.

### Step 3: Set up the ML service

```bash
cd ml-service

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate        # on Windows: .venv\Scripts\activate

# Install dependencies (~2 min, MediaPipe is large)
pip install -r requirements.txt

# Embed the knowledge base into ChromaDB (one-time setup)
# Make sure ChromaDB container is running first!
python -m app.langchain.embedder

# Start the ML service
uvicorn app.main:app --reload --port 8000
```

You should see:
```
INFO: MediaPipe Pose initialised.
INFO: RAG chain initialised (ChromaDB @ localhost:8001).
INFO: Uvicorn running on http://0.0.0.0:8000
```

### Step 4: Start Spring Boot services

Open 4 separate terminal windows (or use tabs):

```bash
# Terminal 1 — API Gateway
cd services/api-gateway
mvn spring-boot:run

# Terminal 2 — Patient Service
cd services/patient-service
mvn spring-boot:run

# Terminal 3 — Exercise Service
cd services/exercise-service
mvn spring-boot:run

# Terminal 4 — Appointment Service
cd services/appointment-service
mvn spring-boot:run

# Optional Terminal 5 — Video Service (only needed for video calls)
cd services/video-service
mvn spring-boot:run
```

> **First run is slow** (~2-3 minutes) because Maven downloads dependencies. Subsequent runs take ~20 seconds each.

Each service reads from the `.env` file in the project root. Spring Boot maps `POSTGRES_URL` → `spring.datasource.url` automatically.

### Step 5: Start the frontend

```bash
cd frontend
npm install    # first time only
npm run dev
```

Open **http://localhost:5173** in your browser.

### Step 6: Verify everything works

1. Go to http://localhost:5173
2. Log in with `physio@physiolink.dev` / `password`
3. You should see the Physio Dashboard with one patient (John Smith)
4. Click the patient → see session history (empty at first)
5. Log out → log in as `patient@physiolink.dev` / `password`
6. You should see the Patient Dashboard
7. Click "Start" next to any exercise
8. Allow camera access → watch the real-time pose tracking begin!

---

## 11. Environment Variables Reference

See the fully annotated [`.env.example`](.env.example) for detailed documentation.

| Variable | Mandatory | Default | Purpose |
|----------|-----------|---------|---------|
| `JWT_SECRET` | ✅ | — | HMAC-SHA256 signing key. Generate: `openssl rand -hex 32` |
| `JWT_EXPIRY_MS` | No | 86400000 | Token lifetime (24 hours) |
| `POSTGRES_DB` | No | `physiolink` | Database name |
| `POSTGRES_USER` | No | `physio` | DB username |
| `POSTGRES_PASSWORD` | No | `physio123` | DB password |
| `POSTGRES_URL` | No | `jdbc:postgresql://postgres:5432/physiolink` | Full JDBC URL |
| `REDIS_HOST` | No | `redis` | Redis hostname |
| `KAFKA_BOOTSTRAP` | No | `kafka:9092` | Kafka broker address |
| `MINIO_ENDPOINT` | No | `http://minio:9000` | MinIO API URL |
| `MINIO_ACCESS_KEY` | No | `minioadmin` | MinIO root username |
| `MINIO_SECRET_KEY` | No | `minioadmin` | MinIO root password |
| `CHROMA_HOST` | No | `chromadb` | ChromaDB hostname |
| `CHROMA_PORT` | No | `8000` | ChromaDB port |
| `GEMINI_API_KEY` | ✅ | — | Google AI API key — get from https://aistudio.google.com/app/apikey |
| `MAIL_HOST` | No | `mailhog` | SMTP server hostname |
| `MAIL_PORT` | No | `1025` | SMTP server port |
| `VITE_API_BASE_URL` | No | `http://localhost:8080` | API gateway URL for browser |
| `VITE_ML_WS_URL` | No | `ws://localhost:8000` | ML WebSocket URL for browser |
| `VITE_VIDEO_WS_URL` | No | `ws://localhost:8084` | Video WebSocket URL for browser |

---

## 12. Demo Credentials

These are seeded into the database automatically by `infra/db/init.sql`:

| Role | Email | Password |
|------|-------|----------|
| Physiotherapist | `physio@physiolink.dev` | `password` |
| Patient | `patient@physiolink.dev` | `password` |

The patient (John Smith) is pre-assigned to the physio (Dr. Sarah Chen) with a medical history of "Left knee ACL reconstruction (2024)". Five exercises are pre-loaded.

---

## 13. Running Tests

### ML Service (Python)

```bash
cd ml-service
source .venv/bin/activate

# Run all tests
pytest tests/ -v

# Run with coverage report
pytest tests/ -v --cov=app --cov-report=term-missing
```

The tests in `tests/test_scorer.py` cover:
- `calc_angle()`: right angles, straight lines, symmetry, 45° cases
- `score_frame()`: in-range accuracy, out-of-range, unknown joints, all 8 joints
- Landmark index sanity checks (all indices 0–32)

### Spring Boot (Patient Service)

```bash
cd services/patient-service

# Run all tests (uses H2 in-memory DB — no Docker needed)
mvn test

# Run a specific test class
mvn test -Dtest=AuthServiceTest
mvn test -Dtest=JwtUtilTest
```

The tests cover:
- `JwtUtilTest`: JWT generation produces valid 3-part token for both roles
- `AuthServiceTest`: register (patient/physio roles, duplicate email, bad role defaults, password hashing), login (correct creds, wrong password, unknown email)

---

## 14. Docker Compose (Full Stack)

The full stack (`infra/docker-compose.yml`) builds and runs everything including all Spring Boot services, the ML service, and the frontend — but requires all service Dockerfiles to build successfully.

```bash
# Copy and configure .env first!
cp .env.example .env
# Edit .env — set JWT_SECRET and GEMINI_API_KEY at minimum

# Start everything (takes 5-10 min first time to build all images)
docker compose -f infra/docker-compose.yml up -d --build

# Check status
docker compose -f infra/docker-compose.yml ps

# View logs for a specific service
docker compose -f infra/docker-compose.yml logs -f ml-service

# Embed the knowledge base (run after ml-service is healthy)
docker exec physiolink-ml python -m app.langchain.embedder

# Tear down everything
docker compose -f infra/docker-compose.yml down -v
```

**URLs when using full Docker stack**:
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- ML Service: http://localhost:8000
- MinIO Console: http://localhost:9001
- MailHog Web UI: http://localhost:8025 (dev compose only)

---

## 15. Where to Start Reading the Code

If you want to understand how everything fits together, read the files in this order:

### Start with the data model
1. [`infra/db/init.sql`](infra/db/init.sql) — understand every table and relationship

### Then understand auth
2. [`services/patient-service/src/.../dto/RegisterRequest.java`](services/patient-service/src/main/java/com/physiolink/patient/dto/RegisterRequest.java) — what data a registration needs
3. [`services/patient-service/src/.../service/AuthService.java`](services/patient-service/src/main/java/com/physiolink/patient/service/AuthService.java) — how passwords are hashed and JWTs issued
4. [`services/patient-service/src/.../security/JwtUtil.java`](services/patient-service/src/main/java/com/physiolink/patient/security/JwtUtil.java) — what's inside a JWT
5. [`services/api-gateway/src/.../config/JwtAuthFilter.java`](services/api-gateway/src/main/java/com/physiolink/gateway/config/JwtAuthFilter.java) — how the gateway validates and injects headers

### Then understand the frontend auth flow
6. [`frontend/src/services/api.ts`](frontend/src/services/api.ts) — how the JWT is attached to every request
7. [`frontend/src/hooks/useAuth.ts`](frontend/src/hooks/useAuth.ts) — how auth state is stored and managed
8. [`frontend/src/App.tsx`](frontend/src/App.tsx) — routing with role-based guards

### Then understand the pose tracking system
9. [`ml-service/app/pose/scorer.py`](ml-service/app/pose/scorer.py) — the maths (read the `calc_angle` function)
10. [`ml-service/app/pose/estimator.py`](ml-service/app/pose/estimator.py) — MediaPipe wrapper (simple!)
11. [`ml-service/app/pose/ws_handler.py`](ml-service/app/pose/ws_handler.py) — the WebSocket session loop
12. [`frontend/src/pages/patient/SessionPage.tsx`](frontend/src/pages/patient/SessionPage.tsx) — how the browser drives the session

### Finally, the async AI pipeline
13. [`services/exercise-service/src/.../SessionEventProducer.java`](services/exercise-service/src/main/java/com/physiolink/exercise/kafka/SessionEventProducer.java) — publishes Kafka event
14. [`ml-service/app/kafka/consumer.py`](ml-service/app/kafka/consumer.py) — consumes event
15. [`ml-service/app/langchain/plan_generator.py`](ml-service/app/langchain/plan_generator.py) — sends prompt to Gemini
16. [`ml-service/app/langchain/rag_chain.py`](ml-service/app/langchain/rag_chain.py) — RAG Q&A chain

---

## Common Gotchas & FAQ

**Q: The ML service crashes on startup with a MediaPipe error.**  
A: MediaPipe requires specific OpenCV. Make sure you installed `opencv-python-headless` (not `opencv-python`) — the headless version works without a display.

**Q: JWT errors after changing `JWT_SECRET` in .env.**  
A: Old tokens signed with the old secret are now invalid. Clear `localStorage` in the browser (DevTools → Application → Local Storage → Clear All) and log in again.

**Q: The knowledge base embedding fails with "No .txt files found".**  
A: The `knowledge_base/physio_clinical_guidelines.txt` file must exist. If you see this error, check that the file is in `ml-service/knowledge_base/`.

**Q: Spring Boot fails to connect to Kafka on startup.**  
A: The Kafka container takes ~15–20 seconds to be ready after `docker compose up`. The services will retry. If they fail permanently, check: `docker logs physiolink-kafka`.

**Q: Camera permission denied on SessionPage.**  
A: Modern browsers only allow `getUserMedia()` on `localhost` or HTTPS. If you're accessing via a non-localhost URL, you need HTTPS.

**Q: Recovery plans are not appearing after sessions.**  
A: The plan generation is async and depends on: (1) Kafka consumer running in ML service, (2) `GEMINI_API_KEY` being set, (3) The Gemini API being reachable. Check `docker logs physiolink-ml` for errors.

**Q: MailHog is not showing appointment reminder emails.**  
A: Reminders only fire within 24 hours of the scheduled appointment time. Book an appointment for "in the next 30 minutes" and wait for the 15-minute cron job to run.
