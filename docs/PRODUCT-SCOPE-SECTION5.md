# Product scope — “Section 5” and release posture

This document captures items called **out of scope** in [MANUAL-VERIFICATION.md](../MANUAL-VERIFICATION.md) so they stay explicit product choices rather than forgotten gaps.

## Live Tor / DoT on-device proof

- **Unit / instrumented tests** exercise encoding, loopback DNS, and **DnsEndToEndInstrumentedTest** (Tor + DoT path when network allows).
- **Full production SLO** (bootstrap time, captive portals, airplane mode recovery) is **not** guaranteed by tests alone; track in release notes when tightening.

## Full adlist / scale E2E

- Large list refresh, memory, and time-to-first-query are validated by **device soak** and **Lists → Refresh**, not a single CI job.
- For CI, prefer **Tier 1** JVM tests + **targeted** instrumented tests; optional long-run job documented in [scripts/verify-harness.sh](../scripts/verify-harness.sh).

## Play Console / OEM battery policy

- **Foreground service type `specialUse`** is declared with a subtype string; OEMs may still kill or restrict background work.
- **Play policy** changes over time; before Play release, re-check **foreground service justification**, **data safety**, and **VPN/DNS** adjacent policies even though this app is **not** a VPN.

## sing-box end-to-end

- Record outcomes in [2026-04-08-sing-box-test-notes.md](../2026-04-08-sing-box-test-notes.md).
- Use [scripts/sing_box_loopback_hint.sh](../scripts/sing_box_loopback_hint.sh) as a quick reminder when a device has no `sing-box` binary.
