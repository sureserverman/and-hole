#!/usr/bin/env python3
"""
DNS probe for And-hole Android loopback listener.

TCP: RFC 1035 length prefix + message (same as DnsTcpServer).
UDP: raw DNS message datagram (same as UdpDnsServer).

Used with: adb forward udp:LOCAL udp:PORT (default) or adb forward tcp:LOCAL tcp:PORT.

Expect modes:
  blocked   — A answer RDATA 0.0.0.0 (heuristic: last 4 bytes zero).
  pass      — no reply (timeout / EOF / empty).
  test-host — test.pi-hole.local → 192.0.2.1 (last 4 bytes).
"""
from __future__ import annotations

import argparse
import os
import random
import subprocess
import sys
from typing import cast

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if _SCRIPT_DIR not in sys.path:
    sys.path.insert(0, _SCRIPT_DIR)

from dns_probe_lib import (
    Protocol,
    adb_forward_add,
    adb_forward_remove,
    build_query,
    dns_exchange,
    evaluate_expect,
    normalize_fqdn,
    random_query_id,
)


def main() -> int:
    p = argparse.ArgumentParser(
        description="DNS probe via adb forward to And-hole Android listener (TCP or UDP).",
    )
    p.add_argument("fqdn", help="Query name (e.g. 0.fls.doubleclick.net. or example.com.)")
    p.add_argument(
        "--expect",
        choices=("blocked", "pass", "test-host"),
        required=True,
        help="blocked=0.0.0.0 A; pass=no reply; test-host=192.0.2.1",
    )
    p.add_argument(
        "--protocol",
        choices=("tcp", "udp"),
        default="udp",
        help="Transport (default udp). TCP is kept for non-app probes or legacy troubleshooting.",
    )
    p.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("DNS_PORT", "53535")),
        help="Device DNS listen port (TCP and UDP use same port on device)",
    )
    p.add_argument("--timeout", type=float, default=8.0, help="Socket timeout seconds")
    p.add_argument("--adb", default=os.environ.get("ADB", "adb"), help="adb binary")
    p.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="adb -s")
    p.add_argument(
        "--no-forward",
        action="store_true",
        help="Connect to 127.0.0.1:--local-port directly (forward already set up)",
    )
    p.add_argument("--local-port", type=int, default=0, help="Local port (--no-forward) or 0=random with forward")
    args = p.parse_args()

    proto = cast(Protocol, args.protocol)
    local_port = args.local_port
    if not args.no_forward:
        if local_port <= 0:
            local_port = 15350 + random.randint(0, 499)
        try:
            adb_forward_add(proto, local_port, args.port, args.adb, args.serial)
        except subprocess.CalledProcessError as e:
            err = (e.stderr or str(e) or "").lower()
            hint = ""
            if args.protocol == "udp" and ("udp" in err or "unknown socket" in err):
                hint = (
                    " (for UDP without adb udp: forward, run: ./gradlew :app:connectedDebugAndroidTest "
                    "--tests org.pihole.android.service.DnsUdpLoopbackInstrumentedTest)"
                )
            print(f"ERROR: adb forward failed: {e.stderr or e}{hint}", file=sys.stderr)
            return 2
    else:
        if local_port <= 0:
            print("ERROR: --no-forward requires --local-port", file=sys.stderr)
            return 2

    name = normalize_fqdn(args.fqdn)
    try:
        qid = random_query_id()
        query = build_query(qid, name)
        resp = dns_exchange(proto, "127.0.0.1", local_port, query, args.timeout)
        code, msg = evaluate_expect(args.expect, name, resp, proto)
        print(msg)
        return code
    finally:
        if not args.no_forward:
            adb_forward_remove(proto, local_port, args.adb, args.serial)


if __name__ == "__main__":
    sys.exit(main())
