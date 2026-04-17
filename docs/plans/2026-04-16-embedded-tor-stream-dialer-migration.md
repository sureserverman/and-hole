# Embedded Tor Stream Dialer Migration Implementation Plan

> **For Claude:** Use `${SUPERPOWERS_SKILLS_ROOT}/skills/collaboration/executing-plans/SKILL.md` to implement this plan task-by-task.

**Goal:** Replace the app's Tor-over-local-SOCKS upstream path with an app-internal Tor transport boundary so DNS-over-TLS requests are dialed through an embedded Tor engine API instead of `127.0.0.1:9050`.

**Architecture:** First isolate the current SOCKS-specific assumptions behind small transport interfaces so `DotClient` and `TorDotDnsUpstream` stop depending directly on `Socket(proxy)`. Then introduce a `TorRuntime` plus `TorStreamDialer` abstraction in `:core:tor`, keep a temporary SOCKS-backed compatibility adapter for parity, and finally swap in a true embedded Tor engine implementation that returns bidirectional streams directly. TLS must move from `SSLSocket` to stream-based `SSLEngine` because the final transport is no longer guaranteed to be a `java.net.Socket`.

**Tech Stack:** Kotlin, Android foreground service, coroutines, `info.guardianproject:tor-android:0.4.8.17.2` as the current baseline, Room/DataStore, JUnit4, Truth, Turbine, Android instrumented tests, `SSLEngine` for stream-based TLS, selected embedded Tor engine with JNI/FFI bridge.

> Current repo state: Tasks 1 through 9 are effectively implemented in a working form. The app now ships an embedded Arti JNI bridge with `auto` fallback to the TorService compatibility runtime, plus a Settings toggle to force embedded or compatibility mode during rollout/debugging.

---

## Why this is a large migration

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

Important constraints:

- The current codebase has no low-level Tor stream API. It wraps `TorService` and checks readiness by probing `127.0.0.1:9050` in `core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt`.
- `DotClient` currently requires a real `Socket` and `SSLSocket`, which is incompatible with a generic embedded stream API.
- The app depends on Tor performing remote hostname resolution. Preserve the current `InetSocketAddress.createUnresolved(upstreamHost, upstreamPort)` behavior when moving off SOCKS.
- Option 2 is not viable unless the selected embedded engine can:
  - expose bootstrap progress,
  - create outbound streams to `(hostname, port)`,
  - keep remote DNS resolution inside Tor,
  - survive Android app/service lifecycle.
- If Phase 0 fails to identify a viable engine API, stop and revert to Option 1 (private non-default local SOCKS endpoint).

## Existing files that define the current boundary

- `app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt`
- `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt`
- `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/PooledDotSession.kt`
- `core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt`
- `app/src/main/java/org/pihole/android/service/DnsForegroundService.kt`
- `app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt`

## Phase overview

1. Prove the embedded-engine approach is feasible and document the decision.
2. Refactor transport and TLS boundaries without changing app behavior.
3. Replace Tor lifecycle and dialer implementation behind interfaces.
4. Remove SOCKS-specific diagnostics and tests, then verify the full DNS path.

## Abort criteria

Abort after Task 1 if no engine API can provide `(hostname, port) -> bidirectional stream`.

Also abort after Task 1 if the only plausible engine requires a brand-new Android native bridge/toolchain effort that is not accepted for this migration scope.

## Non-goals

- Do not add `VpnService` in this plan.
- Do not change DNS filtering behavior in `DnsServerController`.
- Do not redesign the query log schema.
- Do not change upstream providers beyond what is required for transport migration.

### Task 1: Add an ADR and a hard feasibility gate

**Files:**
- Create: `docs/adr/2026-04-16-embedded-tor-runtime.md`
- Modify: `docs/plans/2026-04-16-embedded-tor-stream-dialer-migration.md`
- Test: none

**Step 1: Write the ADR with explicit go/no-go criteria**

Document:

```md
# Embedded Tor Runtime Decision

## Required capabilities
- bootstrap state callback or poll API
- outbound stream dial by hostname and port
- no mandatory public SOCKS listener
- embeddable in Android app/service lifecycle
- recoverable close/restart semantics

## Reject if any are missing
- only exposes daemon-style local ports
- requires root/system privileges
- cannot preserve remote hostname resolution through Tor
```

**Step 2: Record the current baseline and migration risk**

Document:

```md
Current boundary:
- Tor lifecycle: `TorController`
- readiness: `tryConnectSocks()`
- transport: `DotClient.openOverSocks()`

Primary migration risks:
- no usable engine API
- TLS over generic stream complexity
- background service lifecycle regressions
```

**Step 3: Add explicit abort criteria to the plan**

Add a short section to this plan:

```md
Abort after Task 1 if no engine API can provide `(hostname, port) -> bidirectional stream`.
```

**Step 4: Review the ADR manually**

Run: no command required. Read the ADR and verify it answers:
- what capability is required,
- what failure conditions stop the migration,
- why local SOCKS is insufficient.

Expected: ADR is concrete enough that another engineer could choose a runtime without guessing.

**Step 5: Commit**

```bash
git add docs/adr/2026-04-16-embedded-tor-runtime.md docs/plans/2026-04-16-embedded-tor-stream-dialer-migration.md
git commit -m "docs: add embedded tor runtime decision record"
```

### Task 2: Introduce transport abstractions in `:core:upstream`

**Files:**
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/transport/BidirectionalStream.kt`
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/transport/StreamDialer.kt`
- Create: `core/upstream/src/test/java/org/pihole/android/core/upstream/transport/StreamDialerContractTest.kt`
- Modify: `core/upstream/build.gradle.kts`
- Test: `core/upstream/src/test/java/org/pihole/android/core/upstream/transport/StreamDialerContractTest.kt`

**Step 1: Write the failing contract test**

```kotlin
class StreamDialerContractTest {
    @Test
    fun bidirectionalStream_roundTripsBytes() {
        val stream = FakeBidirectionalStream(
            reads = ArrayDeque(listOf(byteArrayOf(0x12, 0x34)))
        )

        stream.writeFully(byteArrayOf(0x01, 0x02))

        assertThat(stream.writes).containsExactly(byteArrayOf(0x01, 0x02))
        assertThat(stream.readFully(2)).isEqualTo(byteArrayOf(0x12, 0x34))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.transport.StreamDialerContractTest`

Expected: FAIL because `BidirectionalStream` and test fakes do not exist.

**Step 3: Add minimal transport interfaces**

```kotlin
interface BidirectionalStream : Closeable {
    fun writeFully(bytes: ByteArray)
    fun readFully(length: Int): ByteArray
}

fun interface StreamDialer {
    fun connect(host: String, port: Int): BidirectionalStream
}
```

**Step 4: Add the fake stream in the test**

```kotlin
private class FakeBidirectionalStream(
    val reads: ArrayDeque<ByteArray>,
) : BidirectionalStream {
    val writes = mutableListOf<ByteArray>()

    override fun writeFully(bytes: ByteArray) {
        writes += bytes.copyOf()
    }

    override fun readFully(length: Int): ByteArray {
        val next = reads.removeFirst()
        require(next.size == length)
        return next
    }

    override fun close() = Unit
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.transport.StreamDialerContractTest`

Expected: PASS

**Step 6: Commit**

```bash
git add core/upstream/src/main/java/org/pihole/android/core/upstream/transport/BidirectionalStream.kt core/upstream/src/main/java/org/pihole/android/core/upstream/transport/StreamDialer.kt core/upstream/src/test/java/org/pihole/android/core/upstream/transport/StreamDialerContractTest.kt core/upstream/build.gradle.kts
git commit -m "refactor: add upstream transport abstractions"
```

### Task 3: Refactor `DotClient` off direct SOCKS socket construction

**Files:**
- Modify: `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt`
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/SocksStreamDialer.kt`
- Modify: `core/upstream/src/test/java/org/pihole/android/core/upstream/dot/DotRequestEncodingTest.kt`
- Create: `core/upstream/src/test/java/org/pihole/android/core/upstream/dot/DotClientDialerTest.kt`
- Test: `core/upstream/src/test/java/org/pihole/android/core/upstream/dot/DotClientDialerTest.kt`

**Step 1: Write the failing dialer test**

```kotlin
class DotClientDialerTest {
    @Test
    fun exchange_usesDialerHostAndPort() {
        val dialRequests = mutableListOf<Pair<String, Int>>()
        val fakeStream = RecordingStream(byteArrayOf(0x00, 0x01))
        val client = DotClient(
            upstreamHost = "resolver.example",
            upstreamPort = 853,
            dialer = StreamDialer { host, port ->
                dialRequests += host to port
                fakeStream
            },
            tlsFactory = { _, stream -> stream.asTlsChannel() },
        )

        client.exchange(byteArrayOf(0x01))

        assertThat(dialRequests).containsExactly("resolver.example" to 853)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.dot.DotClientDialerTest`

Expected: FAIL because `DotClient` still creates `Socket(proxy)` internally.

**Step 3: Add `SocksStreamDialer` as the compatibility adapter**

```kotlin
class SocksStreamDialer(
    private val socksHost: String,
    private val socksPort: Int,
) : StreamDialer {
    override fun connect(host: String, port: Int): BidirectionalStream {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        val socket = Socket(proxy)
        socket.tcpNoDelay = true
        socket.soTimeout = 30_000
        socket.connect(InetSocketAddress.createUnresolved(host, port), 30_000)
        return SocketBidirectionalStream(socket)
    }
}
```

**Step 4: Refactor `DotClient` constructor to accept a dialer**

Target shape:

```kotlin
class DotClient(
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val dialerFactory: (Int) -> StreamDialer = { port -> SocksStreamDialer("127.0.0.1", port) },
    private val tlsFactory: TlsChannelFactory = PlatformTlsChannelFactory(),
)
```

Keep behavior unchanged for now. The app should still use SOCKS through the adapter.

**Step 5: Run unit tests**

Run:
- `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.dot.DotClientDialerTest`
- `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.dot.DotRequestEncodingTest`

Expected: PASS

**Step 6: Commit**

```bash
git add core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt core/upstream/src/main/java/org/pihole/android/core/upstream/dot/SocksStreamDialer.kt core/upstream/src/test/java/org/pihole/android/core/upstream/dot/DotClientDialerTest.kt core/upstream/src/test/java/org/pihole/android/core/upstream/dot/DotRequestEncodingTest.kt
git commit -m "refactor: route dot client through dialer abstraction"
```

### Task 4: Introduce stream-based TLS and remove `SSLSocket` dependence

**Files:**
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/tls/TlsChannel.kt`
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/tls/TlsChannelFactory.kt`
- Create: `core/upstream/src/main/java/org/pihole/android/core/upstream/tls/SsLEngineTlsChannel.kt`
- Create: `core/upstream/src/test/java/org/pihole/android/core/upstream/tls/SsLEngineTlsChannelTest.kt`
- Modify: `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt`
- Test: `core/upstream/src/test/java/org/pihole/android/core/upstream/tls/SsLEngineTlsChannelTest.kt`

**Step 1: Write the failing TLS framing test**

```kotlin
class SsLEngineTlsChannelTest {
    @Test
    fun channel_writesAndReadsApplicationBytes() {
        val transport = FakeBidirectionalStream(...)
        val channel = fakeTlsFactory().openClientChannel("resolver.example", transport)

        channel.writeFully(byteArrayOf(0x00, 0x02, 0xAA, 0xBB))
        val reply = channel.readFully(4)

        assertThat(reply).isEqualTo(byteArrayOf(0x00, 0x02, 0xCC.toByte(), 0xDD.toByte()))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.tls.SsLEngineTlsChannelTest`

Expected: FAIL because no TLS channel exists.

**Step 3: Implement a stream-based TLS channel**

Required technical details:

- Use `SSLContext.getDefault().createSSLEngine(host, port)` in client mode.
- Set SNI host name before handshake.
- Drive handshake manually with `wrap()` and `unwrap()`.
- Maintain encrypted network buffers and cleartext application buffers separately.
- Never assume one `wrap()` maps to one socket write or one `unwrap()` maps to one app read.
- Implement orderly `close_notify`.

Minimal interfaces:

```kotlin
interface TlsChannel : Closeable {
    fun writeFully(bytes: ByteArray)
    fun readFully(length: Int): ByteArray
}

fun interface TlsChannelFactory {
    fun openClientChannel(host: String, transport: BidirectionalStream): TlsChannel
}
```

**Step 4: Update `DotClient` to write DNS frames through `TlsChannel`**

Replace `DataInputStream` / `DataOutputStream` usage with:

```kotlin
val tls = tlsFactory.openClientChannel(upstreamHost, transport)
tls.writeFully(lengthPrefixedMessage)
val header = tls.readFully(2)
val len = ...
val body = tls.readFully(len)
```

**Step 5: Run unit tests**

Run:
- `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.tls.SsLEngineTlsChannelTest`
- `./gradlew :core:upstream:testDebugUnitTest --tests org.pihole.android.core.upstream.dot.DotClientDialerTest`
- `./gradlew :core:upstream:testDebugUnitTest`

Expected: PASS

**Step 6: Commit**

```bash
git add core/upstream/src/main/java/org/pihole/android/core/upstream/tls/TlsChannel.kt core/upstream/src/main/java/org/pihole/android/core/upstream/tls/TlsChannelFactory.kt core/upstream/src/main/java/org/pihole/android/core/upstream/tls/SsLEngineTlsChannel.kt core/upstream/src/test/java/org/pihole/android/core/upstream/tls/SsLEngineTlsChannelTest.kt core/upstream/src/main/java/org/pihole/android/core/upstream/dot/DotClient.kt
git commit -m "refactor: move dot transport to sslengine tls channel"
```

### Task 5: Add `TorRuntime` and decouple app code from `TorService`

**Files:**
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntime.kt`
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorBootstrapState.kt`
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorDialer.kt`
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntimeFactory.kt`
- Modify: `core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt`
- Create: `core/tor/src/test/java/org/pihole/android/core/tor/runtime/TorRuntimeContractTest.kt`
- Test: `core/tor/src/test/java/org/pihole/android/core/tor/runtime/TorRuntimeContractTest.kt`

**Step 1: Write the failing runtime contract test**

```kotlin
class TorRuntimeContractTest {
    @Test
    fun controller_readsBootstrapFromRuntimeInsteadOfSocksPort() = runTest {
        val runtime = FakeTorRuntime(states = flowOf(TorBootstrapState.Starting, TorBootstrapState.Ready))
        val controller = TorController(runtime)

        controller.beginStartAndMonitor(this)

        assertThat(controller.state.value).isEqualTo(TorState.Ready)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:tor:testDebugUnitTest --tests org.pihole.android.core.tor.runtime.TorRuntimeContractTest`

Expected: FAIL because `TorController` still owns `TorService` and SOCKS probing.

**Step 3: Define the runtime interfaces**

```kotlin
interface TorRuntime : Closeable {
    val bootstrap: StateFlow<TorBootstrapState>
    fun start(scope: CoroutineScope)
    fun dialer(): StreamDialer
}
```

Bootstrap state should carry:
- `Stopped`
- `Starting(progress: Int?, summary: String?)`
- `Ready`
- `Failed(message: String)`

**Step 4: Refactor `TorController` into a thin adapter**

`TorController` should:
- subscribe to `TorRuntime.bootstrap`,
- map bootstrap state to existing `TorState`,
- stop depending on `Socket().connect(127.0.0.1:9050)`.

Do not remove the old implementation yet. Move it behind a compatibility runtime in the next task.

**Step 5: Run tests**

Run: `./gradlew :core:tor:testDebugUnitTest`

Expected: PASS

**Step 6: Commit**

```bash
git add core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntime.kt core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorBootstrapState.kt core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorDialer.kt core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntimeFactory.kt core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt core/tor/src/test/java/org/pihole/android/core/tor/runtime/TorRuntimeContractTest.kt
git commit -m "refactor: decouple tor controller from torservice"
```

### Task 6: Add a temporary compatibility runtime to hold behavior steady

**Files:**
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorServiceSocksRuntime.kt`
- Modify: `app/src/main/java/org/pihole/android/service/DnsForegroundService.kt`
- Modify: `app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt`
- Modify: `app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt`
- Test: `app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt`

**Step 1: Write the failing app wiring test**

Extend or add a test that asserts app wiring requests a `TorRuntime` and no longer constructs upstream with raw `socksPort`.

```kotlin
assertThat(dnsUpstream.runtime).isNotNull()
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests org.pihole.android.service.DnsForegroundServiceTest`

Expected: FAIL because `DnsForegroundService` still instantiates `TorDotDnsUpstream(torController = torController)`.

**Step 3: Implement the compatibility runtime**

`TorServiceSocksRuntime` should:
- own the existing `TorService` lifecycle code,
- emit bootstrap state from broadcasts / `getInfo("status/bootstrap-phase")`,
- expose `dialer()` as `SocksStreamDialer("127.0.0.1", configuredPort)`.

This task is a compatibility checkpoint, not the final architecture.

**Step 4: Update app wiring**

`DnsForegroundService` should:
- obtain a `TorRuntime` from `TorRuntimeFactory`,
- pass the runtime to `TorController`,
- pass `runtime.dialer()` into `TorDotDnsUpstream`.

**Step 5: Run verification**

Run:
- `./gradlew :app:testDebugUnitTest --tests org.pihole.android.service.DnsForegroundServiceTest`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsEndToEndInstrumentedTest`

Expected:
- unit test PASS
- instrumented test retains current baseline behavior

**Step 6: Commit**

```bash
git add core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorServiceSocksRuntime.kt app/src/main/java/org/pihole/android/service/DnsForegroundService.kt app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt
git commit -m "refactor: route app through tor runtime compatibility layer"
```

### Task 7: Implement the real embedded Tor runtime and dialer

**Files:**
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorRuntime.kt`
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorEngineBridge.kt`
- Create: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorStream.kt`
- Modify: `core/tor/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/tor/src/test/java/org/pihole/android/core/tor/runtime/EmbeddedTorRuntimeTest.kt`
- Test: `core/tor/src/test/java/org/pihole/android/core/tor/runtime/EmbeddedTorRuntimeTest.kt`

**Step 1: Write the failing embedded runtime test**

```kotlin
class EmbeddedTorRuntimeTest {
    @Test
    fun dialer_returnsStreamWithoutLocalSocksDependency() {
        val engine = FakeEmbeddedEngine()
        val runtime = EmbeddedTorRuntime(engine)

        runtime.start(TestScope())
        val stream = runtime.dialer().connect("resolver.example", 853)

        assertThat(engine.connectRequests).containsExactly("resolver.example" to 853)
        assertThat(stream).isNotNull()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:tor:testDebugUnitTest --tests org.pihole.android.core.tor.runtime.EmbeddedTorRuntimeTest`

Expected: FAIL because no embedded engine bridge exists.

**Step 3: Define the engine bridge**

Bridge API should isolate the selected runtime:

```kotlin
interface EmbeddedTorEngineBridge : Closeable {
    fun start(scope: CoroutineScope, listener: (TorBootstrapState) -> Unit)
    fun openStream(host: String, port: Int): EmbeddedTorStream
}
```

`EmbeddedTorStream` must support:
- blocking or suspending write of opaque bytes,
- blocking or suspending exact-length reads,
- close.

Do not leak vendor-specific classes out of this package.

**Step 4: Implement `EmbeddedTorRuntime` against the bridge**

Key technical requirements:
- start exactly once,
- propagate bootstrap state to `StateFlow`,
- create streams without binding any public local SOCKS port,
- close streams and engine on service stop,
- fail fast with `TorBootstrapState.Failed` if bridge initialization fails.

**Step 5: Run unit tests**

Run: `./gradlew :core:tor:testDebugUnitTest --tests org.pihole.android.core.tor.runtime.EmbeddedTorRuntimeTest`

Expected: PASS

**Step 6: Commit**

```bash
git add core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorRuntime.kt core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorEngineBridge.kt core/tor/src/main/java/org/pihole/android/core/tor/runtime/EmbeddedTorStream.kt core/tor/build.gradle.kts gradle/libs.versions.toml core/tor/src/test/java/org/pihole/android/core/tor/runtime/EmbeddedTorRuntimeTest.kt
git commit -m "feat: add embedded tor runtime bridge"
```

### Task 8: Switch production app wiring from compatibility runtime to embedded runtime

**Files:**
- Modify: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntimeFactory.kt`
- Modify: `app/src/main/java/org/pihole/android/service/DnsForegroundService.kt`
- Modify: `app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt`
- Modify: `data/src/main/java/org/pihole/android/data/runtime/DebugRuntimeStatus.kt`
- Modify: `docs/DNS-TRIAGE.md`
- Test: `app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt`

**Step 1: Write the failing diagnostics test or assertion**

Add a test that diagnostics no longer mention SOCKS port probing as the primary Tor readiness signal.

Expected string examples:

```text
Ready (embedded tor runtime)
Starting (bootstrap in progress)
Failed: <message>
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest`

Expected: FAIL because diagnostics still mention `127.0.0.1:9050`.

**Step 3: Flip the factory**

`TorRuntimeFactory` should now return `EmbeddedTorRuntime` in production code. Keep the SOCKS runtime available only for debug fallback if needed.

**Step 4: Remove SOCKS-specific diagnostics from app service**

Delete or replace:
- `probeSocksTcp(...)`
- “SOCKS reachable on 127.0.0.1:9050”
- `waitForTorSocks(...)` assumptions in E2E tests

Replace with:
- bootstrap progress,
- embedded runtime state,
- last stream dial failure,
- last upstream TLS failure.

**Step 5: Run verification**

Run:
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsEndToEndInstrumentedTest`

Expected:
- diagnostics tests PASS
- DNS E2E still proves:
  - `test.pi-hole.local.` local answer,
  - manifest blocklist null answer,
  - at least one upstream A answer over embedded Tor transport

**Step 6: Commit**

```bash
git add core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorRuntimeFactory.kt app/src/main/java/org/pihole/android/service/DnsForegroundService.kt app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt data/src/main/java/org/pihole/android/data/runtime/DebugRuntimeStatus.kt docs/DNS-TRIAGE.md
git commit -m "feat: switch upstream transport to embedded tor runtime"
```

### Task 9: Remove dead SOCKS-only code and tighten verification

**Files:**
- Modify: `core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt`
- Delete: `core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorServiceSocksRuntime.kt`
- Modify: `app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt`
- Modify: `core/upstream/src/main/java/org/pihole/android/core/upstream/dot/SocksStreamDialer.kt`
- Modify: `gradle/libs.versions.toml`
- Test: full module suite

**Step 1: Write the failing cleanup assertion**

Add or update tests to fail if production runtime falls back to `127.0.0.1:9050`.

Example:

```kotlin
assertThat(debugStatus.torLine).doesNotContain("9050")
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest :core:tor:testDebugUnitTest`

Expected: FAIL while old strings / fallback code remain.

**Step 3: Delete dead compatibility code**

Remove:
- production references to `TorServiceSocksRuntime`
- public strings and comments that describe readiness by SOCKS probe
- `SocksStreamDialer` if no longer needed anywhere
- `tor-android` dependency only if the embedded bridge fully replaces it

**Step 4: Run the full verification suite**

Run:
- `./gradlew :core:upstream:testDebugUnitTest`
- `./gradlew :core:tor:testDebugUnitTest`
- `./gradlew :core:dns:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsEndToEndInstrumentedTest`

Expected: all PASS

**Step 5: Commit**

```bash
git add core/tor/src/main/java/org/pihole/android/core/tor/TorController.kt app/src/androidTest/java/org/pihole/android/service/DnsEndToEndInstrumentedTest.kt core/upstream/src/main/java/org/pihole/android/core/upstream/dot/SocksStreamDialer.kt gradle/libs.versions.toml
git rm core/tor/src/main/java/org/pihole/android/core/tor/runtime/TorServiceSocksRuntime.kt
git commit -m "refactor: remove local socks tor integration"
```

## Important technical guidance

### 1. Preserve hostname privacy

Do not regress from remote hostname resolution through Tor to local Android resolver lookups.

Bad:

```kotlin
InetAddress.getByName(host)
```

Good:

- pass `host` as a hostname string into the embedded Tor engine,
- let the engine resolve it inside Tor,
- keep SNI equal to the original hostname at the TLS layer.

### 2. `SSLEngine` is mandatory once the transport is not a `Socket`

Do not try to keep `SSLSocket` by wrapping fake sockets or ad hoc pipes. That path is fragile and makes handshake, shutdown, and buffering harder to reason about.

### 3. Keep the migration staged

Do not jump directly from `Socket(proxy)` to the final embedded engine.

Required sequence:
- transport abstraction,
- TLS abstraction,
- Tor runtime abstraction,
- compatibility runtime,
- embedded runtime swap,
- cleanup.

### 4. Avoid leaking vendor-specific engine types

All engine-specific code must stay under:

- `core/tor/src/main/java/org/pihole/android/core/tor/runtime/`

The rest of the app should only know:
- `TorRuntime`
- `StreamDialer`
- `TlsChannel`

### 5. Treat bootstrap and stream dial as separate failure surfaces

Track and log separately:
- bootstrap failed,
- stream open failed,
- TLS handshake failed,
- DoT reply invalid,
- upstream exhausted endpoints.

### 6. Preserve existing DNS semantics

The migration must not change:
- blocklist matching order,
- local record precedence,
- null answer synthesis,
- query log fields,
- CNAME post-filtering.

## Rollback plan

If the embedded engine proves unstable after Task 8:

1. Revert `TorRuntimeFactory` to `TorServiceSocksRuntime`.
2. Keep transport/TLS abstractions; they are still useful.
3. Leave diagnostics wording generic enough to support both runtimes.
4. Open a follow-up plan focused on runtime stability rather than transport redesign.

## Definition of done

- No production path depends on `127.0.0.1:9050`.
- `DotClient` no longer constructs `Socket(proxy)`.
- Tor readiness is reported from runtime bootstrap state, not local TCP port probes.
- At least one upstream A record resolves through the embedded Tor transport in on-device E2E.
- Existing local block and local record DNS behavior remains unchanged.
- Docs and diagnostics describe embedded Tor transport, not SOCKS.
