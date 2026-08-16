# Malarm

A minimal native-Kotlin Android alarm clock app (`com.malarm`).

- Add/edit/delete alarms with repeat presets (daily, weekdays, weekends, monthly, custom days)
- One-shot date alarms and snooze
- Exact-alarm scheduling with ringing and vibration
- Survives process death and reboots (BOOT_COMPLETED)

## Installation

Download `malarm.apk` from the latest [release](https://github.com/adriablo/malarm/releases).

**On the phone (no computer):** tap the downloaded `malarm.apk`, allow "Install unknown apps" for your browser/Files app, then tap **Install**. Play Protect may warn it looks like an internal test build — that's expected (it's a debug build); tap "More details → Install anyway".

**Via adb:**

```
adb install malarm.apk
```

## Build Requirements

- JDK 17+
- Android SDK (compileSdk 36, targetSdk 36)
- Gradle 8.13 (wrapper included)
- Runs on Android 8.0+ (minSdk 26)

Build: `./gradlew assembleDebug`

## Screenshots

<img src="screenshots/main.png" width="280" alt="Alarm list">
<img src="screenshots/add-alarm-dialog.png" width="280" alt="Add alarm dialog">
<img src="screenshots/time-picker.png" width="280" alt="Time picker">
<img src="screenshots/date-picker.png" width="280" alt="Date picker">
<img src="screenshots/repeat-options.png" width="280" alt="Repeat options">
<img src="screenshots/weekly-days.png" width="280" alt="Weekly days picker">
