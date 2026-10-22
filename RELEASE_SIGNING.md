# Signing and update continuity

Petal Wake v0.1.23 keeps the signing certificate used by previous Obtainium APKs. Android verifies the application ID, version code, and signing certificate before installing an update. Changing to a newly generated key without a compatible, tested rotation would strand existing installations and their private calendar data.

For a future public release:

1. Create a dedicated release key in protected storage, with an offline recovery copy. Never commit it or its passwords.
2. Confirm the current certificate fingerprint against an installed APK and a saved v0.1.23 APK.
3. Build a signing lineage from the current key to the new key, then test an in-place update on supported Android versions, including the user's actual phone. Verify alarms, calendar links, launcher icon aliases, and encrypted cache migration after the update.
4. If any supported device cannot follow the lineage, provide a clearly labeled settings export and migration guide before changing keys. Settings export intentionally omits private calendar links and alarms, so those must be reconnected and rechecked after a fresh install.
5. Publish the new certificate fingerprint and exact APK SHA-256 with the release. Keep the existing key until the migration has been confirmed.

Do not publish a differently signed APK under the current Obtainium package without completing the migration tests.
