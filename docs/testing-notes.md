# Testing Notes

Agent-facing notes for automated (Robolectric) and emulator testing.

## Robolectric gotchas

- **Shared state persists across tests in the same class** (same Application):
  `SharedPreferences`, the Room event-log DB, and `AlarmManager` all carry over
  between `@Test` methods. Add `@Before` cleanup to test classes that touch
  them: clear the `"malarm"` prefs, clear the event log
  (`runBlocking { EventLog.clear(context) }`), and cancel scheduled alarms.
- `System.currentTimeMillis()` in Robolectric is shadowed; don't rely on the
  real wall clock for assertions. For pure time logic, prefer an injected/pure
  function (e.g. `MainActivity.formatTimeUntil(totalMinutes)` takes minutes
  directly) instead of controlling the shadow clock.
- The snooze picker's dialog is a `MaterialAlertDialogBuilder` list; find it via
  `ShadowDialog.getShownDialogs()` and search the decor view for the `ListView`
  (it is not `android.R.id.list`).
- `AlarmManager` shadow: filter `scheduledAlarms` by intent action
  (`ACTION_ALARM`) when counting user alarms — the periodic reschedule alarm is
  also present.

## Emulator techniques

- **Timezone**: `adb shell cmd alarm set-timezone America/New_York`
- **Wall clock (epoch ms)**: `adb shell cmd alarm set-time <epoch-ms>`
- **Debug alarm**: launching with `--ez debug_schedule true` schedules a one-shot
  ~1 min out, but it only works on a **fresh `onCreate`** — force-stop the app
  first, or it silently does nothing.
- **Dialog text entry via adb**: the emulator IME often won't focus a dialog
  `EditText` from `adb shell input tap`/`input text`, so label editing can't be
  driven by automation. This is a test-harness limitation, not an app bug (the
  same code path works for new-alarm creation).
- Inspect the event log directly:
  `adb shell "run-as com.malarm sqlite3 databases/alarm-events 'SELECT datetime(timestamp/1000,"'"'"'unixepoch'"'"'"), type, alarmId, label, details FROM event_log ORDER BY id DESC LIMIT 20;'"`
- Check scheduled alarms:
  `adb shell dumpsys alarm | grep -A1 com.malarm`
- Check the ringtone service is running/stopped:
  `adb shell dumpsys activity services | grep RingtoneService`
- Notifications:
  `adb shell dumpsys notification`

## Manual QA

The emulator test checklist lives in `docs/manual-tests.md`. It is **on-demand
only** — do not read or execute it during routine development.
