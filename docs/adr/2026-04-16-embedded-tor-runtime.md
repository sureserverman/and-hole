# Embedded Tor Runtime Decision

## Status

Accepted and implemented, with staged rollout controls.

## Context

The current upstream DNS path depends on loopback SOCKS:

- Tor lifecycle: `core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt`
- readiness: `TorController.tryConnectSocks()`
- transport: `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt#openOverSocks`
- app wiring: `app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt`

Current request path:

```text
DnsServerController
  -> TorDotDnsUpstream
    -> DotClient
      -> Socket(Proxy(Type.SOCKS, 127.0.0.1:9050))
        -> TorService
          -> Tor network
```

Target path:

```text
DnsServerController
  -> TorDotDnsUpstream
    -> TorStreamDialer.connect(host, port)
      -> embedded Tor engine
        -> Tor network
```

This migration exists because loopback SOCKS is the wrong long-term boundary for an app-internal upstream transport. It couples readiness to local TCP probing, forces `DotClient` to depend on `Socket`/`SSLSocket`, and makes the Tor transport look like an external sidecar instead of a runtime capability.

## Required capabilities

The selected embedded runtime must provide all of the following:

- bootstrap state callback or poll API
- outbound stream dial by hostname and port
- no mandatory public SOCKS listener
- embeddable in Android app/service lifecycle
- recoverable close/restart semantics

## Reject if any are missing

Abort the migration if the candidate runtime:

- only exposes daemon-style local ports
- requires root or system privileges
- cannot preserve remote hostname resolution through Tor
- cannot be packaged into this app without introducing a new native bridge/toolchain plan

## Baseline and risks

Current boundary:

- Tor lifecycle: `TorController`
- readiness: `tryConnectSocks()`
- transport: `DotClient.openOverSocks()`

Primary migration risks:

- no usable engine API
- TLS over generic stream complexity
- background service lifecycle regressions
- native bridge build and packaging complexity on Android

## Feasibility assessment

The current `tor-android` integration does not satisfy the target boundary. In this repo it exposes `TorService`, and the app currently proves readiness by probing `127.0.0.1:9050`.

Arti appears to satisfy the API shape required by the target architecture:

- it can bootstrap as an embeddable client,
- it can open outbound streams directly by `(hostname, port)`,
- it does not require a public SOCKS listener for that stream API.

However, the official Arti Android guidance also states that:

- Arti does not provide ready-made Java/Kotlin bindings,
- Android integration currently requires writing a JNI bridge,
- Android builds require Rust targets plus NDK and Cargo-based native packaging.

That means the migration is only feasible if we explicitly accept a new native integration track:

- Rust crate(s) in or alongside this repo,
- Cargo + Android NDK build wiring,
- JNI surface for bootstrap state, stream open, read/write, and close,
- lifecycle and crash containment for the native runtime.

## Decision

Proceed with the migration by introducing an Android native bridge based on embedded Arti.

The shipped implementation in this repo now includes:

- a Rust + JNI bridge under `core/tor/arti-bridge/`,
- Cargo + Android NDK build wiring in `:core:tor`,
- explicit Arti bootstrap with progress reporting,
- direct `(hostname, port) -> stream` dialing for upstream DoT,
- an app preference that can force `embedded`, force `compatibility`, or use `auto` fallback.

Do not treat this as a simple dependency swap. The accepted implementation required a dedicated native toolchain and runtime bridge.

## Consequences

If we proceed:

- Tasks 2 through 6 remain useful and should isolate transport, TLS, and runtime boundaries before the native swap.
- Tasks 7 through 9 require a dedicated native implementation effort, not just Kotlin refactoring.
- Diagnostics must describe both the requested runtime mode and the actual runtime in use, because `auto` can fall back to the compatibility path.
- Operational rollout should remain reversible until embedded Arti proves stable on the target device set.

If we do not proceed:

- keep the transport/TLS refactors for future optionality,
- fall back to the plan's Option 1: a private, non-default local SOCKS endpoint,
- keep diagnostics generic enough to support a later embedded runtime.
