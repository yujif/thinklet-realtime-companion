# Contributing

Thanks for considering a contribution to THINKLET Realtime Companion.

This repository contains Android app code and review tooling for a THINKLET + OpenAI Realtime API demo. Keep contributions focused on source code, tests, documentation, and sample configuration that can be shared publicly.

## Safety rules

- Do not commit real OpenAI API keys, bearer tokens, private endpoint URLs, Android signing materials, or local `.env` files.
- Do not commit raw THINKLET recordings, camera frames, session logs, generated review HTML, or files copied from a real user's device session.
- Do not include customer-specific details, private contracts, private screenshots, or personal information in issues, pull requests, test fixtures, or docs.
- Use synthetic examples or newly recorded public-demo data when a reproduction needs sample data.

## Local checks

Run the focused tests for the area you changed before opening a pull request.

```bash
python3 -m unittest discover -s tests
./gradlew test
```

## Pull requests

- Explain the user-visible behavior or documentation change.
- Mention the THINKLET device and Android version when hardware behavior is involved.
- List the exact tests you ran.
- Keep unrelated refactors out of the same pull request.
