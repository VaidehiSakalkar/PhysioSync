"""
WebSocket handler — processes webcam frames and streams pose feedback.
"""
import json
import logging
import os

import httpx
from fastapi import WebSocket, WebSocketDisconnect

from app.pose.estimator import process_frame
from app.pose.scorer import score_frame

logger = logging.getLogger(__name__)

EXERCISE_SERVICE_URL = os.getenv("EXERCISE_SERVICE_URL", "http://localhost:8082")


def init_mediapipe() -> None:
    """Delegated to estimator — called from main.py lifespan."""
    from app.pose.estimator import init_mediapipe as _init
    _init()


async def _fetch_exercise(exercise_id: str) -> dict | None:
    """Fetch exercise config from exercise-service REST API."""
    if not exercise_id:
        return None
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(f"{EXERCISE_SERVICE_URL}/api/exercises/{exercise_id}")
            if resp.status_code == 200:
                return resp.json()
    except Exception as exc:
        logger.warning("Could not fetch exercise %s: %s", exercise_id, exc)
    return None


async def handle_session(websocket: WebSocket, exercise_id: str = "") -> None:
    """
    Main WebSocket loop.

    Protocol (JSON over WS):
      Client → Server: {"frame": "<base64-jpeg>"}
      Server → Client: {
          "detected": bool,
          "angle": float,
          "accuracy": float,   # 0.0 – 1.0
          "feedback": str,
          "repCount": int,
          "inRange": bool,
          "landmarks": [...]   # only when detected
      }
    """
    await websocket.accept()

    exercise = await _fetch_exercise(exercise_id)
    if exercise is None:
        # Fall back to a neutral exercise so the WS stays open for testing
        exercise = {
            "targetJoint": "LEFT_KNEE",
            "targetAngleMin": 70.0,
            "targetAngleMax": 110.0,
        }
        logger.warning("Exercise %s not found — using default config.", exercise_id)

    rep_count = 0
    in_rep = False

    try:
        while True:
            raw = await websocket.receive_text()
            payload = json.loads(raw)

            frame_result = process_frame(payload.get("frame", ""))

            if not frame_result["detected"]:
                await websocket.send_json({"detected": False, "repCount": rep_count})
                continue

            score = score_frame(frame_result["landmarks"], exercise)

            # Hysteresis rep counter — prevents false counts at range boundary
            if score["inRange"] and not in_rep:
                in_rep = True
            elif not score["inRange"] and in_rep:
                in_rep = False
                rep_count += 1

            await websocket.send_json({
                **score,
                "detected": True,
                "repCount": rep_count,
                "landmarks": frame_result["landmarks"],
            })

    except WebSocketDisconnect:
        logger.info("Client disconnected from pose session (exercise=%s, reps=%d)", exercise_id, rep_count)
    except Exception as exc:
        logger.exception("Unexpected error in pose session: %s", exc)
        await websocket.close(code=1011)
