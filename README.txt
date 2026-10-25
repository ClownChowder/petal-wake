PETAL WAKE — ANDROID ALARM APP 0.1.37

This is an early installable Android build. It creates wake-up alarms from a live iCalendar sharing link or imported iCalendar (.ics) file. It also supports one-time personal alarms.

VERSION 0.1.37 — STABILITY AND RESPONSIVE CALENDAR REFRESH (PREVIEW)
- Slow linked personal audio no longer blocks the main screen, sound preview, or alarm controls. If a linked file cannot start within five seconds, the bundled backup tone sounds; a late file response cannot restart a dismissed alarm.
- Automatic and manual calendar downloads wait on a separate network worker, so slow calendar servers do not block saving settings. Downloaded results are applied on the serialized alarm worker; a removed calendar cannot be restored by an older response. A failed download keeps saved offline shifts.
- Optional music-pack downloads have a separate worker. The bundled music collection and privacy model are unchanged; streaming services were not added.

VERSION 0.1.36 — ACCESSIBLE ALARM DISMISSAL (PREVIEW)
- TalkBack and Switch Access users can activate I’m up to open a clear confirmation. Keep ringing returns to the active alarm; Dismiss alarm stops it. The long-click accessibility action still works. Ordinary touch still requires a two-second hold, so a short tap cannot silently stop an alarm.
- While an accessibility service is enabled, the alarm notification and full-screen control both say to choose I’m up to confirm dismissal.
- No accessibility service is required for normal use. The confirmation appears only while an accessibility service is enabled. It does not request a new app permission or change phone accessibility settings.

VERSION 0.1.35 — TWO PHONE ALARM VOLUME CONTROLS (LOCAL CANDIDATE)
- Wake-up Settings has separate Starting and Ending phone alarm volume sliders, plus percentage boxes for typing either value. The meter shows the effective Android alarm-volume levels. Gradual rise changes the actual phone alarm stream over the selected duration; with rise off, alarms start at the ending level.
- The ending slider or percentage box changes the phone-wide alarm volume immediately. The starting slider or percentage box and rise duration are saved with Save now. Android rounds typed percentages to its available whole volume steps; the effective rounded values appear in the meter.
- This candidate is undergoing v1 reliability, accessibility, device, and signing checks. It is not the published Obtainium update until release evidence is recorded.

VERSION 0.1.32 — CLEAR MAXIMUM ALARM VOLUME
- The Wake-up screen labels its Android alarm-volume slider as Maximum phone alarm volume and displays the actual phone step plus an approximate percentage. Move it left to limit how loud Petal Wake gets. With gradual rise off, the alarm starts at that level; with gradual rise on, it stops there. The setting changes the real Android alarm stream immediately and also affects other alarm apps. Android’s volume steps mean percentages are approximate.
- The separate Rise starts at control remains available; if the chosen start would exceed the selected maximum, the alarm starts at the maximum. This version is a local candidate until it is verified and published.

VERSION 0.1.31 — EDITOR SAVES, READINESS AND PHONE ALARM VOLUME
- Calendar-rule and custom-theme editors show unsaved changes and keep them onscreen when you tap Back, Close, Cancel, or another page. Calendar rules close only after Android confirms the save; a failed save keeps the edits ready to correct and retry.
- Alarm-readiness checks now include upcoming personal alarms when work alarms are off. A phone setting that prevents a personal alarm can trigger the same generic warning without revealing the alarm title.
- Wake-up Settings now have one Phone alarm volume slider that changes Android’s actual alarm stream and shows its measured step. Gradual alarm rise has a separate duration and a selectable starting percentage of the phone’s full alarm-volume range (10–100%). If the start would exceed the selected end level, it starts at the end level. The real 15-second test uses the same alarm audio path as scheduled alarms. A completed or interrupted rise returns the phone alarm stream to its selected level; a volume-key change during ringing takes precedence.
- The old app playback gain and Quiet wake-up preset are retired from the visible Settings screen. Existing app gain settings migrate to full so the phone alarm slider is the audible level control. Android’s alarm stream is global: other alarm apps share the selected level and any rise while Petal Wake rings.
- This preview is distributed as GitHub Latest for Obtainium. The app ID and signer remain unchanged.

VERSION 0.1.30 — CLEAR SELECTIONS AND FULL-VOLUME START
- Alarms begin immediately at 100% app playback by default. This update applies that starting setting once to existing installs; you can lower it afterward or opt into Quiet wake-up again.
- Settings shows the exact Android alarm-volume step alongside the app playback percentage, provides an Adjust phone alarm volume shortcut and a real 15-second test, and explains each step for choosing a lower level. Recordings can vary in loudness even at the same setting.
- Selected sections, sound categories, recordings, themes, and icon designs have stronger outlines and checkmarks. Unsaved alarm preferences stay on the Settings page when you tap another page, section, or Android Back. The save warning clears only after Android confirms the save.
- The app ID and signing certificate are unchanged for in-place Obtainium updates. Review the next wake time and Android alarm volume after updating.

VERSION 0.1.29 — QUIET FOCUS UI REFINEMENT
- Clearer next-alarm, work-alarm, personal-alarm, and sound-selection surfaces across all 16 built-in themes and saved custom themes.
- Stronger visual separation for both light and dark palettes while keeping the app icon and native alarm palette aligned with the selected theme.
- Sound categories remain visible on narrow phones; navigation and control spacing are more consistent.
- No new permissions, account features, or telemetry. Review the next wake time and Android alarm permissions after updating.

VERSION 0.1.28 — SNOOZE AND SETTINGS IMPORT RELIABILITY
- Snooze responds even while a calendar refresh is busy, and repeated taps schedule just one snooze.
- Settings import rejects out-of-range numbers instead of accepting a wrapped value.
- The app ID and signing certificate remain unchanged for in-place Obtainium updates. Review the next wake time and Android alarm permissions after updating.

VERSION 0.1.27 — WAKE RELIABILITY & PERSONAL AUDIO
- Alarm delivery now confirms playback and schedules one backup delivery if the foreground audio service fails to start. A missing selected recording falls back to a bundled sound, then the system alarm tone.
- The alarm keeps ringing if its screen is left or the task is moved away, and the notification can reopen it. Ordinary touch requires a two-second hold to dismiss. With TalkBack or Switch Access enabled, activating I’m up opens a separate Dismiss alarm confirmation; Keep ringing cancels it. An accessible long-click also dismisses. Choose a maximum ring duration of 15, 30, or 60 minutes.
- Sounds can be filtered by genre. Import personal audio into app-private offline storage, or link to a file through Android's document picker. Imported audio is recommended for dependable alarms; a missing linked file safely falls back. Personal-file names and links are not included in settings exports.
- Optional orchestra-pack downloading asks before making a GitHub connection and explains that GitHub can see the downloader's network address.
- Tomorrow's alarm preview recreates its Android notification channel before posting, so a fresh app-data state does not mark an unseen notice as sent.
- The release retains the existing app ID and signing certificate for in-place Obtainium updates. Review your next wake time and phone permissions after updating.

VERSION 0.1.26 — PETAL KEY THEME CONTRAST
- The 16 curated Petal Key family palettes were already included. Appearance now names the actual selection count instead of the retired 40-theme count.
- The next-alarm card uses each palette's main color and a high-contrast text color. Navigation, section accents, panels, and sound selection have stronger visual hierarchy while preserving the selected theme and launcher icon.
- Theme choices now show their background and two accent colors so they are easier to compare. Custom themes use the same updated layout.

VERSION 0.1.25 — ALL-DAY CLARITY & STABILITY
- Home and Calendar now flag every all-day work block with no alarm. The Set start time action opens the correct calendar, leaves the time blank, and requires the actual shift start time before saving. The import control remains available when the setup checklist is complete.
- Audio-preview callbacks ignore released players; media, audio focus, wake locks, WebView, and receivers are cleaned up through their lifecycle. Background refresh jobs finish reliably and repeated foreground refresh requests are combined.
- Petal Wake remains the app name and the six-petal clock remains the default icon. Appearance copy now refers to Petal Wake instead of Petal Key.

VERSION 0.1.24 — AVAILABILITY & PHONE MOVE
- Availability-only iCalendar feeds with explicit busy intervals can now create work alarms. Free and tentative intervals are ignored, and overlapping busy intervals are combined. A work-only calendar is essential: the feed cannot distinguish work from other busy appointments.
- All-day busy intervals still need a chosen start time. A Limited view link has no event titles, so leave title filtering blank. Review the next wake-up time after connecting.
- Settings includes a new-phone checklist. The settings file still excludes calendar links and personal alarms; create a fresh sharing link on the new phone, check permissions and alarms, then revoke the old link.
- The signing check script compares package, version code, and certificate against a previous APK. This build retains the existing signer for Obtainium updates. See RELEASE_SIGNING.md before any key change.

VERSION 0.1.23 — CALENDAR PRIVACY & SETUP
- Calendar sharing links and downloaded .ics caches are encrypted with a device-bound Android Keystore key. Existing links and caches migrate in place when read. Already scheduled alarms stay scheduled if decryption fails; reconnect the calendar if Android has removed its key.
- Calendar setup now recommends a dedicated work-only calendar and explains Proton sharing. Petal Wake 0.1.23 needed Full view to receive shift events; Limited view availability blocks were identified but not scheduled.
- Replacing a link asks for confirmation and downloads the new calendar before saving it. After checking new shifts, revoke the old link in the calendar service. A saved link can be copied only after Android authentication, and the clipboard is marked sensitive.
- Lock-screen privacy hides alarm details by default; it can be changed under Settings > Calendars & privacy. Settings are divided into Wake-up, Calendars & privacy, Appearance, and Diagnostics. A first-run checklist helps verify the next alarm.
- The app's local WebView now uses a scoped HTTPS asset origin and main-frame, origin-checked messages rather than an all-frame JavaScript interface. The settings export remains private and now includes the lock-screen preference.
- The existing APK signing certificate remains unchanged for Obtainium update compatibility. See RELEASE_SIGNING.md for the dedicated-key migration plan.

VERSION 0.1.22 — ALARM SAFETY & SETTINGS
- After a successful calendar download, changed, added, or removed future work wake-ups appear in local activity history. A private notification summarizes the changes while work alarms are enabled. A provider can still delay publishing a change; Petal Wake cannot detect it before the feed changes.
- Optional unattended backup alert: after 5, 10, or 15 minutes without a response, the gentle selection changes to a full-volume standard ring. It is off by default. The phone's alarm volume still applies.
- Recent activity lists scheduled, ringing, snoozed, dismissed, timed-out, and changed alarms. It stores no calendar links or event titles, keeps at most 100 entries for 30 days, and can be cleared in the app.
- Settings can be saved to and restored from a JSON file with Android's document picker. Calendar links, calendar contents, personal alarms, and activity history are excluded. Reconnect calendars on a new phone and check Android permissions.
- Settings also offers a complete wake-up volume preview using media volume. It plays the selected sound through the configured volume rise without creating an alarm.
- The optional orchestra pack remains downloadable from the compatible v0.1.21 release and plays offline after installation.

INSTALL & FIRST TEST
1. Install petal-wake.apk from the GitHub release or update it through Obtainium. Android may ask you to allow installation from the browser or file manager you used. Requires Android 8.0 or newer with an updated Android System WebView.
2. Open Petal Wake > Settings. Allow exact alarms, alarm notifications, and showing alarms on the lock screen. Increase your phone's alarm volume if it is muted. Check that Do Not Disturb allows alarms and that Android permits background activity for Petal Wake.
3. Tap "Try an alarm in 15 seconds", lock the phone, and verify that it rings. Test both I’m up and Snooze before relying on it for a work shift.
4. Add your work calendar, review the displayed shifts and wake-up times, then enable Work alarms.

CONNECT A WORK CALENDAR
In Petal Wake > Calendar, select Proton, Google, Outlook, iCloud, Fastmail, Zoho, Nextcloud, Teamup, When I Work, Deputy, 7shifts, Planday, or Other. The app displays the steps for obtaining a live iCalendar link from the selected service. Paste the link into Calendar sharing link and review the downloaded shifts before enabling work alarms. Select Add as another calendar to connect up to five more feeds, each with its own name, title filter, wake-up lead time, and all-day shift time. The first calendar and its settings migrate in place on upgrade. Some work scheduling feeds are available only on the service's website, and some employers may disable sharing.

For a paid Proton account:
- Open https://calendar.proton.me in a browser.
- Settings > All settings > Calendars > select your WORK calendar.
- Under "Share with anyone", create and copy a sharing link.
- Paste it into Petal Wake > Calendar > Calendar sharing link.
- A calendar containing only work shifts is easiest: leave the title filter blank.
- Choose Limited view for a work-only calendar if you want to hide event details. Each busy interval can become a work alarm, so do not include personal appointments. Leave title filtering blank. Full view also works and is needed for title filtering. Anyone holding the link can see the information exposed by that link; revoke unused links. Petal Wake encrypts the link on your phone and fetches the calendar directly; it does not send it to an app server.

For a free Proton account, or if you prefer files:
- Export the work calendar from Proton Calendar's web Import/export settings.
- In Petal Wake > Calendar, tap Import calendar file and select the .ics file.
- File imports are snapshots, not live connections. Import a fresh file after every schedule change. Importing replaces the previous connection; replacing a connection with a link requires a successful download first.

Official instructions:
https://proton.me/support/share-calendar-via-link
https://proton.me/support/protoncalendar-calendars

SCHEDULE & CONTROLS
- Default: wake 90 minutes before a shift. Change this to 0–1,440 minutes in Settings or per connected calendar. Optional early/later shift rules override the lead time by the shift start time; shifts between the two thresholds and all-day shifts use their calendar’s ordinary lead time.
- Optional Tomorrow’s check notification shows the next day’s first work alarm and the last calendar download. It is checked by Android’s background job near the chosen evening time, so delivery can be delayed; a successful download does not prove that the provider has published a recent shift change.
- The Alarms screen and an optional readiness notice warn when exact-alarm permission, alarm notifications, lock-screen alarm display, or phone alarm volume stop being ready.
- Optional case-insensitive event-title filter, such as "Work" or "Shift".
- Timed recurring events, exception dates, moved/canceled occurrences and timezones are supported. All-day entries are shown with Start time needed. In Settings, enable Set a time for all-day work shifts and choose the shift start time before creating their alarms.
- Skip an individual shift, restore it, or pause all work alarms. Personal alarms and existing snoozes operate independently.
- Snooze: 1–60 minutes. Gradual volume: off or 5/15/30/60/120 seconds. When a gradual rise is chosen, it starts at a loudness-oriented 40% (squared gain curve) and reaches the selected app playback level over the chosen time. The default is 100% app playback immediately, with no rise. Vibration uses maximum-amplitude repeating pulses and can be switched off. This update resets playback to the new default once; later preference changes are respected.
- Up to 128 future work alarms are scheduled across the next 60 days. The list displays the next 128 shifts; the home screen shows the first 12. Background refresh extends the window. Up to 32 personal alarms can be added.
- Alarms ring for up to 30 minutes if unattended. Simultaneous alarms share one ringing screen; snooze applies to the alarms currently ringing.
- The app uses Android's native AlarmManager and a foreground audio service. It does not need an open browser to ring.

MUSIC
- Four original orchestra excerpts, 16 gentle orchestral/piano selections, six nature sounds and 12 standard tones remain bundled offline. The other 28 original orchestra excerpts are an optional 60 MB orchestra pack in Sounds. The app downloads the pack from this version’s GitHub release, verifies its SHA-256, and stores it privately for offline playback. All musical selections are recorded instrumental performances from the Musopen DVD compilation released for reuse.
- Every musical selection is a recorded instrumental performance. The extra orchestral choices are softer edits of existing works, not eight additional compositions. The previous MIDI music has been removed. Excerpts were chosen from quieter passages where possible; some selections are more lively.
- Stereo AAC, 192 kb/s at 44.1 kHz, with loudness balancing and gentle fades at the excerpt boundaries. Alarm playback repeats the selected excerpt.
- New installs and obsolete MIDI selections use the gentle Morning Mood edit by default. A selected standard alarm tone remains selected. Calendar settings and alarms are preserved by an APK upgrade.
- 12 original standard tones remain: beeps, pulses, bells, marimba, harp, chimes, gong and classic ring.
- Six nature options are included: Gentle ocean surf, Atlantic lapping waves, Soft shoreline wash, Steady soft rain, Distant rolling thunder, and Rain with far-off rumbles. These are edited CC0 field recordings, with crossfades and softened peaks.
- Gentle music is selected for steadier passages, with slow swell control and lower mastering targets (-27 LUFS / -11 dBTP). Nature uses -28 LUFS / -14 dBTP targets; thunder is filtered to soften sharp cracks. No chamber or sacred/religious music was added.
- The core APK includes 38 offline sounds. Installing the optional orchestra pack brings the total to 66: 48 music choices, 6 nature sounds, and 12 standard tones. A previously selected orchestra-pack sound is remembered across upgrade but plays the bundled gentle default until the pack is installed. The Sounds screen explains this fallback. Previews last up to 20 seconds and use media volume; alarms use the alarm audio stream.
- Recording publisher: https://musopen.org/blog/musopen-kickstarter-project-1-complete/
- Compilation and rights: https://archive.org/details/musopen-dvd (Public Domain Mark 1.0).
- Full recording sources, excerpt offsets, edits and licensing are in app/src/main/assets/credits.txt and the in-app Music sources & credits screen.

LOOK & BRANDING
The interface and alarm screen use Scribe-inspired dark navy, sapphire blue, teal and pale cream text. Petal Wake is the selected name, with the six-petal clock icon (option D), recolored charcoal, rose pink, mauve and warm ivory in the launcher and app header. Android adaptive and themed launcher icons are included. The native status-bar background matches the dark app, and the alarm screen is divided into full-width Snooze and I’m up halves. The application ID stays app.daybreak so this APK updates the existing app and preserves its settings.

PRACTICAL LIMITS
- Petal Wake requests link updates approximately every 15 minutes and when opened. Android may delay background jobs.
- Proton says shared calendar changes can take up to EIGHT HOURS to appear. A successful download does not guarantee that Proton has published your latest edit. Check wake-up times after schedule changes, especially changes made shortly before a shift.
- Failed downloads retain the previously downloaded calendar and alarms. Last successful download time and refresh errors are displayed.
- Alarms already scheduled can ring without internet. New/changed shifts require a successful calendar update.
- Calendar > Calendar check shows how many event records arrived, how many are all-day, and how many upcoming entries your title filter excluded. Copy calendar check copies counts only; it does not include your link, titles, UIDs, or event details.
- Empty downloads and availability-only feeds have an explanation on the Calendar screen. Explicit busy intervals in availability-only feeds are treated as work. Petal Wake cannot identify which busy blocks represent work, so use a dedicated work-only calendar and confirm the resulting alarms.
- A newly imported shift whose wake-up time is already in the past will not suddenly ring; the app flags it so you can add a personal alarm.
- Alarms are restored after restarting and unlocking the phone. Force-stopping Petal Wake prevents alarms until it is opened again. Powering off the phone prevents Android alarms.
- Some less common ICS forms (period-valued RDATE and RANGE=THISANDFUTURE overrides) are rejected with an error instead of partially scheduling them.
- Automated calendar tests use fixture calendars rather than a real Proton account. Native alarm playback has also been verified on the attached Pixel phone. Device-specific battery controls and Do Not Disturb behavior still depend on your phone settings.

PRIVACY
Calendar data, the sharing link and settings are stored in the app's private on-device storage. Android backup is disabled. There is no app account, advertising, analytics, or hosted backend. Disconnect removes the current calendar data and its work alarms. Uninstalling removes all local app data and alarms.

BUILD FROM SOURCE
Open the included source folder in Android Studio; use JDK 17, Android SDK 35 and Build Tools 35.0.0. Gradle 8.11.1 is pinned in the wrapper with a SHA-256 distribution checksum. Android Gradle Plugin: 8.10.1.

  ./gradlew :app:assembleDebug
  ./gradlew :app:testDebugUnitTest

The output is app/build/outputs/apk/debug/app-debug.apk. This package is a development APK, not a Play Store release. A release build needs your own signing key and normal store preparation.

Android emulator smoke tests use a custom platform Instrumentation runner (no additional test libraries). Run ONLY against a disposable emulator: the test replaces that emulator's app data and calendar fixtures. It tests media playback, scheduling, moving, skipping, firing, dismissing and snoozing alarms.

  ./gradlew :app:assembleDebugAndroidTest
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell pm grant app.daybreak android.permission.POST_NOTIFICATIONS
  adb shell appops set app.daybreak SCHEDULE_EXACT_ALARM allow
  adb shell appops set app.daybreak USE_FULL_SCREEN_INTENT allow
  adb shell am instrument -w app.daybreak.test/app.daybreak.NativeSmokeTest

Third-party code licenses are in THIRD_PARTY_NOTICES.txt and bundled in the app's assets. The source archive includes no account credentials or signing key.

CALENDAR CONNECTION REGRESSION TESTS (0.1.1)
Run on a disposable emulator only:
  adb shell am instrument -w app.daybreak.test/app.daybreak.CalendarConnectionTest
These 22 checks exercise the actual Connect form and native download/parser using fake HTTPS responses inside the test process (timed/all-day/empty/availability-only/403 responses). They do not access a real Proton account.

VOLUME BUILDUP (0.1.7)
Android VolumeShaper now runs the fade on the audio playback clock. The 40–100% level is mapped to a squared gain curve for a more noticeable buildup; this is a UI scale, not calibrated acoustic loudness. The ringing screen shows a percentage derived from the native shaper gain. Music dynamics still vary. Existing fade duration, selected sound and volume ceiling remain unchanged.
Reference: https://developer.android.com/media/platform/volumeshaper

GENTLE & NATURE COLLECTION (0.1.7)
Open Sounds > Gentle music for the 16 softer choices, or Sounds > Nature for waves, rain and distant thunder. All music includes the original louder performances as well. Tap a play button to preview for up to 20 seconds; tap Use to make it your alarm sound. This update preserves your current sound and playback preferences. Strong vibration plus a lower playback ceiling is worth trying when sharing a bedroom.

Nature sources:
https://freesound.org/people/Noted451/sounds/531015/
https://freesound.org/people/davidgtr1/sounds/581488/
https://opengameart.org/content/beach-ocean-waves
https://opengameart.org/content/rain-ambient-not-loopable-2-versions-available
https://opengameart.org/content/rain-long-thunder
These recordings are marked CC0 1.0. Complete credit and edit details are in the bundled credits.txt.

NAME UPDATE (0.1.7)
Petal Clock is now Petal Wake. The package ID and signing certificate are unchanged so this APK upgrades the existing app. The name is updated in Android’s launcher, the app header, ringing screen, setup messages and standard-tone credits. Existing calendar data, alarms, selected sound and playback preferences are preserved.

ICON PALETTE (0.1.9)
The selected six-petal icon now follows Petal Key’s charcoal/rose/mauve/ivory direction: charcoal #28262D, rose #D89AAA, mauve #A783A8, warm ivory #F4EADC. Android launcher, adaptive icon and in-app brand mark use this palette.

APPEARANCE PROFILES (0.1.9)
Open Settings > Appearance. Each option saves immediately and updates the entire app, full-screen alarm, notification color and actual Android launcher icon together.
1. Navy & Teal: deep navy, sapphire blue, teal and pale cream.
2. Rose & Ivory: charcoal #28262D, rose pink #D89AAA, mauve #A783A8 and warm ivory #F4EADC. Complementary darker surfaces keep the rose profile readable at night; rose action buttons use charcoal text.
The currently approved Rose & Ivory icon is the default on this upgrade. Choose Navy & Teal to return to the previous look. Calendar, alarms and playback settings are preserved. Android launchers may take a moment to refresh an icon. Existing home-screen shortcuts managed by some launchers may need to be re-added from the app drawer.

Launcher entries use Android activity aliases; the real app and calendar share activity remain enabled. Android 13+ changes both entries in one PackageManager transaction; earlier versions enable the chosen entry before disabling the other. The launcher entry opens the real activity and finishes, allowing the current screen to remain open while icons switch.
References:
https://developer.android.com/guide/topics/manifest/activity-alias-element
https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSettings(java.util.List)

Appearance integration checks (disposable emulator only):
  adb -s emulator-5554 shell am instrument -w app.daybreak.test/app.daybreak.AppearanceTest
Do not run test instrumentation on your personal phone.

20 THEMES & INDEPENDENT ICON DESIGNS (0.1.10)
Open Settings > Appearance > Choose theme & icon. Select a shape in Icon design, then select any Color theme below. Each choice saves immediately. Your selected icon shape remains selected when you change themes, and its actual Android launcher icon takes on that theme's colors. The header and theme previews update with the same design.

20 themes:
1. Navy & Teal — Navy, sapphire, teal and pale cream.
2. Rose & Ivory — Charcoal, rose pink, mauve and warm ivory.
3. Midnight Violet — Ink violet, lilac and soft silver.
4. Moonstone — Pearl, slate blue and silver sage.
5. Ocean Mist — Sea glass, ocean teal and warm white.
6. Forest Moss — Pine, soft moss and linen.
7. Sage & Linen — Sage white, olive and natural linen.
8. Lavender Cream — Vanilla cream, lavender and plum.
9. Peach Dawn — Soft peach, terracotta and ivory.
10. Blush Cloud — Pale blush, dusky rose and cocoa.
11. Amber Ember — Espresso, amber and toasted cream.
12. Cocoa & Oat — Cocoa, oat milk and soft copper.
13. Sand & Sea — Warm sand, sea blue and driftwood.
14. Arctic Blue — Frost white, glacier blue and slate.
15. Plum Velvet — Black plum, muted berry and champagne.
16. Berry Moon — Deep berry, dusty pink and pale gold.
17. Twilight Indigo — Indigo, periwinkle and moonlight.
18. Mint Daybreak — Mint white, eucalyptus and pale blue.
19. Terracotta Dusk — Clay charcoal, soft apricot and sage.
20. Graphite & Pearl — Graphite, pearl and quiet blue.

Six original icon designs:
D. Petal Clock
A. Dawn Note
B. Moon Chime
C. Lavender Bell
E. Morning Baton
F. Calendar Song

The 20 palettes include 11 night palettes and nine day palettes. Native alarm buttons, notification color, WebView controls, status and navigation backgrounds follow the theme. Text and main action color pairs were checked for at least 4.5:1 contrast.

There are 120 bundled, offline launcher icon combinations. This is a chooser for the original six designs; it does not import arbitrary photos. The app is still named Petal Wake regardless of the chosen icon.

Your existing theme is preserved on upgrade and the current Petal icon remains selected by default. The calendar, alarms, music and playback preferences are preserved. The previous NavyLauncher and RoseLauncher component names are retained for the original Petal design. The launcher entry opens MainActivity in its own task before finishing, so later theme/icon changes do not close the running app or its alarm screen.
Reference: https://developer.android.com/guide/components/activities/tasks-and-back-stack

CREATE AND SAVE CUSTOM THEMES (0.1.11)
Settings > Appearance > Choose theme & icon > Your themes > Create.
1. Give the theme a name.
2. Choose Background, Main accent, Second accent and Text with the color pickers or six-digit hex fields.
3. Review the live icon/alarm preview. Panels and text shades are derived for readable contrast; the selected accent colors are preserved in the in-app icon preview.
4. Tap Save & use theme.
Saved themes appear under Your themes. Tap Use theme, Edit or Remove. You can save up to 50 custom themes alongside all 20 presets. Removing the selected custom theme returns to Rose & Ivory. Cancel leaves the current saved theme unchanged. The icon design stays independently selectable from the six original designs.

ACTUAL APP ICON (0.1.12)
All 20 built-in themes include all six matching app icon designs. Selecting a built-in theme changes the actual Android launcher icon; no extra home-screen icon is needed. The separate home-screen icon option and widget have been removed.
Custom palettes still use exact colors inside the app. Android launcher icons must be bundled in the APK, so arbitrary custom palettes use the closest built-in app icon palette. An APK update is required to bundle an exact new palette.
The custom theme editor and saved themes work offline.

Existing calendar, alarms, music, volume and vibration preferences are preserved on upgrade.

Official platform references:
https://developer.android.com/guide/topics/manifest/activity-alias-element
https://developer.android.com/develop/ui/views/appwidgets/discoverability

Custom-theme integration test (owned disposable emulator ONLY):
  adb -s emulator-5554 shell am instrument -w app.daybreak.test/app.daybreak.CustomThemeTest
This test changes emulator theme fixtures and runs an exact alarm. Do not run instrumentation on your personal phone.

PETAL WAKE 0.1.13 — QUIET FOCUS

Quiet Focus now guides the Alarms, Calendar, Sounds, Settings, Appearance, custom theme editor and dialogs. The app uses consistent sans-serif type, larger readable supporting text, restrained theme accents, clear line icons, solid surfaces and 44px or larger button targets. The header stays visible while scrolling.
The next alarm is the main focus on Alarms, with its date, related shift start and wake-up lead time. Skip once skips that work alarm through Android's native scheduler. Restore in the work list brings it back. The home screen shows three additional work shifts; View all shifts opens Calendar for the complete list.
Calendar instructions are expandable. Successful calendar counts are summarized without a repeated message panel; errors and all-day setup guidance remain visible. Sounds keeps all 66 offline recordings and tones, search and previews. Preferences are grouped by work schedule and playback.
All 20 color profiles, six independent icon designs, saved custom themes and matching built-in launcher icons remain available. Midnight Violet remains selected on the attached phone. No separate home-screen icon option is included.
The ringing screen retains the full-screen Snooze and I'm up touch areas, with coordinated sans-serif labels. Alarm scheduling, playback, the one-minute volume rise and vibration behavior are unchanged.

Install over the existing app. Calendar, scheduled alarms, music and playback preferences are preserved.


PETAL WAKE 0.1.14 — CALENDAR CONFIDENCE & GENTLER AUDIO

CALENDAR CONFIDENCE
The Alarms screen shows calendar download status and recency beside the next alarm. Tap it to open Calendar, where you can see the last successful download and last attempt. Download failure stays visible even when local alarm settings are changed. A successful download confirms receipt from Proton; publication delays can still affect whether a changed shift is included.
Imported .ics files are explicitly labelled as snapshots that require importing again. They do not receive stale-download notices.
Settings > Calendar confidence lets you turn silent sync notices on or off. They are enabled by default for shared calendar links, subject to Android notification permissions. A notice appears after two failures lasting at least 15 minutes, or after 8 hours without a successful download. The separate Calendar sync notices channel is silent and does not vibrate by default. Notices exclude calendar links and shift titles, open Calendar when tapped, and stop after recovery, disconnect or disabling notices. Dismissing a notice does not make it reappear repeatedly for the same unresolved issue. Background checks remain subject to Android scheduling.
Saved offline alarms remain in place when a calendar download fails.

GENTLER AUDIO
All 48 music and six nature recordings have been remastered from source recordings/PCM loops. Slow swell control and mild compression reduce large level changes. Music uses a -28 LUFS / -12 dBTP ceiling; nature uses -29 LUFS / -14 dBTP ceilings, with additional encoded-peak headroom where needed. These are ceilings; some selections remain softer to respect peak limits.
Two-second circular crossfades make 88-second loops. Steady-level starts at quiet stereo crossings replace long introductory/end fades. Pitch, tempo, performer credits and original licenses remain unchanged. Filtering of sharp thunder frequencies is retained. The 12 standard tones are unchanged. All 66 choices still work offline.
Measured opening half-second RMS values range from -33.22 to -29.72 dBFS. Median rolling level spread falls from 10.54 to 2.59 dB compared with the source passages. The largest measured decoded loop-boundary sample jump is 0.00509. Full per-file measurements are included in AUDIO-MASTERING-0.1.14.json.

OPTIONAL QUIET WAKE-UP PRESET
In Settings, scroll to Alarm preferences > Snooze & playback > Quiet wake-up. Tap Use Quiet wake-up for a saved 70% playback ceiling and one-minute rise (starting at 40% of that selected ceiling). The chosen recording, vibration, work schedule and Android alarm-stream volume are retained. Restore previous playback returns to the settings used before selecting the preset. Unsaved work-preference edits remain intact.
The preset is optional. Installing this update preserves your existing playback settings. Remastered music and nature recordings are included automatically.

Install over the existing app. Calendar, alarms, saved themes, icon design, selected sound, volume and vibration preferences are preserved.

Platform/audio references:
https://developer.android.com/develop/ui/compose/notifications/channels
https://www.ffmpeg.org/ffmpeg-filters.html


PETAL WAKE 0.1.15 — PROTECTED WORK ALARM SWITCH

Turning off Work alarms requires a fresh Android identity check. On Android 11 or newer, the system prompt accepts a supported strong fingerprint or face authenticator, or the phone's existing PIN, password, or pattern. Android 10 uses its system biometric/credential prompt. Android 8–9 use the phone's screen-lock confirmation.

The switch stays visibly on while authentication is pending. Canceling, incorrect credentials, unavailable authentication, or a missing screen lock leaves work alarms enabled and scheduled. Turning Work alarms on requires no identity check.

Disconnecting the calendar while Work alarms is enabled requires the same identity check. Disconnecting an already paused calendar retains the usual confirmation.

Petal Wake never receives or stores your PIN, fingerprint, or face data. Authentication is handled by Android. There is no separate Petal Wake PIN or reusable unlocked session.

Turning Work alarms off pauses work alarms and retains the calendar link, downloaded shifts, and preferences. Personal alarms remain independent. Snooze, I'm up, and skipping one shift keep their existing behavior.

Install this update over the existing app to preserve saved settings. This feature protects the in-app work alarm switch and active-calendar disconnect; it does not restrict Android app removal, clearing app data, or changing system settings.

Android implementation references:
https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder
https://developer.android.com/reference/android/app/KeyguardManager

0.1.17: sixteen approved family palettes, matching all six bundled launcher shapes; retired built-in IDs migrate, custom themes and other preferences remain.

PETAL WAKE 0.1.18 — TWO-SECOND ALARM DISMISSAL

The full-screen “I’m up” control now requires a two-second press and hold. A visible countdown bar shows progress; releasing early cancels dismissal and leaves the alarm ringing. Snooze remains a one-tap action.
The notification’s “I’m up” action now opens the same alarm screen, so it cannot stop the alarm directly. The ongoing alarm notification explains the hold gesture.

PETAL WAKE 0.1.19 — CALENDAR SERVICE GUIDE

The Calendar setup screen now provides a service menu with inline sharing-link directions for Proton Calendar, Google Calendar, Outlook, Apple iCloud Calendar, and other calendar or work scheduling apps. The link connector accepts standard secure iCalendar subscription URLs from those services. Existing calendar links and alarms are retained when updating.

VERSION 0.1.21 — CALENDAR & RELIABILITY UPDATES
- Connect up to six work calendars (one original calendar plus five additional live links). Each has its own name, title filter, lead time, and all-day start time. The existing calendar and settings are preserved on upgrade. The Calendar page can remove an additional feed without disconnecting the others; Disconnect all calendars still requires Android authentication while work alarms are enabled.
- Optional early/later shift rules override the per-calendar lead time according to local shift start time. An all-day shift uses its calendar's normal lead time after its start time is set.
- Optional evening check shows tomorrow's first work shift and alarm, including the age of the oldest calendar download. Android may delay this background notification. Proton may take up to eight hours to publish shared-calendar changes; check changed shifts directly.
- Alarm readiness is displayed prominently if exact alarm access, notifications, lock-screen presentation, or phone alarm volume is unavailable. A notice is sent when Android permits notifications.
- Core APK now includes 38 offline sounds. The 28 additional orchestra recordings are in the attached petal-wake-music-pack.zip release asset. Download them in Sounds; the app verifies the pack SHA-256 before installing it. If a previously chosen orchestra-pack sound is missing, the app rings with its gentle bundled default while remembering the selection. Reinstalling the pack restores that choice. Downloading the pack needs an internet connection once; playback afterward is offline.
- For source, use the attached petal-wake-source.zip release asset. GitHub's automatically generated source links represent the repository tag and may not contain this release's new files or music pack.
