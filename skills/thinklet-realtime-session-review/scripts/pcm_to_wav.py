#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import wave


def convert_pcm_to_wav(pcm_path: Path, wav_path: Path, sample_rate: int) -> float:
    data = pcm_path.read_bytes()
    with wave.open(str(wav_path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(data)
    return len(data) / 2 / sample_rate


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Convert THINKLET Realtime Companion mic/assistant PCM files to WAV."
    )
    parser.add_argument("session_dir", type=Path)
    parser.add_argument("--sample-rate", type=int, default=24_000)
    args = parser.parse_args()

    for stem in ("mic", "assistant"):
        pcm_path = args.session_dir / f"{stem}.pcm"
        wav_path = args.session_dir / f"{stem}.wav"
        if not pcm_path.exists():
            print(f"skip {pcm_path}: missing")
            continue
        duration = convert_pcm_to_wav(pcm_path, wav_path, args.sample_rate)
        print(f"wrote {wav_path} ({duration:.3f}s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
