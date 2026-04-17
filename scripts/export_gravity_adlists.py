#!/usr/bin/env python3
"""
Export And-hole gravity.db adlist rows into default_adlists.json for the Android app.

Usage:
  python3 scripts/export_gravity_adlists.py /var/lib/pihole/gravity.db \\
    -o data/src/main/assets/default_adlists.json

Or stdout:
  python3 scripts/export_gravity_adlists.py ./gravity.db

Only http(s) addresses are emitted. Duplicate addresses (same URL, different adlist.type) are
deduplicated by keeping the first row ordered by id.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("gravity_db", type=Path, help="Path to And-hole gravity.db")
    p.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Write JSON here (default: stdout)",
    )
    args = p.parse_args()

    if not args.gravity_db.is_file():
        print(f"error: not a file: {args.gravity_db}", file=sys.stderr)
        return 1

    con = sqlite3.connect(f"file:{args.gravity_db}?mode=ro", uri=True)
    try:
        cur = con.execute(
            """
            SELECT address, enabled
            FROM adlist
            WHERE address LIKE 'http://%' OR address LIKE 'https://%'
            ORDER BY id ASC
            """
        )
        rows = cur.fetchall()
    finally:
        con.close()

    seen: set[str] = set()
    out: list[dict[str, object]] = []
    for address, enabled in rows:
        url = (address or "").strip()
        if not url:
            continue
        key = url.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append({"url": url, "enabled": bool(enabled)})

    text = json.dumps(out, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
