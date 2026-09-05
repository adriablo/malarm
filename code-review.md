# Malarm — In-Depth Code Review

Scope: entire app (`app/src/main`), all unit tests (`app/src/test`,
`app/src/testDebug`), resources, build config, Fastlane metadata, `README.md`,
`AGENTS.md`, `docs/*`.
Commit reviewed: `280dca4` ("Preserve elapsed snooze across clock changes").
Refreshed: `2911b3d` ("Drop duplicate alarm deliveries and auto-disable
expired date alarms") plus the uncommitted working tree — see §10 Refresh
addendum for what changed and what was re-verified. This file itself is
untracked (`??` in `git status`), so the original "`git ls-files`" method
below never included it.
Method: full read of every `.kt`, `.xml`, `.md`, `.kts`/`.toml` file listed in
`git ls-files`, plus `lint-results-debug.txt` and `git log`.

Overall: small, well-tested, thoughtfully documented alarm app. Scheduling core
(`AlarmScheduler.nextTrigger`), the elapsed-realtime snooze design, and the
FSI-notification path are sound and well defended in `docs/design-notes.md`.
Test coverage is good (13 test classes, ~1780 lines of tests for ~2070 lines
of app code, up from 11 / ~1300 / ~1900 at review time). Most findings below
are edge cases, inconsistencies between two parallel re-anchor paths, and
hardening — not fundamental flaws. They are ordered by severity.

---

## 1. Bugs (wrong behavior reachable from UI / system events)

### 1.1 Foreground time-change handler kills an active snooze; background path preserves it — inconsistent, foreground is wrong
- `app/src/main/java/com/malarm/MainActivity.kt:40-51` calls
  `scheduler.cancel(alarm, ...)` (cancels **both** main + snooze slots), while
  `app/src/main/java/com/malarm/BootReceiver.kt:35` and
  `app/src/main/java/com/malarm/AlarmReceiver.kt:45` deliberately call
  `cancelMain()` to preserve the elapsed-based snooze.
- `docs/design-notes.md:49-60` and `docs/manual-tests-release.md` §6.8 specify snooze
  survives clock jumps. The `MainActivity.timeChangeReceiver` violates the
  spec whenever the app happens to be open during a TIME_SET/TIMEZONE_CHANGED.
- Fix: use `cancelMain()` in `MainActivity.timeChangeReceiver`, matching the
  other two paths. Add a Robolectric test: arm a snooze, send
  `ACTION_TIME_CHANGED` to the activity receiver, assert the snooze
  `PendingIntent` survives.

### 1.2 Export reports success when the output stream is null
- `app/src/main/java/com/malarm/SettingsActivity.kt:22-28`:
  `contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { ... }` inside
  `runCatching`. If `openOutputStream` returns null, the `?.` chain silently
  skips the write, `runCatching` succeeds, and the user still gets
  `R.string.export_done`.
- Fix: `val out = openOutputStream(uri) ?: throw IOException("null stream")`,
  or explicitly branch on null to show `export_error`.

### 1.3 Combined date + repeat payload becomes an immortal repeating alarm
- `Alarm` allows `dateMillis != null` **and** `repeatDays/monthlyDay` set
  simultaneously. Precedence is split-brain:
  - `AlarmScheduler.kt:137-146` fires the date branch and ignores repeat.
  - `AlarmFormatter.kt:36-45` displays the date branch.
  - `AlarmReceiver.kt:92` checks `alarm.isRepeating` (true via the stale
    `monthlyDay`/`repeatDays`) and **reschedules** instead of disabling the
    one-shot (`AlarmStore` path at `:95` never runs).
- Reachable via import (no validation in `AlarmExport.parse`), and marginally
  via `MainActivity.showWeeklyDaysPicker` Save-without-touch path (§3.3).
- Fix: normalize on parse/save — e.g. `require(dateMillis == null ||
  (!isRepeating))`, or strip repeat fields when `dateMillis != null` in
  `fromJson`/`saveDialog`/`applyImport`, plus a test for a hostile import file.

### 1.4 Import silently drops malformed entries, count misleads
- `app/src/main/java/com/malarm/AlarmExport.kt:38-42` skips unparseable array
  elements with `getOrNull()` (skip at `:40`). `SettingsActivity.applyImport` then toasts
  "N alarms imported" where N excludes the dropped ones, with no warning.
- Fix: return `(parsed, skipped)` or reject the file when `skipped > 0`; at
  minimum log `IMPORTED` with `details = "$ok imported, $skipped skipped"` and
  surface it in the confirm/toast strings.

### 1.5 Enabled-but-never-ringing alarm survives import without feedback
- `AlarmScheduler.schedule` does `nextTrigger(...) ?: return` with no log.
  `SettingsActivity.applyImport` schedules every `enabled` alarm, so an import
  containing e.g. `monthlyDay = 0` or a past date stays **enabled** forever
  with no "will never ring" treatment. The manual-save path
  (`MainActivity.saveDialog:532-556`, rule at `:541-556`) auto-disables with
  a toast; import does not.
- Fix: mirror the save-dialog check in `applyImport` (disable + count
  never-ringing alarms, toast accordingly).

### 1.6 `requestCode()` can go negative at large ids, breaking the disjoint-namespace invariant
- `app/src/main/java/com/malarm/AlarmScheduler.kt:122-124`:
  `hash * ROLE_STRIDE + role` overflows `Int` for `alarmId ≳ 430M` and yields
  negative codes, colliding with `REQUEST_RESCHEDULE = -1` territory (`:120`).
  The comment above the companion object and the test
  `alarmRequestCodesAreNonNegative` (only to 1M) both assume non-negativity.
- Practically unreachable via `nextId()` today, but the invariant is load-
  bearing for `PendingIntent` identity. Fix: `Math.floorMod(hash * STRIDE +
  role, Int.MAX_VALUE)` or `(... and Int.MAX_VALUE)`, and extend the test to
  `Long.MAX_VALUE`-adjacent ids.

### 1.7 Second concurrent alarm is absorbed (single-slot `RingtoneService` + single `NOTIFICATION_ID`)
- `RingtoneService.onStartCommand:37-56` only rings when `player == null`
  (guard at `:46`); `AlarmNotifier.NOTIFICATION_ID = 1001` is global. Two
  alarms firing together → second intent is dropped (sound, label, reschedule
  aside — reschedule already happened in the receiver, but the user only
  sees/hears the first).
  - `docs/manual-tests-release.md` §12.1 waves this through as "absorbed — confirm
  acceptable". It should at least be a logged known-limitation in
  `design-notes.md`, or the service should serialize (queue second alarm, or
  use per-alarm notification IDs with `stopSelf(startId)` semantics).
- Related: the 5-minute `stopHandler` (`:53-61`) from the **first** alarm is
  never reset on a second start, so a second alarm can be cut off early.

---

## 2. Reliability / robustness weaknesses

### 2.1 Whole alarm DB is one `SharedPreferences` string; corrupt JSON = silent total loss
- `AlarmStore.read:101-104` returns null on any parse failure; `all()` then
  returns `emptyList()`, `get()` returns null. No log, no backup, no
  migration. One torn `apply()` write (process kill mid-persist) wipes the
  user's alarms on next read.
- Mitigations (cheap): keep a `alarms.bak` copy of the last good raw string;
  fall back to it when `JSONObject(raw)` fails and log an event; validate
  `hour in 0..23`, `minute in 0..59`, `repeatDays ⊆ 1..7`, `monthlyDay in
  1..31` in `fromJson`.

### 2.2 `EventLog.getDb()` builds Room on the calling (main) thread
- `app/src/main/java/com/malarm/EventLog.kt:61-65` calls
  `Room.databaseBuilder(...).build()` synchronously from `log()`, which is
  invoked from `onReceive`/click handlers on the main thread. First call does
  disk I/O → ANR risk. Use a `@Volatile` double-checked lock with background
  init, or pre-warm in `Application.onCreate`.
- Also `scope = CoroutineScope(Dispatchers.IO)` is unstructured (never
  cancelled, no `SupervisorJob`); a failing `insert` cancels nothing today
  but the pattern is fragile. Prefer an application-scoped scope.

### 2.3 `MediaPlayer` failure leaves a zombie non-playing instance
- `RingtoneService.startRinging:69-88`: `player = MediaPlayer().apply {...}` —
  if `setDataSource/prepare` throws, `runCatching` swallows it and `player`
  stays non-null, looping flag set, never started, never retried with the
  default URI. The alarm then vibrates silently with no audible fallback.
- Fix: build the player in a local, `release()` on failure, fall back to
  `RingtoneManager.getDefaultUri(TYPE_ALARM)`, and if that also fails log an
  event.

### 2.4 No `directBootAware` — alarms cannot fire before first unlock after reboot
- `BootReceiver` + `RingtoneService` + `AlarmStore` prefs are not
  direct-boot-aware; `RECEIVE_BOOT_COMPLETED` is only delivered after unlock.
  An alarm scheduled during the locked-after-reboot window is missed. Document
  as a limitation or add `android:directBootAware="true"` + device-protected
  storage for the alarm prefs/DB.

### 2.5 `START_NOT_STICKY` + 5-minute self-stop can lose a ringing alarm
- If the system kills `RingtoneService` mid-ring, it is not restarted and the
  notification is gone (`onDestroy` cancels it). Acceptable for a 5-min cap,
  but worth a one-line comment; `stopSelf()` in the delayed callback should
  use the startId form to avoid killing a successor alarm (§1.7).

### 2.6 `registerReceiver` without an explicit exported flag
- `MainActivity.kt:161` uses the 2-arg `registerReceiver(receiver,
  filter)`. On API 33+ the 3-arg form with `RECEIVER_NOT_EXPORTED` is the
  canonical/lint-clean call for system broadcasts; the 2-arg overload still
  works but draws lint attention and is fragile across targetSdk bumps. Same
  file already uses `RECEIVER_NOT_EXPORTED` correctly in tests
  (`AlarmActivityTest.kt:49-54`).

### 2.7 WakeLock bridge held 30 s unconditionally
- `AlarmReceiver.handleAlarm:102-104` acquires `bridgeLock` with a 30 s timeout
  and never releases early. If the FSI posts in <1 s, the lock is held ~29 s
  needlessly. Minor battery cost; release after `startForegroundService` /
  `notify` returns (with timeout as fallback).

---

## 3. UX / state-management issues

### 3.1 Edit dialog does not survive rotation (documented, not fixed)
- `MainActivity.dialog/editing/dialogBinding` are plain fields; rotation
  destroys the `AlertDialog` and all unsaved edits. `docs/manual-tests-release.md`
  §12.4 records this as a "known limitation". Either retain via
  `DialogFragment`/`onSaveInstanceState`/`ViewModel`, or set
  `android:configChanges` handling for the dialog — documenting data loss is
  not a fix.

### 3.2 Repeat-picker `pending` vs `editing` split loses selections on dismiss
- `MainActivity.showAlarmDialog:274-412` (single-choice block at `:367-411`): tapping a single-choice option only
  sets local `pending`; `editing` updates solely on Save. Tapping outside /
  Back dismisses without Save → selection silently discarded (labels refresh
  from stale `editing` in `onDismiss`). Monthly/Custom branches call
  `dialog.dismiss()` which fires that same `onDismiss` before opening the
  sub-picker, causing a visible label flicker.
- Simpler canonical pattern: update `editing` immediately on option tap and
  drop the `pending` variable, or use `setSingleChoiceItems` with direct
  commit.

### 3.3 `showWeeklyDaysPicker` Save-without-touch preserves an illegal date+repeat combo
- `MainActivity.kt:465-487` (`pendingDays ?: initial.repeatDays` at `:475`, save at `:481-487`): with
  `pendingDate` initialized to `initial.dateMillis`. Opening the picker from a
  date alarm and pressing Save without touching the list keeps **both**
  `dateMillis` and `repeatDays` → feeds §1.3. Clear `dateMillis` whenever the
  picker is confirmed with a non-empty day set, regardless of whether the list
  was touched.

### 3.4 `nextEvenHour` is misnamed; midnight case looks wrong until analyzed
- `MainActivity.kt:568` computes "now + 5 min, rounded **up** to the top
  of the hour", not the next even hour. Name it `nextTopOfHour` /
  `defaultNewAlarmTime`. The `23:58 → 01:00` test expectation is correct for
  round-up semantics but reads like a bug next to the current name.

### 3.5 Notification actions have no icons (`addAction(0, ...)`)
- `AlarmNotifier.kt:70-72` passes `0` as the action icon. Works on AOSP but
  renders as text-only / misaligned on several OEM skins. Supply small
  drawables (`ic_snooze`, `ic_dismiss`, …).

### 3.6 Hardcoded chooser title, vague Clear title
- `EventLogActivity.exportLog:74` hardcodes `"Export log"`; add
  `R.string.export_log_chooser`. `clearLog` uses `R.string.clear` ("Clear") as
  the dialog title — use a dedicated `clear_log_title`.

### 3.7 Settings rows are bare `LinearLayout`s, not accessible list items
- `activity_settings.xml` rows lack `focusable`, `clickable`, and `contentDescription`
  semantics beyond the background ripple. Canonical would be `MaterialTextView`
  rows with `?attr/selectableItemBackground` + `android:focusable="true"`, or
  a `PreferenceFragmentCompat`. Low priority.

---

## 4. Non-standard / non-canonical patterns

1. **Business logic inside `MainActivity`** — `formatTimeUntil`,
   `nextEvenHour`, `timeUntil` are `internal` methods on the Activity purely
   for testability (`MainActivityTest` builds a full activity per assertion).
   Canonical: top-level pure functions in `AlarmFormatter` (or a
   `TimeUtils` object) — cheaper tests, reusable from notifications/widgets.
2. **`AlarmAdapter.submit()` wrapper** (`AlarmAdapter.kt:22`) shadows
   `ListAdapter.submitList` for "churn" reasons. It hides the
   `commitCallback` overload and confuses readers; call `submitList` directly.
3. **Two `stopRinging` implementations** — `AlarmNotifier.stopRinging`
   (service + notification) vs `AlarmReceiver.stopRinging` (private dup,
   `:70-73`). `handleSnooze` uses the former, `handleDismiss` the latter.
   Keep one.
4. **`AlarmActivity.snooze()/dismiss()` via `sendBroadcast` to own receiver**
   (`AlarmActivity.kt:78-94`). In-process work (schedule snooze, stop service)
   round-trips through the broadcast queue instead of calling `AlarmScheduler`
   + `AlarmNotifier` directly. Adds latency (ringtone lingers) and a failure
   mode (receiver disabled/killed). The explicit-to-non-exported broadcast is
   legal but unusual; direct calls are canonical here.
5. **`AlarmScheduler.ROLE_*` constants live on the scheduler but are really
   `PendingIntent`-identity concerns** shared by `AlarmNotifier`. Either move
   `requestCode()` + roles to a small `PendingIntents` helper or keep as-is
   with a doc link — current split is workable but surprising.
6. **Date formatting duplicated** — `AlarmFormatter.repeat` date branch,
   `MainActivity.dateLabel`, and `EventLog.formatTimestamp` each roll their
   own `DateFormat.getDateInstance(MEDIUM)`. Centralize in `AlarmFormatter`.
7. **`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` for an alarm** — there is no
   alarm FGS type, so media-playback is the de-facto workaround and the
   manifest declares it correctly. Still worth a comment citing the
   alternative (`shortService` on API 34+ is insufficient for a 5-min ring);
   reviewers will flag it otherwise.
8. **`USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` both declared** (manifest
   `:11-12`). Only `SCHEDULE_EXACT_ALARM` is needed for `setAlarmClock`;
   `USE_EXACT_ALARM` is a signature-level permission with no effect here.
   Remove it to avoid reviewer confusion.
9. **`@MainThread` on `AlarmStore` mutators** — true today (all callers are
   main), but the annotation promises "must be main" rather than describing
   reality, and `apply()`-based persistence is main-safe but not main-bound.
   Either enforce (assert `Looper.myLooper()`) or drop the annotations; as-is
   they add noise.
10. **Test-only formatting glitch** — `AlarmStoreTest.kt:11-12` puts `@Config`
    on the same line as `@RunWith`. Works, but every other test class uses two
    lines; fix for consistency.

---

## 5. Duplication

| # | Duplication | Locations |
|---|-------------|-----------|
| D1 | Stop-service + cancel-notification (2 impls) | `AlarmNotifier.stopRinging:81-84` vs `AlarmReceiver.stopRinging:70-73` |
| D2 | `DateFormat.MEDIUM` date rendering | `AlarmFormatter.repeat:38`, `MainActivity.dateLabel:591` |
| D3 | Ringtone label `getString(ringtone_format, …)` | `MainActivity.showAlarmDialog:288`, `MainActivity.ringtoneLabel:600-602` |
| D4 | Timestamp formatting (`SimpleDateFormat`) | `EventLog.formatTimestamp:78` vs `AlarmExport.fileName:20` (different patterns, same boilerplate) |
| D5 | Alarm list icon paths (launcher vs small icon differ only by fill + size) | `drawable/ic_launcher.xml` vs `drawable/ic_alarm_small.xml` |
| D6 | Six `time_step_*` strings + six nearly identical step buttons | `strings.xml:23-28`, `dialog_alarm.xml:51-128`, `MainActivity:307-327` — could be a string-array + loop-inflated views |
| D7 | Per-test Robolectric boilerplate (clear prefs, clear log, cancel alarms) copy-pasted across 8 test classes | `AlarmReceiverTest.setUp`, `BootReceiverTest.setUp`, `AlarmActivityTest.setUp`, `SnoozePickerActivityTest.setUp`, `DebugScheduleTest.cleanState`, `MainActivityTest` (ad hoc per test), … — extract a `TestFixtures.clearAppState(context)` helper (cf. `docs/testing-notes.md` gotcha section, which exists precisely because of this). The post-review test batches copied the pattern again instead of extracting it. |
| D8 | Snooze durations enumerated in two places with different ranges | `SnoozePickerActivity.SNOOZE_OPTIONS = 5..480` vs Settings `1..60` NumberPicker — intentional, but the relationship is undocumented |

---

## 6. Unused / dead items

- `AlarmExport.SCHEMA_VERSION`, `appVersion`, `appVersionCode`, `exportedAt`
  are **written but never read** (`AlarmExport.kt:11-17`). `import()` ignores
  `schemaVersion` entirely, so a future schema change has no migration hook.
  Either check it (reject `> CURRENT` with a dedicated error) or stop writing
  it.
- `AlarmScheduler.nextTrigger` weekly loop `for (i in 0..7)` (`:183`) iterates
  8 times; 7 (`0..6` / `until 7`) suffices. Harmless but reads as off-by-one.
- `AlarmScheduler.schedule(..., log = true)` `SCHEDULED` detail
  (`:29`, shared by the new `scheduleAt`) `"Time: ${alarm.hour}:${alarm.minute}"`
  is never consumed by UI and is not zero-padded (`8:5` for 08:05). Either
  format with `%02d` or drop the detail.
- `EventType.UNKNOWN` is only a Room-migration fallback; fine, but the
  `0.5.5` changelog entry about "older app version" crash implies version 1→2
  migration coverage that has **no test**. Add a `toEventType("BOGUS") ==
  UNKNOWN` unit test (one line).
- `R.string.time_step_*`, `minutes_only`/`hours_only` are shared between
  countdown (`formatTimeUntil`) and snooze labels (`snoozeLabel`) — not unused,
  but overloading "5 min" for both countdown and picker labels conflates two
  concepts; harmless.
- No unused imports/resources found beyond the above; `ic_settings`,
  `main_menu`, `permissionGate` views, and all `strings.xml` entries are
  referenced. `local.properties` and `app/build/` are correctly **untracked**
  (verified via `git ls-files`).

---

## 7. Documentation issues

1. **README snooze claim is inaccurate**: "pick a preset (5–30 min, 1–8 h) or
   a custom duration" — the picker offers 5/10/15/30 min + 1/2/4/8 h; the only
   "custom" is the Settings default (1–60 min) used by the quick-Snooze
   action. Reword to "presets 5 min–8 h, plus a configurable default snooze".
2. **Dangling `§6.8` reference** in `BootReceiver.kt:34` ("survives untouched
   (§6.8)"). Design notes have no numbered sections. Point at the "Snooze"
   heading instead.
3. **`docs/design-notes.md` "One-shot semantics" vs `AlarmScheduler`**: the
   "past date → auto-disabled" rule lives only in `MainActivity.saveDialog`,
   not in the scheduler/store layer — import and receiver paths bypass it
   (§1.5). Either move the rule down a layer or scope the doc to "via the
   edit dialog".
4. **`docs/testing-notes.md` + `AlarmSchedulerAndroidTest.tearDown`**: the
   teardown sets `ShadowAlarmManager.setCanScheduleExactAlarms(false)` without
   restoring the prior value — global shadow mutation that can leak into
   other test classes depending on Robolectric's per-class sandbox reuse.
   Save/restore instead.
5. **Fastlane `full_description.txt`** claims "weekly" repeat; the UI offers
   daily/weekdays/weekends/monthly/custom-days but no first-class "weekly on
   X" preset (custom days covers it). One-word fix: "weekly (custom days)".
6. **AGENTS.md workflow vs reality**: "Run `./gradlew test lint` before
   proposing…" — the committed `lint-results-debug.txt` (not tracked, but
   present in workspace) shows only pre-existing `InlinedApi`/`UnusedAttribute`
   warnings, consistent with a clean gate. No action; confirming the gate is
   green on this commit would close the loop.
7. **Changelogs**: `fastlane/.../changelogs/11.txt` matches versionCode 11 /
   0.6.1 — in sync per AGENTS.md rule 2. Older changelogs (2–10) present.
   `2.txt` veers into prose ("Snooze duration is now configurable…"
   concatenated without blank line after the 0.5.2 entry) — cosmetic.

---

## 8. Test gaps (what to add next)

1. `AlarmStore` corrupt-JSON fallback (§2.1) and validation of hostile
   `fromJson` (hour 99, `monthlyDay` 0/32, unknown day ints).
2. `AlarmExport.import` with one bad element among good ones (§1.4) and with
   `schemaVersion > CURRENT` (§6).
3. `requestCode` non-negativity for ids near `Int.MAX_VALUE*STRIDE` and
   `Long.MAX_VALUE` (§1.6).
4. Foreground `timeChangeReceiver` preserves snooze (§1.1) — currently only
   `BootReceiverTest.timeChangePreservesSnooze_bootCancelsIt` covers the
   background path.
5. `SettingsActivity` export with null stream (§1.2) and import of empty list
   (destructive wipe — assert the confirm copy warns).
6. Concurrent-fire: two alarms same trigger → assert defined behavior (§1.7).
7. `RingtoneService` bad-URI fallback (§2.3) and second-start-during-ring
   timeout behavior.
8. `Converters.toEventType("BOGUS") == UNKNOWN` migration guard.
9. Multi-API Robolectric run (e.g. `sdk = [30, 35]`) for the pre-S exact path
   — today only one test pins `sdk = [30]`.

---

## 9. Suggested fix order

1. §1.1 (one-line `cancelMain` swap + test) — spec violation, user-visible
   snooze loss.
2. §1.2 (null-stream branch) — data-loss-adjacent UX lie, two lines.
3. §1.3 + §3.3 (normalize date-vs-repeat on all write paths) — immortal
   alarms.
4. §1.4 + §1.5 (import parity with save-dialog: skip reporting + never-ring
   disable) — import is the only unguarded write path.
5. §2.1 (backup + validation) + §2.2 (background Room init) — hardening.
6. §1.6, §2.3, §2.6, §4.1–4.4 (small cleanups) — batch as a hygiene PR.
7. Docs touch-ups (§7.1, §7.2, §7.5) alongside the next changelog.

No F-Droid guardrail violations found: `applicationId com.malarm` unchanged,
versionCode 11 / versionName 0.6.1 in `app/build.gradle.kts`, no
`signingConfig`, `fastlane/metadata/android/en-US/` intact with matching
`changelogs/11.txt`, no AGP/Gradle/compileSdk/minSdk drift versus the frozen
recipe.

---

## 10. Refresh addendum (`2911b3d` + working tree)

Refresh method: `git log`/`git diff` review plus targeted re-reads of every
cited location — not a second full read. Nothing in §1–§9 was invalidated;
§1.1 was re-verified live (`MainActivity.kt:44` still cancels both slots).

### Landed since `280dca4`, previously unreviewed

- **Commit `2911b3d`** ("Drop duplicate alarm deliveries and auto-disable
  expired date alarms"): `AlarmReceiver` now drops redeliveries inside a 30 s
  `(alarm, snooze)` window, and expired date alarms are auto-disabled on
  receive/startup. Narrows §1.5/§7.3 (receiver and startup paths now treat
  never-ringing dates) but does **not** close them — the import path
  (`applyImport`) is still unguarded. The dedup window itself is new
  unreviewed logic: keying, clock source (`currentTimeMillis` vs elapsed),
  and the 30 s constant deserve a look.
- **Working tree, app code**: `AlarmScheduler.scheduleAt()` (exact-millis
  scheduling bypassing minute-precision `nextTrigger`, debug-use only) plus
  `debug_schedule_secs` (`MainActivity.handleDebugIntent`) and the
  `handleDebugIntent`-after-rearm reorder — the reorder fixed a real bug
  (re-arm loop overwrote sub-minute debug alarms, rolling them to tomorrow)
  caught by the new `DebugScheduleTest`. Risk note: `scheduleAt` is public
  on the scheduler with only a doc comment keeping production paths off it.
- **Working tree, docs**: manual QA split into `manual-tests-smoke.md` +
  `manual-tests-release.md` (`manual-tests.md` is now an index); the
  on-demand periodic-check command was fixed from implicit (`-a/-p`, which
  never resolves — `AlarmReceiver` has no intent-filter) to explicit
  (`-n com.malarm/.AlarmReceiver`), and release 7.2 was corrected to
  background-not-force-stop (stopped packages drop broadcasts). Budgets
  re-synced (smoke ~7–10 min, release delta ~55–65 min).
- **Working tree, tests** (14 new, all green): snooze-cancel on
  delete/edit, edit persist + reschedule, configured-snooze wiring,
  fire→snooze→dismiss and boot/tz log sequences, periodic re-arm on boot,
  startup past-date disable, notifier 3-action content, full-screen
  Snooze/Dismiss/Custom wiring. Plus hardening: `BootReceiverTest`
  waits for all expected event types (was first-nonempty), `AlarmStoreTest`
  clears prefs in `setUp` (was order-dependent).

### §8 status

All 9 gap items remain open — the new tests covered adjacent manual-test
logic (snooze wiring, log sequences) rather than these. Highest value
unchanged: #4 (foreground-receiver snooze preservation, pairs with still-live
§1.1), #2 (hostile import — pairs with §1.3–§1.5), #8 (one-line converters
guard).

### F-Droid re-check

Unchanged: `applicationId com.malarm`, versionCode 11 / versionName 0.6.1,
no `signingConfig`, `fastlane/metadata/android/en-US/` intact, no recipe
drift. Docs-only + test-only + DEBUG-gated changes need no new MR.

A full second read is warranted only if §1.1, scheduling, or the dedup
window is touched; otherwise this refresh plus §10 keeps the review current.
