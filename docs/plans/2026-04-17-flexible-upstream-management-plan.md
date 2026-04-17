# Project Plan: Flexible Tor And Upstream DNS Management
Date: 2026-04-17

## Research Summary

### Online sources
- None required for the initial plan. This feature fits the repo's existing Tor, DNS, DataStore, Room, and Compose architecture, so the plan is grounded primarily in local code and ADRs.

### Vault / local docs
- `docs/adr/2026-04-16-embedded-tor-runtime.md`
  - Confirms the current architecture separates DNS serving from Tor transport and already supports requested-vs-effective Tor runtime mode.
  - Confirms `auto`, `embedded`, and `compatibility` are rollout controls, not user-facing upstream policy.
- `MANUAL-VERIFICATION.md`
  - Confirms the app's current authoritative end-to-end proof is `DnsEndToEndInstrumentedTest`, with `DnsForegroundService` as the runtime boundary.
  - Confirms live Tor/DoT behavior is verified on-device, not just with unit tests.
- `docs/PRODUCT-SCOPE-SECTION5.md`
  - Confirms we should avoid turning this into an unbounded resolver-protocol project. A scoped v1 is preferable.

### Project context
- Tor runtime preference currently lives in DataStore as one string in [AppPreferences.kt](/home/user/dev/and-hole/data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt).
- Settings currently expose only three Tor runtime choices in [SettingsScreen.kt](/home/user/dev/and-hole/feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt).
- `DnsForegroundService` always creates a Tor runtime and always uses `TorDotDnsUpstream` in [DnsForegroundService.kt](/home/user/dev/and-hole/app/src/main/java/org/pihole/android/service/DnsForegroundService.kt).
- `TorDotDnsUpstream` already performs ordered endpoint retries, but the resolver list is hardcoded to two Cloudflare DoT endpoints in [TorDotDnsUpstream.kt](/home/user/dev/and-hole/app/src/main/java/org/pihole/android/resolver/TorDotDnsUpstream.kt).
- The correct abstraction boundary for fallback is `DnsUpstream` in [DnsUpstream.kt](/home/user/dev/and-hole/core/dns/src/main/java/org/pihole/android/core/dns/upstream/DnsUpstream.kt), not `DnsServerController`.
- Backup/export currently covers Room-backed user data only, not DataStore-backed settings, in [DataBackup.kt](/home/user/dev/and-hole/data/src/main/java/org/pihole/android/data/backup/DataBackup.kt).

### Planning decisions
- Keep v1 scoped to DNS-over-TLS upstreams only. The repo already has `DotClient`; adding DoH/plain DNS in the same change would widen the transport surface without being required by the user request.
- Split "whether upstream uses Tor" from "which Tor runtime to use when Tor is enabled". The current single `torRuntimeMode` preference conflates those concerns.
- Store the ordered upstream resolver list in Room, not in `Preferences`. It is structured, ordered, user-managed data and should be included in existing export/import flows.
- Keep per-resolver health and cooldown state in memory first. Persist only the declarative config in v1.

## Preflight

- [ ] `./gradlew test :app:assembleDebug :data:kspDebugKotlin`
  - Baseline local build and JVM tests pass before any config refactor.
- [ ] `./gradlew :app:connectedDebugAndroidTest :data:connectedDebugAndroidTest`
  - Existing instrumented coverage is green before runtime behavior changes.
- [ ] Confirm current `DnsEndToEndInstrumentedTest` is runnable on a device with network access.
  - This is the integration gate for "Tor enabled + upstream resolution still works".
- [ ] Decide migration default for existing installs.
  - Recommended: preserve today's behavior by migrating existing users to `tor enabled = true` plus two seeded Cloudflare DoT resolvers.
- [ ] Decide backup scope.
  - Recommended: include resolver list in JSON backup; leave simple runtime toggles in DataStore unless product wants "full settings backup".

---

## Stage 1: Separate Policy From Runtime

**Goal:** Introduce a configuration model that can express Tor on/off plus an ordered manual upstream resolver list without breaking current installs.
**Depends on:** none
**Blocks:** Stages 2, 3, 4
**Risk:** MEDIUM — this changes persisted config shape and migration behavior
**Rollback:** Revert new config tables/prefs and re-seed the hardcoded Cloudflare list; keep `torRuntimeMode` as the only upstream control

### Task 1.1: Define the new upstream policy model
- **Depends on:** none
- **Blocks:** Tasks 1.2, 1.3
- **Parallel:** YES
- **Test:** New unit test proves the config model can express:
  - Tor disabled
  - Tor enabled + auto runtime
  - multiple enabled resolvers in explicit priority order
  - optional TLS server name override
- **Red-Green max cycles:** 3

Recommended model:
- DataStore:
  - `upstream_use_tor: Boolean`
  - keep `tor_runtime_mode` for `auto|embedded|compatibility`
- Room:
  - new `upstream_resolvers` table with `id`, `label`, `host`, `port`, `tlsServerName`, `enabled`, `sortOrder`

### Task 1.2: Add migration and defaults
- **Depends on:** Task 1.1
- **Blocks:** Task 1.3, Stage 2
- **Parallel:** NO
- **Test:** Existing-install migration test proves a fresh upgraded app gets:
  - `upstream_use_tor = true`
  - current `tor_runtime_mode` preserved
  - seeded resolver rows matching today's Cloudflare onion + `tor.cloudflare-dns.com` order
- **Red-Green max cycles:** 3

### Task 1.3: Add repository APIs for settings and resolver management
- **Depends on:** Tasks 1.1, 1.2
- **Blocks:** Stages 2, 3, 4
- **Parallel:** NO
- **Test:** Repository tests prove create, update, delete, enable/disable, and reorder operations emit the expected ordered list
- **Red-Green max cycles:** 3

**Stage gate:**
- [ ] Existing users preserve current behavior after migration
- [ ] New config shape is represented without JSON blobs in `Preferences`
- [ ] No regressions in `AppPreferencesInstrumentedTest` or Room schema generation

---

## Stage 2: Replace Hardcoded Upstream With Configurable Failover

**Goal:** Generalize the upstream implementation so it can use manual resolver entries and fail over from primary to secondary resolvers.
**Depends on:** Stage 1
**Blocks:** Stage 3, Stage 4
**Risk:** HIGH — this changes live network behavior and failure semantics
**Rollback:** Restore `TorDotDnsUpstream` hardcoded client list and remove new dialer/failover strategy

### Task 2.1: Introduce a transport-agnostic upstream dial path
- **Depends on:** Stage 1
- **Blocks:** Tasks 2.2, 2.3
- **Parallel:** YES
- **Test:** New unit tests prove `DotClient` can use:
  - a direct TCP dialer when Tor is disabled
  - the current Tor runtime dialer when Tor is enabled
- **Red-Green max cycles:** 3

Implementation direction:
- Keep `DotClient` as the DoT protocol client.
- Add a direct stream dialer in `:core:upstream`.
- Keep Tor routing as a dialer choice, not a separate DNS implementation.

### Task 2.2: Replace `TorDotDnsUpstream` with ordered resolver-chain logic
- **Depends on:** Task 2.1
- **Blocks:** Task 2.3, Stage 3
- **Parallel:** NO
- **Test:** New upstream tests prove:
  - resolver 1 success short-circuits the chain
  - resolver 1 failure falls through to resolver 2
  - all-disabled or all-failed chains return `null`
  - diagnostics capture the resolver that failed and the resolver that finally answered
- **Red-Green max cycles:** 3

Recommended scope:
- Sequential priority order
- transient retries per resolver
- simple in-memory cooldown after repeated transport failures
- no round-robin or weighted balancing in v1

### Task 2.3: Define failure classification and cooldown rules
- **Depends on:** Task 2.2
- **Blocks:** Stage 3
- **Parallel:** NO
- **Test:** Unit tests prove transient failures move to fallback while valid DNS protocol responses do not trigger unnecessary failover
- **Red-Green max cycles:** 3

Recommended fallback rules:
- Fail over on connect timeout, TLS failure, I/O failure, or malformed response
- Do not fail over on a valid DNS response from the upstream just because the answer is empty or blocked by policy
- Cool down a resolver briefly after repeated transport failures, then retry it later so the primary can recover

**Stage gate:**
- [ ] Upstream behavior is fully data-driven from the resolver list
- [ ] Tor on/off only changes the dialer path, not the DNS server core
- [ ] Resolver fallback is covered by deterministic JVM tests

---

## Stage 3: Wire Runtime Lifecycle And Diagnostics

**Goal:** Make `DnsForegroundService` react to the new policy model and expose enough status to understand what the resolver chain is doing.
**Depends on:** Stage 2
**Blocks:** Stage 4, Stage 5
**Risk:** HIGH — service lifecycle and diagnostics are runtime-critical
**Rollback:** Keep the current service lifecycle and diagnostics text, and only allow the feature behind a dev flag if needed

### Task 3.1: Stop creating Tor when Tor is disabled
- **Depends on:** Stage 2
- **Blocks:** Tasks 3.2, 3.3
- **Parallel:** YES
- **Test:** `DnsForegroundService` unit or instrumented test proves service start does not bootstrap Tor when upstream Tor is off
- **Red-Green max cycles:** 3

### Task 3.2: Reactively rebuild upstream policy when settings change
- **Depends on:** Task 3.1
- **Blocks:** Task 3.3, Stage 4
- **Parallel:** NO
- **Test:** Service-level test proves changing resolver config or Tor toggle causes an upstream rebuild without corrupting the listener lifecycle
- **Red-Green max cycles:** 3

Implementation note:
- Extend the existing `combine(...)` in `DnsForegroundService` to include upstream policy inputs, not just snapshot/rules/local records/port/bind state.

### Task 3.3: Replace single-string diagnostics with structured upstream status
- **Depends on:** Tasks 3.1, 3.2
- **Blocks:** Stage 4, Stage 5
- **Parallel:** NO
- **Test:** `DefaultDnsControlRepositoryTest` or diagnostics tests prove the snapshot shows:
  - requested Tor setting
  - effective runtime mode when Tor is on
  - active resolver
  - last failed resolver
  - failover count or last failover event
- **Red-Green max cycles:** 3

Recommended change:
- Replace `DnsUpstreamStatus.lastForwardFailure: String?` with a structured debug object instead of concatenated free-text.

**Stage gate:**
- [ ] Service can run with Tor disabled
- [ ] Service can switch between Tor-enabled and Tor-disabled policy safely
- [ ] Diagnostics explain both "why resolution failed" and "which resolver answered"

---

## Stage 4: Settings UX For Manual Management

**Goal:** Expose Tor and resolver management in a way that is flexible but still testable and understandable.
**Depends on:** Stage 3
**Blocks:** Stage 5
**Risk:** MEDIUM — UI complexity can exceed the value if not kept narrow
**Rollback:** Hide advanced controls behind a temporary debug gate and ship only the backend/config work first

### Task 4.1: Replace the current Tor-only section with policy controls
- **Depends on:** Stage 3
- **Blocks:** Tasks 4.2, 4.3
- **Parallel:** YES
- **Test:** UI test proves:
  - Tor toggle is visible
  - runtime selector is only interactive when Tor is enabled
  - current values survive recomposition/navigation
- **Red-Green max cycles:** 3

Recommended UI:
- `Use Tor for upstream DNS` switch
- `Tor runtime` radio group shown when Tor is on
- concise note that runtime choice matters only while Tor routing is enabled

### Task 4.2: Add resolver list CRUD and ordering
- **Depends on:** Task 4.1
- **Blocks:** Task 4.3, Stage 5
- **Parallel:** NO
- **Test:** UI/viewmodel tests prove add, edit, delete, enable/disable, and move-up/move-down work and persist
- **Red-Green max cycles:** 3

Recommended v1 UI fields:
- label
- host
- port
- optional TLS server name override
- enabled
- priority order

Recommended v1 UX:
- avoid drag-and-drop initially
- use explicit `Move up` / `Move down` actions for testability

### Task 4.3: Add validation and reset affordances
- **Depends on:** Tasks 4.1, 4.2
- **Blocks:** Stage 5
- **Parallel:** NO
- **Test:** Validation tests prove invalid host/port/TLS-SNI combinations cannot be saved
- **Red-Green max cycles:** 3

Validation rules:
- require at least one enabled resolver
- require port in valid range
- allow hostnames, IPs, and `.onion` names
- if `tlsServerName` is blank, default it to `host`

**Stage gate:**
- [ ] Users can disable Tor without losing resolver management
- [ ] Users can configure more than one upstream resolver
- [ ] Users can understand primary vs fallback order without reading diagnostics

---

## Stage 5: Backup, Verification, And Rollout Hardening

**Goal:** Ensure the new management model is portable, testable, and safe to ship.
**Depends on:** Stage 4
**Blocks:** none
**Risk:** MEDIUM — rollout quality depends on migration and device-level verification
**Rollback:** Ship backend only behind a feature flag, or defer backup/import support if schedule is tight

### Task 5.1: Extend backup/import for resolver config
- **Depends on:** Stage 4
- **Blocks:** Task 5.2
- **Parallel:** YES
- **Test:** Backup round-trip test proves resolver list export/import preserves ordering, enabled state, and TLS server name override
- **Red-Green max cycles:** 3

### Task 5.2: Expand automated coverage
- **Depends on:** Task 5.1
- **Blocks:** Task 5.3
- **Parallel:** NO
- **Test:** Run:
  - `./gradlew :core:upstream:testDebugUnitTest`
  - `./gradlew :data:testDebugUnitTest :data:connectedDebugAndroidTest`
  - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsEndToEndInstrumentedTest`
- **Red-Green max cycles:** 3

Recommended new automated cases:
- primary resolver succeeds
- primary fails, secondary succeeds
- Tor disabled direct DoT succeeds
- Tor enabled still resolves via current runtime
- migration seeds legacy defaults correctly
- settings UI preserves order and enabled state

### Task 5.3: Add manual verification scenarios
- **Depends on:** Task 5.2
- **Blocks:** none
- **Parallel:** NO
- **Test:** Update `MANUAL-VERIFICATION.md` with:
  - Tor on, primary works
  - Tor on, primary broken, fallback works
  - Tor off, direct resolver works
  - all resolvers broken, diagnostics explain the failure
- **Red-Green max cycles:** 2

**Stage gate:**
- [ ] Export/import story is explicit
- [ ] Device-level Tor and non-Tor paths are both verified
- [ ] Diagnostics are good enough to debug resolver failover in the field

---

## Execution Order

1. Stage 1 first, because the app currently cannot express the requested behavior at all.
2. Stage 2 next, because fallback belongs in the upstream layer, not in UI or service code.
3. Stage 3 before UI, because live runtime behavior and diagnostics must exist before exposing advanced controls.
4. Stage 4 after the backend is trustworthy.
5. Stage 5 before calling the feature done.

## Recommended Non-Goals For V1

- Do not add DoH, plain UDP upstreams, or per-resolver protocol selection yet.
- Do not add resolver load balancing.
- Do not persist health/cooldown counters across app restarts.
- Do not mix backup of all DataStore settings into this change unless product explicitly wants full-settings portability.
