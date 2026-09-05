# Manual Release Test — Malarm (per-release delta, ~55–65 min)

> **Agent instruction:** This document is for **manual/emulator QA only**. Do not read, load, or execute it during normal development tasks. Only consult it when the user explicitly asks for manual tests, verification on the emulator, or a full QA pass. When triggered, follow the sections below against a real emulator and report results per scenario.

Slow per-release pass: everything **not** in the fast smoke test. A full release pass = `docs/manual-tests-smoke.md` first, then this file. Grouped by disruptive setup so you reboot / change timezone as few times as possible.

Run on the emulator (API 35+ recommended). The debug-signed APK is `app/build/outputs/apk/debug/malarm.apk`. Budget: ~55–65 min including 1 reboot.

Prerequisite: smoke (S1–S12) green on the same build.

## Setup

- Start from the smoke end-state or a fresh install: `adb uninstall com.malarm` then install the debug APK — or a Quick Boot snapshot (see speed tips in `docs/manual-tests.md`).
- Grant notifications (`POST_NOTIFICATIONS`) and exact-alarm access on first launch (exact alarms are special app access — use the `appops` commands in the quick reference, not `pm grant`).
- Settings lives behind the gear icon in the top app bar.
- Note the device timezone and current time before testing. Restore them at the end.
- Batch the disruptive blocks: §5 (timezone/clock) then §6 (reboot) then §8 (permissions) — one reboot and one permission cycle total. Verify via `dumpsys`/sqlite where stated instead of waiting.

---

## 1. Alarm creation remainder (not in smoke)

Smoke covers 1.1 (one-shot) and 1.2 (daily). Test the rest here.

| # | Scenario | Expected |
|---|----------|----------|
| 1.3 | Add a weekdays alarm (Mon–Fri) | Shows "Weekdays"; next trigger = next weekday |
| 1.4 | Add a weekends alarm (Sat+Sun) | Shows "Weekends" |
| 1.5 | Add a monthly alarm (e.g. day 16) | Shows "Monthly on day 16"; trigger = next month 16th |
| 1.6 | Add a date alarm (future date) | Shows the date; trigger = that date at alarm time |
| 1.7 | Add a custom-days alarm (pick Mon+Wed) | Shows "Mon, Wed" |
| 1.8 | Open the dialog for a new alarm | Label field first; time defaults to the next even hour (now+5m rounded up) |
| 1.9 | Tap the -15/-10/-5/+5/+10/+15 step buttons | Time shifts by that many minutes per tap |

## 2. Time edge cases

| # | Scenario | Expected |
|---|----------|----------|
| 2.1 | Set a one-shot time in the past (e.g. 12:00 when it's 13:00) | **Rolls to tomorrow 12:00**; toast "Will ring in …" |
| 2.2 | Set a date alarm with a past date | **Auto-disabled** with "This alarm will never ring" toast; alarm appears disabled |
| 2.3 | Set a monthly day 31 | Fires on next month that has a 31st (skips short months) |
| 2.4 | Set a monthly day 29 in a non-leap year | Skips February, fires March 29 |

## 3. Enable / edit / delete remainder

Smoke covers 3.1/3.2 (toggle) and 3.4 (list delete).

| # | Scenario | Expected |
|---|----------|----------|
| 3.3 | Edit an alarm's time/repeat and save | Changes persist; alarm re-scheduled to new time |
| 3.5 | Delete via the edit dialog | Same as 3.4 (removed from list and AlarmManager; DELETED + CANCELLED) |
| 6.5 | **Edit (change) the snoozed alarm before it re-fires** | Snooze cancelled; re-scheduled to the new time; does not ring at the old snooze time |

## 4. Firing + snooze remainder

Smoke covers unlocked heads-up/dismiss/snooze (4.1–4.3), locked wake/dismiss/back (5.1, 5.4, 5.6), and delete-cancels-snooze (6.4).

| # | Scenario | Expected |
|---|----------|----------|
| 4.4 | Tap notification Custom | Snooze picker opens; select a preset → snoozes for that duration |
| 5.2 | Tap full-screen Snooze | Default snooze; ringing stops; returns to the previous screen (not the main app) |
| 5.3 | Tap full-screen Custom | Picker opens over the lock screen; select → snoozes; returns to previous screen |
| 5.5 | Dismiss while the device has a secure lock (PIN) | Alarm still shows over the lock screen (showWhenLocked) |
| 6.1 | Snooze for 5 min (default) | Snooze alarm armed ~5 min out (`dumpsys alarm`); ringing stops |
| 6.2 | Snooze for 8 h via picker | Snooze armed ~8 h out; ringing stops |
| 6.3 | Snoozed alarm fires again | Rings again; one-shot becomes disabled after, repeating re-arms |
| 6.6 | Snooze a *disabled* one-shot (via re-fire path) | Snooze still rings (snooze bypasses disabled check) |
| 6.7 | Open the snooze picker (Custom), then cancel/back out | Ringing **stops**; no snooze is armed |
| 6.8 | Snooze, then change the wall clock past the snooze time | Snooze still fires ~on schedule (elapsed-based timing, immune to clock jumps) |

## 5. Timezone / clock block (do together)

| # | Scenario | Expected |
|---|----------|----------|
| 7.1 | Change timezone (e.g. `cmd alarm set-timezone`) with app open | All alarms re-anchor to the new local wall time; event log shows TIMEZONE_CHANGED |
| 7.2 | Background the app with Home (do NOT force-stop — a stopped package drops the broadcast), change timezone, then fire the periodic check on demand (explicit `-n`, see quick reference; do NOT wait 4h) | Periodic check re-anchors alarms; event log shows PERIODIC_CHECK "Rescheduling" + SCHEDULED per alarm |
| 7.3 | Manually set clock forward/back with app open | Alarms re-anchor to wall time; event log shows TIMEZONE_CHANGED (via TIME_SET) |
| 10.3 | Check timezone change | TIMEZONE_CHANGED, then CANCELLED("Time change") + SCHEDULED per alarm |
| 10.4 | Check periodic check | PERIODIC_CHECK appears ~every 4h (not more often) with "No change" or "Rescheduling" |

Restore the original timezone/clock when done.

## 6. Reboot block (1–2 reboots total)

| # | Scenario | Expected |
|---|----------|----------|
| 8.1 | Reboot with enabled alarms | BOOT_COMPLETED logged; all enabled alarms rescheduled |
| 8.2 | Reboot with only disabled alarms | Nothing scheduled; no ringing |
| 8.3 | Reboot; check periodic reschedule is re-armed | `ACTION_RESCHEDULE_ALL` present every 4h cadence |
| 8.4 | Force-stop the app with enabled alarms, then relaunch | Alarms re-armed quietly on start (no log spam, no toasts); next triggers intact |
| 10.2 | Check boot reschedule | BOOT_COMPLETED, then CANCELLED("Boot") + SCHEDULED per alarm |

## 7. Settings + event log

| # | Scenario | Expected |
|---|----------|----------|
| 9.1 | Remove inactive alarms with an expired/disabled alarm present | Only the inactive one is removed; toast shows count |
| 9.2 | Remove inactive with none present | "No inactive alarms" toast |
| 9.3 | Export alarms | System CreateDocument picker opens; default name `malarm-alarms-<ISO-timestamp>.json` |
| 9.3b | Open Settings via the top-app-bar gear | Settings opens; rows for snooze duration, export/import, remove inactive, GitHub, F-Droid, event log, version |
| 9.4 | Import a valid backup | Replaces all alarms; toast shows imported count |
| 9.5 | Import a malformed file | "Import failed" toast; nothing changes |
| 9.6 | Set snooze duration in Settings | Used as the "Snooze" (default) duration |
| 9.7 | Open Event log | Shows recent events with timestamps, type, label (id) |
| 9.8 | Export Event log | Share sheet opens with full text dump |
| 9.9 | Clear Event log | Confirmation dialog; after confirm, log is empty |
| 10.1 | Check a full fire→snooze→dismiss cycle | Log shows FIRED, SNOOZED (with minutes), DISMISSED (with alarm id + label) |
| 10.5 | Dismiss from notification | DISMISSED logged with the alarm id + label |

## 8. Permissions / OS integration

Smoke covers 11.5 (buttons fit) as a free check. Do the disruptive permission flows here — share a single fresh install across 11.1–11.3 (deny → verify → re-grant between rows) instead of reinstalling per row.

| # | Scenario | Expected |
|---|----------|----------|
| 11.1 | First launch on Android 13+ | Notification permission requested; gate blocks UI until granted |
| 11.2 | Deny notifications | Snackbar; permission gate stays; alarms degrade gracefully |
| 11.3 | Deny exact-alarm access (Android 12–13) | Warning snackbar; alarms fall back to inexact `setWindow` (fires within ~1 min) |
| 11.4 | Do Not Disturb on, alarm fires (exact granted) | Alarm still rings (setAlarmClock is DND-exempt) |

Re-grant notifications + exact alarms when done so later sections behave.

## 9. Regression checks

| # | Scenario | Expected |
|---|----------|----------|
| 12.1 | Two alarms at the same time | Both armed; second rings after first (or absorbed — confirm acceptable behavior) |
| 12.2 | App killed (swiped away) before alarm fires | Alarm still fires (AlarmManager persists) |
| 12.3 | Rotate the device during an active alarm | Alarm continues; buttons still clear of nav bar |
| 12.4 | Rotate during alarm edit dialog | Note: dialog is lost (known limitation — no saved instance state) |

---

## Quick command references

```bash
# Grant permissions (Android 13+ emulator)
adb shell pm grant com.malarm android.permission.POST_NOTIFICATIONS
# Exact alarms + full-screen intent are special app access, not runtime permissions:
adb shell appops set com.malarm SCHEDULE_EXACT_ALARM allow
adb shell appops set com.malarm USE_FULL_SCREEN_INTENT allow

# Schedule a debug alarm ~1 min out (fresh onCreate only — force-stop first);
# --ei debug_schedule_secs 20 fires in ~20 s (debug builds only)
adb shell am force-stop com.malarm
adb shell am start --ez debug_schedule true --ei debug_schedule_secs 20 -n com.malarm/.MainActivity

# Fire the periodic 4h check on demand (for 7.2 / 10.4 — no waiting).
# Explicit -n: AlarmReceiver has no intent-filter, so implicit -a/-p never resolves.
# Background the app first; do NOT force-stop (stopped packages drop broadcasts).
adb shell am broadcast -n com.malarm/.AlarmReceiver -a com.malarm.ACTION_RESCHEDULE_ALL

# Change timezone
adb shell cmd alarm set-timezone America/New_York

# Change wall clock (epoch ms)
adb shell cmd alarm set-time <epoch-ms>

# Check scheduled alarms
adb shell dumpsys alarm | grep -A1 com.malarm

# Check notifications
adb shell dumpsys notification

# Check running ringtone service
adb shell dumpsys activity services | grep RingtoneService

# View event log (app DB)
adb shell "run-as com.malarm sqlite3 databases/alarm-events 'SELECT datetime(timestamp/1000,"'"'"'unixepoch'"'"'","'"'"') , type, alarmId, label, details FROM event_log ORDER BY id DESC LIMIT 20;'"

# Reboot
adb reboot
```
