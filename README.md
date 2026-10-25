# Petal Wake

Petal Wake is an Android alarm app that schedules work wake-ups from shared iCalendar feeds and offers gentle recorded music and nature sounds. This source tree is version **0.1.37** (Android version code 38).

Version 0.1.37 is a preview with safer linked-audio fallback and background calendar refresh. Check the [GitHub releases page](https://github.com/FourWags/petal-wake/releases) for the published APK and verification notes. The compatible optional orchestra pack remains available from the [v0.1.23 release](https://github.com/FourWags/petal-wake/releases/tag/v0.1.23).

See [README.txt](README.txt) for setup, calendar sharing, features, practical limits, and testing notes. See [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and [CREDITS.txt](CREDITS.txt) for audio and upstream attribution.

## Obtainium

Source: `https://github.com/FourWags/petal-wake`

APK filename filter: `^petal-wake\.apk$`

Android package: `app.daybreak`

Signing certificate SHA-256: `386f097ea7de3749f0b0cc87e433d0f8a8a70cebb1848d7e7cb4bd5f60cff2b4`

Obtainium detects published releases. Updating the app requires a new APK signed with the same key and a higher version code. Signing keys, calendar links, pairing tokens, and personal app data are not published.

## Build

Install JDK 17 and the Android SDK for API 35, set up the SDK path through `ANDROID_HOME` or a local `local.properties`, and run `./gradlew assembleRelease`. The release variant is non-debuggable and uses the existing update certificate to preserve in-place Obtainium updates, including Android 8. The signing key is kept outside this repository. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md). [VALIDATION.txt](VALIDATION.txt) records completed checks and limits. Use `tools/check-update-continuity.sh` to compare an APK against the previous version before distributing it.

## Credits

See [CREDITS.txt](CREDITS.txt) for original authors, upstream projects, recording and model creators, and applicable licenses. Original copyright notices remain in the source and app assets. No upstream endorsement is implied.
