# PhysioLink

A remote physiotherapy platform with real-time AI pose feedback, LangChain-powered recovery planning, and WebRTC video consultations.

## Architecture

```
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

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, TailwindCSS, Vite |
| Backend | Spring Boot 3.2.x (Java 21), Spring Cloud Gateway |
| ML | FastAPI, MediaPipe, LangChain, Gemini Pro |
| Databases | PostgreSQL 15, Redis 7, ChromaDB, MinIO |
| Messaging | Apache Kafka |
| DevOps | Docker, Docker Compose, Jenkins, Ansible |

## Quick Start (Local Dev)

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

Frontend: http://localhost:3000  
API Gateway: http://localhost:8080  
ML Service: http://localhost:8000  
MinIO Console: http://localhost:9001  

## Project Structure

```
physiolink/
├── frontend/                  # React 18 + TypeScript + TailwindCSS
├── services/
│   ├── api-gateway/           # Spring Cloud Gateway + JWT auth
│   ├── patient-service/       # Patient profiles + auth
│   ├── exercise-service/      # Exercise library + session logs
│   ├── appointment-service/   # Booking + email reminders
│   └── video-service/         # WebRTC signaling + MinIO recordings
├── ml-service/                # FastAPI + MediaPipe + LangChain
└── infra/                     # Docker Compose + Jenkins + Ansible
```

## Environment Variables

Copy `.env.example` to `.env` and fill in real values. See `.env.example` for all required variables.

> **Never commit `.env` to version control.**

## Implementation Phases

- **Phase 1** ✅ — Mono-repo scaffold, infra docker-compose, DB schema
- **Phase 2** — Spring Boot service implementations
- **Phase 3** — ML pose estimation engine
- **Phase 4** — React frontend
- **Phase 5** — WebRTC video consultations
- **Phase 6** — DevOps (Jenkins + Ansible)
- **Phase 7** — Polish + load testing

## License

MIT
