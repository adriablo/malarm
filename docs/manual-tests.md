# Manual Tests — Malarm (index)

> **Agent instruction:** These documents are for **manual/emulator QA only**. Do not read, load, or execute them during normal development tasks. Only consult them when the user explicitly asks to run manual tests, verify behavior on the emulator, or does a full QA pass.

- **Per-commit fast pass (~7–10 min, non-disruptive):** `docs/manual-tests-smoke.md` — 12 cases, no reboot/timezone/clock/DND. Run this for each commit.
- **Per-release slow pass (~55–65 min):** run smoke first, then `docs/manual-tests-release.md` — the remaining ~45 cases (repeat variants, edge cases, timezone/clock, reboot, permissions/DND, import/export, rotation, event-log audit).

Run on the emulator (API 35+ recommended). The debug-signed APK is `app/build/outputs/apk/debug/malarm.apk`.

## Speed tips (both passes)

- **Quick Boot snapshot:** cold-boot once, install, grant notifications + exact-alarm + FSI, set a PIN (for locked-screen 5.5), disable animations (`settings put global window_animation_scale 0`, same for `transition_` and `animator_`), then snapshot with no alarms saved. Restore (~10 s) instead of reinstalling. Full `adb uninstall` fresh is release-only.
- **Fast debug fires (debug builds):** `am start --ez debug_schedule true --ei debug_schedule_secs 20` fires in ~20 s instead of ~1 min. Sub-minute delays bypass minute truncation on purpose (`MainActivity.handleDebugIntent`).
- **Never wait for the 4h check:** `am broadcast -n com.malarm/.AlarmReceiver -a com.malarm.ACTION_RESCHEDULE_ALL` fires the periodic re-anchor on demand (7.2 / 10.4). Must be explicit (`-n`): `AlarmReceiver` has no intent-filter so the implicit `-a/-p` form never resolves. Do NOT force-stop first — background the app with Home instead; a force-stopped package drops the broadcast.
- **Verify via `dumpsys`/sqlite, not by waiting:** `dumpsys alarm | grep -A1 com.malarm` for arming, the sqlite event-log query in the release quick reference for log audits.
- **One reboot, one permission cycle:** release §5 → §6 → §8 in order; 11.1–11.3 share a single install with deny/re-grant between rows.
