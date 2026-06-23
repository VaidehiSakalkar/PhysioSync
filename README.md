# PhysioLink (PhysioSync)

A remote physiotherapy platform with real-time AI pose feedback, LangChain-powered recovery planning, and WebRTC video consultations.

## 🚀 Project Overview

PhysioLink is a comprehensive, microservices-based application designed to bridge the gap between physiotherapists and patients. It offers real-time pose estimation to ensure exercises are performed correctly, AI-generated recovery plans using LangChain, and a robust microservices backend for scalability and resilience.

## 🏗️ Architecture

```text
┌─────────────┐     REST/WS      ┌──────────────┐
│   React 18  │ ───────────────► │  API Gateway │ :8080
│  TypeScript │                  │  Spring Boot │
│  TailwindCSS│ ◄─ pose frames ─ └──────┬───────┘
└─────────────┘                         │ routes to
                                        ▼
                           ┌────────────────────────┐
                           │  patient-service  :8081 │
                           │  exercise-service :8082 │
                           │  appointment-svc  :8083 │
                           │  video-service    :8084 │
                           └────────────────────────┘
                                        │ Kafka events
                                        ▼
                           ┌────────────────────────┐
                           │  ML Service  FastAPI    │ :8000
                           │  MediaPipe Pose         │
                           │  LangChain RAG          │
                           └────────────────────────┘
```

## 🧩 Detailed Component Summary

### 💻 Frontend Architecture (React 18 + Vite + TailwindCSS)

The frontend is built as a Single Page Application (SPA) utilizing React 18, TypeScript, and styled with TailwindCSS. It connects to the backend via REST APIs and WebSockets.

**Pages & Dashboards (`src/pages/`)**
*   **`auth/LoginPage.tsx`**: Secure authentication screen for patients and physiotherapists.
*   **`patient/PatientDashboard.tsx`**: Patient portal for viewing assigned exercise plans, progress tracking, and upcoming appointments.
*   **`patient/SessionPage.tsx`**: The core interactive page for patients to perform exercises with real-time AI pose estimation feedback streamed via WebSockets.
*   **`physio/PhysioDashboard.tsx`**: Dedicated dashboard for physiotherapists to manage patients, review session logs, and assign new recovery plans.

**Reusable Components (`src/components/`)**
*   **`layout/Header.tsx`**: Main navigation component.
*   **`ui/`**: A library of reusable, styled components including `Badge.tsx`, `Button.tsx`, `Card.tsx`, `Input.tsx`, and `Spinner.tsx`.

**Custom Hooks (`src/hooks/`)**
*   **`useAuth.ts`**: Manages the authentication state and JWT tokens across the application.
*   **`useWebSocket.ts`**: Establishes and manages the WebSocket connection critical for streaming video frames to the ML service and receiving real-time pose feedback.
*   **`useAsync.ts`**: Utility hook for handling asynchronous API call states (loading, error, success).

**API Services (`src/services/`)**
*   Modules dedicated to external API communication using Axios: `authService.ts`, `patientService.ts`, `exerciseService.ts`, `appointmentService.ts`, and `sessionService.ts`.

---

### ⚙️ Backend Services (Spring Boot + Java 21)

The core backend follows a microservices architecture managed via Spring Cloud and containerized with Docker.

*   **`api-gateway`**: The central entry point for the frontend. It routes all HTTP requests to the appropriate microservices and acts as the primary layer for JWT authentication validation.
*   **`patient-service`**: Manages patient and physiotherapist profiles, registration, and handles the core authentication flow.
*   **`exercise-service`**: Stores the centralized library of physical therapy exercises and logs patient session histories and progress.
*   **`appointment-service`**: Handles the scheduling of consultations and utilizes Redis for caching/sessions. Sends out email reminders.
*   **`video-service`**: Manages WebRTC signaling for live video consultations and interacts with MinIO for storing recorded sessions.

---

### 🧠 Machine Learning Service (FastAPI + Python)

The ML service is the intelligence hub of PhysioLink, handling real-time computer vision and generative AI tasks.

*   **Pose Estimation Module (`app/pose/`)**: 
    *   **`estimator.py`**: Integrates Google's **MediaPipe** for high-performance skeletal tracking and pose estimation.
    *   **`scorer.py`**: Compares the user's real-time pose against the ideal exercise form and calculates accuracy scores.
    *   **`ws_handler.py`**: High-throughput WebSocket handler that receives frames from the frontend, runs them through the estimator, and returns real-time feedback.
*   **AI Recovery Planning Module (`app/langchain/`)**: 
    *   **`embedder.py` & `rag_chain.py`**: Implements a Retrieval-Augmented Generation (RAG) pipeline to fetch relevant medical/exercise context.
    *   **`plan_generator.py`**: Utilizes **LangChain** and **Gemini Pro** to dynamically generate personalized physical therapy recovery plans based on patient data.
*   **Event Driven Processing (`app/kafka/`)**:
    *   **`consumer.py`**: Kafka consumer that listens to events from the Spring Boot services (e.g., a new session completed) to trigger asynchronous ML tasks like post-session analysis or plan updates.

---

### 🏗️ Infrastructure & DevOps

The entire stack is containerized and orchestrated for easy deployment and local development.

*   **Databases & Storage**:
    *   **PostgreSQL 15**: Primary relational data store for the Spring Boot microservices.
    *   **Redis 7**: High-performance in-memory caching and session management.
    *   **ChromaDB**: Specialized vector database to support the LangChain RAG pipeline with semantic search.
    *   **MinIO**: S3-compatible object storage for managing video assets and user uploads.
*   **Messaging**:
    *   **Apache Kafka & Zookeeper**: Distributed event streaming platform to decouple the Java microservices from the Python ML service, enabling asynchronous processing.
*   **DevOps (`infra/`)**:
    *   **`docker-compose.yml`**: Full-stack configuration to spin up all databases, message brokers, microservices, and the frontend with a single command.
    *   Directories for **Jenkins** and **Ansible** are structured for upcoming CI/CD and deployment automation phases.

## 🏁 Quick Start (Local Dev)

### 1. Start infrastructure
```bash
cp .env.example .env
# Fill in secrets in .env
docker compose -f infra/docker-compose.dev.yml up -d
```

### 2. Run Spring Boot services
```bash
cd services/patient-service && mvn spring-boot:run
cd services/exercise-service && mvn spring-boot:run
cd services/api-gateway && mvn spring-boot:run
# ... etc
```

### 3. Run ML service
```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 4. Run frontend
```bash
cd frontend
npm install
npm run dev
```

### Full Stack (Docker Compose)
```bash
docker compose -f infra/docker-compose.yml up --build
```

Frontend: `http://localhost:3000`  
API Gateway: `http://localhost:8080`  
ML Service: `http://localhost:8000`  
MinIO Console: `http://localhost:9001`  

## 📜 License

MIT
