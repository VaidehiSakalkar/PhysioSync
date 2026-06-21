"""
Kafka consumer — listens to session.completed events and triggers plan generation.
"""
import json
import logging
import os
import threading

logger = logging.getLogger(__name__)


def start_consumer() -> threading.Thread:
    """Start the Kafka consumer in a daemon thread."""
    thread = threading.Thread(target=_consume_loop, daemon=True, name="kafka-consumer")
    thread.start()
    logger.info("Kafka consumer thread started.")
    return thread


def _consume_loop() -> None:
    try:
        from kafka import KafkaConsumer
        import httpx

        bootstrap = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
        exercise_url = os.getenv("EXERCISE_SERVICE_URL", "http://localhost:8082")

        consumer = KafkaConsumer(
            "session.completed",
            bootstrap_servers=bootstrap,
            group_id="ml-service",
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            auto_offset_reset="earliest",
            enable_auto_commit=True,
        )
        logger.info("Kafka consumer ready — listening on session.completed")

        for message in consumer:
            event = message.value
            logger.info("Received session.completed event: %s", event)
            _handle_event(event, exercise_url)

    except Exception as exc:
        logger.exception("Kafka consumer loop failed: %s", exc)


def _handle_event(event: dict, exercise_service_url: str) -> None:
    """Generate a recovery plan from a session.completed event and store it."""
    from app.langchain.plan_generator import generate_plan
    import httpx

    patient_id = event.get("patientId")
    condition = event.get("condition", "General rehabilitation")

    # Fetch last 4 sessions for this patient
    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.get(
                f"{exercise_service_url}/api/exercises/sessions/{patient_id}",
                params={"limit": 4},
            )
            sessions = resp.json() if resp.status_code == 200 else []

            exercises_resp = client.get(
                f"{exercise_service_url}/api/exercises/routines/{patient_id}"
            )
            exercises = exercises_resp.json() if exercises_resp.status_code == 200 else []
    except Exception as exc:
        logger.warning("Could not fetch patient data for plan generation: %s", exc)
        return

    plan = generate_plan(condition, sessions, exercises)

    # Store plan via exercise-service
    try:
        with httpx.Client(timeout=10.0) as client:
            client.put(
                f"{exercise_service_url}/api/exercises/plans/{patient_id}",
                json={"plan": plan, "sessionLogId": event.get("sessionLogId")},
            )
        logger.info("Recovery plan stored for patient %s", patient_id)
    except Exception as exc:
        logger.warning("Could not store recovery plan: %s", exc)
