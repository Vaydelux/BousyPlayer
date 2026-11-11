#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -x ./gradlew && -f gradle/wrapper/gradle-wrapper.jar ]]; then
  runner=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  runner=(gradle)
else
  echo 'ERROR: Android Gradle unavailable. Install Gradle 8.13, JDK 17 and Android SDK 36, or run GitHub Actions.' >&2
  exit 127
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" && ! -f local.properties ]]; then
  echo 'ERROR: Android SDK path is not set. Configure ANDROID_HOME or local.properties.' >&2
  exit 127
fi
# Fail the build if the actual session or native-surface simulations regress.
"${runner[@]}" --no-daemon --stacktrace \
  :session-smoke:run :native-smoke:run \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# Enumerate actual resolved Maven versions and scan known OSV advisories.
# On network/scan failure or known vulnerability, do not publish a debug APK.
"${runner[@]}" --no-daemon :app:generateDebugSbom
python3 tools/test_supply_chain.py
python3 tools/supply_chain.py --sbom app/build/reports/security/debug-maven-sbom.cdx.json \
  --report app/build/reports/security/osv-findings.json --enforce
for abi in armeabi-v7a arm64-v8a; do
  python3 tools/verify-apk.py "$abi" "app/build/outputs/apk/debug/app-$abi-debug.apk"
done
# Verify actual ELF payloads and compare the shipped ARM32 and ARM64 library sets.
python3 tools/test-native-inventory.py
python3 tools/native_inventory.py \
  --arm32 app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk \
  --arm64 app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
  --version 0.29.0 \
  --inventory app/build/reports/security/debug-native-inventory.json \
  --sbom app/build/reports/security/debug-native-file-sbom.cdx.json
