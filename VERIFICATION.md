# Device verification — 2026-10-22

Test device: Samsung Galaxy S24 Ultra (SM-S928B), Android 16. The user authorized
Shizuku for the new helper and personally approved matching pairing codes on both
devices. The original Play Store app remained installed with its data intact.

## Passed

- Both APKs compile. Five Android JVM tests cover independent Mac/Java crypto
  vectors, P-256/HKDF/SAS, directional ChaCha20-Poly1305, authentication failures,
  replay/reordering, bounded/expired GATT fragments and Mac-compatible envelope
  field types. The Mac's independent 67-check protocol suite also passes.
- Native Android device checks verify its actual ECDH/ChaCha providers, replay
  rejection and GATT reassembly.
- The exported helper rejects ADB-shell access because the caller lacks its
  signature permission. Main app and helper use matching signing certificates.
- With the user's Shizuku grant, the helper starts the **real** Android hotspot,
  returns WPA2 credentials and successfully stops it. No local-only hotspot or
  Wi-Fi Direct API is used.
- The new Bluetooth service namespace avoids collisions with the original app.
  Both apps can run together. The Mac targets the service advertised by the
  selected companion, then requires saved-key proof to authenticate it.
- The replacement pairs with the Mac using the scanned/pasted public challenge,
  matching-code approval and authenticated encrypted replies.
- A new Mac text copy arrives automatically in Android's system clipboard;
  pasting into Android Settings search reproduced the exact nonsensitive sample.
- The phone's explicit clipboard-send button delivers an authenticated
  `clipboard.update` to the Mac.
- The full Mac → encrypted BLE → companion → signature-protected helper → Shizuku
  → Android tethering flow returns valid credentials. The reverse stop command
  shuts down tethering.
- While that hotspot was active, Android's current tethering state reported
  `Upstream wanted: true`, an interface in `TetheredState`, and a mobile-data
  upstream interface. After cleanup, `Upstream wanted` returned to false. The
  Mac's existing Wi-Fi connection was not switched by this probe.
- An Android process restart and APK update preserve the Keystore-wrapped pairing.
  After starting the phone connection again, the Mac handles GATT service
  invalidation and stale transports automatically. In the device test, a fresh
  phone process regained an authenticated session in approximately five seconds,
  without another QR or approval, while the original app was also running.

## Remaining verification and scope

The already-active hotspot fix builds successfully and has a device regression
check in `--hotspot`: a second enable request must return the same state and,
when available, the same credentials. The helper checks for an active AP before
starting; code 18 (duplicate request) waits up to ten seconds for an active AP
instead of reporting a Shizuku/carrier failure. This fix has not yet been run on
a phone because no ADB device was connected during its validation.

Actual Mac Wi-Fi association and an internet request through that Wi-Fi connection
still require macOS Location approval. The tested probe validates tethering,
cellular upstream and credential exchange while leaving Mac Wi-Fi unchanged.

The offline camera scanner compiles; pairing was device-tested through public QR
text entry rather than physically scanning with the camera.

This is an independent first version, not recovered original private source.
Files, calls, camera streaming and screen sharing remain unimplemented.
Notification mirroring, media controls and web-link handoff are described below. Android background clipboard rules still limit
phone → Mac automation, so that direction uses an explicit send action.


## Artifact checks

The standalone Android source ZIP was extracted into a separate folder and its
own `./build.sh` successfully built both APKs and passed all five JVM tests without
accessing the Mac repository's source or fixture files. Both installed Android
APKs match their delivered files byte for byte, and both signatures verify with
the same certificate. The final Mac ZIP was extracted, passed strict nested
signature verification and matched the tested executable.


## Live device status and notch panel

- On the Galaxy S24 Ultra, native device checks read the registered carrier
  `INDOSATOOREDOO` and a valid 4/4 modem level, plus battery status, without any
  runtime phone/location permission. The companion now publishes changed status
  summaries every two seconds over its authenticated link.
- The Mac protocol suite includes missing/legacy telemetry, zero vs. unknown
  signal, out-of-range/fractional signal values, carrier control characters and
  unknown network kinds.
- The formerly empty notch detail route now displays device status and hotspot
  Connect/Disconnect controls. Native SwiftUI previews of the actual panel source
  were rendered in connected, connecting, hotspot-connected and offline states.


## Notification mirroring implementation (2026-10-22)

- Implemented explicit opt-in sharing and the Android notification-access settings link.
- Implemented encrypted posts, updates, removals, quiet reconnect snapshots,
  dismissal and supported free-form replies/actions, with stale revision checks.
- Added JVM tests for filtering, UTF-8 bounds and BLE event queue ordering/capacity.
- Added debug-only `NotificationFixtureActivity`; replies/actions remain local
  and record only test-success booleans. The fixture is absent from release builds.
- Android debug and release builds and all 14 JVM tests pass. The release APK
  manifest excludes the debug fixture. Mac protocol tests pass all 92 checks.
- With notification access granted by the user on the Galaxy S24 Ultra, the
  encrypted Mac/Android link passed quiet initial sync, posts, updates, actions,
  RemoteInput replies, Mac dismissal, phone removal, disconnect clearing and
  quiet resynchronization after simulated sleep/wake. The fixture receiver
  independently confirmed the action and exact synthetic reply; no real message
  was sent, and the fixture was removed afterward.
- Ordinary third-party app actions/replies, permission revocation, locked-phone
  behavior, optional macOS banners and physical sleep/radio recovery still need
  separate verification. Native Mac UI automation became unavailable; later
  checks used production transport/bridge state and the Android receiver.


## Media, web-link handoff and radio recovery (2026-10-24)

The connected Galaxy S24 Ultra passed these tests over the authenticated encrypted
Mac/Android link, with existing pairing records retained:

- A silent debug MediaSession provided fixed synthetic metadata. Mac commands
  exercised play, pause, next, previous, exact 42,000 ms seek, toggle and stop.
  Turning sharing off cleared Mac media and rejected controls; reenabling restored
  the session, and releasing it cleared the Mac. No real audio played.
- Disabled link receiving returned a failure to the Mac. Enabling receiving allowed
  a notification for the fixed public example.com URL. Its PendingIntent opened
  the companion's review Activity, with browser opening left to explicit user
  choice. The production ACTION_SEND review screen was opened with the public
  test URL; confirming **Send to paired Mac** delivered it to Mac settings.
  No browser opened.
- Mac-only app muting removed the fixture locally while the phone independently
  retained it, suppressed updates and reconnect snapshots, and restored it quietly
  after allowing the app again. Normal notification sharing stayed off; an
  explicit debug-only opt-in allowed just the fixed synthetic fixture.
- Two physical Android Bluetooth off/on cycles cleared the old Mac session and
  automatically regained an authenticated connection, without app restart or
  pairing approval. Retired GATT callbacks are rejected by generation. Bluetooth
  was left on and test sharing preferences were restored.

All 17 JVM tests and the Mac's 126 protocol checks pass. Both debug APKs and both
release targets build. The helper's legacy target is intentionally sideloaded,
and its already-authorized Shizuku hidden Binder call has a narrow lint exception;
all other release lint checks remain enabled. Release manifest inspection excludes
NotificationFixtureActivity, MediaFixtureActivity and HandoffFixtureActivity.

Run the Mac repository's scripts with an opt-in diagnostic PID:
`test-phone-media.py`, `test-phone-handoff.py`, `test-phone-muting.py` and
`test-phone-radio.py`. The radio script deliberately toggles phone Bluetooth.
Third-party player compatibility, real audio behavior and full browser navigation
remain outside the silent fixture checks. No calls or messages were sent.
