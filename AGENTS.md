# AGENTS.md

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
   from GitHub tags.

## Workflow

- Publish a new release: bump versionName/versionCode, tag `vX.Y.Z`, push tag.
  F-Droid picks it up automatically (~24-48h).
- Do not touch the GitLab fork or open new MRs unless the build recipe changes.
