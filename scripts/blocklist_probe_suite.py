#!/usr/bin/env python3
"""
Manifest-driven blocklist verification: read device suffixDeny, probe blocked vs pass over UDP by default.

Requires: debuggable APK, compiled manifest (Refresh lists), DnsForegroundService listening.
"""
from __future__ import annotations

import argparse
import json
import os
import random
import subprocess
import sys
from typing import List, Optional, Sequence, Tuple, cast

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if _SCRIPT_DIR not in sys.path:
    sys.path.insert(0, _SCRIPT_DIR)

from dns_probe_lib import (
    Protocol,
    adb_forward_add,
    adb_forward_remove,
    adb_supports_udp_forward,
    build_query,
    dns_exchange,
    evaluate_expect,
    normalize_fqdn,
    random_query_id,
)

SUFFIX_DENY_KEY = "suffixDeny"
DEFAULT_PASS_CANDIDATES = ("example.com", "dns.google")


def normalize_manifest_entry(entry: str) -> str:
    return normalize_fqdn(entry.strip())


def is_usable_blocked(entry: str) -> bool:
    n = entry.strip().lower()
    if not n.endswith("."):
        n = n + "."
    body = n.rstrip(".")
    if not body or "." not in body:
        return False
    try:
        for label in body.split("."):
            label.encode("ascii")
            if not label or len(label) > 63:
                return False
    except UnicodeEncodeError:
        return False
    return True


def select_blocked(suffix_deny: Sequence[object], k: int) -> List[str]:
    out: List[str] = []
    for e in suffix_deny:
        if not isinstance(e, str):
            continue
        if not is_usable_blocked(e):
            continue
        out.append(normalize_fqdn(e))
        if len(out) >= k:
            break
    return out


def select_pass(suffix_deny: Sequence[object], candidates: Tuple[str, ...]) -> List[str]:
    deny_exact = {normalize_manifest_entry(x) for x in suffix_deny if isinstance(x, str)}
    out: List[str] = []
    for c in candidates:
        n = normalize_fqdn(c)
        if n not in deny_exact:
            out.append(n)
    return out


def fetch_manifest(adb: str, serial: Optional[str]) -> dict:
    cmd = [adb]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(
        [
            "shell",
            "run-as",
            "org.pihole.android",
            "cat",
            "files/compiled-snapshot/manifest.json",
        ],
    )
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(
            f"adb manifest fetch failed (exit {r.returncode}): {r.stderr or r.stdout or 'no output'}",
        )
    text = r.stdout.strip()
    if not text:
        raise RuntimeError("empty manifest from device (run Refresh lists / compile snapshot first)")
    return json.loads(text)


def resolve_protocols(transport: str, adb: str, serial: Optional[str]) -> List[Protocol]:
    if transport == "tcp":
        return ["tcp"]
    if transport == "udp":
        if not adb_supports_udp_forward(adb, serial):
            print(
                "ERROR: this adb build does not support udp: forwarding; use Google platform-tools or --transport tcp",
                file=sys.stderr,
            )
            sys.exit(2)
        return ["udp"]
    if transport == "both":
        if adb_supports_udp_forward(adb, serial):
            return ["tcp", "udp"]
        print(
            "WARN: adb has no udp forward; running TCP probes only. "
            "For UDP loopback without adb udp:, run instrumented test: "
            "./gradlew :app:connectedDebugAndroidTest "
            "--tests org.pihole.android.service.DnsUdpLoopbackInstrumentedTest",
            file=sys.stderr,
        )
        return ["tcp"]
    raise ValueError(transport)


def run_one(
    protocol: Protocol,
    local_port: int,
    remote_port: int,
    fqdn: str,
    expect: str,
    adb: str,
    serial: Optional[str],
    timeout: float,
    *,
    strict_pass: bool = False,
) -> int:
    adb_forward_add(protocol, local_port, remote_port, adb, serial)
    try:
        q = build_query(random_query_id(), fqdn)
        resp = dns_exchange(protocol, "127.0.0.1", local_port, q, timeout)
        code, msg = evaluate_expect(expect, fqdn, resp, protocol, strict_pass=strict_pass)
        print(msg)
        return code
    finally:
        adb_forward_remove(protocol, local_port, adb, serial)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--transport",
        choices=("tcp", "udp", "both"),
        default="udp",
        help="Probe transport (default udp). 'both' exercises every transport the target listener exposes.",
    )
    p.add_argument(
        "--blocked-count",
        type=int,
        default=3,
        help="Number of suffixDeny entries to probe as blocked (default 3)",
    )
    p.add_argument("--port", type=int, default=int(os.environ.get("DNS_PORT", "53535")))
    p.add_argument("--timeout", type=float, default=8.0)
    p.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    p.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    p.add_argument(
        "--no-test-host",
        action="store_true",
        help="Skip test.pi-hole.local diagnostic query",
    )
    p.add_argument(
        "--pass",
        dest="pass_candidates",
        default=",".join(DEFAULT_PASS_CANDIDATES),
        help="Comma-separated pass probe names (must not appear exactly in suffixDeny)",
    )
    p.add_argument(
        "--strict-pass",
        action="store_true",
        help="For expect=pass: fail on empty response, non-NOERROR rcode, or ANCOUNT<1 (host probes only).",
    )
    args = p.parse_args()

    try:
        data = fetch_manifest(args.adb, args.serial)
    except (RuntimeError, json.JSONDecodeError) as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2

    suffix = data.get(SUFFIX_DENY_KEY)
    if not isinstance(suffix, list) or len(suffix) == 0:
        print(
            "ERROR: suffixDeny missing or empty — Refresh lists and wait for compile on device first.",
            file=sys.stderr,
        )
        return 2

    blocked = select_blocked(suffix, args.blocked_count)
    if len(blocked) < args.blocked_count:
        print(
            f"ERROR: need {args.blocked_count} usable multi-label blocked names, got {len(blocked)}",
            file=sys.stderr,
        )
        return 2

    candidates = tuple(x.strip() for x in args.pass_candidates.split(",") if x.strip())
    pass_names = select_pass(suffix, candidates)
    if not pass_names:
        print(
            "ERROR: no pass candidates left (all listed in suffixDeny). Change --pass list.",
            file=sys.stderr,
        )
        return 2

    cases: List[Tuple[str, str]] = []
    if not args.no_test_host:
        cases.append((normalize_fqdn("test.pi-hole.local"), "test-host"))
    for b in blocked:
        cases.append((b, "blocked"))
    for n in pass_names:
        cases.append((n, "pass"))

    protos = resolve_protocols(args.transport, args.adb, args.serial)
    for proto in protos:
        pcast = cast(Protocol, proto)
        for fqdn, expect in cases:
            local_port = 15350 + random.randint(0, 499)
            try:
                rc = run_one(
                    pcast,
                    local_port,
                    args.port,
                    fqdn,
                    expect,
                    args.adb,
                    args.serial,
                    args.timeout,
                    strict_pass=args.strict_pass and expect == "pass",
                )
            except subprocess.CalledProcessError as e:
                print(f"ERROR: adb forward failed: {e.stderr or e}", file=sys.stderr)
                return 2
            if rc != 0:
                return rc

    print(f"OK: blocklist_probe_suite ({args.transport}) all {len(cases)} case(s) x {len(protos)} transport(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
