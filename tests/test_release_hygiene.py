import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

# Patterns that must never appear in a tracked file. Kept narrow (real key/token shapes) so
# this doesn't flag the word "key" or placeholder examples in docs.
SECRET_PATTERNS = [
    re.compile(r"sk-[A-Za-z0-9_-]{20,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
]

# Paths that must stay untracked: local secrets/config, signing material, and real-device
# session output. Each entry is also checked against .gitignore below.
DANGEROUS_TRACKED_PATH_PATTERNS = [
    re.compile(r"(^|/)local\.properties$"),
    re.compile(r"(^|/)\.env(\..*)?$"),
    re.compile(r"\.(keystore|jks|p12|pem)$"),
    re.compile(r"(^|/)sessions/"),
    re.compile(r"(^|/)device-sessions/"),
    re.compile(r"(^|/)reports/"),
    re.compile(r"(^|/)\.DS_Store$"),
]

# Generated build output that must stay out of the tracked file list.
GENERATED_DIR_PREFIXES = ("app/build/", ".gradle/", ".kotlin/", "__pycache__/")

REQUIRED_GITIGNORE_ENTRIES = [
    "local.properties",
    ".env",
    "*.keystore",
    "*.jks",
    "*.p12",
    "*.pem",
    "build/",
    "sessions/",
    "device-sessions/",
    "reports/",
    ".DS_Store",
    "__pycache__/",
]


def tracked_files():
    output = subprocess.run(
        ["git", "ls-files"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return [line for line in output.splitlines() if line]


class ReleaseHygieneTest(unittest.TestCase):
    def test_no_tracked_file_contains_a_secret_shaped_value(self):
        offenders = []
        for relative_path in tracked_files():
            path = ROOT / relative_path
            if not path.is_file():
                continue
            try:
                text = path.read_text(errors="ignore")
            except (UnicodeDecodeError, OSError):
                continue
            for pattern in SECRET_PATTERNS:
                if pattern.search(text):
                    offenders.append(f"{relative_path}: matched {pattern.pattern}")
        self.assertEqual([], offenders)

    def test_no_dangerous_or_generated_paths_are_tracked(self):
        files = tracked_files()
        dangerous = [
            path
            for path in files
            if any(pattern.search(path) for pattern in DANGEROUS_TRACKED_PATH_PATTERNS)
        ]
        generated = [
            path
            for path in files
            if any(path.startswith(prefix) for prefix in GENERATED_DIR_PREFIXES)
        ]
        self.assertEqual([], dangerous)
        self.assertEqual([], generated)

    def test_gitignore_covers_every_dangerous_and_generated_path_pattern(self):
        gitignore = (ROOT / ".gitignore").read_text()
        missing = [
            entry for entry in REQUIRED_GITIGNORE_ENTRIES if entry not in gitignore
        ]
        self.assertEqual([], missing)

    def test_license_file_exists_and_is_referenced_by_readme(self):
        license_text = (ROOT / "LICENSE").read_text()
        self.assertIn("MIT License", license_text)
        readme = (ROOT / "README.md").read_text()
        self.assertIn("MIT License", readme)
        self.assertIn("LICENSE", readme)

    def test_public_docs_cover_the_required_safety_topics(self):
        readme = (ROOT / "README.md").read_text()
        contributing = (ROOT / "CONTRIBUTING.md").read_text()
        security = (ROOT / "SECURITY.md").read_text()

        for doc_name, text, required_phrases in (
            ("README.md", readme, ["API", "sessions/", "SECURITY.md", "CONTRIBUTING.md"]),
            ("CONTRIBUTING.md", contributing, ["API", "signing", "raw THINKLET recordings"]),
            ("SECURITY.md", security, ["vulnerabilit"]),
        ):
            for phrase in required_phrases:
                self.assertIn(
                    phrase,
                    text,
                    f"{doc_name} is missing expected content: {phrase!r}",
                )
        self.assertNotIn("Before the public repository is finalized", security)

    def test_modern_openai_key_shape_is_detected(self):
        self.assertTrue(SECRET_PATTERNS[0].search("sk-proj-" + "a" * 32))

    def test_readme_setup_instructions_do_not_reference_a_missing_subdirectory(self):
        readme = (ROOT / "README.md").read_text()
        self.assertNotIn("cd thinklet-realtime-companion", readme)
        contributing = (ROOT / "CONTRIBUTING.md").read_text()
        self.assertNotIn("cd thinklet-realtime-companion", contributing)

    def test_ci_workflow_still_runs_android_build_and_python_tests(self):
        workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text()
        self.assertIn("./gradlew build", workflow)
        self.assertIn("python3 -m unittest discover -s tests", workflow)


if __name__ == "__main__":
    unittest.main()
