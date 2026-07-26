from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
from typing import Any


DEFAULT_REVIEW_TITLE = "Realtime Companion Session Review"
DEFAULT_USE_CASE_ID = "generic_realtime_conversation"
DEFAULT_USE_CASE_LABEL = "Generic Realtime Conversation"
COOKING_SUPPORT_USE_CASE_ID = "cooking_support"
ENGLISH_CONVERSATION_USE_CASE_ID = "english_conversation_learning"

DEFAULT_REVIEW_FOCUS = {
    "title": "Generic Realtime Conversation Review Focus",
    "items": (
        "Check whether the assistant answers the current user request directly and concisely.",
        "Confirm camera frames and user transcripts are used only when they clarify the current situation.",
        "Review transcript, latency, cost, and media sync before using the session as public demo evidence.",
    ),
}
REVIEW_FOCUS_BY_USE_CASE_ID = {
    ENGLISH_CONVERSATION_USE_CASE_ID: {
        "title": "English Conversation Review Focus",
        "items": (
            "Check learner utterances for English/Japanese mix and whether the assistant understood the intent.",
            "Confirm each response gives a natural phrase, correction or short explanation, and a speakable practice line.",
            "Review transcript, latency, cost, and media sync before using the session as public demo evidence.",
        ),
    },
    COOKING_SUPPORT_USE_CASE_ID: {
        "title": "Cooking Support Review Focus",
        "items": (
            "Check whether camera frames and user transcripts match the cooking context.",
            "Confirm guidance is short, actionable, and safe for the current cooking step.",
            "Review update_cooking_memory tool calls, memory_updated events, latency, cost, and media sync.",
        ),
    },
}

REALTIME_PRICING = {
    "model": "gpt-realtime-2",
    "verified_date": "2026-06-21",
    "source_url": "https://developers.openai.com/api/docs/pricing",
    "rates_per_1m_tokens": {
        "text": {"input": 4.00, "cached_input": 0.40, "output": 24.00},
        "audio": {"input": 32.00, "cached_input": 0.40, "output": 64.00},
        "image": {"input": 5.00, "cached_input": 0.50, "output": 0.00},
    },
}


def load_jsonl(path: Path) -> tuple[list[dict[str, Any]], int]:
    rows: list[dict[str, Any]] = []
    malformed = 0
    if not path.exists():
        return rows, malformed
    buffer: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        if buffer:
            buffer.append(line)
            candidate = "\n".join(buffer)
            try:
                value = json.loads(candidate)
            except json.JSONDecodeError:
                if _json_balance(candidate) <= 0:
                    malformed += 1
                    buffer = []
                continue
            if isinstance(value, dict):
                rows.append(value)
            else:
                malformed += 1
            buffer = []
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            if line.lstrip().startswith(("{", "[")) and _json_balance(line) > 0:
                buffer = [line]
                continue
            malformed += 1
            continue
        if isinstance(value, dict):
            rows.append(value)
        else:
            malformed += 1
    if buffer:
        malformed += 1
    return rows, malformed


def collect_transcript(response: dict[str, Any]) -> str:
    parts: list[str] = []
    for output in response.get("output") or []:
        if not isinstance(output, dict):
            continue
        for content in output.get("content") or []:
            if isinstance(content, dict) and content.get("transcript"):
                parts.append(str(content["transcript"]))
    return "\n".join(parts)


def usage_response_from_row(
    row: dict[str, Any],
    *,
    session_id: str,
    index: int,
    local_time_s: float,
) -> dict[str, Any]:
    response = row.get("response") if isinstance(row.get("response"), dict) else {}
    usage = response.get("usage") if isinstance(response.get("usage"), dict) else {}
    input_details = usage.get("input_token_details") if isinstance(usage.get("input_token_details"), dict) else {}
    cached_details = (
        input_details.get("cached_tokens_details") if isinstance(input_details.get("cached_tokens_details"), dict) else {}
    )
    output_details = usage.get("output_token_details") if isinstance(usage.get("output_token_details"), dict) else {}
    status_details = response.get("status_details") if isinstance(response.get("status_details"), dict) else {}
    return {
        "session_id": session_id,
        "index": index,
        "response_id": str(response.get("id") or ""),
        "event_id": str(row.get("event_id") or ""),
        "status": str(response.get("status") or "unknown"),
        "reason": str(status_details.get("reason") or ""),
        "transcript": collect_transcript(response),
        "local_time_s": float(local_time_s),
        "time_is_approximate": True,
        "tokens": {
            "total": _int(usage.get("total_tokens")),
            "input": _int(usage.get("input_tokens")),
            "output": _int(usage.get("output_tokens")),
            "input_text": _int(input_details.get("text_tokens")),
            "input_audio": _int(input_details.get("audio_tokens")),
            "input_image": _int(input_details.get("image_tokens")),
            "input_cached": _int(input_details.get("cached_tokens")),
            "cached_text": _int(cached_details.get("text_tokens")),
            "cached_audio": _int(cached_details.get("audio_tokens")),
            "cached_image": _int(cached_details.get("image_tokens")),
            "output_text": _int(output_details.get("text_tokens")),
            "output_audio": _int(output_details.get("audio_tokens")),
            "reasoning": _int(output_details.get("reasoning_tokens")),
        },
    }


def summarize_raw_event(row: dict[str, Any]) -> dict[str, Any]:
    payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
    response = payload.get("response") if isinstance(payload.get("response"), dict) else row.get("response")
    if not isinstance(response, dict):
        response = {}
    status_details = response.get("status_details") if isinstance(response.get("status_details"), dict) else {}
    return {
        "direction": str(row.get("direction") or ""),
        "type": str(row.get("type") or ""),
        "payload_type": str(payload.get("type") or ""),
        "response_status": str(response.get("status") or ""),
        "response_reason": str(status_details.get("reason") or ""),
        "large_payload_removed": "audio" in payload,
    }


def collect_user_transcripts(event_rows: list[dict[str, Any]], *, session_id: str) -> list[dict[str, Any]]:
    transcripts: list[dict[str, Any]] = []
    speech_start_times_by_item: dict[str, float] = {}
    last_speech_start_s = 0.0
    for row in event_rows:
        payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
        payload_type = str(payload.get("type") or row.get("type") or "")
        if payload_type == "input_audio_buffer.speech_started":
            event_time = _payload_audio_time_s(payload)
            if event_time is not None:
                last_speech_start_s = event_time
                item_id = str(payload.get("item_id") or "")
                if item_id:
                    speech_start_times_by_item[item_id] = event_time
            continue
        if payload_type != "conversation.item.input_audio_transcription.completed":
            continue
        transcript = str(payload.get("transcript") or "").strip()
        if not transcript:
            continue
        item_id = str(payload.get("item_id") or "")
        transcripts.append(
            {
                "session_id": session_id,
                "item_id": item_id,
                "transcript": transcript,
                "local_time_s": speech_start_times_by_item.get(item_id, last_speech_start_s),
            }
        )
    return transcripts


def build_response_time_index(
    event_rows: list[dict[str, Any]],
    *,
    frame_times_s: list[float],
    frame_cadence_s: float,
) -> dict[str, float]:
    response_times: dict[str, float] = {}
    image_sent_count = 0
    for row in event_rows:
        if row.get("direction") == "client" and row.get("type") == "image.sent":
            image_sent_count += 1
            continue
        payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
        payload_type = str(payload.get("type") or "")
        if payload_type not in {"response.created", "response.done"}:
            continue
        response = payload.get("response") if isinstance(payload.get("response"), dict) else {}
        response_id = str(response.get("id") or "")
        if not response_id:
            continue
        event_time = _time_for_image_count(image_sent_count, frame_times_s, frame_cadence_s)
        if payload_type == "response.created" or response_id not in response_times:
            response_times[response_id] = event_time
    return response_times


def build_response_event_timing(
    event_rows: list[dict[str, Any]],
    *,
    frame_times_s: list[float],
    frame_cadence_s: float,
) -> dict[str, dict[str, float]]:
    timings: dict[str, dict[str, float]] = {}
    image_sent_count = 0
    last_speech_start_s: float | None = None
    last_speech_stop_s: float | None = None
    last_commit_s: float | None = None

    for row in event_rows:
        if row.get("direction") == "client" and row.get("type") == "image.sent":
            image_sent_count += 1
            continue
        payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
        payload_type = str(payload.get("type") or row.get("type") or "")
        event_time = _payload_audio_time_s(payload)
        if event_time is None:
            event_time = _time_for_image_count(image_sent_count, frame_times_s, frame_cadence_s)

        if payload_type == "input_audio_buffer.speech_started":
            last_speech_start_s = event_time
            continue
        if payload_type == "input_audio_buffer.speech_stopped":
            last_speech_stop_s = event_time
            continue
        if payload_type == "input_audio_buffer.committed":
            last_commit_s = event_time
            continue

        response_id = _response_id_from_payload(payload)
        if not response_id:
            continue
        timing = timings.setdefault(response_id, {})
        if payload_type == "response.created":
            if last_speech_stop_s is not None:
                event_time = max(event_time, last_speech_stop_s)
            timing["speech_started_s"] = _coalesce(timing.get("speech_started_s"), last_speech_start_s)
            timing["speech_stopped_s"] = _coalesce(timing.get("speech_stopped_s"), last_speech_stop_s)
            timing["committed_s"] = _coalesce(timing.get("committed_s"), last_commit_s)
            timing["created_s"] = event_time
        elif payload_type in {"response.output_item.added", "response.content_part.added"}:
            timing.setdefault("first_output_s", event_time)
        elif payload_type == "response.output_audio_transcript.delta":
            timing.setdefault("first_transcript_s", event_time)
            timing.setdefault("first_output_s", event_time)
        elif payload_type == "response.output_audio.delta":
            timing.setdefault("first_audio_s", event_time)
            timing.setdefault("first_output_s", event_time)
        elif payload_type == "response.done":
            timing["done_s"] = event_time

    for timing in timings.values():
        _add_delta(timing, "speech_stopped_s", "created_s", "vad_to_created_s")
        _add_delta(timing, "created_s", "first_output_s", "created_to_first_output_s")
        _add_delta(timing, "created_s", "first_audio_s", "created_to_first_audio_s")
        _add_delta(timing, "speech_stopped_s", "first_audio_s", "speech_stop_to_first_audio_s")
        _add_delta(timing, "created_s", "done_s", "created_to_done_s")
    return timings


def session_duration_s(timeline_rows: list[dict[str, Any]], fallback_ms: int = 0) -> float:
    times = [float(row["time"]) for row in timeline_rows if isinstance(row.get("time"), (int, float))]
    if len(times) >= 2:
        return max(1.0, (max(times) - min(times)) / 1000.0)
    if fallback_ms > 0:
        return max(1.0, fallback_ms / 1000.0)
    return 1.0


def session_duration_fallback_ms(metadata: dict[str, Any]) -> int:
    extension_prompt_ms = _int(metadata.get("extension_prompt_millis"))
    if extension_prompt_ms > 0:
        return extension_prompt_ms + max(0, _int(metadata.get("extension_timeout_millis")))
    return _int(metadata.get("max_duration_millis"))


def discover_session_ids(sessions_root: Path) -> tuple[str, ...]:
    if not sessions_root.is_dir():
        return ()
    return tuple(sorted(path.name for path in sessions_root.iterdir() if path.is_dir()))


def build_report(
    sessions_root: Path,
    session_ids: tuple[str, ...] | None = None,
    *,
    asset_prefix: str = "",
    use_case_id: str = DEFAULT_USE_CASE_ID,
    use_case_label: str = DEFAULT_USE_CASE_LABEL,
) -> dict[str, Any]:
    if session_ids is None:
        session_ids = discover_session_ids(sessions_root)
    sessions: list[dict[str, Any]] = []
    responses: list[dict[str, Any]] = []
    user_transcripts: list[dict[str, Any]] = []
    event_summaries: list[dict[str, Any]] = []
    warnings: list[str] = []
    global_offset = 0.0

    for session_id in session_ids:
        session_dir = sessions_root / session_id
        metadata = _read_json(session_dir / "metadata.json")
        timeline_rows, timeline_bad = load_jsonl(session_dir / "timeline.jsonl")
        usage_rows, usage_bad = load_jsonl(session_dir / "usage.jsonl")
        event_rows, event_bad = load_jsonl(session_dir / "events.jsonl")
        duration = session_duration_s(timeline_rows, session_duration_fallback_ms(metadata))
        frame_times = _frame_times_s(session_dir, timeline_rows)
        response_times = build_response_time_index(
            event_rows,
            frame_times_s=frame_times,
            frame_cadence_s=float(metadata.get("frame_cadence_millis") or 2000) / 1000.0,
        )
        response_timings = build_response_event_timing(
            event_rows,
            frame_times_s=frame_times,
            frame_cadence_s=float(metadata.get("frame_cadence_millis") or 2000) / 1000.0,
        )
        for transcript in collect_user_transcripts(event_rows, session_id=session_id):
            transcript["index"] = len(user_transcripts)
            transcript["global_time_s"] = global_offset + float(transcript.get("local_time_s") or 0)
            user_transcripts.append(transcript)
        response_rows = [row for row in usage_rows if row.get("type") == "response.done"]
        step = duration / max(1, len(response_rows))

        for local_index, row in enumerate(response_rows):
            response = row.get("response") if isinstance(row.get("response"), dict) else {}
            response_id = str(response.get("id") or "")
            local_time = response_times.get(response_id, min(duration, local_index * step))
            item = usage_response_from_row(
                row,
                session_id=session_id,
                index=len(responses),
                local_time_s=local_time,
            )
            item["time_is_approximate"] = response_id not in response_times
            item["timing"] = response_timings.get(response_id, {})
            item["global_time_s"] = global_offset + item["local_time_s"]
            item["cost"] = calculate_realtime_cost(item)
            responses.append(item)

        event_summaries.extend({**summarize_raw_event(row), "session_id": session_id} for row in event_rows[:400])
        session_warnings: list[str] = []
        if timeline_bad:
            session_warnings.append(f"{timeline_bad} malformed timeline rows")
        if usage_bad:
            session_warnings.append(f"{usage_bad} malformed usage rows")
        if event_bad:
            session_warnings.append(f"{event_bad} malformed event rows")
        if use_case_id == COOKING_SUPPORT_USE_CASE_ID:
            memory_tool_calls = count_update_cooking_memory_tool_calls(event_rows)
            memory_updated_events = count_memory_updated_events(timeline_rows)
            session_warnings.append(
                f"update_cooking_memory tool calls: {memory_tool_calls}, memory_updated events: {memory_updated_events}"
            )
            if memory_tool_calls == 0 and memory_updated_events == 0 and cooking_memory_is_empty(
                session_dir / "cooking-memory.json"
            ):
                session_warnings.append(
                    "cooking-memory.json is empty and update_cooking_memory did not fire; "
                    "likely tool-call non-activation, not a persistence failure"
                )
        warnings.extend(f"{session_id}: {item}" for item in session_warnings)

        sessions.append(
            {
                "id": session_id,
                "metadata": metadata,
                "duration_s": duration,
                "global_start_s": global_offset,
                "media": {
                    "camera": _asset_path(
                        _relative_if_exists(session_dir / "camera.mp4", sessions_root),
                        asset_prefix,
                    ),
                    "mic": _asset_path(
                        _relative_if_exists(session_dir / "mic.wav", sessions_root),
                        asset_prefix,
                    ),
                    "assistant": _asset_path(
                        _relative_if_exists(session_dir / "assistant.wav", sessions_root),
                        asset_prefix,
                    ),
                },
                "frames": [
                    _asset_path(_relative_path(path, sessions_root), asset_prefix)
                    for path in sorted((session_dir / "frames").glob("*.jpg"))[::30]
                ],
                "warnings": session_warnings,
            }
        )
        global_offset += duration

    return {
        "use_case": {
            "id": use_case_id,
            "label": use_case_label,
        },
        "sessions": sessions,
        "responses": responses,
        "user_transcripts": user_transcripts,
        "event_summaries": event_summaries,
        "metrics": _metrics(responses, sessions),
        "pricing": REALTIME_PRICING,
        "warnings": warnings,
    }


def calculate_realtime_cost(item: dict[str, Any]) -> dict[str, float]:
    tokens = item.get("tokens") if isinstance(item.get("tokens"), dict) else item
    rates = REALTIME_PRICING["rates_per_1m_tokens"]
    text_input = max(0, _int(tokens.get("input_text")) - _int(tokens.get("cached_text")))
    audio_input = max(0, _int(tokens.get("input_audio")) - _int(tokens.get("cached_audio")))
    image_input = max(0, _int(tokens.get("input_image")) - _int(tokens.get("cached_image")))
    input_usd = (
        text_input * rates["text"]["input"]
        + audio_input * rates["audio"]["input"]
        + image_input * rates["image"]["input"]
    ) / 1_000_000
    cached_usd = (
        _int(tokens.get("cached_text")) * rates["text"]["cached_input"]
        + _int(tokens.get("cached_audio")) * rates["audio"]["cached_input"]
        + _int(tokens.get("cached_image")) * rates["image"]["cached_input"]
    ) / 1_000_000
    output_usd = (
        _int(tokens.get("output_text")) * rates["text"]["output"]
        + _int(tokens.get("output_audio")) * rates["audio"]["output"]
    ) / 1_000_000
    return {
        "input_usd": input_usd,
        "cached_usd": cached_usd,
        "output_usd": output_usd,
        "total_usd": input_usd + cached_usd + output_usd,
    }


def count_update_cooking_memory_tool_calls(event_rows: list[dict[str, Any]]) -> int:
    return sum(
        1
        for row in event_rows
        if _payload_type(row) == "response.function_call_arguments.done"
        and _payload_name(row) == "update_cooking_memory"
    )


def count_memory_updated_events(timeline_rows: list[dict[str, Any]]) -> int:
    return sum(1 for row in timeline_rows if str(row.get("type") or "") == "memory_updated")


def cooking_memory_is_empty(path: Path) -> bool:
    memory = _read_json(path)
    if not memory:
        return True
    text_keys = ("recipe", "current_task", "current_phase", "last_assistant_guidance")
    list_keys = ("ingredients_used", "user_preferences", "open_questions")
    return all(not str(memory.get(key) or "").strip() for key in text_keys) and all(
        not _nonblank_list_values(memory.get(key)) for key in list_keys
    )


def render_html(report: dict[str, Any], *, title: str = DEFAULT_REVIEW_TITLE) -> str:
    data_json = json.dumps(report, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    use_case = report.get("use_case") if isinstance(report.get("use_case"), dict) else {}
    use_case_id = str(use_case.get("id") or DEFAULT_USE_CASE_ID)
    use_case_label = str(use_case.get("label") or DEFAULT_USE_CASE_LABEL)
    review_focus = _review_focus_html(use_case_id)
    return f"""<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(title)}</title>
  <style>{_css()}</style>
</head>
<body>
  <main>
    <header>
      <h1>{html.escape(title)}</h1>
      <p class="muted">Use case: {html.escape(use_case_label)}. Realtime API responses, usage, transcripts, video, audio, and sampled frames.</p>
      <section id="metrics" class="metrics"></section>
      <section id="costEstimate" class="cost-estimate"></section>
      {review_focus}
      <section id="warnings" class="warnings"></section>
    </header>
    <section class="dashboard">
      <div class="media-panel">
        <div class="toolbar"><label>Session <select id="sessionSelect"></select></label><span id="mediaDurations" class="duration-note"></span></div>
        <video id="video" controls preload="metadata" muted></video>
        <div class="audio-grid">
          <label>mic.wav<audio id="micAudio" controls preload="metadata"></audio></label>
          <label>assistant.wav<audio id="assistantAudio" controls preload="metadata"></audio></label>
        </div>
        <section class="transcript-panel">
          <h2>User Transcripts</h2>
          <div id="userTranscripts"></div>
        </section>
        <section class="timeline-panel">
          <h2>Global Response Timeline</h2>
          <div id="timeline" class="timeline"></div>
          <h2>Usage Tracks</h2>
          <div id="usageTracks"></div>
        </section>
        <section>
          <h2>Frames</h2>
          <div id="frames" class="frames"></div>
        </section>
      </div>
      <div class="analysis-panel">
        <div class="filters">
          <div id="statusButtons" class="status-buttons" aria-label="Status filters"></div>
          <select id="statusFilter">
            <option value="all">All responses</option>
            <option value="completed">Completed</option>
            <option value="cancelled">Cancelled</option>
            <option value="nonzero">Non-zero usage</option>
          </select>
          <input id="search" type="search" placeholder="Search transcript, response id, reason, session">
        </div>
        <div id="responseTable"></div>
        <section id="detail" class="detail"></section>
        <section>
          <h2>Raw Event Summary</h2>
          <div id="eventSummary"></div>
        </section>
      </div>
    </section>
  </main>
  <script>window.REVIEW_DATA = {data_json};</script>
  <script>{_js()}</script>
</body>
</html>
"""


def _review_focus_html(use_case_id: str) -> str:
    focus = REVIEW_FOCUS_BY_USE_CASE_ID.get(use_case_id, DEFAULT_REVIEW_FOCUS)
    title = focus["title"]
    items = focus["items"]
    item_html = "".join(f"<li>{html.escape(item)}</li>" for item in items)
    return f"""<section class="review-focus">
        <h2>{html.escape(title)}</h2>
        <ul>{item_html}</ul>
      </section>"""


def write_report(
    sessions_root: Path,
    output: Path,
    session_ids: tuple[str, ...] | None = None,
    *,
    title: str = DEFAULT_REVIEW_TITLE,
    asset_prefix: str = "",
    use_case_id: str = DEFAULT_USE_CASE_ID,
    use_case_label: str = DEFAULT_USE_CASE_LABEL,
) -> None:
    report = build_report(
        sessions_root,
        session_ids,
        asset_prefix=asset_prefix,
        use_case_id=use_case_id,
        use_case_label=use_case_label,
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render_html(report, title=title), encoding="utf-8")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build a static Realtime Companion API/usage review HTML page.")
    parser.add_argument("--sessions-root", type=Path, default=Path("device-sessions"))
    parser.add_argument("--output", type=Path, default=Path("device-sessions/review.html"))
    parser.add_argument(
        "--sessions",
        nargs="*",
        default=None,
        help="Session ids to include. Defaults to every subdirectory of --sessions-root.",
    )
    parser.add_argument("--title", default=DEFAULT_REVIEW_TITLE)
    parser.add_argument("--use-case-id", default=DEFAULT_USE_CASE_ID)
    parser.add_argument("--use-case-label", default=DEFAULT_USE_CASE_LABEL)
    parser.add_argument(
        "--asset-prefix",
        default="",
        help="Prefix prepended to generated media/frame links, for example raw/sessions.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    session_ids = tuple(args.sessions) if args.sessions is not None else None
    write_report(
        args.sessions_root,
        args.output,
        session_ids,
        title=args.title,
        asset_prefix=args.asset_prefix,
        use_case_id=args.use_case_id,
        use_case_label=args.use_case_label,
    )
    if session_ids is None:
        session_ids = discover_session_ids(args.sessions_root)
    if not session_ids:
        print(f"Warning: no session directories found under {args.sessions_root}")
    print(f"Wrote {args.output}")
    return 0


def _read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def _relative_path(path: Path, base: Path) -> str:
    return path.relative_to(base).as_posix()


def _relative_if_exists(path: Path, base: Path) -> str:
    return _relative_path(path, base) if path.exists() else ""


def _asset_path(relative_path: str, asset_prefix: str) -> str:
    if not relative_path:
        return ""
    clean_prefix = asset_prefix.strip("/")
    return f"{clean_prefix}/{relative_path}" if clean_prefix else relative_path


def _frame_times_s(session_dir: Path, timeline_rows: list[dict[str, Any]]) -> list[float]:
    start_time = next(
        (float(row["time"]) for row in timeline_rows if isinstance(row.get("time"), (int, float))),
        None,
    )
    times: list[float] = []
    for path in sorted((session_dir / "frames").glob("*.jpg")):
        try:
            timestamp_ms = float(path.stem.split("-")[-1])
        except ValueError:
            continue
        if start_time is None:
            times.append(len(times) * 2.0)
        else:
            times.append(max(0.0, (timestamp_ms - start_time) / 1000.0))
    return times


def _time_for_image_count(image_sent_count: int, frame_times_s: list[float], frame_cadence_s: float) -> float:
    if frame_times_s:
        index = max(0, min(image_sent_count - 1, len(frame_times_s) - 1))
        return frame_times_s[index]
    return max(0.0, image_sent_count * frame_cadence_s)


def _metrics(responses: list[dict[str, Any]], sessions: list[dict[str, Any]]) -> dict[str, Any]:
    completed = sum(1 for item in responses if item["status"] == "completed")
    cancelled = sum(1 for item in responses if item["status"] == "cancelled")
    return {
        "sessions": len(sessions),
        "responses": len(responses),
        "completed": completed,
        "cancelled": cancelled,
        "duration_s": sum(float(session.get("duration_s") or 0) for session in sessions),
        "total_peak": max((item["tokens"].get("total", 0) for item in responses), default=0),
        "image_peak": max((item["tokens"].get("input_image", 0) for item in responses), default=0),
        "audio_peak": max((item["tokens"].get("input_audio", 0) for item in responses), default=0),
        "text_peak": max((item["tokens"].get("input_text", 0) for item in responses), default=0),
        "cached_peak": max((item["tokens"].get("input_cached", 0) for item in responses), default=0),
        "slow_first_audio": sum(
            1 for item in responses if float((item.get("timing") or {}).get("created_to_first_audio_s") or 0) >= 10
        ),
    }


def _int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def _payload_type(row: dict[str, Any]) -> str:
    payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
    return str(payload.get("type") or row.get("type") or "")


def _payload_name(row: dict[str, Any]) -> str:
    payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
    return str(payload.get("name") or row.get("name") or "")


def _nonblank_list_values(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _json_balance(text: str) -> int:
    depth = 0
    in_string = False
    escaped = False
    for char in text:
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = in_string
            continue
        if char == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if char in "{[":
            depth += 1
        elif char in "}]":
            depth -= 1
    return depth


def _response_id_from_payload(payload: dict[str, Any]) -> str:
    response = payload.get("response") if isinstance(payload.get("response"), dict) else {}
    return str(response.get("id") or payload.get("response_id") or "")


def _payload_audio_time_s(payload: dict[str, Any]) -> float | None:
    for key in ("audio_end_ms", "audio_start_ms"):
        value = payload.get(key)
        if isinstance(value, (int, float)):
            return max(0.0, float(value) / 1000.0)
    return None


def _coalesce(current: float | None, fallback: float | None) -> float | None:
    return current if current is not None else fallback


def _add_delta(timing: dict[str, float], start_key: str, end_key: str, delta_key: str) -> None:
    start = timing.get(start_key)
    end = timing.get(end_key)
    if isinstance(start, (int, float)) and isinstance(end, (int, float)):
        timing[delta_key] = max(0.0, float(end) - float(start))


def _css() -> str:
    return """
:root { color-scheme: light; --bg:#f7f8f5; --ink:#19201d; --muted:#63706a; --line:#d7ddd6; --panel:#fff; --accent:#0f766e; --cancel:#b45309; --done:#2563eb; }
* { box-sizing: border-box; }
body { margin:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; background:var(--bg); color:var(--ink); }
main { max-width:1440px; margin:0 auto; padding:24px 18px 44px; }
h1 { margin:0 0 6px; font-size:28px; letter-spacing:0; }
h2 { margin:18px 0 10px; font-size:17px; letter-spacing:0; }
.muted { color:var(--muted); margin:0 0 16px; }
.metrics { display:grid; grid-template-columns:repeat(auto-fit,minmax(130px,1fr)); gap:8px; margin:14px 0; }
.metric { background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:10px 12px; }
.metric span { display:block; color:var(--muted); font-size:12px; font-weight:700; text-transform:uppercase; }
.metric strong { display:block; margin-top:3px; font-size:22px; }
.warnings:not(:empty) { border:1px solid #f4c67a; background:#fff7e6; border-radius:6px; padding:8px 10px; margin:10px 0; color:#7c4a03; }
.dashboard { display:grid; grid-template-columns:minmax(420px,1.04fr) minmax(420px,.96fr); gap:16px; align-items:start; }
.media-panel,.analysis-panel { min-width:0; }
.toolbar,.filters { display:flex; gap:8px; align-items:center; margin:10px 0; }
.filters { flex-wrap:wrap; }
.duration-note { color:var(--muted); font-size:12px; font-weight:700; }
select,input { border:1px solid var(--line); background:#fff; color:var(--ink); border-radius:6px; padding:8px 9px; font:inherit; }
input { width:100%; }
.status-buttons { display:flex; gap:6px; flex-wrap:wrap; }
.filter-button { border:1px solid var(--line); background:#fff; color:var(--ink); border-radius:999px; padding:7px 10px; font:inherit; font-size:13px; font-weight:800; cursor:pointer; }
.filter-button.active { border-color:var(--accent); background:#e7f5f2; color:#0f766e; }
.cost-estimate:not(:empty) { background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:10px 12px; margin:10px 0 14px; }
.cost-estimate h2 { margin-top:0; }
.cost-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:8px; margin:8px 0; }
.cost-grid div { border:1px solid var(--line); border-radius:5px; padding:8px; background:#fdfdfb; }
.cost-grid span { display:block; color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
.cost-grid strong { display:block; margin-top:2px; font-size:18px; }
.cost-note { color:var(--muted); font-size:12px; line-height:1.45; }
.cost-note details { margin-top:6px; }
.cost-note summary { cursor:pointer; font-weight:800; color:var(--ink); }
.cost-note code { font-size:12px; }
.review-focus { background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:10px 12px; margin:10px 0 14px; }
.review-focus h2 { margin-top:0; }
.review-focus ul { margin:8px 0 0; padding-left:20px; line-height:1.45; }
video { width:100%; aspect-ratio:16/9; background:#111; border-radius:6px; border:1px solid var(--line); display:block; }
.audio-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-top:8px; }
.audio-grid label { display:grid; gap:4px; color:var(--muted); font-size:12px; font-weight:700; }
audio { width:100%; height:34px; }
.timeline-panel,.detail,#responseTable,#eventSummary,.transcript-panel { background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:10px; }
.timeline { position:relative; height:72px; border:1px solid var(--line); background:#fdfdfb; border-radius:5px; overflow:hidden; }
.session-band { position:absolute; top:0; bottom:0; left:var(--left); width:var(--width); border-right:1px solid var(--line); background:rgba(15,118,110,.06); }
.marker { position:absolute; top:10px; width:8px; height:52px; left:var(--left); transform:translateX(-4px); border-radius:99px; border:1px solid rgba(0,0,0,.18); cursor:pointer; background:var(--done); }
.marker.cancelled { background:var(--cancel); }
.marker.selected { outline:3px solid var(--accent); outline-offset:2px; z-index:3; }
.track { margin:8px 0; display:grid; grid-template-columns:92px 1fr; gap:8px; align-items:center; }
.track-label { color:var(--muted); font-size:12px; font-weight:800; }
.track-bar { position:relative; height:24px; border:1px solid var(--line); border-radius:5px; background:#fff; overflow:hidden; }
.point { position:absolute; bottom:0; width:5px; left:var(--left); height:var(--height); background:var(--accent); opacity:.75; }
table { width:100%; border-collapse:collapse; font-size:13px; }
th,td { text-align:left; vertical-align:top; border-bottom:1px solid var(--line); padding:7px 6px; }
th { color:var(--muted); position:sticky; top:0; background:#fff; z-index:1; }
tr { cursor:pointer; }
tr.selected { background:#e7f5f2; }
.table-wrap { max-height:440px; overflow:auto; }
.status { display:inline-block; border-radius:999px; padding:1px 7px; font-weight:800; font-size:12px; color:#fff; background:var(--done); }
.status.cancelled { background:var(--cancel); }
.latency { display:inline-block; border-radius:4px; padding:1px 5px; font-weight:800; background:#edf7f5; color:#0f766e; }
.latency.slow { background:#fff7e6; color:#a15c07; }
.latency.severe { background:#fde8e8; color:#b42318; }
.transcript { max-width:420px; white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.45; }
.timing-table { margin:8px 0; max-width:520px; }
.timing-table td:first-child { color:var(--muted); font-weight:700; width:210px; }
.detail pre { white-space:pre-wrap; overflow:auto; background:#f5f7f4; border:1px solid var(--line); border-radius:5px; padding:8px; }
.frames { display:grid; grid-template-columns:repeat(auto-fill,minmax(118px,1fr)); gap:8px; }
.frames img { width:100%; aspect-ratio:16/9; object-fit:cover; border-radius:5px; border:1px solid var(--line); background:#e8ece7; }
@media (max-width: 980px) { .dashboard { grid-template-columns:1fr; } .audio-grid { grid-template-columns:1fr; } }
"""


def _js() -> str:
    return r"""
const data = window.REVIEW_DATA;
const PRICING = data.pricing || {
  model: "gpt-realtime-2",
  verified_date: "2026-06-21",
  source_url: "https://developers.openai.com/api/docs/pricing",
  rates_per_1m_tokens: {
    text: { input: 4.00, cached_input: 0.40, output: 24.00 },
    audio: { input: 32.00, cached_input: 0.40, output: 64.00 },
    image: { input: 5.00, cached_input: 0.50, output: 0.00 },
  },
};
let selectedIndex = data.responses.length ? data.responses[0].index : -1;
let activeSessionId = data.sessions.length ? data.sessions[0].id : "";
let mediaSyncLocked = false;
let pendingSeekTime = null;
let mediaSeekGeneration = 0;
let ignoreMediaEventsUntil = 0;
let lastPlaybackSelectionSync = 0;
let autoScrollSelectedResponse = true;
let selectedUserTranscriptIndex = -1;
const $ = id => document.getElementById(id);
const fmt = s => {
  s = Math.max(0, Number(s) || 0);
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60).toString().padStart(2, "0");
  return `${m}:${sec}`;
};
function token(item, key) { return (item.tokens && item.tokens[key]) || 0; }
function money(value) {
  value = Number(value) || 0;
  if (value === 0) return "$0.0000";
  if (value < 0.01) return `$${value.toFixed(4)}`;
  return `$${value.toFixed(2)}`;
}
function calculateRealtimeCost(item) {
  if (item.cost && Number.isFinite(Number(item.cost.total_usd))) return item.cost;
  const rates = PRICING.rates_per_1m_tokens;
  const textInput = Math.max(0, token(item, "input_text") - token(item, "cached_text"));
  const audioInput = Math.max(0, token(item, "input_audio") - token(item, "cached_audio"));
  const imageInput = Math.max(0, token(item, "input_image") - token(item, "cached_image"));
  const inputUsd = (
    textInput * rates.text.input +
    audioInput * rates.audio.input +
    imageInput * rates.image.input
  ) / 1000000;
  const cachedUsd = (
    token(item, "cached_text") * rates.text.cached_input +
    token(item, "cached_audio") * rates.audio.cached_input +
    token(item, "cached_image") * rates.image.cached_input
  ) / 1000000;
  const outputUsd = (
    token(item, "output_text") * rates.text.output +
    token(item, "output_audio") * rates.audio.output
  ) / 1000000;
  return { input_usd: inputUsd, cached_usd: cachedUsd, output_usd: outputUsd, total_usd: inputUsd + cachedUsd + outputUsd };
}
function totalCost(items) {
  return items.reduce((acc, item) => {
    const cost = calculateRealtimeCost(item);
    acc.input_usd += Number(cost.input_usd) || 0;
    acc.cached_usd += Number(cost.cached_usd) || 0;
    acc.output_usd += Number(cost.output_usd) || 0;
    acc.total_usd += Number(cost.total_usd) || 0;
    return acc;
  }, { input_usd: 0, cached_usd: 0, output_usd: 0, total_usd: 0 });
}
function renderMetrics() {
  const labels = [
    ["Sessions", data.metrics.sessions],
    ["Responses", data.metrics.responses],
    ["Completed", data.metrics.completed],
    ["Cancelled", data.metrics.cancelled],
    ["Duration", fmt(data.metrics.duration_s)],
    ["Token peak", data.metrics.total_peak],
    ["Image peak", data.metrics.image_peak],
    ["Slow audio", data.metrics.slow_first_audio || 0],
    ["Cached peak", data.metrics.cached_peak],
  ];
  $("metrics").innerHTML = labels.map(([k,v]) => `<div class="metric"><span>${k}</span><strong>${v}</strong></div>`).join("");
  $("warnings").textContent = (data.warnings || []).join(" / ");
}
function renderCostEstimate() {
  const all = totalCost(data.responses || []);
  const visible = totalCost(filteredResponses());
  const source = escapeHtml(PRICING.source_url);
  $("costEstimate").innerHTML = `<h2>Realtime API Cost Estimate</h2>
    <div class="cost-grid">
      <div><span>All responses</span><strong>${money(all.total_usd)}</strong></div>
      <div><span>Visible filter</span><strong>${money(visible.total_usd)}</strong></div>
      <div><span>All input</span><strong>${money(all.input_usd)}</strong></div>
      <div><span>All cached</span><strong>${money(all.cached_usd)}</strong></div>
      <div><span>All output</span><strong>${money(all.output_usd)}</strong></div>
    </div>
    <div class="cost-note">
      Estimated from recorded token usage using ${escapeHtml(PRICING.model)} prices verified on ${escapeHtml(PRICING.verified_date)}.
      Prices can change, so confirm current rates at <a href="${source}" target="_blank" rel="noopener">OpenAI API pricing</a>.
      <details>
        <summary>Calculation logic</summary>
        <p>For each modality: uncached input tokens use the input rate, cached tokens use the cached input rate, and output text/audio tokens use output rates. Image output is not charged for this Realtime usage estimate.</p>
        <p><code>cost = ((input_text - cached_text) * 4.00 + cached_text * 0.40 + (input_audio - cached_audio) * 32.00 + cached_audio * 0.40 + (input_image - cached_image) * 5.00 + cached_image * 0.50 + output_text * 24.00 + output_audio * 64.00) / 1,000,000</code></p>
      </details>
    </div>`;
}
function renderSessionSelect() {
  $("sessionSelect").innerHTML = data.sessions.map(s => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.id)}</option>`).join("");
  $("sessionSelect").value = activeSessionId;
  $("sessionSelect").onchange = () => setActiveSession($("sessionSelect").value, null);
}
function renderStatusButtons() {
  const options = [
    ["all", "All"],
    ["completed", "Completed only"],
    ["cancelled", "Cancelled only"],
    ["nonzero", "Non-zero usage"],
  ];
  const current = $("statusFilter").value;
  $("statusButtons").innerHTML = options.map(([value,label]) =>
    `<button type="button" class="filter-button ${value === current ? "active" : ""}" onclick="setStatusFilter('${value}')">${label}</button>`
  ).join("");
}
function setStatusFilter(value) {
  $("statusFilter").value = value;
  renderAll(false);
}
function setActiveSession(sessionId, seekTime) {
  const sessionChanged = activeSessionId !== sessionId;
  activeSessionId = sessionId;
  $("sessionSelect").value = sessionId;
  const session = data.sessions.find(s => s.id === sessionId);
  if (!session) return;
  const media = session.media || {};
  setMediaSource($("video"), media.camera || "", sessionChanged);
  $("video").muted = true;
  setMediaSource($("micAudio"), media.mic || "", sessionChanged);
  setMediaSource($("assistantAudio"), media.assistant || "", sessionChanged);
  renderFrames(session);
  setSelectedUserTranscriptForTime(seekTime !== null && seekTime !== undefined ? seekTime : 0);
  renderUserTranscripts();
  if (seekTime !== null && seekTime !== undefined) {
    seekMedia(seekTime);
  }
}
function setMediaSource(el, src, forceReload) {
  if (forceReload || el.dataset.baseSrc !== src) {
    el.dataset.baseSrc = src;
    el.src = src;
    if (src) el.load();
  }
}
function updateMediaDurations() {
  const video = $("video");
  const mic = $("micAudio");
  const assistant = $("assistantAudio");
  const parts = [];
  if (Number.isFinite(video.duration)) parts.push(`video ${fmt(video.duration)}`);
  if (Number.isFinite(mic.duration)) parts.push(`mic ${fmt(mic.duration)}`);
  if (Number.isFinite(assistant.duration)) parts.push(`assistant ${fmt(assistant.duration)}`);
  $("mediaDurations").textContent = parts.join(" / ");
}
function mediaElements() {
  return [$("video"), $("micAudio")].filter(Boolean);
}
function clampMediaTime(el, time) {
  const duration = Number.isFinite(el.duration) ? el.duration : time;
  return Math.max(0, Math.min(Number(time) || 0, duration || 0));
}
function setMediaTime(el, time) {
  const target = clampMediaTime(el, time);
  if (Number.isFinite(el.duration) && el.duration > 0) {
    if (Math.abs((el.currentTime || 0) - target) > 0.05) {
      el.currentTime = target;
    }
  } else {
    el.addEventListener("loadedmetadata", () => setMediaTime(el, time), { once: true });
    if (el.src) el.load();
  }
}
function seekMedia(time, shouldPlay=false) {
  const targetTime = Number(time) || 0;
  pendingSeekTime = targetTime;
  const seekGeneration = ++mediaSeekGeneration;
  ignoreMediaEventsUntil = window.performance.now() + 1200;
  mediaSyncLocked = true;
  const assistant = $("assistantAudio");
  assistant.pause();
  for (const el of mediaElements()) {
    setMediaTime(el, targetTime);
  }
  assistant.src = assistant.dataset.baseSrc || assistant.getAttribute("src") || "";
  window.setTimeout(() => {
    if (seekGeneration !== mediaSeekGeneration) return;
    for (const el of mediaElements()) setMediaTime(el, targetTime);
    mediaSyncLocked = false;
    if (shouldPlay) playPrimaryMedia(seekGeneration);
    else pendingSeekTime = null;
  }, 160);
}
function playPrimaryMedia(seekGeneration=mediaSeekGeneration) {
  if (seekGeneration !== mediaSeekGeneration) return;
  const video = $("video");
  const mic = $("micAudio");
  $("assistantAudio").pause();
  video.muted = true;
  if (pendingSeekTime !== null) {
    for (const el of mediaElements()) setMediaTime(el, pendingSeekTime);
    pendingSeekTime = null;
  }
  if (video.src) video.play().catch(() => {});
  if (mic.src) mic.play().catch(() => {});
}
function syncMediaTime(source) {
  if (mediaSyncLocked || window.performance.now() < ignoreMediaEventsUntil) return;
  mediaSyncLocked = true;
  for (const el of mediaElements()) {
    if (el !== source) setMediaTime(el, source.currentTime || 0);
  }
  window.setTimeout(() => { mediaSyncLocked = false; }, 0);
}
function syncMediaPlayback(source, shouldPlay) {
  for (const el of mediaElements()) {
    if (el === source || !el.src) continue;
    if (shouldPlay && el.paused) {
      el.play().catch(() => {});
    } else if (!shouldPlay && !el.paused) {
      el.pause();
    }
  }
}
function bindMediaSync() {
  for (const el of mediaElements()) {
    el.addEventListener("loadedmetadata", () => {
      updateMediaDurations();
      if (pendingSeekTime !== null) setMediaTime(el, pendingSeekTime);
    });
    el.addEventListener("seeking", () => syncMediaTime(el));
    el.addEventListener("play", () => {
      syncMediaTime(el);
      syncMediaPlayback(el, true);
    });
    el.addEventListener("pause", () => syncMediaPlayback(el, false));
  }
}
function filteredResponses() {
  const status = $("statusFilter").value;
  const q = $("search").value.trim().toLowerCase();
  return data.responses.filter(item => {
    if (status === "completed" && item.status !== "completed") return false;
    if (status === "cancelled" && item.status !== "cancelled") return false;
    if (status === "nonzero" && token(item, "total") === 0) return false;
    if (!q) return true;
    const userText = (data.user_transcripts || []).filter(t => t.session_id === item.session_id).map(t => t.transcript).join(" ");
    return [item.session_id, item.response_id, item.status, item.reason, item.transcript, userText].join(" ").toLowerCase().includes(q);
  });
}
function renderTimeline() {
  const total = Math.max(1, data.metrics.duration_s || 1);
  $("timeline").innerHTML = [
    ...data.sessions.map(s => `<div class="session-band" title="${escapeHtml(s.id)}" style="--left:${(s.global_start_s/total)*100}%;--width:${(s.duration_s/total)*100}%"></div>`),
    ...filteredResponses().map(item => `<button class="marker ${escapeHtml(item.status)} ${item.index===selectedIndex?"selected":""}" style="--left:${((item.global_time_s||0)/total)*100}%" title="${escapeHtml(item.session_id)} ${fmt(item.local_time_s)} ${escapeHtml(item.status)}" onclick="selectResponse(${item.index})"></button>`)
  ].join("");
}
function renderUsageTracks() {
  const tracks = [["total","total"],["image","input_image"],["audio","input_audio"],["text","input_text"],["cached","input_cached"]];
  const responses = filteredResponses();
  $("usageTracks").innerHTML = tracks.map(([label,key]) => {
    const max = Math.max(1, ...data.responses.map(item => token(item, key)));
    const points = responses.map(item => `<span class="point" title="${label} ${token(item,key)}" style="--left:${(item.index/Math.max(1,data.responses.length-1))*100}%;--height:${Math.max(3,(token(item,key)/max)*100)}%"></span>`).join("");
    return `<div class="track"><div class="track-label">${label}</div><div class="track-bar">${points}</div></div>`;
  }).join("");
}
function timeCell(item) {
  const approx = item.time_is_approximate ? " approx" : "";
  const global = Number.isFinite(Number(item.global_time_s))
    ? `<br><span class="muted">global ${fmt(item.global_time_s)}</span>`
    : "";
  return `${escapeHtml(item.session_id)}<br><span class="muted">local ${fmt(item.local_time_s)}${approx}</span>${global}`;
}
function renderTable() {
  const rows = filteredResponses().map(item => `<tr data-response-index="${item.index}" class="${item.index===selectedIndex?"selected":""}" onclick="selectResponse(${item.index})">
    <td>${item.index + 1}</td><td>${timeCell(item)}</td>
    <td><span class="status ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span><br><span class="muted">${escapeHtml(item.reason || "")}</span></td>
    <td>${latencyCell(item)}</td>
    <td class="transcript">${escapeHtml(item.transcript || "")}</td>
    <td>${token(item,"total").toLocaleString()}<br><span class="muted">in ${token(item,"input").toLocaleString()} / out ${token(item,"output").toLocaleString()}</span><br><span class="muted">est. ${money(calculateRealtimeCost(item).total_usd)}</span></td>
  </tr>`).join("");
  $("responseTable").innerHTML = `<div class="table-wrap"><table><thead><tr><th>#</th><th>time</th><th>status</th><th>latency</th><th>transcript</th><th>tokens</th></tr></thead><tbody>${rows}</tbody></table></div>`;
}
function renderDetail() {
  const item = data.responses.find(r => r.index === selectedIndex);
  if (!item) { $("detail").innerHTML = "<h2>Response Detail</h2><p class=\"muted\">No response selected.</p>"; return; }
  $("detail").innerHTML = `<h2>Response Detail</h2>
    <p><strong>${escapeHtml(item.response_id || item.event_id || "(no id)")}</strong> <span class="status ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span></p>
    <p class="muted">${escapeHtml(item.session_id)} / local ${fmt(item.local_time_s)} / ${escapeHtml(item.reason || "no reason")}</p>
    <p><strong>Estimated cost:</strong> ${money(calculateRealtimeCost(item).total_usd)}</p>
    ${timingDetail(item)}
    <pre>${escapeHtml(item.transcript || "(no transcript)")}</pre>
    <pre>${escapeHtml(JSON.stringify(item.tokens, null, 2))}</pre>`;
  const byType = {};
  for (const event of data.event_summaries.filter(e => e.session_id === item.session_id)) {
    const key = event.payload_type || event.type || event.direction || "unknown";
    byType[key] = (byType[key] || 0) + 1;
  }
  $("eventSummary").innerHTML = `<table><tbody>${Object.entries(byType).sort((a,b)=>b[1]-a[1]).slice(0,24).map(([k,v]) => `<tr><td>${escapeHtml(k)}</td><td>${v}</td></tr>`).join("")}</tbody></table>`;
}
function renderFrames(session) {
  const frames = session.frames || [];
  $("frames").innerHTML = frames.length ? frames.map(src => `<img src="${escapeHtml(src)}" loading="lazy" alt="">`).join("") : "<p class=\"muted\">No frames available.</p>";
}
function renderUserTranscripts() {
  const rows = userTranscriptsForSession(activeSessionId)
    .map((item, rowIndex) => {
      const transcriptIndex = userTranscriptIndex(item, rowIndex);
      const time = Number(item.local_time_s) || 0;
      return `<tr data-user-transcript-index="${transcriptIndex}" class="${transcriptIndex === selectedUserTranscriptIndex ? "selected" : ""}" onclick="selectUserTranscript(${transcriptIndex}, ${time})">
      <td>${fmt(item.local_time_s)}</td>
      <td>${escapeHtml(item.transcript || "")}</td>
    </tr>`;
    })
    .join("");
  $("userTranscripts").innerHTML = rows
    ? `<div class="table-wrap"><table><thead><tr><th>time</th><th>user transcript</th></tr></thead><tbody>${rows}</tbody></table></div>`
    : "<p class=\"muted\">No user transcripts in this session. Enable input audio transcription for future sessions.</p>";
}
function userTranscriptsForSession(sessionId) {
  return (data.user_transcripts || []).filter(item => item.session_id === sessionId);
}
function userTranscriptIndex(item, fallback) {
  const value = Number(item.index);
  return Number.isFinite(value) ? value : fallback;
}
function userTranscriptIndexForPlaybackTime(sessionId, time) {
  const transcripts = userTranscriptsForSession(sessionId);
  if (!transcripts.length) return -1;
  let selectedRowIndex = 0;
  for (let i = 0; i < transcripts.length; i += 1) {
    if ((Number(transcripts[i].local_time_s) || 0) <= (Number(time) || 0) + 0.25) {
      selectedRowIndex = i;
    }
  }
  return userTranscriptIndex(transcripts[selectedRowIndex], selectedRowIndex);
}
function setSelectedUserTranscriptForTime(time) {
  const nextIndex = userTranscriptIndexForPlaybackTime(activeSessionId, time);
  if (nextIndex === selectedUserTranscriptIndex) return false;
  selectedUserTranscriptIndex = nextIndex;
  return true;
}
function selectUserTranscript(index, time) {
  selectedUserTranscriptIndex = index;
  renderUserTranscripts();
  seekMedia(time, true);
}
function syncUserTranscriptSelectionToPlayback() {
  const video = $("video");
  if (!video || !activeSessionId) return;
  if (window.performance.now() < ignoreMediaEventsUntil) return;
  if (!setSelectedUserTranscriptForTime(video.currentTime || 0)) return;
  renderUserTranscripts();
  scrollSelectedUserTranscriptIntoView();
}
function scrollSelectedUserTranscriptIntoView() {
  const row = document.querySelector(`tr[data-user-transcript-index="${selectedUserTranscriptIndex}"]`);
  if (!row) return;
  row.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" });
}
function responseForPlaybackTime(sessionId, time) {
  const visible = filteredResponses().filter(item => item.session_id === sessionId);
  const candidates = visible.filter(item => item.local_time_s <= time + 0.25);
  return candidates.length ? candidates[candidates.length - 1] : visible[0];
}
function syncSelectionToPlayback() {
  const video = $("video");
  if (!video || video.paused || !activeSessionId) return;
  const now = window.performance.now();
  if (now < ignoreMediaEventsUntil) return;
  if (now - lastPlaybackSelectionSync < 500) return;
  lastPlaybackSelectionSync = now;
  const item = responseForPlaybackTime(activeSessionId, video.currentTime || 0);
  if (!item || item.index === selectedIndex) return;
  selectedIndex = item.index;
  renderTimeline();
  renderTable();
  renderDetail();
  scrollSelectedResponseIntoView();
}
function scrollSelectedResponseIntoView() {
  if (!autoScrollSelectedResponse) return;
  const row = document.querySelector(`tr[data-response-index="${selectedIndex}"]`);
  if (!row) return;
  row.scrollIntoView({ block: "center", inline: "nearest", behavior: "smooth" });
}
function selectResponse(index, options={}) {
  const item = data.responses.find(r => r.index === index);
  if (!item) return;
  selectedIndex = index;
  setActiveSession(item.session_id, item.local_time_s || 0);
  seekMedia(item.local_time_s || 0, true);
  renderAll(false);
  scrollSelectedResponseIntoView();
}
function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[ch]));
}
function timingValue(item, key) {
  return item.timing && Number.isFinite(Number(item.timing[key])) ? Number(item.timing[key]) : null;
}
function fmtDelta(value) {
  return value === null ? "n/a" : `${value.toFixed(1)}s`;
}
function latencySeverity(seconds) {
  if (seconds === null) return "";
  if (seconds >= 20) return " severe";
  if (seconds >= 10) return " slow";
  return "";
}
function latencyCell(item) {
  const vadToCreated = timingValue(item, "vad_to_created_s");
  const createdToAudio = timingValue(item, "created_to_first_audio_s");
  const speechToAudio = timingValue(item, "speech_stop_to_first_audio_s");
  return `<span class="latency${latencySeverity(createdToAudio)}">API ${fmtDelta(createdToAudio)}</span><br>
    <span class="muted">VAD ${fmtDelta(vadToCreated)} / total ${fmtDelta(speechToAudio)}</span>`;
}
function timingDetail(item) {
  const fields = [
    ["speech started", "speech_started_s", false],
    ["speech stopped", "speech_stopped_s", false],
    ["response.created", "created_s", false],
    ["first output", "first_output_s", false],
    ["first audio", "first_audio_s", false],
    ["response.done", "done_s", false],
    ["VAD to created", "vad_to_created_s", true],
    ["created to first audio", "created_to_first_audio_s", true],
    ["speech stop to first audio", "speech_stop_to_first_audio_s", true],
    ["created to done", "created_to_done_s", true],
  ];
  const rows = fields.map(([label,key,isDelta]) => {
    const value = timingValue(item, key);
    const text = isDelta ? fmtDelta(value) : (value === null ? "n/a" : fmt(value));
    return `<tr><td>${label}</td><td>${text}</td></tr>`;
  }).join("");
  return `<table class="timing-table"><tbody>${rows}</tbody></table>`;
}
function renderAll(resetMedia=true) {
  renderMetrics();
  renderStatusButtons();
  renderCostEstimate();
  renderSessionSelect();
  if (resetMedia) setActiveSession(activeSessionId, null);
  renderTimeline();
  renderUsageTracks();
  renderTable();
  renderDetail();
}
$("statusFilter").onchange = () => renderAll(false);
$("search").oninput = () => renderAll(false);
renderAll(true);
bindMediaSync();
for (const el of [$("video"), $("micAudio"), $("assistantAudio")]) {
  el.addEventListener("loadedmetadata", updateMediaDurations);
  el.addEventListener("durationchange", updateMediaDurations);
}
$("video").addEventListener("timeupdate", syncSelectionToPlayback);
$("video").addEventListener("timeupdate", syncUserTranscriptSelectionToPlayback);
"""


if __name__ == "__main__":
    raise SystemExit(main())
