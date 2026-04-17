#!/usr/bin/env bash
# Hint script for sing-box → app loopback DNS (grand plan item: real client proof).
# Does not install sing-box; use after USB debugging is enabled.

set -euo pipefail
echo "=== sing-box / loopback checklist ==="
echo "1. Install a sing-box binary or VPN client that can use custom DNS on the device,"
echo "   or run sing-box on a host and forward to the phone (advanced)."
echo "2. Start the app DNS listener: Settings → Start DNS listener (or boot auto-start)."
echo "3. Point sing-box DNS at loopback (see Home for port, default 53535):"
echo "   Prefer TCP for SFA/graphical clients with TUN+gvisor (e.g. type \"tcp\" server 127.0.0.1 port);"
echo "   UDP also works from the app side (see DnsUdpLoopbackInstrumentedTest)."
echo ""
if command -v adb >/dev/null 2>&1; then
  echo "adb: device sing-box on PATH?"
  adb shell command -v sing-box 2>/dev/null || echo "  (not found — expected on stock devices)"
else
  echo "adb not in PATH; skipped device check."
fi
echo ""
echo "See: 2026-04-08-sing-box-test-notes.md and MANUAL-VERIFICATION.md (Tier 3)."
