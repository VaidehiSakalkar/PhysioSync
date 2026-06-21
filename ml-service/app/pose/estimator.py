"""
Pose estimator — MediaPipe Pose wrapper.
Initialized once at FastAPI startup for performance.
"""
import base64
import logging

import cv2
import mediapipe as mp
import numpy as np

logger = logging.getLogger(__name__)

mp_pose = mp.solutions.pose
_pose_instance: mp_pose.Pose | None = None


def init_mediapipe() -> None:
    """Called once during app startup (lifespan hook)."""
    global _pose_instance
    _pose_instance = mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        smooth_landmarks=True,
        min_detection_confidence=0.6,
        min_tracking_confidence=0.6,
    )
    logger.info("MediaPipe Pose initialised.")


def process_frame(b64_frame: str) -> dict:
    """
    Decode a base64 JPEG frame, run MediaPipe Pose, return landmarks.

    Returns:
        {"detected": False}  — if no pose found
        {"detected": True, "landmarks": [...33 landmarks...]}
    """
    if _pose_instance is None:
        raise RuntimeError("MediaPipe not initialised. Call init_mediapipe() first.")

    img_bytes = base64.b64decode(b64_frame)
    np_arr = np.frombuffer(img_bytes, np.uint8)
    frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    if frame is None:
        return {"detected": False}

    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    result = _pose_instance.process(rgb)

    if not result.pose_landmarks:
        return {"detected": False}

    return {
        "detected": True,
        "landmarks": _serialize_landmarks(result.pose_landmarks.landmark),
    }


def _serialize_landmarks(landmarks) -> list[dict]:
    return [
        {"x": lm.x, "y": lm.y, "z": lm.z, "visibility": lm.visibility}
        for lm in landmarks
    ]
