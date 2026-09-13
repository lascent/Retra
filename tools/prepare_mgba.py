#!/usr/bin/env python3
"""Prepare Retra's project-local mGBA checkout at the locked revision."""
from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCK = ROOT / "third_party" / "mgba.lock"
DEST = ROOT / "third_party" / "mgba"


def read_lock() -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in LOCK.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep:
            raise SystemExit(f"Invalid lock line: {raw}")
        result[key.strip()] = value.strip()
    if not result.get("repository") or not result.get("revision"):
        raise SystemExit("third_party/mgba.lock must define repository and revision")
    return result


def run(*args: str, cwd: pathlib.Path | None = None) -> str:
    proc = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode:
        sys.stderr.write(proc.stderr)
        raise SystemExit(proc.returncode)
    return proc.stdout.strip()


def main() -> None:
    lock = read_lock()
    repo = lock["repository"]
    revision = lock["revision"].lower()

    git = shutil.which("git")
    if not git:
        raise SystemExit("Git is required to prepare the locked mGBA source checkout")

    if not DEST.exists():
        DEST.parent.mkdir(parents=True, exist_ok=True)
        run(git, "clone", "--filter=blob:none", "--no-checkout", repo, str(DEST))
    elif not (DEST / ".git").exists():
        raise SystemExit(f"{DEST} exists but is not a Git checkout; move it aside and rerun")

    run(git, "fetch", "--depth=1", "origin", revision, cwd=DEST)
    run(git, "checkout", "--detach", revision, cwd=DEST)
    actual = run(git, "rev-parse", "HEAD", cwd=DEST).lower()
    if actual != revision:
        raise SystemExit(f"mGBA revision mismatch: expected {revision}, got {actual}")

    dirty = run(git, "status", "--porcelain", "--untracked-files=no", cwd=DEST)
    if dirty:
        raise SystemExit("mGBA checkout has tracked local modifications after checkout")

    (DEST / ".retra-mgba-revision").write_text(revision + "\n", encoding="utf-8")
    print(f"mGBA ready at {revision}")


if __name__ == "__main__":
    main()
