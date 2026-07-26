import json
import tempfile
import unittest
from pathlib import Path

from scripts.build_session_review_html import (
    build_report,
    build_arg_parser,
    build_response_event_timing,
    build_response_time_index,
    calculate_realtime_cost,
    collect_user_transcripts,
    collect_transcript,
    load_jsonl,
    render_html,
    session_duration_fallback_ms,
    summarize_raw_event,
    usage_response_from_row,
)


DEFAULT_REVIEW_TITLE = "Realtime Companion Session Review"


class SessionReviewParserTests(unittest.TestCase):
    def test_load_jsonl_keeps_good_rows_and_counts_bad_rows(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "events.jsonl"
            path.write_text('{"ok": true}\nnot-json\n{"ok": 2}\n', encoding="utf-8")

            rows, malformed = load_jsonl(path)

        self.assertEqual(rows, [{"ok": True}, {"ok": 2}])
        self.assertEqual(malformed, 1)

    def test_load_jsonl_accepts_pretty_printed_json_objects(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "events.jsonl"
            path.write_text(
                '{"direction":"client","payload":{\n'
                '  "type":"session.update",\n'
                '  "session":{"model":"gpt-realtime-2"}\n'
                '}}\n'
                '{"direction":"server","type":"websocket.open"}\n',
                encoding="utf-8",
            )

            rows, malformed = load_jsonl(path)

        self.assertEqual(malformed, 0)
        self.assertEqual(rows[0]["payload"]["type"], "session.update")
        self.assertEqual(rows[1]["type"], "websocket.open")

    def test_collect_transcript_from_response_output(self):
        response = {
            "output": [
                {
                    "content": [
                        {"type": "output_audio", "transcript": "春菊は茹でます。"},
                        {"type": "output_text", "text": "ignored"},
                    ],
                    "phase": "final_answer",
                }
            ]
        }

        self.assertEqual(collect_transcript(response), "春菊は茹でます。")

    def test_usage_response_extracts_status_reason_tokens_and_transcript(self):
        row = {
            "type": "response.done",
            "event_id": "event_1",
            "response": {
                "id": "resp_1",
                "status": "cancelled",
                "status_details": {"reason": "turn_detected"},
                "output": [{"content": [{"transcript": "はい。"}], "phase": "final_answer"}],
                "usage": {
                    "total_tokens": 42,
                    "input_tokens": 30,
                    "output_tokens": 12,
                    "input_token_details": {
                        "text_tokens": 3,
                        "audio_tokens": 4,
                        "image_tokens": 5,
                        "cached_tokens": 6,
                        "cached_tokens_details": {
                            "text_tokens": 1,
                            "audio_tokens": 2,
                            "image_tokens": 3,
                        },
                    },
                    "output_token_details": {
                        "text_tokens": 7,
                        "audio_tokens": 8,
                        "reasoning_tokens": 9,
                    },
                },
            },
        }

        item = usage_response_from_row(row, session_id="s1", index=2, local_time_s=12.5)

        self.assertEqual(item["response_id"], "resp_1")
        self.assertEqual(item["status"], "cancelled")
        self.assertEqual(item["reason"], "turn_detected")
        self.assertEqual(item["transcript"], "はい。")
        self.assertEqual(item["tokens"]["total"], 42)
        self.assertEqual(item["tokens"]["input_image"], 5)
        self.assertEqual(item["tokens"]["cached_image"], 3)
        self.assertEqual(item["local_time_s"], 12.5)

    def test_calculate_realtime_cost_uses_cached_modality_breakdown(self):
        cost = calculate_realtime_cost(
            {
                "tokens": {
                    "input_text": 101,
                    "cached_text": 1,
                    "input_audio": 202,
                    "cached_audio": 2,
                    "input_image": 303,
                    "cached_image": 3,
                    "output_text": 400,
                    "output_audio": 500,
                }
            }
        )

        expected = (
            100 * 4.00
            + 1 * 0.40
            + 200 * 32.00
            + 2 * 0.40
            + 300 * 5.00
            + 3 * 0.50
            + 400 * 24.00
            + 500 * 64.00
        ) / 1_000_000
        self.assertAlmostEqual(cost["total_usd"], expected)
        self.assertAlmostEqual(cost["input_usd"], (100 * 4.00 + 200 * 32.00 + 300 * 5.00) / 1_000_000)
        self.assertAlmostEqual(cost["cached_usd"], (1 * 0.40 + 2 * 0.40 + 3 * 0.50) / 1_000_000)
        self.assertAlmostEqual(cost["output_usd"], (400 * 24.00 + 500 * 64.00) / 1_000_000)

    def test_build_response_time_index_uses_created_event_frame_time(self):
        events = [
            {"direction": "client", "type": "image.sent"},
            {"direction": "client", "type": "image.sent"},
            {
                "direction": "server",
                "payload": {
                    "type": "response.created",
                    "response": {"id": "resp_1"},
                },
            },
            {"direction": "client", "type": "image.sent"},
            {
                "direction": "server",
                "payload": {
                    "type": "response.done",
                    "response": {"id": "resp_1"},
                },
            },
        ]

        index = build_response_time_index(
            events,
            frame_times_s=[6.6, 8.7, 10.7],
            frame_cadence_s=2.0,
        )

        self.assertEqual(index["resp_1"], 8.7)

    def test_build_response_event_timing_links_vad_and_first_audio(self):
        events = [
            {
                "direction": "server",
                "payload": {
                    "type": "input_audio_buffer.speech_started",
                    "audio_start_ms": 6100,
                },
            },
            {
                "direction": "server",
                "payload": {
                    "type": "input_audio_buffer.speech_stopped",
                    "audio_end_ms": 7200,
                },
            },
            {
                "direction": "server",
                "payload": {
                    "type": "input_audio_buffer.committed",
                },
            },
            {"direction": "client", "type": "image.sent"},
            {
                "direction": "server",
                "payload": {
                    "type": "response.created",
                    "response": {"id": "resp_1"},
                },
            },
            {"direction": "client", "type": "image.sent"},
            {
                "direction": "server",
                "payload": {
                    "type": "response.output_audio.delta",
                    "response_id": "resp_1",
                },
            },
            {
                "direction": "server",
                "payload": {
                    "type": "response.done",
                    "response": {"id": "resp_1"},
                },
            },
        ]

        timings = build_response_event_timing(
            events,
            frame_times_s=[8.0, 10.0],
            frame_cadence_s=2.0,
        )

        timing = timings["resp_1"]
        self.assertEqual(timing["speech_started_s"], 6.1)
        self.assertEqual(timing["speech_stopped_s"], 7.2)
        self.assertEqual(timing["created_s"], 8.0)
        self.assertEqual(timing["first_audio_s"], 10.0)
        self.assertAlmostEqual(timing["vad_to_created_s"], 0.8)
        self.assertAlmostEqual(timing["created_to_first_audio_s"], 2.0)
        self.assertAlmostEqual(timing["speech_stop_to_first_audio_s"], 2.8)

    def test_collect_user_transcripts_from_completed_input_audio_events(self):
        events = [
            {
                "direction": "server",
                "payload": {
                    "type": "input_audio_buffer.speech_started",
                    "audio_start_ms": 1200,
                    "item_id": "item_1",
                },
            },
            {
                "direction": "server",
                "payload": {
                    "type": "input_audio_buffer.speech_stopped",
                    "audio_end_ms": 2800,
                    "item_id": "item_1",
                },
            },
            {
                "direction": "server",
                "payload": {
                    "type": "conversation.item.input_audio_transcription.completed",
                    "item_id": "item_1",
                    "transcript": "卵はどこですか",
                },
            },
        ]

        transcripts = collect_user_transcripts(events, session_id="s1")

        self.assertEqual(
            transcripts,
            [
                {
                    "session_id": "s1",
                    "item_id": "item_1",
                    "transcript": "卵はどこですか",
                    "local_time_s": 1.2,
                }
            ],
        )

    def test_summarize_raw_event_drops_audio_payload(self):
        row = {
            "direction": "client",
            "payload": {
                "type": "input_audio_buffer.append",
                "audio": "A" * 5000,
            },
        }

        summary = summarize_raw_event(row)

        self.assertEqual(summary["payload_type"], "input_audio_buffer.append")
        self.assertNotIn("AAAA", json.dumps(summary))
        self.assertTrue(summary["large_payload_removed"])

    def test_session_duration_fallback_prefers_extension_policy(self):
        self.assertEqual(
            30 * 60 * 1000 + 30 * 1000,
            session_duration_fallback_ms(
                {
                    "extension_prompt_millis": 30 * 60 * 1000,
                    "extension_timeout_millis": 30 * 1000,
                    "max_duration_millis": 10 * 60 * 1000,
                },
            ),
        )
        self.assertEqual(10 * 60 * 1000, session_duration_fallback_ms({"max_duration_millis": 10 * 60 * 1000}))

    def test_render_html_contains_relative_media_links_and_transcript(self):
        report = {
            "sessions": [
                {
                    "id": "s1",
                    "media": {
                        "camera": "s1/camera.mp4",
                        "mic": "s1/mic.wav",
                        "assistant": "s1/assistant.wav",
                    },
                    "frames": ["s1/frames/frame-1.jpg"],
                    "warnings": [],
                }
            ],
            "responses": [
                {
                    "session_id": "s1",
                    "index": 0,
                    "response_id": "resp_1",
                    "status": "completed",
                    "reason": "",
                    "transcript": "春菊は茹でます。",
                    "user_transcript": "春菊はどうしますか",
                    "local_time_s": 4.0,
                    "time_is_approximate": True,
                    "timing": {"created_to_first_audio_s": 12.3},
                    "tokens": {"total": 12, "input": 8, "output": 4},
                }
            ],
            "event_summaries": [],
            "user_transcripts": [
                {
                    "session_id": "s1",
                    "item_id": "item_1",
                    "transcript": "春菊はどうしますか",
                    "local_time_s": 3.0,
                }
            ],
            "metrics": {"completed": 1, "cancelled": 0, "total_peak": 12, "slow_first_audio": 1},
            "warnings": [],
        }

        rendered = render_html(report, title="Review")

        self.assertIn("s1/camera.mp4", rendered)
        self.assertIn("s1/mic.wav", rendered)
        self.assertIn("s1/assistant.wav", rendered)
        self.assertIn("春菊はどうしますか", rendered)
        self.assertIn("User Transcripts", rendered)
        self.assertLess(rendered.index("User Transcripts"), rendered.index("Global Response Timeline"))
        self.assertIn("春菊は茹でます。", rendered)
        self.assertIn("window.REVIEW_DATA", rendered)
        self.assertIn("bindMediaSync", rendered)
        self.assertIn("seekMedia", rendered)
        self.assertIn("mediaDurations", rendered)
        self.assertIn("syncSelectionToPlayback", rendered)
        self.assertIn("scrollSelectedResponseIntoView", rendered)
        self.assertIn("created to first audio", rendered)
        self.assertIn("latencyCell", rendered)
        self.assertNotIn("assistantAudio.addEventListener(\"play\"", rendered)
        self.assertIn("Completed only", rendered)
        self.assertIn("setStatusFilter", rendered)
        self.assertIn("completed", rendered)
        self.assertIn("Realtime API Cost Estimate", rendered)
        self.assertIn("https://developers.openai.com/api/docs/pricing", rendered)
        self.assertIn("calculateRealtimeCost", rendered)
        self.assertIn("white-space:pre-wrap", rendered)
        self.assertNotIn(".transcript { max-width:320px; white-space:nowrap", rendered)
        self.assertIn("selectedUserTranscriptIndex", rendered)
        self.assertIn("data-user-transcript-index", rendered)
        self.assertIn('class="${transcriptIndex === selectedUserTranscriptIndex ? "selected" : ""}"', rendered)
        self.assertIn("selectUserTranscript", rendered)
        self.assertIn("syncUserTranscriptSelectionToPlayback", rendered)
        self.assertIn('addEventListener("timeupdate", syncUserTranscriptSelectionToPlayback)', rendered)
        self.assertIn("if (now < ignoreMediaEventsUntil) return;", rendered)
        self.assertNotIn("if (mediaSyncLocked || now < ignoreMediaEventsUntil) return;", rendered)
        self.assertIn("function timeCell(item)", rendered)
        self.assertIn('item.time_is_approximate ? " approx" : ""', rendered)
        self.assertIn("global ${fmt(item.global_time_s)}", rendered)
        self.assertNotIn('${fmt(item.local_time_s)} approx</span>', rendered)

    def test_render_html_includes_selected_use_case_context(self):
        report = {
            "sessions": [],
            "responses": [],
            "metrics": {},
            "warnings": [],
            "use_case": {
                "id": "english_conversation_learning",
                "label": "English Conversation Learning",
            },
        }

        rendered = render_html(report)

        self.assertIn(DEFAULT_REVIEW_TITLE, rendered)
        self.assertIn("English Conversation Learning", rendered)
        self.assertIn('"use_case":{"id":"english_conversation_learning","label":"English Conversation Learning"}', rendered)

    def test_render_html_includes_english_conversation_review_focus(self):
        report = {
            "sessions": [],
            "responses": [],
            "metrics": {},
            "warnings": [],
            "use_case": {
                "id": "english_conversation_learning",
                "label": "English Conversation Learning",
            },
        }

        rendered = render_html(report)

        self.assertIn("English Conversation Review Focus", rendered)
        self.assertIn("learner utterances", rendered)
        self.assertIn("correction or short explanation", rendered)
        self.assertNotIn("update_cooking_memory", rendered)

    def test_render_html_includes_generic_realtime_conversation_review_focus(self):
        report = {
            "sessions": [],
            "responses": [],
            "metrics": {},
            "warnings": [],
            "use_case": {
                "id": "generic_realtime_conversation",
                "label": "Generic Realtime Conversation",
            },
        }

        rendered = render_html(report)

        self.assertIn("Generic Realtime Conversation Review Focus", rendered)
        self.assertIn("assistant answers the current user request", rendered)
        self.assertIn("transcript, latency, cost, and media sync", rendered)
        self.assertNotIn("update_cooking_memory", rendered)
        self.assertNotIn("learner utterances", rendered)

    def test_default_cli_title_is_not_cooking_specific(self):
        args = build_arg_parser().parse_args([])

        self.assertEqual(DEFAULT_REVIEW_TITLE, args.title)
        self.assertNotIn("Cooking", args.title)

    def test_seek_playback_does_not_restart_same_transcript_multiple_times(self):
        rendered = render_html({"sessions": [], "responses": [], "metrics": {}, "warnings": []})

        self.assertIn("let mediaSeekGeneration = 0;", rendered)
        self.assertIn("const seekGeneration = ++mediaSeekGeneration;", rendered)
        self.assertIn("playPrimaryMedia(seekGeneration)", rendered)
        self.assertIn("if (seekGeneration !== mediaSeekGeneration) return;", rendered)
        self.assertIn("pendingSeekTime = null;", rendered)
        self.assertNotIn("window.setTimeout(playPrimaryMedia, 80)", rendered)

    def test_media_sync_does_not_hard_seek_audio_on_every_timeupdate(self):
        rendered = render_html({"sessions": [], "responses": [], "metrics": {}, "warnings": []})

        self.assertIn('el.addEventListener("seeking", () => syncMediaTime(el));', rendered)
        self.assertNotIn('el.addEventListener("timeupdate", () => {', rendered)
        self.assertNotIn("if (!el.paused) syncMediaTime(el);", rendered)

    def test_build_report_can_prefix_asset_links(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = root / "s1"
            (session / "frames").mkdir(parents=True)
            (session / "metadata.json").write_text(
                json.dumps({"frame_cadence_millis": 2000, "max_duration_millis": 1000}),
                encoding="utf-8",
            )
            (session / "timeline.jsonl").write_text(
                '{"type":"start_requested","time":1000}\n{"type":"stopped","time":2000}\n',
                encoding="utf-8",
            )
            (session / "usage.jsonl").write_text("", encoding="utf-8")
            (session / "events.jsonl").write_text("", encoding="utf-8")
            (session / "camera.mp4").write_bytes(b"video")
            (session / "mic.wav").write_bytes(b"mic")
            (session / "assistant.wav").write_bytes(b"assistant")
            (session / "frames" / "frame-1000.jpg").write_bytes(b"frame")

            report = build_report(root, ("s1",), asset_prefix="raw/sessions")

        media = report["sessions"][0]["media"]
        self.assertEqual("raw/sessions/s1/camera.mp4", media["camera"])
        self.assertEqual("raw/sessions/s1/mic.wav", media["mic"])
        self.assertEqual("raw/sessions/s1/assistant.wav", media["assistant"])
        self.assertEqual(["raw/sessions/s1/frames/frame-1000.jpg"], report["sessions"][0]["frames"])

    def test_build_report_hides_cooking_memory_warning_for_default_generic_use_case(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = root / "s1"
            session.mkdir()
            (session / "metadata.json").write_text(
                json.dumps({"frame_cadence_millis": 2000, "max_duration_millis": 1000}),
                encoding="utf-8",
            )
            (session / "timeline.jsonl").write_text(
                '{"type":"start_requested","time":1000}\n{"type":"stopped","time":2000}\n',
                encoding="utf-8",
            )
            (session / "usage.jsonl").write_text("", encoding="utf-8")
            (session / "events.jsonl").write_text("", encoding="utf-8")
            (session / "cooking-memory.json").write_text(
                json.dumps(
                    {
                        "recipe": "",
                        "current_task": "",
                        "current_phase": "",
                        "ingredients_used": [],
                        "user_preferences": [],
                        "open_questions": [],
                        "last_assistant_guidance": "",
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            report = build_report(root, ("s1",))

        self.assertEqual([], report["warnings"])
        self.assertEqual([], report["sessions"][0]["warnings"])

    def test_build_report_warns_when_cooking_support_empty_memory_has_no_tool_call(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = root / "s1"
            session.mkdir()
            (session / "metadata.json").write_text(
                json.dumps({"frame_cadence_millis": 2000, "max_duration_millis": 1000}),
                encoding="utf-8",
            )
            (session / "timeline.jsonl").write_text(
                '{"type":"start_requested","time":1000}\n{"type":"stopped","time":2000}\n',
                encoding="utf-8",
            )
            (session / "usage.jsonl").write_text("", encoding="utf-8")
            (session / "events.jsonl").write_text(
                json.dumps(
                    {
                        "direction": "server",
                        "payload": {
                            "type": "response.function_call_arguments.done",
                            "name": "different_tool",
                        },
                    },
                    ensure_ascii=False,
                )
                + "\n",
                encoding="utf-8",
            )
            (session / "cooking-memory.json").write_text(
                json.dumps(
                    {
                        "recipe": "",
                        "current_task": "",
                        "current_phase": "",
                        "ingredients_used": [],
                        "user_preferences": [],
                        "open_questions": [],
                        "last_assistant_guidance": "",
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            report = build_report(
                root,
                ("s1",),
                use_case_id="cooking_support",
                use_case_label="Cooking Support",
            )

        self.assertIn("s1: update_cooking_memory tool calls: 0, memory_updated events: 0", report["warnings"])
        self.assertIn(
            "s1: cooking-memory.json is empty and update_cooking_memory did not fire; likely tool-call non-activation, not a persistence failure",
            report["warnings"],
        )

    def test_build_report_hides_cooking_memory_warning_for_non_cooking_use_case(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            session = root / "s1"
            session.mkdir()
            (session / "metadata.json").write_text(
                json.dumps({"frame_cadence_millis": 2000, "max_duration_millis": 1000}),
                encoding="utf-8",
            )
            (session / "timeline.jsonl").write_text(
                '{"type":"start_requested","time":1000}\n{"type":"stopped","time":2000}\n',
                encoding="utf-8",
            )
            (session / "usage.jsonl").write_text("", encoding="utf-8")
            (session / "events.jsonl").write_text("", encoding="utf-8")
            (session / "cooking-memory.json").write_text(
                json.dumps(
                    {
                        "recipe": "",
                        "current_task": "",
                        "current_phase": "",
                        "ingredients_used": [],
                        "user_preferences": [],
                        "open_questions": [],
                        "last_assistant_guidance": "",
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            report = build_report(
                root,
                ("s1",),
                use_case_id="english_conversation_learning",
                use_case_label="English Conversation Learning",
            )

        self.assertEqual([], report["warnings"])
        self.assertEqual([], report["sessions"][0]["warnings"])


if __name__ == "__main__":
    unittest.main()
