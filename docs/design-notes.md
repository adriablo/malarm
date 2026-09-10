# Design Notes & Platform Constraints

Agent-facing notes. Read these before touching scheduling, the alarm UI, or the
event log, so you don't reintroduce a reverted experiment or rely on a
misleading API.

## Alarm presentation (how the full-screen alarm shows)

- Alarms are scheduled with `AlarmManager.setAlarmClock(...)`, which is the
  reliable mechanism: it wakes the screen when locked, shows over the keyguard,
  and is **exempt from Do Not Disturb**.
- The full-screen alarm (`AlarmActivity`) is launched by the **notification's
  `setFullScreenIntent`**. This is deliberate and reliable.
- **Do not "improve" this with a direct `startActivity` from the receiver.** It
  was tried; Android's Background Activity Launch (BAL) restrictions block it
  in the app-switch state, so the alarm could go off with no UI at all. The
  FSI-notification path is the one that works. (`AlarmReceiver` therefore does
  **not** launch `AlarmActivity` directly.)
- `AlarmActivity` and `SnoozePickerActivity` use an **empty `taskAffinity`**
  so they run in their own task and, when finished, return to whatever was
  beneath (previous app / lock screen) instead of the main Malarm activity.
- When the device is **unlocked**, Android demotes the full-screen intent to a
  **heads-up notification** (system behavior). The alarm still rings; there is
  no way to force a full-screen jump over an unlocked in-use app. This is
  expected, not a bug.

## Distinguishing "is the screen on" (lock state)

- `KeyguardManager.isDeviceLocked` is **wrong** on devices without a secure
  PIN: it returns `false` even when the screen is off.
- Use `PowerManager.isInteractive()` to decide "is the user looking at the
  phone" / "should we wake the screen."

## Snooze

- The notification and full-screen alarm both offer a snooze **picker**
  (`SnoozePickerActivity`) with presets 5/10/15/30 min and 1/2/4/8 h. The
  notification also has a quick **Snooze** action that uses the configured
  default from Settings.
- Deleting or editing a snoozed alarm **cancels the snooze** (via
  `AlarmScheduler.cancel`, which cancels both the main and snooze
  PendingIntents). A snoozed-then-deleted alarm does not ring.
- **Back press on the full-screen alarm snoozes** (`AlarmActivity` routes it
  through `snooze()` + `finish()`). Leaving without an explicit choice must
  never orphan the ringtone.
- Snooze timing is based on **`ELAPSED_REALTIME_WAKEUP`** (`setExactElapsed`),
  so manual clock changes can't shift or swallow a pending snooze.

## Time / timezone re-anchoring

- A **force-stop cancels all PendingIntents** and nothing re-arms them until
  the next reboot, so `MainActivity.onCreate` re-schedules enabled alarms
  quietly on every start (`schedule(..., log = false)`; idempotent, skips
  expired one-shots via `nextTrigger()`).
- Timezone changes are detected by the `TimeZone.getDefault().id` guard.
- Manual clock changes (same timezone) are detected by comparing the wall clock
  against `SystemClock.elapsedRealtime()` (the calibration pair stored in
  `AlarmStore`). `ElapsedRealtime` ignores clock changes.
- The periodic check runs on `ELAPSED_REALTIME` every 2h. It re-anchors only
  when the timezone id changed **or** the wall clock jumped beyond tolerance.

## One-shot semantics

- A one-shot with a time already past today **rolls to tomorrow**.
- A one-shot with a past date is **auto-disabled** with a
  "will never ring" message rather than left as a dead enabled alarm.
  Enforced on the save dialog, on boot, and on app start — but **not** on
  import, which can still leave an enabled never-ringing alarm (open
  code-review §1.5).

## Event log

- Events are logged with the alarm id and label where available. DISMISSED
  carries the alarm id (the dismiss broadcast includes `EXTRA_ALARM_ID`).
  A dismiss that preserves the series adds `Re-armed` details and is
  followed by a SCHEDULED row; a dismiss with no re-arm (fired one-shot)
  has neither. A repeating alarm therefore always shows SCHEDULED after
  DISMISSED — a CANCELLED(Dismiss)+DISMISSED with no following SCHEDULED
  and no DISABLED/DELETED means tomorrow was disarmed.
- SCHEDULED details name the exact armed trigger
  (`Next: yyyy-MM-dd HH:mm:ss`) so a missed occurrence is identifiable by
  date, not just wall-clock time.
- `PERIODIC_CHECK` is logged at **fire time** in `handleRescheduleAll`, not at
  schedule time — otherwise it spams the log on every app open / tz change.
- `APP_START` is logged only when `MainActivity` finds a past-due armed trigger
  and re-arms it, so a missed alarm followed by `APP_START` means the app was
  opened late (instance rolled forward). Normal app opens stay out of the log;
  the per-alarm re-arm itself stays quiet (`log = false`).
- `MISSED` is logged by `MissedAlarmWatchdog` (runs on app start and on every
  periodic tick, including a tick with no clock change): an enabled alarm whose
  recorded trigger passed 5+ min ago with no `FIRED` in that trigger's delivery
  window. Each `schedule` records the trigger in `AlarmStore`, each cancel
  clears it; snooze instances are out of scope. Each trigger is examined once,
  and routine periodic ticks do not add a `PERIODIC_CHECK` row.
- `AlarmScheduler.cancel()` takes an optional `reason` (e.g. "Boot",
  "Time change", "Reschedule") so boot/tz reschedule floods are identifiable
  as reschedules, distinct from user cancellations.
