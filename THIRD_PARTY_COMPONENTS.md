# Third-party component inventory

Resolved release runtime graph reviewed 2026-10-28 for local candidate 0.1.39. This list records the direct and transitive Java/Kotlin libraries, plus bundled fonts and audio. Refresh it when versions or packaging change. The full notices are in `THIRD_PARTY_NOTICES.txt`; the app packages the same text as `assets/notices.txt`.

| Component | Version | License |
| --- | --- | --- |
| net.sf.biweekly:biweekly | 0.6.8 | BSD 2-Clause |
| com.github.mangstadt:vinnie | 2.0.2 | MIT |
| androidx.webkit:webkit | 1.12.1 | Apache 2.0 |
| androidx.annotation:annotation | 1.8.1 | Apache 2.0 |
| androidx.annotation:annotation-jvm | 1.8.1 | Apache 2.0 |
| androidx.annotation:annotation-experimental | 1.4.1 | Apache 2.0 |
| androidx.core:core | 1.1.0 | Apache 2.0 |
| androidx.lifecycle:lifecycle-runtime | 2.0.0 | Apache 2.0 |
| androidx.lifecycle:lifecycle-common | 2.0.0 | Apache 2.0 |
| androidx.arch.core:core-common | 2.0.0 | Apache 2.0 |
| androidx.versionedparcelable:versionedparcelable | 1.1.0 | Apache 2.0 |
| androidx.collection:collection | 1.0.0 | Apache 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib | 1.7.10 | Apache 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-common | 1.7.10 | Apache 2.0 |
| org.jetbrains:annotations | 13.0 | Apache 2.0 |

Biweekly also embeds notices for `commons-codec` and Google recurrence code. Preserve those embedded notices in `THIRD_PARTY_NOTICES.txt` when updating biweekly.

| Bundled asset | License or status | Notice location |
| --- | --- | --- |
| DM Sans font | SIL Open Font License 1.1 | `app/src/main/assets/licenses/DM-Sans-OFL.txt` |
| Fraunces font | SIL Open Font License 1.1 | `app/src/main/assets/licenses/Fraunces-OFL.txt` |
| Musopen performances | Public Domain Mark 1.0 as recorded upstream; check jurisdiction-specific rights before each market launch | `app/src/main/assets/credits.txt` |
| Six nature recordings and twelve original standard tones | CC0 1.0 | `app/src/main/assets/credits.txt` |

The optional 28-track orchestra archive is distributed separately. Its individual track and performer records are in the app's `credits.txt` and source catalog. A Public Domain Mark describes the identified work's status; it is not a rights warranty. Four Wags proprietary terms do not apply to the third-party material above.
