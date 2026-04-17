#!/usr/bin/env bash
# Automated verification harness for And-hole Android (tiers 1–3 + optional UDP loopback DNS probe).
# Usage: ./scripts/verify-harness.sh [options]
# Options:
#   --tier1-only       Run JVM tests + assemble + KSP only (no device).
#   --no-connected       Skip Gradle connectedDebugAndroidTest.
#   --no-adb             Skip install / launch / optional DNS probe.
#   --dns-probe          After starting DnsForegroundService via adb, send a UDP DNS query
#                        for test.pi-hole.local (default port 53535). Requires --no-adb false.
#   --query-log-check    After running probes, fetch query_log row count via debug broadcast receiver.
#   --dns-probe-suite    After starting the DNS listener, run scripts/blocklist_probe_suite.py (manifest-driven
#                        suffixDeny blocked + pass probes, plus test.pi-hole.local). Default --transport udp.
#                        Requires python3 + debug APK + non-empty compiled manifest.
#   --nav-smoke          On-device only: run FeatureScreensNavTest (bottom nav + primary content tags).
#   --dns-e2e            On-device only: run DnsEndToEndInstrumentedTest (blocklist NULL A + Tor DoT NOERROR).
#                        Requires USB device, network, Tor bootstrap, and compiled manifest for block test.
#   --assemble           Force ./gradlew :app:assembleDebug before adb install.
#   --apk PATH           Path to APK (default: app/build/outputs/apk/debug/app-debug.apk).
#   --port N             DNS listen port for probe (default: 53535).
#   --serial ID          Pass to adb -s ID (else uses ANDROID_SERIAL if set).
# Environment:
#   ADB                  adb binary (default: adb). ANDROID_SERIAL is respected when unset.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ADB="${ADB:-adb}"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
DNS_PORT="${DNS_PORT:-53535}"
PKG="org.pihole.android"
MAIN_COMPONENT="${PKG}/.MainActivity"
FGS_COMPONENT="${PKG}/.service.DnsForegroundService"
# Debug APK only: FGS from broadcast is denied on API 34+; use exported debug activity.
DEBUG_START_DNS_ACTIVITY="${PKG}/.debug.DebugStartDnsActivity"
DEBUG_STOP_DNS_RECEIVER="${PKG}/.debug.DebugDnsReceiver"
DEBUG_QUERY_LOG_STATS_ACTION="org.pihole.android.debug.QUERY_LOG_STATS"

TIER1_ONLY=0
NO_CONNECTED=0
NO_ADB=0
DNS_PROBE=0
DNS_PROBE_SUITE=0
QUERY_LOG_CHECK=0
DNS_E2E=0
NAV_SMOKE=0
ASSEMBLE=0

adb() { command "$ADB" "$@"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tier1-only) TIER1_ONLY=1 ;;
    --no-connected) NO_CONNECTED=1 ;;
    --no-adb) NO_ADB=1 ;;
    --dns-probe) DNS_PROBE=1 ;;
    --query-log-check) QUERY_LOG_CHECK=1 ;;
    --dns-probe-suite) DNS_PROBE_SUITE=1 ;;
    --dns-e2e) DNS_E2E=1 ;;
    --nav-smoke) NAV_SMOKE=1 ;;
    --assemble) ASSEMBLE=1 ;;
    --apk) APK="$2"; shift ;;
    --port) DNS_PORT="$2"; shift ;;
    --serial) export ANDROID_SERIAL="$2"; shift ;;
    -h|--help)
      cat <<'EOF'
Usage: ./scripts/verify-harness.sh [options]

Runs Tier 1 (Gradle test, assembleDebug, data KSP), Tier 2 (connected tests if a device
is connected), Tier 3 (adb install + MainActivity cold start), and optional UDP DNS probes.

Options:
  --tier1-only     JVM tests + assemble + KSP only (no device).
  --no-connected   Skip connectedDebugAndroidTest.
  --no-adb         Skip install, launch, and DNS probes.
  --dns-probe      Start DnsForegroundService via adb and probe 127.0.0.1:<port> with UDP DNS.
  --query-log-check  Fetch `query_log` row count via debug broadcast receiver.
  --dns-probe-suite  Manifest-driven blocklist checks (scripts/blocklist_probe_suite.py, UDP by default; needs python3).
  --nav-smoke        Run FeatureScreensNavTest only (replaces full Tier 2 app connected suite when set).
  --dns-e2e          Run DnsEndToEndInstrumentedTest on device (authoritative block + upstream resolution).
  (For sing-box loopback hints: scripts/sing_box_loopback_hint.sh — not run by this harness.)
  --assemble       Always run :app:assembleDebug before adb install.
  --apk PATH       APK path (default: app/build/outputs/apk/debug/app-debug.apk).
  --port N         DNS port for --dns-probe / --dns-probe-suite (default: 53535).
  --serial ID      Set ANDROID_SERIAL for adb.

Environment: ADB (default: adb), ANDROID_SERIAL, DNS_PORT.
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
  shift
done

log() { printf '%s\n' "$*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

have_device() {
  adb get-state 1>/dev/null 2>&1 || return 1
  [[ "$(adb get-state 2>/dev/null)" == "device" ]]
}

run_tier1() {
  log "=== Tier 1: JVM tests, assembleDebug, KSP (data) ==="
  ./gradlew test :app:assembleDebug :data:kspDebugKotlin
}

run_tier2() {
  if [[ "$NO_CONNECTED" -eq 1 ]]; then
    log "=== Tier 2: skipped (--no-connected) ==="
    return 0
  fi
  if ! have_device; then
    log "=== Tier 2: skipped (no adb device) ==="
    return 0
  fi
  if [[ "$NAV_SMOKE" -eq 1 ]]; then
    log "=== Tier 2: FeatureScreensNavTest only (--nav-smoke) ==="
    ./gradlew :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.FeatureScreensNavTest \
      || fail "FeatureScreensNavTest failed"
    return 0
  fi
  log "=== Tier 2: connectedDebugAndroidTest (app + data) ==="
  ./gradlew :app:connectedDebugAndroidTest :data:connectedDebugAndroidTest
}

ensure_apk() {
  if [[ -f "$APK" ]] && [[ "$ASSEMBLE" -eq 0 ]]; then
    return 0
  fi
  log "=== Building debug APK ==="
  ./gradlew :app:assembleDebug
  APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  [[ -f "$APK" ]] || fail "APK not found after assemble: $APK"
}

run_adb_smoke() {
  if [[ "$NO_ADB" -eq 1 ]]; then
    log "=== Tier 3 adb: skipped (--no-adb) ==="
    return 0
  fi
  if ! have_device; then
    log "=== Tier 3 adb: skipped (no adb device) ==="
    return 0
  fi
  ensure_apk
  log "=== Tier 3: adb install + cold start MainActivity ==="
  adb install -r "$APK"
  adb shell am force-stop "$PKG" 2>/dev/null || true
  out="$(adb shell am start -W -n "$MAIN_COMPONENT" 2>&1)" || fail "am start failed"
  echo "$out"
  echo "$out" | grep -q 'Status: ok' || fail "MainActivity start did not report ok status"
  log "Tier 3 install + launch: OK"
}

run_dns_probe() {
  if [[ "$DNS_PROBE" -eq 0 ]]; then
    return 0
  fi
  if [[ "$NO_ADB" -eq 1 ]]; then
    fail "--dns-probe requires adb (remove --no-adb)"
  fi
  if ! have_device; then
    fail "--dns-probe requires a connected device"
  fi
  command -v python3 >/dev/null 2>&1 || fail "python3 required for --dns-probe"
  [[ -x "$ROOT/scripts/dns_tcp_probe.py" ]] || fail "missing executable $ROOT/scripts/dns_tcp_probe.py"
  log "=== Optional: DNS loopback probe (UDP DNS via adb forward → test.pi-hole.local) ==="
  log "Starting DnsForegroundService via debug activity, port ${DNS_PORT}..."
  # Non-exported FGS cannot be started via `am start-foreground-service`; broadcast FGS is denied on API 34+.
  start_out="$(adb shell am start -W -n "$DEBUG_START_DNS_ACTIVITY" 2>&1)" || true
  echo "$start_out"
  echo "$start_out" | grep -q 'Status: ok' || fail "DebugStartDnsActivity did not start (install debug APK)"
  sleep 6
  python3 "$ROOT/scripts/dns_tcp_probe.py" test.pi-hole.local \
    --expect test-host \
    --protocol udp \
    --port "$DNS_PORT" \
    --adb "$ADB" \
    || fail "dns_tcp_probe.py failed (adb udp: forward required; otherwise use DnsUdpLoopbackInstrumentedTest)"
  adb shell am broadcast -a org.pihole.android.debug.STOP_DNS -n "$DEBUG_STOP_DNS_RECEIVER" 2>/dev/null || true
  adb shell am stopservice "$FGS_COMPONENT" 2>/dev/null || true
  adb shell am force-stop "$PKG" 2>/dev/null || true
  log "DNS probe: OK"
}

run_dns_probe_suite() {
  if [[ "$DNS_PROBE_SUITE" -eq 0 ]]; then
    return 0
  fi
  if [[ "$NO_ADB" -eq 1 ]]; then
    fail "--dns-probe-suite requires adb (remove --no-adb)"
  fi
  if ! have_device; then
    fail "--dns-probe-suite requires a connected device"
  fi
  command -v python3 >/dev/null 2>&1 || fail "python3 required for --dns-probe-suite"
  [[ -x "$ROOT/scripts/blocklist_probe_suite.py" ]] || fail "missing executable $ROOT/scripts/blocklist_probe_suite.py"
  log "=== Blocklist DNS suite (manifest-driven UDP via blocklist_probe_suite.py, port ${DNS_PORT}) ==="
  log "Starting DnsForegroundService via debug activity..."
  start_out="$(adb shell am start -W -n "$DEBUG_START_DNS_ACTIVITY" 2>&1)" || true
  echo "$start_out"
  echo "$start_out" | grep -q 'Status: ok' || fail "DebugStartDnsActivity did not start (install debug APK)"
  sleep 8
  export DNS_PORT
  python3 "$ROOT/scripts/blocklist_probe_suite.py" --port "$DNS_PORT" --adb "$ADB" --transport udp \
    || fail "blocklist_probe_suite.py failed (empty manifest: Refresh lists on device first; see MANUAL-VERIFICATION.md)"
  log "DNS probe suite: OK"
  adb shell am broadcast -a org.pihole.android.debug.STOP_DNS -n "$DEBUG_STOP_DNS_RECEIVER" 2>/dev/null || true
  adb shell am stopservice "$FGS_COMPONENT" 2>/dev/null || true
  adb shell am force-stop "$PKG" 2>/dev/null || true
}

run_dns_e2e() {
  if [[ "$DNS_E2E" -eq 0 ]]; then
    return 0
  fi
  if [[ "$NO_ADB" -eq 1 ]]; then
    fail "--dns-e2e requires adb (remove --no-adb)"
  fi
  if ! have_device; then
    fail "--dns-e2e requires a connected device"
  fi
  log "=== DNS E2E: DnsEndToEndInstrumentedTest (on-device UDP; Tor + manifest) ==="
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsEndToEndInstrumentedTest \
    || fail "DnsEndToEndInstrumentedTest failed (refresh lists, network, Tor; see MANUAL-VERIFICATION.md)"
  log "DNS E2E: OK"
}

run_query_log_check() {
  if [[ "$QUERY_LOG_CHECK" -eq 0 ]]; then
    return 0
  fi
  if [[ "$NO_ADB" -eq 1 ]]; then
    fail "--query-log-check requires adb (remove --no-adb)"
  fi
  if ! have_device; then
    fail "--query-log-check requires a connected device"
  fi
  log "=== Optional: query_log count (debug broadcast) ==="
  adb shell logcat -c 2>/dev/null || true
  adb shell am broadcast -a "$DEBUG_QUERY_LOG_STATS_ACTION" -n "$PKG/.debug.DebugQueryLogReceiver" 1>/dev/null 2>&1 || true
  # Let receiver log a line like: "I DebugQueryLog: query_log count=N"
  sleep 1
  out="$(adb logcat -b all -d -s DebugQueryLog:I 2>/dev/null || true)"
  echo "$out"
  count="$(echo "$out" | grep -oE 'query_log count=[0-9]+' | tail -1 | cut -d= -f2)"
  [[ -n "${count:-}" ]] || fail "could not parse query_log count from logcat (is debug APK installed?)"
  log "query_log count=${count}"
  [[ "$count" -ge 1 ]] || fail "query_log count is 0; generate DNS queries then re-run with --dns-probe"
}

main() {
  run_tier1
  if [[ "$TIER1_ONLY" -eq 1 ]]; then
    log "=== Done (--tier1-only) ==="
    exit 0
  fi
  run_tier2
  run_adb_smoke
  run_dns_probe
  run_dns_probe_suite
  run_dns_e2e
  run_query_log_check
  log "=== verify-harness: complete ==="
}

main
