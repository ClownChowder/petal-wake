# Petal Wake

Petal Wake is an Android alarm app that schedules work wake-ups from shared iCalendar feeds and offers gentle recorded music and nature sounds. This source tree is version **0.1.30** (Android version code 31).

The latest APK is the [v0.1.30 release](https://github.com/ClownChowder/petal-wake/releases/tag/v0.1.30), with checksums and source matching that APK. The compatible optional orchestra pack remains available from the [v0.1.23 release](https://github.com/ClownChowder/petal-wake/releases/tag/v0.1.23).

See [README.txt](README.txt) for setup, calendar sharing, features, practical limits, and testing notes. See [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and [CREDITS.txt](CREDITS.txt) for audio and upstream attribution.

## Obtainium

Source: `https://github.com/ClownChowder/petal-wake`

APK filename filter: `^petal-wake\.apk$`

Android package: `app.daybreak`

Signing certificate SHA-256: `386f097ea7de3749f0b0cc87e433d0f8a8a70cebb1848d7e7cb4bd5f60cff2b4`

Obtainium detects published releases. Updating the app requires a new APK signed with the same key and a higher version code. Signing keys, calendar links, pairing tokens, and personal app data are not published.

## Build

Install JDK 17 and the Android SDK for API 35, set up the SDK path through `ANDROID_HOME` or a local `local.properties`, and run `./gradlew assembleDebug`. These personal preview APKs use the same local Android debug signing certificate so they update existing installs. The signing key is not in this repository. A future public release should use a dedicated protected key after testing migration from the existing certificate. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md). The included [VALIDATION.txt](VALIDATION.txt) records checks for this release. Use `tools/check-update-continuity.sh` to compare an APK against the previous version before distributing it.

## Credits

See [CREDITS.txt](CREDITS.txt) for original authors, upstream projects, recording and model creators, and applicable licenses. Original copyright notices remain in the source and app assets. No upstream endorsement is implied.
