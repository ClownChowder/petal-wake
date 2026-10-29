# Rouse 0.1.42 — repair Preview

This Preview makes private phone-move restores recoverable across failed writes and process interruption. A rollback journal preserves the original destination, incomplete restores recover on startup, and retries remain available when recovery succeeds.

Validation: 44 fresh host tests passed; release lint reported no errors and 70 warnings. Exact signed release updated code42 in place on an isolated API 37 emulator. Four failed commit points and a persistent rollback failure recovered, with all five retries passing. Process death after a real write recovered on restart and then retried successfully. Encrypted synthetic round-trip, wrong-password/tamper rejection, existing-data protection and ten encrypted-storage checks passed.

Personal Preview; not production, Google Play, or sale-readiness qualification. No physical phone installation or foreground interaction occurred. Real Obtainium phone upgrades, long-duration behavior, live assistive technology and power/thermal behavior remain unqualified for these artifacts. Sumi and Rouse retain their existing debug-origin preview certificate for update continuity; the APKs are non-debuggable, but this does not qualify production signing. Existing audio and component notices are retained; this release does not establish worldwide recording-rights clearance.

Use the existing Obtainium repository: https://github.com/FourWags/rouse. Stable APK asset: `petal-wake.apk`. Required corresponding source and notices are included in `petal-wake-source.zip`. See CHECKS.txt and SHA256SUMS for exact identities.
