# Petal Wake

Petal Wake is an Android alarm app that schedules work wake-ups from shared iCalendar feeds and offers gentle recorded music and nature sounds. This repository contains the source for version **0.1.21** (Android version code 22).

Download the signed APK from the [v0.1.21 release](https://github.com/ClownChowder/petal-wake/releases/tag/v0.1.21). That release also includes the optional orchestra pack, a source archive, and SHA-256 checksums. The release tag was created before the source was added to this repository, so use the attached `petal-wake-source.zip` for an exact version-specific archive. The `main` branch contains the same v0.1.21 source.

See [README.txt](README.txt) for setup, calendar sharing, features, practical limits, and testing notes. See [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and [CREDITS.txt](CREDITS.txt) for audio and upstream attribution.

## Obtainium

Source: `https://github.com/ClownChowder/petal-wake`

APK filename filter: `^petal-wake\.apk$`

Android package: `app.daybreak`

Signing certificate SHA-256: `386f097ea7de3749f0b0cc87e433d0f8a8a70cebb1848d7e7cb4bd5f60cff2b4`

Obtainium detects published releases. Updating the app requires a new APK signed with the same key and a higher version code. Signing keys, calendar links, pairing tokens, and personal app data are not published.

## Build

Install JDK 17 and the Android SDK for API 35, set up the SDK path through `ANDROID_HOME` or a local `local.properties`, and run `./gradlew assembleDebug`. A production update needs the original private signing key, which is not part of this repository. The included [VALIDATION.txt](VALIDATION.txt) records checks for this release.

## Credits

See [CREDITS.txt](CREDITS.txt) for original authors, upstream projects, recording and model creators, and applicable licenses. Original copyright notices remain in the source and app assets. No upstream endorsement is implied.
