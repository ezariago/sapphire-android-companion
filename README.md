# Sapphire Companion

An independent, editable Android companion for this checkout's reconstructed
[Sapphire Mac integration](https://github.com/ezariago/sapphire-foss). The Play Store APK is not modified, decompiled code is
not bundled, and its package/data/signature are not reused.

This first version provides authenticated Bluetooth pairing/reconnection, phone
battery status, automatic Mac → phone text clipboard delivery, explicit phone →
Mac text clipboard delivery, **real Android internet hotspot start/stop**, and
opt-in notification mirroring with dismissal and supported reply/action buttons.
Opt-in now playing, remote media controls and bidirectional web-link handoff are also supported. Phone-reviewed SMS/dialer drafts now have source implementations. One-file Mac-to-phone BLE staging is also implemented in source. Full SMS inbox/call control, Wi-Fi/bulk or reverse file transfer, phone storage browsing, camera streaming and screen sharing are not implemented.

## Build

Install JDK 17 and Android SDK platform 36/build tools, then run:

```sh
./build.sh
```

On macOS the script finds JDK 17 using `java_home` and defaults `ANDROID_HOME` to
`~/Library/Android/sdk`. Set `JAVA_HOME`/`ANDROID_HOME` for other installations.
Open this repository in Android Studio, or run `./build.sh` at its root. Both
apps remain in one Gradle project with a shared `:ipc` module. Test vectors are
included; the Mac repository is not required to build either Android app.

The script uses the pinned Gradle wrapper, runs JVM protocol tests against the
same independent crypto fixture as the Mac, and produces:

- `build/apks/SapphireCompanion.apk` — `dev.sapphire.companion`, target SDK 36.
- `build/apks/SapphireHotspotHelper.apk` —
  `dev.sapphire.companion.hotspothelper`, target SDK 28.

Both debug APKs use the same local Android debug signing key. Preserve that key
for updates. For distribution, configure a private release signing key and use it
for both apps; do not publish a private signing key or build secrets.

## Install and connect

```sh
adb install --user 0 -r build/apks/SapphireHotspotHelper.apk
adb install --user 0 -r build/apks/SapphireCompanion.apk
```

These packages coexist with `com.shariq.sapphire`. The new companion uses its own
Bluetooth service UUID (`DAB10001-5A50-434F-4D50-414E494F4E31`), avoiding collisions
with the original app's GATT service. Use the updated Mac build supplied alongside
these APKs; older Mac builds scan only for the original service. The Mac currently remembers one
companion identity at a time, so pairing the replacement switches its saved
connection to this app. The original Android app and its data remain installed.

1. Open **Sapphire Hotspot Helper**. Install/start [Shizuku](https://shizuku.rikka.app/)
   if needed, then tap **Authorize hotspot control** and approve its Shizuku
   permission request yourself. Shizuku may need restarting after a phone reboot.
2. Open **Sapphire Companion**. Allow Nearby devices and optionally its persistent
   connection notification. Tap **Pair a Mac**.
3. In the rebuilt Mac app, open Settings → Continuity → **Pair a phone**. Let it
   connect to the phone marked ready to pair.
4. Scan the Mac QR using the companion's **Scan Mac QR code** button, or paste its
   public pairing text and tap **Use pairing text**. Camera permission is only
   requested when using the scanner. The scanner works offline.
5. Compare the six digits on both devices. Confirm on the phone and Mac only if
   they match. Neither ADB nor the developer diagnostics approve pairing.
6. Wait for **Connected securely**. New Mac text copies arrive automatically;
   use **Send phone clipboard to Mac** for the reverse direction. Android's
   background clipboard restrictions still apply. Private/sensitive clips and
   received-from-Mac clips are not sent back to the Mac. Text is limited to less
   than 12,000 UTF-8 bytes.
7. In Mac Continuity settings, click **Connect to phone hotspot** and allow macOS
   Location access to scan Wi-Fi networks. This switches the Mac's Wi-Fi. The Mac
   recognizes this companion's encrypted internet-hotspot capability, and
   **Disconnect hotspot** also asks the helper to stop tethering.

The helper reads the existing Android hotspot configuration; it does not change
its name or password. If the hotspot is already on, the helper reuses it. Android
duplicate-start responses are checked against the actual hotspot state before
returning credentials. For this initial implementation, select **WPA2** security in
Android hotspot settings so automatic Mac joining is supported. Mobile data,
carrier tethering entitlement and coverage still determine internet availability.

## Notification mirroring

1. Enable **Share phone notifications with paired Mac** in the companion.
2. Tap **Allow notification access** and enable Sapphire in Android Settings.
   This grants access to other apps' notification content; it is separate from
   permission to show the companion's own connection notification.
3. Connect to the updated Mac app. Its Phone / Android settings show the active
   notification list and filters. New notifications appear in the notch;
   optional macOS banners require Mac notification permission.
4. Use **Dismiss on phone**, an app action, or **Reply** on Mac. Replies/actions
   require the phone to be unlocked and the notification revision still current.

Sharing defaults to off and uses only the authenticated encrypted Bluetooth
connection. Up to 30 active notifications synchronize on reconnect without
re-alerting for old entries. Secret, ongoing, group-summary, other-user/work-profile
and Sapphire service notifications are excluded. Low-priority and Android system
notifications are excluded unless enabled in Mac settings. Android/OEM sensitive
content redaction is respected. No notification history is persisted by the
companion; optional Mac banners are retained by macOS Notification Center.
Images, icons, attachments and opening phone apps by clicking a Mac banner are
not included in this first implementation. Turn off sharing or revoke Android
notification access to clear the Mac list.

Debug builds include a synthetic test activity, absent from release builds:

```sh
adb shell am start -n dev.sapphire.companion/.NotificationFixtureActivity
```

Its **Test action** and replies stay inside the fixture app. A reply of
`Sapphire reply fixture` produces a success flag; no actual message is sent.
See [VERIFICATION.md](VERIFICATION.md) for tested scope.

## Why two apps, and why Shizuku?

The helper is separate and targets SDK 28, but **an old target alone is not enough
on Android 16**. Current SimpleWear's helper also uses Shizuku for hotspot control;
its main app routes modern Android hotspot requests through that helper. See
[SimpleWear hotspot action](https://github.com/SimpleAppProjects/SimpleWear/blob/1b7b3a5bfe6794bbe9a73f836a21a33b46d71dc0/wearsettings/src/main/java/com/thewizrd/wearsettings/actions/WifiHotspotAction.kt).

Our helper uses user-authorized Shizuku Binder access to Android's tethering
service. It requests internet tethering, keeps carrier entitlement checks enabled
and uses the configured mobile hotspot. It never calls `startLocalOnlyHotspot`
and never substitutes Wi-Fi Direct/local-only networking when tethering fails.
If Shizuku is unavailable or access is denied, the app explains the setup needed
and provides an Android hotspot-settings button. No root installation is required
for the tested Shizuku setup; OEM/OS updates may change hidden API compatibility.

## Source map

| File | Purpose |
| --- | --- |
| `app/.../MainActivity.java` | Phone UI, permission requests, pairing confirmation, clipboard controls |
| `app/.../ScannerActivity.java` | Offline QR scanning using ZXing |
| `app/.../LinkService.java` | BLE peripheral/GATT, pairing state, protocol dispatch, foreground notification |
| `app/.../NotificationMirror.java` | Opt-in notification access, bounded snapshots/events and local action/reply dispatch |
| `app/.../NotificationPolicy.java`, `NotificationEvents.java` | Notification filtering/text limits and coalesced event ordering |
| `app/.../LinkEnvelope.java` | Mac-compatible JSON envelope fields and timestamps |
| `app/.../LinkCrypto.java` | P-256 ECDH, HKDF/SAS, directional ChaCha20-Poly1305 and replay window |
| `app/.../GattFrames.java` | Bounded BLE fragmentation/reassembly |
| `app/.../PairingStore.java` | Android Keystore AES-GCM wrapping of saved pairing data |
| `helper/.../HelperActivity.java` | Shizuku authorization/setup |
| `helper/.../HotspotService.java` | Narrow start/stop tethering broker and credential retrieval |
| `ipc/src/main/aidl/` | Signature-protected app/helper interface |

Each `...` above expands to `src/main/java/dev/sapphire/companion` for the main app
and `src/main/java/dev/sapphire/helper` for the helper.

The exported helper service requires a signature permission. The two APKs must
be signed with the same certificate. Other apps, including the ADB shell, cannot
call it. There is no arbitrary-command executor or unauthenticated network
listener. Pairing keys are encrypted using Android Keystore, backups are disabled,
passwords/clipboard contents are never logged, and phone commands require an
authenticated encrypted BLE session. Public QR text can be supplied to the
launcher for developer testing, but matching-code approval always stays in the UI.

## Tests

```sh
./build.sh
# In the separate Mac checkout: ./scripts/test-phone-reconstruction.sh
./scripts/test-android-companion-device.sh
# Explicitly turns the real phone hotspot on for ten seconds, then stops it:
./scripts/test-android-companion-device.sh --hotspot
```

The device test requires the test APK, a USB-debugging-authorized phone and, for
the optional hotspot check, an installed/authorized helper. The hotspot check also
requests a second start while the hotspot is on and verifies credential reuse. It reports only
readiness/status booleans, never SSIDs, Wi-Fi passwords or pairing keys. It does
not pair the Mac or change its Wi-Fi network. `--hotspot` should be used when the
phone's hotspot is initially off, since cleanup stops tethering.

The Mac app lives in [sapphire-foss](https://github.com/ezariago/sapphire-foss).
This repository is licensed under [AGPL-3.0](LICENSE); see [third-party notices](THIRD-PARTY.md).

Device-tested findings are recorded in `VERIFICATION.md`.


Debug builds also write `cache/link-diagnostics.json` in the main app's private
storage. It contains only process/transport readiness, message type/byte count,
queue size and booleans indicating whether a session/code exists. It contains no
actual code, QR, peer identifier, keys, network credentials or clipboard data.
The file is unavailable to other apps; an authorized ADB developer can inspect it
with `adb shell run-as dev.sapphire.companion cat cache/link-diagnostics.json`.
Release builds do not write it.


After a phone reboot, force stop or APK update, open the companion and tap
**Start connection** if it is stopped; the saved pairing is reused. Shizuku may
also need restarting after reboot. This first version does not auto-start at boot.


### Live phone status in the notch

The companion sends battery percentage, charging state, the default mobile-data SIM’s registered network provider, Android’s cached modem signal level (0–4), and the phone’s active network kind. While connected, it checks for changes every two seconds and sends changed summaries over the authenticated Bluetooth session; the Mac also requests a refresh every 20 seconds. Android may update its cached modem readings less frequently in power-saving mode.

No phone number, SIM identifier, call history, or location is collected for this feature. `ACCESS_NETWORK_STATE` is a normal permission used only to distinguish Wi-Fi/mobile data/no active network. Missing radio data appears as unavailable. On a dual-SIM phone this summarizes the default data SIM, rather than both SIMs.

Click the phone status strip in the Mac notch to see device name, encrypted connection status, battery/charging, carrier, signal, network kind, and **Instant hotspot → Connect / Disconnect**. The hotspot button uses the same helper-backed connection flow as Settings and requires Mac Location access for Wi-Fi discovery.

## Phone media

Enable **Share now playing and allow Mac playback controls** in the companion and
grant its Android notification access. This is a separate opt-in from notification
mirroring. Active-session title, artist, album, duration and position travel over
the authenticated Bluetooth link. The Mac shows the selected player in its music
source list and Phone / Android settings; play, pause, toggle, next, previous,
stop and seek use the phone app's advertised transport actions.

Playing sessions take priority over paused sessions. Commands carry an ephemeral
session ID so a delayed command cannot control a newly selected player. Turning
sharing off clears Mac media and rejects new commands. No audio capture, streaming,
artwork or playback history is included. Third-party players may expose fewer
controls. Android active-session access uses the enabled notification listener,
as described in the [MediaSessionManager documentation](https://developer.android.com/reference/android/media/session/MediaSessionManager).

Debug builds include `MediaFixtureActivity`, a silent synthetic player with no
audio output. The Mac repository's `scripts/test-phone-media.py PID` checks
metadata, transport commands, exact seek position, disabling sharing and session
removal without changing real music playback.

## Web-link handoff

Enable **Receive web links from paired Mac** and allow Sapphire notifications.
In Mac Phone / Android settings, enter a URL and choose **Send link to phone**.
The phone posts a silent notification; tap it to review the full link, then choose
**Open in browser**. Nothing opens automatically. A blocked notification channel
or disabled receiving setting returns a status to the Mac. Only the latest pending
link notification is kept, and disconnect clears it.

To send the other way, choose your browser's **Share → Send link to Mac**, then
confirm **Send to paired Mac**. Sapphire's Mac settings show the full received URL
and an explicit **Open received link on Mac** action. Received links are cleared
from the app when disconnected; neither companion stores a link history.

Both peers accept only a single ASCII HTTP/HTTPS URL of up to 4,096 bytes, without
embedded credentials, backslashes or spaces; percent-encoded paths are supported.
Custom schemes, file URLs and JavaScript URLs are rejected. Pairing and encryption
are required in both directions. Debug builds include `HandoffFixtureActivity`
for the fixed public example.com test URL. It is absent from release APKs.

## Bluetooth radio recovery

While the connection service is running, switching Android Bluetooth off clears
its transport and ephemeral session. Switching it on opens a fresh GATT server
automatically and the saved Mac can authenticate again. Old-server callbacks are
ignored. An explicitly stopped service remains stopped. Two physical off/on
cycles were verified on the Galaxy S24 Ultra; test logs and scope are described
in [VERIFICATION.md](VERIFICATION.md).


## Phone-reviewed SMS and dialer drafts (0.4.0)

Enable **Receive SMS and dialer drafts** to receive an authenticated Mac draft as
a private notification. Review its number/text on your unlocked phone, then open
the system SMS composer or dialer. Sapphire does not send messages, place calls,
read contacts or access the SMS inbox/call history. No SMS or call permission is
requested. The system app still requires the user's final send/call action.

Drafts expire after two minutes, on disconnect/disable or replacement. Only one
in-memory draft is retained; notification PendingIntents contain opaque review
tokens rather than message/recipient data. Short/service codes, URI input,
extensions, pauses and non-ASCII numbers are rejected; use 7–15 digits with an
optional leading plus. Bodies allow up to 4,000 UTF-8 bytes. This is not full
message/conversation/call synchronization or delivery confirmation.

`./build.sh` builds the app/helper and runs JVM fixtures. For safe on-device
policy/intent/crypto checks without replacing or instrumenting the real companion:

```sh
ANDROID_SERIAL=<authorized-usb-device> ./scripts/test-isolated-device.sh
```

That separate fixture package has no permissions, activities or access to the
companion's data. It constructs review intents without launching them. It is
removed afterward. Optional `SAPPHIRE_SLEEP_AFTER_TEST=true` returns an authorized
test phone to sleep; it changes no persistent wake/notification/alarm setting.
Native notification/review UI, opt-in/permission/lockscreen handling and actual
Mac-to-phone draft routing still need attended verification. No real message or
call is used by these tests.


## One-file receive and Save (0.5.0)

Enable **Receive files from paired Mac** for a single encrypted Bluetooth transfer
up to 5 MiB. In the Mac Phone pane, choose one ordinary file. Files are chunked
with acknowledgements, exact size and SHA-256 checks. Completed files are staged
in the companion's private cache, not automatically placed in Downloads or opened.
Use **Save received file…** to choose a document destination, then explicitly
**Discard staged file** when you no longer need that cache copy. Saving verifies
the streamed size/hash and keeps the staged copy; failed saves can leave a partial
chosen document and never delete that destination. Android may evict cache.

Only one active or completed staging slot is supported. Remote names are labels,
never filesystem paths. Partials expire or cancel on disconnect/disable; completed
copies survive disconnect until Discard or OS cache eviction. There are no broad
storage permissions, new network listeners or automatic file-opening actions.
Wi-Fi/high-throughput bulk, batches, reverse transfer, browsing and file clipboard
are still unimplemented.

JVM fixtures cover exact 5 MiB boundaries, staging, malformed data, sequencing,
digest failures, timeout/cancellation, restart/preservation, cache eviction and
verified-copy write failures. The separate USB fixture consumes a public synthetic
packet asset generated by the Mac policy and verifies Android file I/O and exact
bytes without accessing user data or launching an external activity. To regenerate
that asset, run the Mac repository's `scripts/test-phone-file-transfer.sh` and copy
`build/file-transfer-tests/wire.json` to
`devicechecks/src/main/assets/mac-file-transfer.json`; verify semantic agreement.

Actual encrypted BLE file transfer between both normal apps and actual document-
picker/provider Save remain attended checks. No normal companion update is
installed by the isolated fixture script.
