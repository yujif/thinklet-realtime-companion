import json
import tempfile
import unittest
from pathlib import Path

from scripts.validate_generic_session import validate_generic_sessions


def write_minimal_session(root: Path, session_id: str) -> Path:
    session = root / session_id
    session.mkdir()
    (session / "metadata.json").write_text(
        json.dumps(
            {
                "frame_cadence_millis": 2000,
                "max_duration_millis": 1000,
                "use_case_id": "generic_realtime_conversation",
            },
        ),
        encoding="utf-8",
    )
    (session / "timeline.jsonl").write_text(
        '{"type":"start_requested","time":1000}\n{"type":"stopped","time":2000}\n',
        encoding="utf-8",
    )
    (session / "usage.jsonl").write_text("", encoding="utf-8")
    (session / "events.jsonl").write_text("", encoding="utf-8")
    return session


class GenericSessionValidationTest(unittest.TestCase):
    def test_validate_generic_sessions_accepts_non_cooking_session(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_minimal_session(root, "s1")

            result = validate_generic_sessions(root, ("s1",))

        self.assertEqual([], result.failures)
        self.assertIn("Generic Realtime Conversation Review Focus", result.rendered_html)

    def test_validate_generic_sessions_rejects_cooking_memory_artifact(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = write_minimal_session(root, "s1")
            (session / "cooking-memory.json").write_text("{}", encoding="utf-8")

            result = validate_generic_sessions(root, ("s1",))

        self.assertEqual(
            ["s1: unexpected cooking-memory.json for generic_realtime_conversation"],
            result.failures,
        )

    def test_validate_generic_sessions_rejects_non_generic_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = write_minimal_session(root, "s1")
            (session / "metadata.json").write_text(
                json.dumps(
                    {
                        "frame_cadence_millis": 2000,
                        "max_duration_millis": 1000,
                        "use_case_id": "english_conversation_learning",
                    },
                ),
                encoding="utf-8",
            )

            result = validate_generic_sessions(root, ("s1",))

        self.assertEqual(
            ["s1: expected use_case_id generic_realtime_conversation, got english_conversation_learning"],
            result.failures,
        )

    def test_validate_generic_sessions_rejects_missing_use_case_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = write_minimal_session(root, "s1")
            (session / "metadata.json").write_text(
                json.dumps({"frame_cadence_millis": 2000, "max_duration_millis": 1000}),
                encoding="utf-8",
            )

            result = validate_generic_sessions(root, ("s1",))

        self.assertEqual(
            ["s1: missing use_case_id in metadata.json"],
            result.failures,
        )


if __name__ == "__main__":
    unittest.main()
