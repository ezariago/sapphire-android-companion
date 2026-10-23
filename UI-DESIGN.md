# Companion interface

The main app uses Material Components 1.14.0 and
`Theme.Material3Expressive.DayNight.NoActionBar`. The helper has its own theme
and is unaffected.

- **Connection:** saved Mac identity, live service status, secure connection state,
  QR scan, expandable manual/Bluetooth pairing and six-digit code confirmation.
- **Features:** existing clipboard, notification, media, handoff and hotspot
  preferences, using Material switches and tonal actions.
- **Settings:** Android access, connection controls and app information.
- Scanner and incoming/outgoing link confirmation use the same presentation.
- Sapphire blue light/dark palettes, expressive shapes, Material navigation,
  accessible headings, density-independent spacing, system/keyboard insets,
  multiline actions and centered content on wide displays.
- Navigation destination, scroll positions and manual pairing drafts survive
  activity recreation. Pairing records and feature preference keys are unchanged.

## Validation

Build and run the existing unit suite without building the helper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The opt-in UI runner checks the three destinations, retained preference values,
manual pairing expansion, theme recreation with destination retention, and
valid/invalid outgoing link confirmation. It does not start connections, forget
pairings, toggle preferences or send links. It writes presentation screenshots
into the target app's external files directory. It uses app-local night modes
and does not change the system theme.

```sh
./gradlew -PuiChecks :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.sapphire.companion.test/dev.sapphire.companion.UiChecks
```

Verified on the connected Samsung phone: debug build, all 17 unit tests, UI runner,
light/dark screenshots, and manual 140% system text scaling (restored afterward).
Existing full-project lint errors remain in Bluetooth permission handling in
`LinkService`, an API-level check in `PhoneStatus`, and a constant in the debug
notification fixture. No redesign lint errors remain.
