#!/usr/bin/env python3
"""Validate store/listing.md against Google Play character limits.

Play allows at most 80 characters for the short description and 4000 for the
full description, per localisation. This script extracts every fenced block in
store/listing.md and reports the counts, failing the build of the release
notes if any limit is exceeded.

Usage: python3 tools/check_listing.py
"""

from __future__ import annotations

import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(REPO, "store", "listing.md")

SHORT_LIMIT = 80
FULL_LIMIT = 4000


def blocks(text: str):
    """Yield (heading, body) for each fenced block with its heading context."""
    heading = ""
    inside = False
    body = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            if inside:
                yield heading, "\n".join(body).strip("\n")
                body = []
            inside = not inside
            continue
        if inside:
            body.append(line)
        elif line.startswith("#"):
            heading = line.strip("# ").strip()


def main() -> int:
    with open(PATH, encoding="utf-8") as fh:
        text = fh.read()

    failures = 0
    for heading, body in blocks(text):
        lowered = heading.lower()
        if "short description" in lowered:
            limit = SHORT_LIMIT
        elif "full description" in lowered:
            limit = FULL_LIMIT
        else:
            continue
        length = len(body)
        status = "OK  " if length <= limit else "OVER"
        if length > limit:
            failures += 1
        print(f"[{status}] {heading}: {length}/{limit} characters")

    if failures:
        print(f"\n{failures} block(s) exceed the Play limit.")
        return 1
    print("\nAll store-listing blocks are within Play limits.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
