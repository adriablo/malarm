# Manual Smoke Test — Malarm (per-commit, ~7–10 min)

> **Agent instruction:** This document is for **manual/emulator QA only**. Do not read, load, or execute it during normal development tasks. Only consult it when the user explicitly asks to run manual tests or verify behavior on the emulator. When triggered, follow the steps below against a real emulator and report results per scenario.

Fast per-commit pass. Non-disruptive: no reboot, no timezone/clock changes, no DND, no permission-denial flows. For the full slow pass, run this file first, then `docs/manual-tests-release.md`.

Run on the emulator (API 35+ recommended) after a **fresh install** (uninstall first). The debug-signed APK is `app/build/outputs/apk/debug/malarm.apk`. Budget: ~10 min on an already-booted emulator (~7 min with 20 s debug fires, see tip).

## Setup

- Fresh install: `adb uninstall com.malarm` then install the debug APK — or restore a Quick Boot snapshot taken right after install + grants (before any alarm exists) to skip this (~10 s).
- Grant notifications (`POST_NOTIFICATIONS`) and exact-alarm access on first launch (exact alarms are special app access — use the `appops` commands below, not `pm grant`).
- Settings lives behind the gear icon in the top app bar.
- Tip: use the 1-min `debug_schedule` alarm for all firing steps (S4, S6, S8, S10) instead of waiting on real alarm times. It only works on a fresh `onCreate` — force-stop first, every time. For ~20 s fires: `am start --ez debug_schedule true --ei debug_schedule_secs 20` (debug builds only; sub-minute delays bypass minute truncation on purpose — see `MainActivity.handleDebugIntent`).

---

## Steps

| # | Scenario (origin) | Steps | Expected |
|---|-------------------|-------|----------|
| S1 | 1.1 one-shot | Add a one-shot alarm at a future time | Saves; list shows "Once"; appears in `dumpsys alarm` as exact `setAlarmClock` |
| S2 | 1.2 daily | Add a daily alarm | Shows "Daily"; next trigger = tomorrow at the chosen time |
| S3 | 3.1/3.2 toggle | Toggle an alarm off, then back on | Off: removed from `dumpsys alarm`, log DISABLED + CANCELLED. On: re-scheduled, log ENABLED + SCHEDULED |
| S4 | 4.1 unlocked firing | Force-stop, fire a debug alarm (~20 s with `debug_schedule_secs 20`); with phone unlocked + another app in foreground, let it fire | **Heads-up notification** (not full-screen) with Snooze / Custom / Dismiss; ringtone plays |
| S5 | 4.2 dismiss | Tap notification Dismiss | Ringtone stops; notification cleared; returns to the app in use |
| S6 | 4.3 snooze | Force-stop, fire a debug alarm again; tap notification Snooze | Snoozes for the configured default; ringing stops; snooze visible in `dumpsys alarm`; leave armed for S7 |
| S7 | 6.4 delete snoozed | Delete the S6-snoozed alarm **before it re-fires** | Snooze intent gone from `dumpsys alarm` (the real assertion — the 1–2 min spot-check is shorter than the 5-min default, so it can't confirm alone; full wait to re-fire covered in release 6.3) |
| S8 | 5.1 locked firing | Force-stop, fire a debug alarm (~20 s); lock phone + screen off, let it fire | **Screen wakes**; full-screen `AlarmActivity` over the lock screen; buttons visible and clear of the nav bar |
| S9 | 5.4 full-screen dismiss | Tap full-screen Dismiss | Ringing stops; returns to lock screen / previous app |
| S10 | 5.6 back press | Force-stop, fire a debug alarm (~20 s); press Back on the full-screen alarm | **Snoozes** for the default duration; ringtone must not be orphaned (no `RingtoneService` in `dumpsys activity services`); pending snooze cleaned up by S12 |
| S11 | 11.5 buttons fit | During S4/S8 | 3 actions visible: Snooze / Custom / Dismiss, not truncated |
| S12 | 3.4 delete | Delete all test alarms from the list | Removed from list and AlarmManager; log shows DELETED + CANCELLED; `dumpsys alarm` shows no `com.malarm` entries (no leaked snooze) |

Pass criteria: all 12 pass. If S4/S8/S10/S7 fail, stop — those are the highest-risk paths. Anything else (repeat variants, monthly/date edges, timezone, reboot, DND, permissions, import/export, rotation, event-log audit) is covered in `docs/manual-tests-release.md` per release.

---

## Quick commands (smoke only)

```bash
# Fresh install + grant
adb uninstall com.malarm
adb shell appops set com.malarm SCHEDULE_EXACT_ALARM allow
adb shell appops set com.malarm USE_FULL_SCREEN_INTENT allow

# Fire a debug alarm (fresh onCreate only — force-stop first).
# Repeat before EACH firing step (S4, S6, S8, S10); otherwise it silently does nothing.
# Default ~1 min out; --ei debug_schedule_secs 20 fires in ~20 s (debug builds only).
adb shell am force-stop com.malarm
adb shell am start --ez debug_schedule true --ei debug_schedule_secs 20 -n com.malarm/.MainActivity

# Check scheduled alarms
adb shell dumpsys alarm | grep -A1 com.malarm

# Check the ringtone service is running/stopped
adb shell dumpsys activity services | grep RingtoneService
```
