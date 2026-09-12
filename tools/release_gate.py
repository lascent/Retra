#!/usr/bin/env python3
"""Dependency-light Retra release gate.

Runs the repository checks that do not require the Android SDK/NDK or mGBA.
Android/Kotlin/lint/native build checks remain in Gradle/CI.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(label: str, command: list[str]) -> None:
    print(f"\n== {label} ==")
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)


def main() -> int:
    js_files = sorted((ROOT / "app/src/main/assets/retra").glob("*.js"))
    if not js_files:
        print("No JavaScript assets found", file=sys.stderr)
        return 2

    for js_file in js_files:
        run(f"JavaScript syntax: {js_file.name}", ["node", "--check", str(js_file)])

    test_files = [str(path) for path in sorted((ROOT / "tests").glob("*.test.cjs"))]
    run("Regression tests", ["node", "--test", *test_files])
    run("Release structure validation", [sys.executable, "tools/validate_release.py"])

    required = [
        "README.md",
        "LICENSE",
        "THIRD_PARTY_NOTICES.md",
        "CHANGELOG.md",
        "SECURITY.md",
        "CONTRIBUTING.md",
        "docs/RELEASE_NOTES_v1.0.0.md",
        "docs/RELEASE_CHECKLIST_v1.0.0.md",
        "docs/DEVELOPMENT_HISTORY.md",
        "docs/README.md",
    ]
    missing = [name for name in required if not (ROOT / name).is_file()]
    if missing:
        print("Missing release files:", ", ".join(missing), file=sys.stderr)
        return 3

    print("\nRetra dependency-light release gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
