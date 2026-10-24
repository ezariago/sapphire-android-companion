#!/bin/bash
set -euo pipefail
SAPPHIRE_TEST_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SAPPHIRE_TEST_SERIAL="${ANDROID_SERIAL:?Set ANDROID_SERIAL to the authorized USB device}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ -z "${JAVA_HOME:-}" ]]; then export JAVA_HOME="$(/usr/libexec/java_home -v 17)"; fi
cd "$SAPPHIRE_TEST_ROOT"
./gradlew --console=plain :devicechecks:assembleDebug
adb -s "$SAPPHIRE_TEST_SERIAL" get-state
# Own package only; never instrument or replace the user's companion process.
if adb -s "$SAPPHIRE_TEST_SERIAL" shell pm path dev.sapphire.fixture | tr -d '\r' | grep -q '^package:'; then
    echo 'Fixture package is already installed; refusing to replace unrelated existing state.' >&2
    exit 2
fi
SAPPHIRE_INSTALLED=false
cleanup() {
    if [[ "$SAPPHIRE_INSTALLED" == true ]]; then adb -s "$SAPPHIRE_TEST_SERIAL" shell pm uninstall --user 0 dev.sapphire.fixture >/dev/null || true; fi
    if [[ "${SAPPHIRE_SLEEP_AFTER_TEST:-false}" == true ]]; then adb -s "$SAPPHIRE_TEST_SERIAL" shell input keyevent KEYCODE_SLEEP || true; fi
    adb -s "$SAPPHIRE_TEST_SERIAL" shell dumpsys power | grep -E 'mWakefulness=|mStayOn=' || true
}
trap cleanup EXIT
adb -s "$SAPPHIRE_TEST_SERIAL" install --user 0 devicechecks/build/outputs/apk/debug/devicechecks-debug.apk
SAPPHIRE_INSTALLED=true
SAPPHIRE_DEVICE_RESULT="$(adb -s "$SAPPHIRE_TEST_SERIAL" shell am instrument --user 0 -w dev.sapphire.fixture/dev.sapphire.companion.IsolatedDeviceChecks)"
printf '%s\n' "$SAPPHIRE_DEVICE_RESULT"
[[ "$SAPPHIRE_DEVICE_RESULT" == *'INSTRUMENTATION_RESULT: result=passed'* ]]
[[ "$SAPPHIRE_DEVICE_RESULT" == *'INSTRUMENTATION_RESULT: launchedExternalActivities=false'* ]]
