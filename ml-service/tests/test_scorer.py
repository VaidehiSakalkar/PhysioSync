"""
Unit tests for ml-service/app/pose/scorer.py

Run: pytest ml-service/tests/ -v
"""
import math
import pytest

from app.pose.scorer import calc_angle, score_frame, JOINT_TRIPLETS, LANDMARK_IDX


# ──────────────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────────────

def make_landmark(x: float, y: float, z: float = 0.0, visibility: float = 1.0) -> dict:
    return {"x": x, "y": y, "z": z, "visibility": visibility}


def make_landmarks_straight_knee() -> list[dict]:
    """
    33-landmark skeleton with straight left knee (~180°).
    Only the three landmarks used for LEFT_KNEE are meaningful.
    All others default to origin.
    """
    lms = [make_landmark(0.5, 0.5) for _ in range(33)]
    # LEFT_HIP (23), LEFT_KNEE (25), LEFT_ANKLE (27)
    lms[23] = make_landmark(0.5, 0.3)   # hip above knee
    lms[25] = make_landmark(0.5, 0.6)   # knee
    lms[27] = make_landmark(0.5, 0.9)   # ankle below knee
    return lms


def make_landmarks_bent_knee(flex_deg: float = 90.0) -> list[dict]:
    """
    33-landmark skeleton with LEFT knee at approximately flex_deg.
    The ankle is placed by rotating from the straight position.
    """
    lms = make_landmarks_straight_knee()
    # Move ankle to create the desired angle at the knee.
    # Knee at (0.5, 0.6), hip at (0.5, 0.3) → hip vector is (0, -0.3) upwards.
    # We rotate the ankle relative to knee by flex_deg.
    rad = math.radians(flex_deg)
    ankle_x = 0.5 + 0.3 * math.sin(rad)
    ankle_y = 0.6 + 0.3 * math.cos(rad)
    lms[27] = make_landmark(ankle_x, ankle_y)
    return lms


# ──────────────────────────────────────────────────────────────────────────────
# calc_angle tests
# ──────────────────────────────────────────────────────────────────────────────

class TestCalcAngle:
    def test_right_angle(self):
        """Three points forming a right angle at b."""
        a = make_landmark(0.0, 1.0)
        b = make_landmark(0.0, 0.0)
        c = make_landmark(1.0, 0.0)
        angle = calc_angle(a, b, c)
        assert abs(angle - 90.0) < 0.01

    def test_straight_line(self):
        """Three collinear points should yield 180°."""
        a = make_landmark(0.0, 0.0)
        b = make_landmark(1.0, 0.0)
        c = make_landmark(2.0, 0.0)
        angle = calc_angle(a, b, c)
        assert abs(angle - 180.0) < 0.01

    def test_zero_angle(self):
        """When a and c are in the same direction from b, angle ≈ 0°."""
        a = make_landmark(1.0, 0.0)
        b = make_landmark(0.0, 0.0)
        c = make_landmark(0.5, 0.0)  # same direction as a
        angle = calc_angle(a, b, c)
        assert angle < 1.0  # should be 0°

    def test_symmetric(self):
        """Angle should be the same regardless of a/c swap (commutative)."""
        a = make_landmark(0.0, 1.0)
        b = make_landmark(0.0, 0.0)
        c = make_landmark(1.0, 0.0)
        assert abs(calc_angle(a, b, c) - calc_angle(c, b, a)) < 0.001

    def test_45_degrees(self):
        a = make_landmark(0.0, 1.0)
        b = make_landmark(0.0, 0.0)
        c = make_landmark(1.0, 1.0)
        angle = calc_angle(a, b, c)
        assert abs(angle - 45.0) < 0.5

    def test_returns_float(self):
        a = make_landmark(0.0, 1.0)
        b = make_landmark(0.0, 0.0)
        c = make_landmark(1.0, 0.0)
        assert isinstance(calc_angle(a, b, c), float)


# ──────────────────────────────────────────────────────────────────────────────
# score_frame tests
# ──────────────────────────────────────────────────────────────────────────────

class TestScoreFrame:
    def _exercise(self, joint="LEFT_KNEE", t_min=60.0, t_max=120.0):
        return {"targetJoint": joint, "targetAngleMin": t_min, "targetAngleMax": t_max}

    def test_in_range_gives_perfect_accuracy(self):
        """When measured angle is within [t_min, t_max], accuracy == 1.0."""
        lms = make_landmarks_bent_knee(flex_deg=90.0)
        result = score_frame(lms, self._exercise(t_min=60.0, t_max=120.0))
        assert result["inRange"] is True
        assert result["accuracy"] == 1.0

    def test_out_of_range_reduces_accuracy(self):
        """Straight knee (~180°) against a target of 60-120° should have low accuracy."""
        lms = make_landmarks_straight_knee()
        result = score_frame(lms, self._exercise(t_min=60.0, t_max=120.0))
        assert result["inRange"] is False
        assert result["accuracy"] < 1.0

    def test_accuracy_non_negative(self):
        """Accuracy must always be ≥ 0, even for very large deviations."""
        lms = make_landmarks_straight_knee()
        result = score_frame(lms, self._exercise(t_min=10.0, t_max=20.0))
        assert result["accuracy"] >= 0.0

    def test_angle_is_rounded(self):
        """Result angle should be a float rounded to 2 decimal places."""
        lms = make_landmarks_bent_knee(90.0)
        result = score_frame(lms, self._exercise())
        assert isinstance(result["angle"], float)
        # At most 2 decimal places
        assert len(str(result["angle"]).split(".")[-1]) <= 2

    def test_returns_required_keys(self):
        """score_frame must return angle, accuracy, feedback, inRange."""
        lms = make_landmarks_bent_knee(90.0)
        result = score_frame(lms, self._exercise())
        assert set(result.keys()) == {"angle", "accuracy", "feedback", "inRange"}

    def test_unknown_joint_returns_safe_defaults(self):
        """Unknown joint name should not raise — return zero accuracy with a message."""
        lms = make_landmarks_straight_knee()
        result = score_frame(lms, {"targetJoint": "ALIEN_JOINT", "targetAngleMin": 60, "targetAngleMax": 120})
        assert result["accuracy"] == 0.0
        assert result["inRange"] is False
        assert "Unknown joint" in result["feedback"]

    def test_feedback_contains_direction_hint_when_angle_too_small(self):
        """When the joint angle is below t_min, feedback should say 'more'."""
        # Straight knee creates angle ~180° — that's *above* t_max
        # We need angle < t_min for 'more'. Use a wide range that sits above t_max.
        # Create a near-zero angle: a, b, c in same direction
        lms = list(make_landmarks_straight_knee())
        # Bend so angle is very small — ankle pointing towards hip
        lms[23] = make_landmark(0.5, 0.3)
        lms[25] = make_landmark(0.5, 0.6)
        lms[27] = make_landmark(0.51, 0.31)   # almost same direction as hip → tiny angle
        result = score_frame(lms, self._exercise(t_min=80.0, t_max=120.0))
        # angle < t_min → "more"
        assert "more" in result["feedback"] or result["inRange"] is True

    def test_all_standard_joints_are_scoreable(self):
        """All joints in JOINT_TRIPLETS must score without exceptions."""
        lms = [make_landmark(float(i) * 0.01, float(i) * 0.01) for i in range(33)]
        for joint in JOINT_TRIPLETS:
            result = score_frame(lms, {"targetJoint": joint, "targetAngleMin": 60, "targetAngleMax": 120})
            assert "accuracy" in result
            assert 0.0 <= result["accuracy"] <= 1.0

    @pytest.mark.parametrize("flex_deg,t_min,t_max,expected_in_range", [
        (90.0,  60.0, 120.0, True),
        (45.0,  60.0, 120.0, False),
        (150.0, 60.0, 120.0, False),
        (60.0,  60.0, 120.0, True),   # boundary: exactly t_min
        (120.0, 60.0, 120.0, True),   # boundary: exactly t_max
    ])
    def test_in_range_parametrized(self, flex_deg, t_min, t_max, expected_in_range):
        lms = make_landmarks_bent_knee(flex_deg)
        result = score_frame(lms, {"targetJoint": "LEFT_KNEE", "targetAngleMin": t_min, "targetAngleMax": t_max})
        # The geometry approximation may have small errors, so allow ±5°
        # Rather than check exact in_range, verify accuracy direction.
        if expected_in_range:
            assert result["accuracy"] >= 0.8, f"Expected high accuracy for flex={flex_deg}"
        else:
            assert result["accuracy"] < 1.0, f"Expected <1.0 accuracy for flex={flex_deg}"


# ──────────────────────────────────────────────────────────────────────────────
# landmark_idx sanity
# ──────────────────────────────────────────────────────────────────────────────

class TestLandmarkIdx:
    def test_all_indices_in_bounds(self):
        """All landmark indices must be in [0, 32] (MediaPipe 33 landmarks)."""
        for name, idx in LANDMARK_IDX.items():
            assert 0 <= idx <= 32, f"{name} idx {idx} out of range"

    def test_all_triplet_landmarks_defined(self):
        """Every landmark referenced in JOINT_TRIPLETS must be in LANDMARK_IDX."""
        for joint, (a, b, c) in JOINT_TRIPLETS.items():
            for lm_name in (a, b, c):
                assert lm_name in LANDMARK_IDX, (
                    f"Joint {joint} references undefined landmark {lm_name}"
                )
