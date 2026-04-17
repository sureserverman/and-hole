# Foreground service reliability (OEM / battery)

And-hole Android’s loopback DNS runs in a **foreground service** (`DnsForegroundService`). Android and OEMs can still stop or restrict it.

## Universal Android

- **Battery saver** and **restricted background** reduce how long services stay alive.
- **User must allow notifications** (Android 13+) for the foreground notification channel used by the service.
- **Special-use FGS** (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`) requires manifest permission and, on Google Play, a declared justification. Sideloaded builds still hit OEM policy.

## OEM-specific patterns (non-exhaustive)

**Xiaomi / HyperOS / MIUI (incl. many ROM ports)**  
“Autostart”, “Battery saver” per-app, and “Display pop-up windows while running in background” historically affect background starts. Prefer **Unrestricted** battery for the app when you rely on boot auto-start or long sessions.

**Samsung One UI**  
“Sleeping apps” and “Deep sleeping apps” can freeze background work; add an exception for And-hole Android if the listener drops.

**LineageOS / AOSP-based custom ROMs**  
Generally closer to stock, but **Trust** / **Privacy Guard** and per-app battery settings still apply. If boot `BootReceiver` does not start the listener, confirm **auto-start** is enabled in app Settings and that the system did not block **foreground service starts from background** for sideloaded apps.

**OxygenOS / ColorOS / Realme / Oppo**  
Aggressive “app startup management” — allow automatic start for the app when using boot auto-start.

## What to do when the listener vanishes

1. Re-open the app → **Settings** → **Start DNS listener** (watch for the ongoing notification).
2. Set app battery to **Unrestricted** (wording varies by OEM).
3. Capture **Diagnostics → Copy report** after a failure (shows last listener restart time and Tor/upstream lines).
