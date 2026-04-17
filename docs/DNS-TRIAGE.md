# DNS failure triage (And-hole Android)

Use this when “it doesn’t work” — symptoms overlap but causes differ. Pair with **Diagnostics → Copy report** and filtered logcat.

## Logcat filters

- Upstream / DoT / Tor path: `adb logcat -s PiholeDns:D`
- Broader Android system DNS / VPN (when needed): `adb logcat | rg -i 'dns|vpn|netd'`

## Class 1 — Nothing on the loopback port

**Symptoms:** `connection refused`, ICMP port unreachable, or tools show nothing listening on `127.0.0.1:<port>`.

**Check Diagnostics / Runtime**

- `DNS foreground service`: should reflect the service lifecycle.
- `Bridge listener port`: must match your client (sing-box, etc.). Default is **53535** unless changed in preferences.
- `Listener (re)start cycles` / `Last listener (re)start`: rapid bumps can mean Room-driven reloads (Lists refresh, rules edits).

**Typical causes**

- **Listener not started:** Settings → **Start DNS listener** (or boot auto-start + reboot). The app does not bind a port until the foreground service is running.
- **Wrong port** in the client vs app preference.
- **OEM / battery** killed the foreground service (see [OEM-FGS-RELIABILITY.md](OEM-FGS-RELIABILITY.md)).
- **External VPN client:** Some setups are picky about `127.0.0.1` vs tun addresses; this app now listens on **UDP only**, so keep the client on UDP and set Android **Private DNS** to **Off** while testing.

## Class 2 — Port open, but resolution fails (SERVFAIL / timeouts)

**Symptoms:** Client reaches `127.0.0.1:<port>`, but answers are empty, SERVFAIL, or very slow.

**Check Diagnostics / Runtime**

- `Tor runtime:` line — should reach **Ready** before upstream-over-Tor is expected to work.
- `Tor runtime mode:` — shows whether the app is using **Embedded Arti**, **TorService compatibility**, or **auto fallback**.
- `Tor bootstrap progress` / `Tor bootstrap summary` — best-effort Arti bootstrap detail; useful when the runtime is still starting.
- `Tor transport detail:` — shows which runtime transport is actually active.
- `Tor last error` — the last embedded runtime/bootstrap failure string, if any.
- `Last upstream forward failure` — last DoT-over-Tor transport/TLS error string.

**Typical causes**

- **No network** or captive portal.
- **Tor still bootstrapping** (watch `Tor bootstrap progress` / `Tor bootstrap summary`; first embedded cold start can take time).
- **Forced embedded mode** failing on-device — temporarily switch Settings → `Tor runtime` to **Compatibility only** to confirm whether the problem is specific to the embedded engine.
- **Runtime transport usable but upstream still failing** — inspect `Last upstream forward failure` and `adb logcat -s PiholeDns:D`.

## Class 3 — Resolution works, blocking does not

**Symptoms:** Public names resolve, but ads or test domains are not blocked as expected.

**Check Diagnostics**

- **On-disk manifest:** `suffixDeny count` should be > 0 after a successful **Lists → Refresh**.
- **Data (Room):** compiled snapshot line should exist after refresh.

**Typical causes**

- Never ran **Lists → Refresh**; empty adlists; download errors on sources (HTTP URLs blocked: cleartext is off in release manifest).
- **Rules** don’t match the hostname you test (suffix vs exact vs regex).

## Class 4 — sing-box / VPN integration

**Symptoms:** Unpredictable only when another app owns the VPN/tun.

**Actions**

- Confirm the client is configured for **UDP** (And-hole now listens on **UDP only** on the configured port).
- Turn **Settings → Network → Private DNS** to **Off** while isolating issues.
- **And-hole “Logs” vs sing-box logs:** “Path: forwarded upstream (Tor+DoT)” only means **this app’s DNS server** received a query and passed it to Tor+DoT — it does **not** prove sing-box was the client (other apps, Private DNS, or tests can hit the same port).
- sing-box may **log** `127.0.0.1` while the kernel path uses another local address (see [sing-box #2722](https://github.com/SagerNet/sing-box/issues/2722) discussion). If refusal persists, enable **Settings → VPN clients (advanced) → listen on all interfaces** so the listener binds `0.0.0.0` (see in-app warning; trusted networks only).
