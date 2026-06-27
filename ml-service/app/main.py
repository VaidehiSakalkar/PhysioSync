"""
PhysioLink ML Service
FastAPI application with:
- /ws/pose  — WebSocket: real-time pose estimation & scoring
- /api/ask  — POST: RAG-powered patient Q&A
- /api/health — GET: health check
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware

from app.pose import ws_handler
from app.langchain import rag_chain


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup / shutdown lifecycle hook."""
    # Initialise MediaPipe (expensive — done once)
    ws_handler.init_mediapipe()
    # Initialise ChromaDB vector store
    rag_chain.init_vectorstore()
    yield
    # Shutdown: nothing to clean up for now


app = FastAPI(
    title="PhysioLink ML Service",
    description="Pose estimation, joint scoring, RAG recovery planner",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],      # tighten in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── WebSocket — Pose Session ──────────────────────────────────────────────────
@app.websocket("/ws/pose")
async def pose_websocket(websocket: WebSocket, exercise_id: str = ""):
    """
    WebSocket endpoint for real-time pose feedback.
    Client sends: {"frame": "<base64-jpeg>"}
    Server sends: {"detected": bool, "angle": float, "accuracy": float,
                   "feedback": str, "repCount": int, "landmarks": [...]}
    """
    from app.pose.ws_handler import handle_session
    await handle_session(websocket, exercise_id)


# ── REST — Patient Q&A ────────────────────────────────────────────────────────
@app.post("/api/ask")
async def ask_question(body: dict):
    """
    RAG-powered patient question endpoint.
    Body: {"question": str, "patientContext": {}}
    """
    from app.langchain.rag_chain import answer_patient_question
    answer = answer_patient_question(
        question=body.get("question", ""),
        patient_context=body.get("patientContext", {}),
    )
    return {"answer": answer}


# ── Health ────────────────────────────────────────────────────────────────────
@app.get("/api/health")
async def health():
    return {"status": "ok", "service": "ml-service"}
