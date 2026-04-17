# Control Center, Guided Setup, Observability, and Hardening Implementation Plan

> **For Claude:** Use `${SUPERPOWERS_SKILLS_ROOT}/skills/collaboration/executing-plans/SKILL.md` to implement this plan task-by-task.

**Goal:** Turn the app from a technically capable DNS utility into an operable product with a control-center dashboard, guided first-run setup, useful observability, and safer persistence/release behavior.

**Architecture:** Keep the current multi-module structure and add thin repository-style adapters in `data` so Compose screens stop reaching directly into `DatabaseProvider` and `AppPreferences`. Ship in four phases: establish shared runtime state and a control-center home, add guided onboarding and live validation, upgrade logs into observability, then harden storage/privacy/release behavior.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Navigation, Room, DataStore, WorkManager, foreground services, coroutines/Flow, JUnit, Robolectric, Android instrumented Compose tests

---

## Context

Current repo facts that drive this plan:

- `feature/home` is mostly static prose plus a generated sing-box snippet and does not surface service state or primary actions.
- `feature/settings` owns start/stop service controls and several operational settings.
- `app/src/main/java/org/pihole/android/navigation/AppNavHost.kt` exposes six top-level destinations, including `Diagnostics`, which dilutes the main product flow.
- `feature/logs` only shows recent rows plus in-memory search/filter; there are no aggregate queries or product-level insights.
- UI and view models often talk directly to `DatabaseProvider` and `AppPreferences`.
- `data/src/main/java/org/pihole/android/data/db/DatabaseProvider.kt` uses `fallbackToDestructiveMigration()`.
- `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml` currently back up everything implicitly.
- `app/build.gradle.kts` has `isMinifyEnabled = false` for release.

## Product Outcome

When all phases are complete:

- Home becomes the control center: service state, Tor/DoT state, list/rule counts, recent blocked domains, one-tap start/stop/refresh, and direct access to setup help.
- New users can finish setup without understanding the internal networking model first.
- Logs answer product questions quickly: what is being blocked, why, how often, and whether upstream/cache behavior is healthy.
- Persistence and release behavior are safer: no destructive Room fallback, logs/cache are not blindly backed up, release builds are hardened, and the UI reads shared application state from explicit adapters.

## Delivery Constraints

- Preserve the existing package/module layout unless a move removes duplication immediately.
- Prefer adding adapters and view models over introducing a full DI framework in this pass.
- Keep the DNS service behavior stable while changing product UI around it.
- Use TDD where practical: DAO/repository logic with unit or Robolectric tests first, UI behavior with Compose instrumented tests.
- Land each task in a small commit and keep the app shippable after every task.

## Phase Overview

### Phase 1: Control Center Foundation

Deliver a shared runtime snapshot and rebuild Home into the operational hub. Reduce the top-level navigation to the product-critical surfaces.

### Phase 2: Guided Setup

Add a first-run setup checklist, live validation, and generated per-client connection instructions.

### Phase 3: Observability

Extend logs with aggregate queries, product-focused summaries, and drill-down explanations tied back to blocking sources.

### Phase 4: Hardening

Replace destructive migration, tighten backup behavior, reduce direct database/prefs access, and harden the release build.

## References To Keep Open While Executing

- `docs/DNS-TRIAGE.md`
- `docs/OEM-FGS-RELIABILITY.md`
- `docs/PRODUCT-SCOPE-SECTION5.md`
- `app/src/main/java/org/pihole/android/service/DnsForegroundService.kt`
- `feature/home/src/main/java/org/pihole/android/feature/home/HomeScreen.kt`
- `feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt`
- `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsScreen.kt`
- `data/src/main/java/org/pihole/android/data/db/AppDatabase.kt`
- `data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt`

## Technical Design Decisions

1. Do not add Hilt or another DI container in this plan. Use one explicit app container and small repositories first.
2. Keep diagnostics available, but remove it from the bottom navigation. Surface it from Home and Settings as a secondary tool.
3. Keep onboarding inside `feature/home` for now. The setup flow is part of product entry, not a separate feature vertical.
4. Add only the minimal Room schema changes needed for observability and hardening in this cycle.

Recommended new shared interfaces:

```kotlin
data class DnsControlSnapshot(
    val listenerState: DnsForegroundRuntimeState,
    val listenerPort: Int,
    val bindAllInterfaces: Boolean,
    val autoStart: Boolean,
    val torLine: String,
    val socksLine: String,
    val dnsServiceDetail: String,
    val adlistCount: Int,
    val customRuleCount: Int,
    val localDnsCount: Int,
    val recentBlockedDomains: List<String>,
)

interface DnsControlRepository {
    val snapshot: Flow<DnsControlSnapshot>
    suspend fun startListener()
    suspend fun stopListener()
    suspend fun refreshAdlists()
}
```

Recommended aggregate query result types:

```kotlin
data class DomainHitStat(
    val qname: String,
    val hits: Int,
)

data class DecisionCountStat(
    val decision: String,
    val hits: Int,
)
```

Recommended new preferences:

```kotlin
val onboardingCompleted: Flow<Boolean>
val setupClientMode: Flow<String> // "sing_box", "android_private_dns", "other"
val setupBindAllInterfacesDismissed: Flow<Boolean>
```

## Phase 1 Tasks

### Task 1: Add a shared runtime/control repository

**Files:**
- Create: `data/src/main/java/org/pihole/android/data/runtime/DnsControlSnapshot.kt`
- Create: `data/src/main/java/org/pihole/android/data/runtime/DnsControlRepository.kt`
- Create: `data/src/main/java/org/pihole/android/data/runtime/DefaultDnsControlRepository.kt`
- Modify: `data/build.gradle.kts`
- Modify: `data/src/main/java/org/pihole/android/data/runtime/DebugRuntimeStatus.kt`
- Modify: `data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt`
- Modify: `data/src/main/java/org/pihole/android/data/lists/AdlistRefreshEngine.kt`
- Test: `data/src/test/java/org/pihole/android/data/runtime/DefaultDnsControlRepositoryTest.kt`

**Implementation Notes:**
- The repository should combine `DebugRuntimeStatus.snapshot`, `AppPreferences`, DAO counts, and a small “recent blocked domains” query from `QueryLogDao`.
- Do not move service logic into the repository. The repository is a read model plus command adapter.
- Starting/stopping the service should remain Android-context driven, but hide that behind repository methods so UI code stops constructing `Intent`s directly.

**Step 1: Add the failing repository test**

- Write `DefaultDnsControlRepositoryTest.kt` using fake flows or test doubles for prefs/runtime/DAO sources.
- Verify the combined snapshot exposes port, bind mode, rule counts, and recent blocked domains in one object.

Run:

```bash
./gradlew :data:testDebugUnitTest --tests org.pihole.android.data.runtime.DefaultDnsControlRepositoryTest
```

Expected: FAIL because the repository types do not exist yet.

**Step 2: Add minimal types and wiring**

- Create `DnsControlSnapshot.kt` and `DnsControlRepository.kt`.
- Implement `DefaultDnsControlRepository.kt`.
- Add any missing `kotlinx-coroutines-test`, Truth, or AndroidX test helpers to `data/build.gradle.kts` only if the new test needs them.

**Step 3: Expose the small missing inputs**

- Add `AppPreferences` flows/setters needed later by onboarding and Home.
- Add a DAO method for recent blocked domains if the repository needs it.
- Keep query size small, for example `LIMIT 5`.

**Step 4: Run tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests org.pihole.android.data.runtime.DefaultDnsControlRepositoryTest
```

Expected: PASS

**Step 5: Commit**

```bash
git add data/build.gradle.kts data/src/main/java/org/pihole/android/data/runtime data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt data/src/test/java/org/pihole/android/data/runtime/DefaultDnsControlRepositoryTest.kt
git commit -m "feat: add shared dns control repository"
```

### Task 2: Rebuild Home into a control center

**Files:**
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/HomeUiState.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/HomeViewModel.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/HomeViewModelFactory.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/ControlCenterCards.kt`
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/home/src/main/java/org/pihole/android/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/org/pihole/android/MainActivity.kt`
- Test: `app/src/androidTest/java/org/pihole/android/HomeControlCenterInstrumentedTest.kt`

**Implementation Notes:**
- Replace the static snippet-first layout with:
  - service status card
  - start/stop button row
  - refresh-lists action
  - port/bind mode summary
  - Tor/DoT health summary
  - counts for adlists, rules, local records
  - recent blocked domains
  - secondary links to setup and diagnostics
- Keep the sing-box snippet available, but demote it behind an expandable section or bottom sheet.
- Add stable `testTag`s for every primary action and summary card.

**Step 1: Add the failing instrumented test**

- Create `HomeControlCenterInstrumentedTest.kt`.
- Assert the home screen shows a primary status card and action buttons instead of only the old static snippet card.

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.HomeControlCenterInstrumentedTest
```

Expected: FAIL because the new UI does not exist.

**Step 2: Add the view model and UI state**

- Add `HomeUiState.kt`.
- Add `HomeViewModel.kt` backed by `DnsControlRepository`.
- Add factory or app-container access without introducing a DI framework.

**Step 3: Rework `HomeScreen.kt`**

- Replace static text blocks with composables in `ControlCenterCards.kt`.
- Keep copy concise and operational.
- Show one primary error/recovery path at a time instead of dumping all caveats in one paragraph.

**Step 4: Run the test and spot-check the app**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.HomeControlCenterInstrumentedTest
./gradlew :feature:home:testDebugUnitTest
```

Expected: PASS

**Step 5: Commit**

```bash
git add feature/home/build.gradle.kts feature/home/src/main/java/org/pihole/android/feature/home app/src/androidTest/java/org/pihole/android/HomeControlCenterInstrumentedTest.kt
git commit -m "feat: turn home into dns control center"
```

### Task 3: Simplify top-level navigation

**Files:**
- Modify: `app/src/main/java/org/pihole/android/navigation/AppNavHost.kt`
- Modify: `feature/home/src/main/java/org/pihole/android/feature/home/HomeScreen.kt`
- Modify: `feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/org/pihole/android/FeatureScreensNavTest.kt`
- Test: `app/src/androidTest/java/org/pihole/android/FeatureScreensNavTest.kt`

**Implementation Notes:**
- Bottom nav target set should become `Home`, `Rules`, `Logs`, `Settings`.
- Move `Lists` actions into Home and/or Settings.
- Move `Diagnostics` to a secondary path accessible from Home and Settings.
- Keep route names stable only where tests or deep links would otherwise break unnecessarily.

**Step 1: Update the navigation test first**

- Modify `FeatureScreensNavTest.kt` to assert the reduced destination set and new entry points for diagnostics/lists.

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.FeatureScreensNavTest
```

Expected: FAIL because the old bottom nav is still in place.

**Step 2: Update `AppNavHost.kt`**

- Reduce bottom-nav destinations.
- Add internal navigation actions for diagnostics and list management from Home/Settings.

**Step 3: Update affected screens**

- Home should expose shortcuts to list refresh and setup.
- Settings should expose diagnostics and advanced actions.

**Step 4: Re-run tests**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.FeatureScreensNavTest
```

Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/org/pihole/android/navigation/AppNavHost.kt feature/home/src/main/java/org/pihole/android/feature/home/HomeScreen.kt feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt app/src/androidTest/java/org/pihole/android/FeatureScreensNavTest.kt
git commit -m "feat: simplify top-level navigation"
```

## Phase 2 Tasks

### Task 4: Add onboarding state and setup preference flags

**Files:**
- Modify: `data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/SetupState.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/SetupStep.kt`
- Test: `data/src/androidTest/java/org/pihole/android/data/AppPreferencesInstrumentedTest.kt`

**Implementation Notes:**
- Add a durable `onboardingCompleted` flag.
- Track selected client mode and whether the user dismissed the “bind all interfaces” recommendation.
- Keep preference keys ASCII and explicit.

**Step 1: Extend the prefs test**

- Add failing assertions for onboarding flags in `AppPreferencesInstrumentedTest.kt`.

Run:

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.data.AppPreferencesInstrumentedTest
```

Expected: FAIL because the new flows/setters do not exist.

**Step 2: Implement the preference keys and API**

- Add flows and setters in `AppPreferences.kt`.
- Keep defaults conservative: onboarding incomplete, no client selected.

**Step 3: Re-run the prefs test**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.data.AppPreferencesInstrumentedTest
```

Expected: PASS

**Step 4: Commit**

```bash
git add data/src/main/java/org/pihole/android/data/prefs/AppPreferences.kt data/src/androidTest/java/org/pihole/android/data/AppPreferencesInstrumentedTest.kt feature/home/src/main/java/org/pihole/android/feature/home/setup
git commit -m "feat: add onboarding preference state"
```

### Task 5: Implement the guided setup experience

**Files:**
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/SetupWizardCard.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/SetupChecklistCard.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/ClientConfigCard.kt`
- Create: `feature/home/src/main/java/org/pihole/android/feature/home/setup/SetupViewModel.kt`
- Modify: `feature/home/src/main/java/org/pihole/android/feature/home/HomeScreen.kt`
- Modify: `feature/home/build.gradle.kts`
- Test: `app/src/androidTest/java/org/pihole/android/SetupWizardInstrumentedTest.kt`

**Implementation Notes:**
- The setup flow should be card-based, not a full-screen wizard unless that becomes necessary during implementation.
- Minimum checklist:
  - service running
  - port known
  - chosen client mode
  - Private DNS guidance shown
  - bind-all-interfaces recommendation shown only when symptoms match
- `ClientConfigCard` should generate per-client config text from current prefs/runtime, not hard-coded strings in the composable.
- Provide explicit copy actions for generated config and diagnostics report.

**Step 1: Add the failing UI test**

- `SetupWizardInstrumentedTest.kt` should assert that onboarding appears when incomplete and can be dismissed after completing required steps.

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.SetupWizardInstrumentedTest
```

Expected: FAIL

**Step 2: Add the setup view model and state machine**

- `SetupViewModel` should consume `DnsControlRepository` plus onboarding prefs.
- Represent setup steps explicitly; do not infer step completion from UI booleans sprinkled across composables.

Suggested state shape:

```kotlin
data class SetupUiState(
    val showOnboarding: Boolean,
    val selectedClientMode: String?,
    val steps: List<SetupStepState>,
    val generatedConfig: String,
    val recommendedActions: List<String>,
)
```

**Step 3: Implement the cards**

- Add the checklist, client selection, generated config, and “why this matters” help blocks.
- Keep diagnostics and OEM reliability docs linked from setup help.

**Step 4: Re-run tests**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.SetupWizardInstrumentedTest
./gradlew :feature:home:testDebugUnitTest
```

Expected: PASS

**Step 5: Commit**

```bash
git add feature/home/build.gradle.kts feature/home/src/main/java/org/pihole/android/feature/home app/src/androidTest/java/org/pihole/android/SetupWizardInstrumentedTest.kt
git commit -m "feat: add guided setup flow"
```

## Phase 3 Tasks

### Task 6: Extend the query log schema and DAO for insights

**Files:**
- Modify: `data/src/main/java/org/pihole/android/data/db/entity/QueryLogEntity.kt`
- Modify: `data/src/main/java/org/pihole/android/data/db/dao/QueryLogDao.kt`
- Create: `data/src/main/java/org/pihole/android/data/db/Migrations.kt`
- Modify: `data/src/main/java/org/pihole/android/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/org/pihole/android/data/db/DatabaseProvider.kt`
- Test: `data/src/test/java/org/pihole/android/data/QueryLogDaoTest.kt`

**Implementation Notes:**
- Add indexes at least on `timestamp`, `decision`, and possibly `(qname, timestamp)` if query plans justify it.
- Remove `fallbackToDestructiveMigration()` and add a real migration to the next DB version.
- Add DAO methods for:
  - top blocked domains
  - top allowed domains
  - decision counts in a time window
  - latest blocking attribution rows

Suggested query shapes:

```sql
SELECT qname, COUNT(*) AS hits
FROM query_log
WHERE decision = 'blocked' AND timestamp >= :sinceEpochMs
GROUP BY qname
ORDER BY hits DESC
LIMIT :limit
```

```sql
SELECT decision, COUNT(*) AS hits
FROM query_log
WHERE timestamp >= :sinceEpochMs
GROUP BY decision
```

**Step 1: Add failing DAO tests**

- Extend `QueryLogDaoTest.kt` to cover aggregate queries and migration-backed behavior where practical.

Run:

```bash
./gradlew :data:testDebugUnitTest --tests org.pihole.android.data.QueryLogDaoTest
```

Expected: FAIL

**Step 2: Add indexes, queries, and migration**

- Update `QueryLogEntity.kt`.
- Increment Room version in `AppDatabase.kt`.
- Add a migration object in `Migrations.kt`.
- Register the migration in `DatabaseProvider.kt`.

**Step 3: Re-run tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests org.pihole.android.data.QueryLogDaoTest
```

Expected: PASS

**Step 4: Commit**

```bash
git add data/src/main/java/org/pihole/android/data/db data/src/test/java/org/pihole/android/data/QueryLogDaoTest.kt
git commit -m "feat: add query log insight queries and room migration"
```

### Task 7: Turn Logs into an observability surface

**Files:**
- Create: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsInsightsUiState.kt`
- Create: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsInsightsCard.kt`
- Create: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsInsightModels.kt`
- Modify: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsViewModel.kt`
- Modify: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsScreen.kt`
- Modify: `feature/logs/build.gradle.kts`
- Test: `feature/logs/src/test/java/org/pihole/android/feature/logs/LogsInsightsViewModelTest.kt`
- Test: `app/src/androidTest/java/org/pihole/android/LogsScreenInstrumentedTest.kt`

**Implementation Notes:**
- Above the raw row list, add:
  - decision summary chips or cards
  - top blocked domains
  - top allow overrides
  - cache-hit and upstream-pass summaries
  - last refresh / last upstream failure summary if already available from shared state
- Each raw row should answer “why” in plain language. Reuse existing attribution logic but enrich it with source/rule names when possible.
- Keep the existing export/clear flow.

**Step 1: Add failing unit/UI tests**

- Add `LogsInsightsViewModelTest.kt` for aggregation mapping.
- Extend `LogsScreenInstrumentedTest.kt` to assert the insight cards render.

Run:

```bash
./gradlew :feature:logs:testDebugUnitTest --tests org.pihole.android.feature.logs.LogsInsightsViewModelTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.LogsScreenInstrumentedTest
```

Expected: FAIL

**Step 2: Update the view model**

- Replace the pure “recent 200 rows + in-memory filter” shape with a combined state:
  - filtered rows
  - aggregate insights
  - export status
- Keep search text and decision filter behavior stable.

**Step 3: Update the screen**

- Add an insights section before the list.
- Do not overload the row body; use one extra detail line and a drill-down action if needed.

**Step 4: Re-run tests**

Run:

```bash
./gradlew :feature:logs:testDebugUnitTest --tests org.pihole.android.feature.logs.LogsInsightsViewModelTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.LogsScreenInstrumentedTest
```

Expected: PASS

**Step 5: Commit**

```bash
git add feature/logs/build.gradle.kts feature/logs/src/main/java/org/pihole/android/feature/logs feature/logs/src/test/java/org/pihole/android/feature/logs/LogsInsightsViewModelTest.kt app/src/androidTest/java/org/pihole/android/LogsScreenInstrumentedTest.kt
git commit -m "feat: add log insights and observability dashboard"
```

## Phase 4 Tasks

### Task 8: Introduce an explicit app container and remove direct DB/prefs lookups from product UI

**Files:**
- Create: `app/src/main/java/org/pihole/android/AppContainer.kt`
- Modify: `app/src/main/java/org/pihole/android/App.kt`
- Modify: `feature/home/src/main/java/org/pihole/android/feature/home/HomeViewModelFactory.kt`
- Modify: `feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt`
- Modify: `feature/logs/src/main/java/org/pihole/android/feature/logs/LogsViewModelFactory.kt`
- Modify: `feature/lists/src/main/java/org/pihole/android/feature/lists/ListsViewModel.kt`
- Modify: `feature/rules/src/main/java/org/pihole/android/feature/rules/RulesViewModel.kt`
- Test: `app/src/androidTest/java/org/pihole/android/MainActivityTest.kt`

**Implementation Notes:**
- The goal is not “architecture purity”; the goal is to stop screens and view models from constructing core dependencies ad hoc.
- `AppContainer` should expose only the repositories/use-cases needed by product surfaces.
- Do not migrate every class at once. Prioritize Home, Settings, Logs, Lists, Rules.

**Step 1: Add the container with no behavior changes**

- Create `AppContainer.kt`.
- Instantiate it once in `App.kt`.
- Thread it into factories only.

**Step 2: Migrate one surface at a time**

- Home first, then Logs, then Settings, then Lists/Rules.
- After each migration, run focused tests before moving on.

**Step 3: Run regression tests**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.MainActivityTest,org.pihole.android.FeatureScreensNavTest
./gradlew :feature:home:testDebugUnitTest :feature:logs:testDebugUnitTest :data:testDebugUnitTest
```

Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/org/pihole/android/App.kt app/src/main/java/org/pihole/android/AppContainer.kt feature/home/src/main/java/org/pihole/android/feature/home feature/settings/src/main/java/org/pihole/android/feature/settings feature/logs/src/main/java/org/pihole/android/feature/logs feature/lists/src/main/java/org/pihole/android/feature/lists feature/rules/src/main/java/org/pihole/android/feature/rules
git commit -m "refactor: centralize app dependencies"
```

### Task 9: Tighten backup, data retention, and release behavior

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `app/build.gradle.kts`
- Modify: `feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt`
- Modify: `data/src/main/java/org/pihole/android/data/backup/DataBackup.kt`
- Test: `app/src/androidTest/java/org/pihole/android/DiagnosticsScreenInstrumentedTest.kt`

**Implementation Notes:**
- Today cloud backup and device transfer implicitly include everything. Change this deliberately.
- Recommended direction:
  - keep explicit export/import via `DataBackup.kt` for user-managed configuration backup
  - exclude query logs, compiled snapshots, and adlist caches from automatic platform backup
  - decide whether automatic backup should stay enabled at all; if not, document the reasoning clearly in the plan PR
- Enable release minification and add any keep rules required by Compose/Room/FileProvider behavior.
- Settings copy should explain what is and is not included in backups after the change.

**Step 1: Add or update tests/documentation expectations first**

- Extend diagnostics/settings UI tests if they need to reflect new backup messaging.

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.DiagnosticsScreenInstrumentedTest
```

Expected: either FAIL on changed text expectations or PASS if no UI assertion is needed yet.

**Step 2: Tighten backup rules**

- Replace the empty backup XML files with explicit include/exclude rules.
- Ensure logs and generated manifests are excluded.

**Step 3: Harden release build**

- Turn on `isMinifyEnabled = true` in release.
- Add/update `proguard-rules.pro` only if a release smoke build reveals missing keeps.

**Step 4: Run verification**

Run:

```bash
./gradlew :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.DiagnosticsScreenInstrumentedTest
```

Expected: release assembles successfully; diagnostics/settings behavior remains correct.

**Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml feature/settings/src/main/java/org/pihole/android/feature/settings/SettingsScreen.kt data/src/main/java/org/pihole/android/data/backup/DataBackup.kt proguard-rules.pro
git commit -m "chore: harden backup and release behavior"
```

## Final Verification Matrix

Run the full verification set before calling the plan implemented:

```bash
./gradlew :data:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:logs:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.MainActivityTest,org.pihole.android.FeatureScreensNavTest,org.pihole.android.HomeControlCenterInstrumentedTest,org.pihole.android.SetupWizardInstrumentedTest,org.pihole.android.LogsScreenInstrumentedTest,org.pihole.android.DiagnosticsScreenInstrumentedTest
./gradlew :app:assembleRelease
```

Expected outcomes:

- Home exposes operational state and primary DNS actions.
- New users can complete setup without reading raw implementation notes first.
- Logs show both raw entries and aggregate product insights.
- Diagnostics remains available but is no longer a primary destination.
- Room upgrades without wiping user data.
- Automatic backup behavior is explicit and safe.
- Release build assembles with shrinking enabled.

## Rollout Order

1. Ship Phase 1 alone if needed. It provides the largest UX improvement with minimal schema risk.
2. Ship Phase 2 next. It reduces support burden and setup errors.
3. Ship Phase 3 after Phase 1 is stable, because it depends on better shared state and migration work.
4. Ship Phase 4 last, but do not defer migration work too long once new observability queries exist.

## Risks

- Navigation simplification may break existing UI tests if routes/tags are renamed casually.
- The logs insight phase can accidentally become a large analytics rewrite. Keep it to on-device aggregates over recent history only.
- Turning on release minification late without a smoke build is risky. Always assemble release before merging.
- Room migration mistakes are higher risk than the UI work. Treat migration tests as mandatory.

## Non-Goals For This Plan

- Replacing the current module layout
- Introducing Hilt/Koin
- Building remote sync, account systems, or cloud analytics
- Rewriting the DNS service core
- Adding new VPN modes beyond clearer setup guidance for existing ones
