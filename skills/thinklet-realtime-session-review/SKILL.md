---
name: thinklet-realtime-session-review
description: Extract and review THINKLET Realtime Companion real-device session artifacts. Use when working with adb, /sdcard Android session data, events.jsonl, usage.jsonl, timeline.jsonl, camera.mp4, mic.pcm, assistant.pcm, review HTML generation, latency/cost/transcript checks, media review flow, or English Conversation Review Focus verification.
---

# THINKLET Realtime Session Review

This skill covers extracting a THINKLET Realtime Companion real-device session to a PC and checking transcript, latency, cost, media review flow, and per-use-case review focus in the HTML review.

Real-device artifacts contain audio, video, camera frames, transcripts, and API usage.
Never put raw artifacts into the public repo, issues, or pull requests.

## Prerequisites

The working repo is assumed to be the root of the `thinklet-realtime-companion` Android repo.
Run every command below from the repo root.

The device app package is:

```bash
com.yujif.thinklet.realtimecompanion
```

The on-device storage path is:

```bash
/sdcard/Android/data/com.yujif.thinklet.realtimecompanion/files/sessions
```

To make an English conversation practice build, install with:

```bash
./gradlew installDebug -PrealtimeCompanionUseCase=english_conversation_learning
```

## Extracting a device session

First check the adb connection.

```bash
adb devices -l
```

List the sessions on the device.

```bash
adb shell ls -la /sdcard/Android/data/com.yujif.thinklet.realtimecompanion/files/sessions
```

Decide which session id to inspect.
The examples use `2026-06-21T09-54-30Z`.

```bash
SESSION_ID=2026-06-21T09-54-30Z
mkdir -p device-sessions
adb pull /sdcard/Android/data/com.yujif.thinklet.realtimecompanion/files/sessions/$SESSION_ID device-sessions/$SESSION_ID
```

To keep logcat as well, save a tail into the session directory.

```bash
adb logcat -d -v time -t 3000 > device-sessions/$SESSION_ID/logcat-tail.txt
```

## Verifying the extraction

Confirm that at minimum these files exist in the session directory.

```bash
find device-sessions/$SESSION_ID -maxdepth 2 -type f | sort
wc -l device-sessions/$SESSION_ID/events.jsonl device-sessions/$SESSION_ID/usage.jsonl device-sessions/$SESSION_ID/timeline.jsonl
```

Check model, frame cadence, and cost cap in `metadata.json`.

```bash
sed -n '1,120p' device-sessions/$SESSION_ID/metadata.json
```

`events.jsonl`, `usage.jsonl`, and `timeline.jsonl` are the source of truth for Realtime events, response usage, and the timeline.
When the review HTML cannot be read, start from these three files' line counts and JSON parse results.

## Converting PCM to WAV

The device saves `mic.pcm` and `assistant.pcm`.
The HTML review's audio player reads `mic.wav` and `assistant.wav`, so generate WAVs when checking media sync in a browser.

Use the script bundled with this skill.

```bash
python3 skills/thinklet-realtime-session-review/scripts/pcm_to_wav.py device-sessions/$SESSION_ID
```

This app's audio PCM is treated as 24 kHz, 16-bit, mono.
When verifying an implementation changed to a different sample rate, pass the script's `--sample-rate`.

```bash
python3 skills/thinklet-realtime-session-review/scripts/pcm_to_wav.py device-sessions/$SESSION_ID --sample-rate 24000
```

## Generating the HTML review

For an English conversation practice session, pass `--use-case-id english_conversation_learning` and `--use-case-label "English Conversation Learning"`.

```bash
python3 scripts/build_session_review_html.py \
  --sessions-root device-sessions \
  --sessions $SESSION_ID \
  --output device-sessions/review-english-$SESSION_ID.html \
  --use-case-id english_conversation_learning \
  --use-case-label "English Conversation Learning"
```

To review a cooking support session, omit the use case options or pass `cooking_support`.

## Browser check

Open the generated review HTML directly in a browser.

```text
file:///path/to/device-sessions/review-english-$SESSION_ID.html
```

Check the following:

- The header reads `Realtime Companion Session Review`.
- The selected use case is `English Conversation Learning`.
- `English Conversation Review Focus` is displayed.
- Learner utterances show the user transcript.
- The response table shows assistant transcript, latency, token usage, and estimated cost.
- Video, mic.wav, and assistant.wav can all be played from the same review screen.
- Non-cooking reviews do not show `update_cooking_memory` diagnostics.

## Checking the numbers

To summarize the real data without opening the HTML, use this Python snippet.

```bash
python3 - <<'PY'
import json
from pathlib import Path

session = Path("device-sessions") / "2026-06-21T09-54-30Z"
for index, line in enumerate((session / "usage.jsonl").read_text().splitlines(), 1):
    row = json.loads(line)
    response = row["response"]
    usage = response["usage"]
    transcript = " ".join(
        content.get("transcript", "")
        for output in response.get("output", [])
        for content in output.get("content", [])
        if isinstance(content, dict)
    )
    print(
        f"#{index} {response['id']} status={response['status']} "
        f"total={usage['total_tokens']} input={usage['input_tokens']} output={usage['output_tokens']}"
    )
    print(transcript.replace("\n", " / "))
PY
```

To use a different session, replace `2026-06-21T09-54-30Z` in the snippet with the target session id.

Cost is an estimate computed from `REALTIME_PRICING` in `scripts/build_session_review_html.py`.
When the price table values or `verified_date` are stale, check the official pricing and update the script before comparing.

## Handling the results

Real-device artifacts are covered by `.gitignore` and are normally not committed.
When leaving a work record, write only the session id, what was checked, the problems found, and the local path of the review HTML in the task note.
