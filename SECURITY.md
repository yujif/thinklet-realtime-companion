# Security Policy

## Supported Versions

This repository is a prototype Android app for THINKLET + OpenAI Realtime API verification.
It does not have stable public releases yet.

| Version or branch | Security support |
| --- | --- |
| `main` | Best-effort fixes for the current prototype |
| Older prototype branches, forks, or local snapshots | Not supported |

## Reporting a Vulnerability

Please report security vulnerabilities privately.

Use GitHub private vulnerability reporting or a repository security advisory if it is enabled for this repository.
If private vulnerability reporting is not available, open a public issue asking for a private security contact, but do not include vulnerability details in that public issue.

Do not disclose vulnerability details in public issues, pull requests, discussions, or review comments before a fix is available.

## What to Include

When reporting a vulnerability, include:

- Affected branch, commit, or app version
- Environment details, including Android version, device type, and whether THINKLET hardware is involved
- Reproduction steps
- Expected impact
- Any minimal proof of concept needed to demonstrate the issue

Do not include real OpenAI API keys, bearer tokens, Android signing materials, private keys, raw THINKLET recordings, camera frames, or real session logs.
Use redacted data or synthetic test data whenever possible.

## Scope

Reports are in scope when they affect this repository's Android app code, helper scripts, sample configuration, or documentation in a way that could compromise confidentiality, integrity, availability, or user privacy.

Third-party service vulnerabilities, THINKLET platform vulnerabilities, Josee TTS vulnerabilities, Android platform vulnerabilities, or OpenAI API vulnerabilities should be reported to the relevant upstream project or vendor.

## Response Expectations

This prototype has no formal security response SLA yet.
Maintainers should acknowledge valid private reports on a best-effort basis and avoid public disclosure until a fix or mitigation is available.
