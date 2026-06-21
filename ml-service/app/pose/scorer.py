"""
Joint angle scorer and rep counter.

Landmark indices follow MediaPipe's 33-landmark schema:
https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
"""
import numpy as np

# ── Landmark index map ─────────────────────────────────────────────────────────
LANDMARK_IDX: dict[str, int] = {
    "LEFT_SHOULDER": 11,  "RIGHT_SHOULDER": 12,
    "LEFT_ELBOW":    13,  "RIGHT_ELBOW":    14,
    "LEFT_WRIST":    15,  "RIGHT_WRIST":    16,
    "LEFT_HIP":      23,  "RIGHT_HIP":      24,
    "LEFT_KNEE":     25,  "RIGHT_KNEE":     26,
    "LEFT_ANKLE":    27,  "RIGHT_ANKLE":    28,
}

# ── Triplet definitions per target joint ──────────────────────────────────────
# (point_a, joint_b, point_c) — angle is measured at joint_b
JOINT_TRIPLETS: dict[str, tuple[str, str, str]] = {
    "LEFT_KNEE":     ("LEFT_HIP",      "LEFT_KNEE",     "LEFT_ANKLE"),
    "RIGHT_KNEE":    ("RIGHT_HIP",     "RIGHT_KNEE",    "RIGHT_ANKLE"),
    "LEFT_SHOULDER": ("LEFT_ELBOW",    "LEFT_SHOULDER", "LEFT_HIP"),
    "RIGHT_SHOULDER":("RIGHT_ELBOW",   "RIGHT_SHOULDER","RIGHT_HIP"),
    "LEFT_ELBOW":    ("LEFT_SHOULDER", "LEFT_ELBOW",    "LEFT_WRIST"),
    "RIGHT_ELBOW":   ("RIGHT_SHOULDER","RIGHT_ELBOW",   "RIGHT_WRIST"),
    "LEFT_HIP":      ("LEFT_SHOULDER", "LEFT_HIP",      "LEFT_KNEE"),
    "RIGHT_HIP":     ("RIGHT_SHOULDER","RIGHT_HIP",     "RIGHT_KNEE"),
}


def calc_angle(a: dict, b: dict, c: dict) -> float:
    """
    Compute the angle (degrees) at joint b given three landmark dicts.
    Uses 2D (x, y) for robustness against z-depth estimation noise.
    """
    pa = np.array([a["x"], a["y"]])
    pb = np.array([b["x"], b["y"]])
    pc = np.array([c["x"], c["y"]])
    ba = pa - pb
    bc = pc - pb
    cosine = np.dot(ba, bc) / (np.linalg.norm(ba) * np.linalg.norm(bc) + 1e-8)
    return float(np.degrees(np.arccos(np.clip(cosine, -1.0, 1.0))))


def score_frame(landmarks: list[dict], exercise: dict) -> dict:
    """
    Score a single frame against the target exercise.

    Args:
        landmarks: 33-element list from estimator.process_frame()
        exercise:  dict with keys targetJoint, targetAngleMin, targetAngleMax

    Returns:
        {angle, accuracy, feedback, inRange}
    """
    joint = exercise.get("targetJoint", "LEFT_KNEE")
    triplet = JOINT_TRIPLETS.get(joint)
    if triplet is None:
        return {"angle": 0.0, "accuracy": 0.0, "feedback": f"Unknown joint: {joint}", "inRange": False}

    a_key, b_key, c_key = triplet
    a = landmarks[LANDMARK_IDX[a_key]]
    b = landmarks[LANDMARK_IDX[b_key]]
    c = landmarks[LANDMARK_IDX[c_key]]

    angle = calc_angle(a, b, c)
    t_min: float = exercise.get("targetAngleMin", 60.0)
    t_max: float = exercise.get("targetAngleMax", 120.0)
    mid = (t_min + t_max) / 2.0

    in_range = t_min <= angle <= t_max
    if in_range:
        accuracy = 1.0
        feedback = "Great form! Keep going."
    else:
        deviation = abs(angle - mid) - (t_max - t_min) / 2.0
        accuracy = max(0.0, 1.0 - deviation / 30.0)
        direction = "more" if angle < t_min else "less"
        feedback = (
            f"Adjust: bend {direction} — "
            f"target {t_min:.0f}°–{t_max:.0f}°, current {angle:.1f}°"
        )

    return {
        "angle": round(angle, 2),
        "accuracy": round(accuracy, 3),
        "feedback": feedback,
        "inRange": in_range,
    }
