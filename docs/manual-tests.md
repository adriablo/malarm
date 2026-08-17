# Manual Test Checklist — Malarm

> **Agent instruction:** This document is for **manual/emulator QA only**. Do not read, load, or execute it during normal development tasks. Only consult it when the user explicitly asks to run manual tests, verify behavior on the emulator, or does a full QA pass. When triggered, follow the sections below against a real emulator and report results per scenario.

Run these on the emulator (API 35+ recommended) after a **fresh install** (uninstall first) to confirm a clean baseline. The debug-signed APK is `app/build/outputs/apk/debug/malarm.apk`.

## Setup

- Fresh install: `adb uninstall com.malarm` then install the debug APK.
- Grant notifications (`POST_NOTIFICATIONS`) and exact-alarm access on first launch.
- Note the device timezone and current time before testing.

---

## 1. Alarm Creation

| # | Scenario | Expected |
|---|----------|----------|
| 1.1 | Add a one-shot alarm at a future time | Saves; list shows "Once"; appears in `dumpsys alarm` as exact `setAlarmClock` |
| 1.2 | Add a daily alarm | Shows "Daily"; next trigger = tomorrow at the chosen time |
| 1.3 | Add a weekdays alarm (Mon–Fri) | Shows "Weekdays"; next trigger = next weekday |
| 1.4 | Add a weekends alarm (Sat+Sun) | Shows "Weekends" |
| 1.5 | Add a monthly alarm (e.g. day 16) | Shows "Monthly on day 16"; trigger = next month 16th |
| 1.6 | Add a date alarm (future date) | Shows the date; trigger = that date at alarm time |
| 1.7 | Add a custom-days alarm (pick Mon+Wed) | Shows "Mon, Wed" |

## 2. Time Edge Cases

| # | Scenario | Expected |
|---|----------|----------|
| 2.1 | Set a one-shot time in the past (e.g. 12:00 when it's 13:00) | **Rolls to tomorrow 12:00**; toast "Will ring in …" |
| 2.2 | Set a date alarm with a past date | **Auto-disabled** with "This alarm will never ring" toast; alarm appears disabled |
| 2.3 | Set a monthly day 31 | Fires on next month that has a 31st (skips short months) |
| 2.4 | Set a monthly day 29 in a non-leap year | Skips February, fires March 29 |

## 3. Enable / Edit / Delete

| # | Scenario | Expected |
|---|----------|----------|
| 3.1 | Toggle an alarm off | Switch turns off; alarm removed from `dumpsys alarm`; event log shows DISABLED + CANCELLED |
| 3.2 | Toggle it back on | Re-scheduled; event log shows ENABLED + SCHEDULED |
| 3.3 | Edit an alarm's time/repeat and save | Changes persist; alarm re-scheduled to new time |
| 3.4 | Delete an alarm from the list | Removed from list and AlarmManager; event log shows DELETED + CANCELLED |
| 3.5 | Delete via the edit dialog | Same as 3.4 |

## 4. Alarm Firing — Unlocked

| # | Scenario | Expected |
|---|----------|----------|
| 4.1 | Phone unlocked + another app in foreground, alarm fires | **Heads-up notification** (not full-screen) with Snooze / Custom / Dismiss; ringtone plays; FSI demoted |
| 4.2 | Tap notification Dismiss | Ringtone stops; notification cleared; returns to the app in use |
| 4.3 | Tap notification Snooze | Snoozes for the configured default (Settings); ringing stops |
| 4.4 | Tap notification Custom | Snooze picker opens; select a preset → snoozes for that duration |

## 5. Alarm Firing — Locked

| # | Scenario | Expected |
|---|----------|----------|
| 5.1 | Phone locked + screen off, alarm fires | **Screen wakes**; full-screen `AlarmActivity` over the lock screen; Snooze / Custom / Dismiss buttons visible and clear of the nav bar |
| 5.2 | Tap full-screen Snooze | Default snooze; ringing stops; returns to the previous screen (not the main app) |
| 5.3 | Tap full-screen Custom | Picker opens over the lock screen; select → snoozes; returns to previous screen |
| 5.4 | Tap full-screen Dismiss | Ringing stops; returns to lock screen / previous app |
| 5.5 | Dismiss while the device has a secure lock (PIN) | Alarm still shows over the lock screen (showWhenLocked) |

## 6. Snooze Behavior

| # | Scenario | Expected |
|---|----------|----------|
| 6.1 | Snooze for 5 min (default) | Snooze alarm armed ~5 min out (`dumpsys alarm`); ringing stops |
| 6.2 | Snooze for 8 h via picker | Snooze armed ~8 h out; ringing stops |
| 6.3 | Snoozed alarm fires again | Rings again; one-shot becomes disabled after, repeating re-arms |
| 6.4 | **Delete the snoozed alarm before it re-fires** | Snooze alarm cancelled; **does not ring** |
| 6.5 | **Edit (change) the snoozed alarm before it re-fires** | Snooze cancelled; re-scheduled to the new time; does not ring at the old snooze time |
| 6.6 | Snooze a *disabled* one-shot (via re-fire path) | Snooze still rings (snooze bypasses disabled check) |

## 7. Timezone / Clock Changes

| # | Scenario | Expected |
|---|----------|----------|
| 7.1 | Change timezone (e.g. `cmd alarm set-timezone`) with app open | All alarms re-anchor to the new local wall time; event log shows TIMEZONE_CHANGED |
| 7.2 | Change timezone with app closed, wait ≤4h | Periodic check re-anchors alarms |
| 7.3 | Manually set clock forward/back with app open | Alarms re-anchor to wall time; event log shows TIMEZONE_CHANGED (via TIME_SET) |

## 8. Reboot

| # | Scenario | Expected |
|---|----------|----------|
| 8.1 | Reboot with enabled alarms | BOOT_COMPLETED logged; all enabled alarms rescheduled |
| 8.2 | Reboot with only disabled alarms | Nothing scheduled; no ringing |
| 8.3 | Reboot; check periodic reschedule is re-armed | `ACTION_RESCHEDULE_ALL` present every 4h cadence |

## 9. Settings

| # | Scenario | Expected |
|---|----------|----------|
| 9.1 | Remove inactive alarms with an expired/disabled alarm present | Only the inactive one is removed; toast shows count |
| 9.2 | Remove inactive with none present | "No inactive alarms" toast |
| 9.3 | Export alarms | System CreateDocument picker opens |
| 9.4 | Import a valid backup | Replaces all alarms; toast shows imported count |
| 9.5 | Import a malformed file | "Import failed" toast; nothing changes |
| 9.6 | Set snooze duration in Settings | Used as the "Snooze" (default) duration |
| 9.7 | Open Event log | Shows recent events with timestamps, type, label (id) |
| 9.8 | Export Event log | Share sheet opens with full text dump |
| 9.9 | Clear Event log | Confirmation dialog; after confirm, log is empty |

## 10. Event Log Correctness

| # | Scenario | Expected |
|---|----------|----------|
| 10.1 | Check a full fire→snooze→dismiss cycle | Log shows FIRED, SNOOZED (with minutes), DISMISSED (with alarm id + label) |
| 10.2 | Check boot reschedule | BOOT_COMPLETED, then CANCELLED("Boot") + SCHEDULED per alarm |
| 10.3 | Check timezone change | TIMEZONE_CHANGED, then CANCELLED("Time change") + SCHEDULED per alarm |
| 10.4 | Check periodic check | PERIODIC_CHECK appears ~every 4h (not more often) with "No change" or "Rescheduling" |
| 10.5 | Dismiss from notification | DISMISSED logged with the alarm id + label |

## 11. Permissions / OS Integration

| # | Scenario | Expected |
|---|----------|----------|
| 11.1 | First launch on Android 13+ | Notification permission requested; gate blocks UI until granted |
| 11.2 | Deny notifications | Snackbar; permission gate stays; alarms degrade gracefully |
| 11.3 | Deny exact-alarm access (Android 12–13) | Warning snackbar; alarms fall back to inexact `setWindow` (fires within ~1 min) |
| 11.4 | Do Not Disturb on, alarm fires (exact granted) | Alarm still rings (setAlarmClock is DND-exempt) |
| 11.5 | Notification action buttons fit (not truncated) | 3 actions visible: Snooze / Custom / Dismiss |

## 12. Regression Checks

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
