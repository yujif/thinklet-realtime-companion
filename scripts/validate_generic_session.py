from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from scripts.build_session_review_html import (
    DEFAULT_USE_CASE_ID,
    DEFAULT_USE_CASE_LABEL,
    build_report,
    render_html,
)


@dataclass(frozen=True)
class GenericSessionValidationResult:
    failures: list[str]
    rendered_html: str


def validate_generic_sessions(
    sessions_root: Path,
    session_ids: tuple[str, ...],
) -> GenericSessionValidationResult:
    report = build_report(
        sessions_root,
        session_ids,
        use_case_id=DEFAULT_USE_CASE_ID,
        use_case_label=DEFAULT_USE_CASE_LABEL,
    )
    rendered_html = render_html(report)
    failures: list[str] = []

    if "Generic Realtime Conversation Review Focus" not in rendered_html:
        failures.append("review HTML is missing Generic Realtime Conversation Review Focus")

    for session_id in session_ids:
        session_dir = sessions_root / session_id
        metadata = _read_metadata(session_dir / "metadata.json")
        metadata_use_case_id = str(metadata.get("use_case_id") or "").strip()
        if not metadata_use_case_id:
            failures.append(f"{session_id}: missing use_case_id in metadata.json")
        elif metadata_use_case_id != DEFAULT_USE_CASE_ID:
            failures.append(
                f"{session_id}: expected use_case_id {DEFAULT_USE_CASE_ID}, got {metadata_use_case_id}",
            )
        if (session_dir / "cooking-memory.json").exists():
            failures.append(f"{session_id}: unexpected cooking-memory.json for {DEFAULT_USE_CASE_ID}")

    return GenericSessionValidationResult(failures=failures, rendered_html=rendered_html)


def _read_metadata(path: Path) -> dict[str, object]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    if isinstance(value, dict):
        return value
    return {}


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate generic Realtime conversation session artifacts before public review.",
    )
    parser.add_argument("--sessions-root", type=Path, default=Path("device-sessions"))
    parser.add_argument("--sessions", nargs="+", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    result = validate_generic_sessions(args.sessions_root, tuple(args.sessions))
    if result.failures:
        for failure in result.failures:
            print(f"FAIL: {failure}")
        return 1
    print("Generic session validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
