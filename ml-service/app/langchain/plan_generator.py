"""
Recovery plan generator — LangChain LLMChain with structured JSON output.
Called by Kafka consumer after session.completed events.
"""
import json
import logging
import os

logger = logging.getLogger(__name__)


def generate_plan(condition: str, session_history: list, current_exercises: list) -> dict:
    """
    Generate a 4-week progressive rehabilitation plan via Gemini Pro.

    Args:
        condition:         Patient diagnosis string
        session_history:   List of last 4 session log dicts
        current_exercises: List of current exercise dicts

    Returns:
        Parsed JSON dict with weeklyPlan, progressionNotes, redFlags
    """
    try:
        from langchain.prompts import PromptTemplate
        from langchain.chains import LLMChain
        from langchain_google_genai import ChatGoogleGenerativeAI

        llm = ChatGoogleGenerativeAI(
            model="gemini-2.5-flash",
            google_api_key=os.getenv("GEMINI_API_KEY", ""),
        )

        prompt = PromptTemplate(
            input_variables=["condition", "session_history", "current_exercises"],
            template="""
You are an expert physiotherapist. Generate a progressive 4-week rehabilitation plan.

Patient condition: {condition}
Last sessions (avg accuracy per exercise): {session_history}
Current exercise set: {current_exercises}

Return ONLY valid JSON matching this exact structure:
{{
  "weeklyPlan": [
    {{"week": 1, "exercises": [{{"name": "...", "reps": 10, "sets": 3, "notes": "..."}}]}}
  ],
  "progressionNotes": "...",
  "redFlags": ["..."]
}}
""",
        )

        chain = LLMChain(llm=llm, prompt=prompt)
        result = chain.run(
            condition=condition,
            session_history=json.dumps(session_history),
            current_exercises=json.dumps(current_exercises),
        )

        # Strip markdown fences if present
        cleaned = result.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        return json.loads(cleaned)

    except Exception as exc:
        logger.exception("Plan generation failed: %s", exc)
        return {
            "weeklyPlan": [],
            "progressionNotes": "Plan generation failed. Please review manually.",
            "redFlags": ["Automated plan could not be generated."],
        }
