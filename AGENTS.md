# AGENTS.md

## Ground rules (never violate)

- **Never commit or push to any repository** (GitHub, GitLab, or otherwise)
  **without explicit user approval.** Always show the proposed changes and wait
  for a go-ahead before committing, merging, pushing, or tagging.

## Repo and distribution contract

This app is published on F-Droid. The GitLab submission side
(`fdroid/fdroiddata` MR, fork `adriablo/fdroiddata`) is done and frozen; all
future work happens only in this GitHub repo (`adriablo/malarm`). Changes here
must never break the F-Droid build/update pipeline.

## F-Droid guardrails (must not be broken)

1. `applicationId` stays `com.malarm` — changing it breaks the F-Droid
   metadata, update tracking, and existing installs.
2. `versionCode`/`versionName` in `app/build.gradle.kts` must stay in sync with
   the git tag (`vX.Y.Z` -> versionName `X.Y.Z`, versionCode always increasing).
3. Release stays unsigned (no `signingConfig` in committed build config) —
   F-Droid signs the APK itself.
4. `fastlane/metadata/android/en-US/` must stay present — it is the source of
   the app's name, summary, description, screenshots, and changelogs on F-Droid.
5. If the build recipe changes (AGP/Gradle/compileSdk/minSdk versions), a new
   `fdroid/fdroiddata` MR is required; everything else updates automatically
   from GitHub tags. `targetSdk` changes do **not** require a new MR (F-Droid
   builds from the tag), but should be tested on the emulator first.

## Workflow

- Run `./gradlew test lint` before proposing or committing any code change.
- Work on a `release/X.Y.Z` branch off `main`; merge to `main` (fast-forward)
  before tagging.
- Publish a new release: bump versionName/versionCode, tag `vX.Y.Z`, push tag.
  F-Droid picks it up automatically (~24-48h).
- Each release needs a matching
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` alongside the
  version bump.
- Attach the **debug-signed APK** (`app/build/outputs/apk/debug/malarm.apk`) to
  the GitHub release as the `malarm.apk` asset. The release APK is unsigned and
  cannot be installed directly, so the debug APK is the installable build users
  download from GitHub. Do not attach the unsigned release APK (`app/build/
  outputs/apk/release/malarm.apk`) or a separate `malarm-debug.apk` asset.
- Do not touch the GitLab fork or open new MRs unless the build recipe changes.

## Manual QA (on-demand only)

`docs/manual-tests.md` contains the emulator test checklist. It is **not** part
of normal development: do not read or execute it during routine tasks. Consult
it only when the user explicitly asks for manual/emulator testing, verification
of on-device behavior, or a full QA pass, and follow it against a real emulator.

## Docs (consult when relevant)

- `docs/design-notes.md` — platform constraints and deliberate design decisions
  (alarm presentation, lock-state detection, snooze, re-anchoring). Read before
  changing scheduling or alarm-UI code.
- `docs/testing-notes.md` — Robolectric gotchas and emulator/adb techniques.
