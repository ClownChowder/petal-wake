# Signing and update continuity

Rouse v0.1.37 retains the signing certificate used by previous Obtainium APKs. Its release build is non-debuggable but uses that existing certificate to keep in-place updates working. Android verifies the application ID, version code, and signing certificate before installing an update. Run `tools/check-update-continuity.sh previous.apk candidate.apk` before distributing an APK; it checks the package, increasing version code, and unchanged certificate.

The app supports Android 8 (API 26). Android's signing lineage support starts with Android 9 (API 28), so a new certificate cannot provide an in-place update for every supported device. The v1 update-continuity decision is to retain the existing certificate. The key was originally generated for debug builds; it is now treated as a long-lived update credential, stored access-restricted outside the source tree with a separately protected local recovery copy. An offline recovery copy is still needed before calling the signing setup complete. Do not rotate the key while the app promises seamless Android 8 updates.

If a dedicated new key is chosen for a later release:

1. Create a dedicated release key in protected storage, with an offline recovery copy. Never commit it or its passwords.
2. Confirm the current certificate fingerprint against an installed APK and a saved v0.1.23 APK.
3. Decide how Android 8 users will move, since key rotation cannot update their existing installation in place. A fresh install removes app-private data, and the settings file intentionally omits private calendar links and alarms. The in-app new-phone checklist also applies to this path.
4. Build a signing lineage from the current key to the new key only after choosing supported Android versions, then test an in-place update on each supported version, including the user's actual phone. Verify alarms, calendar links, launcher icon aliases, and encrypted cache migration after the update.
5. Publish the new certificate fingerprint and exact APK SHA-256 with the release. Keep the existing key until the migration has been confirmed.

Reference: https://source.android.com/docs/security/features/apksigning/v3

Do not publish a differently signed APK under the current Obtainium package without completing the migration tests.
