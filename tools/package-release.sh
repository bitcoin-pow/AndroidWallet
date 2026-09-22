#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 or newer}"
export JAVA_HOME
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" && -f local.properties ]]; then
    sdk_dir="$(sed -n 's/^sdk.dir=//p' local.properties)"
fi
: "${sdk_dir:?Set ANDROID_HOME to the Android SDK directory}"
build_tools="$sdk_dir/build-tools/${BTCW_BUILD_TOOLS_VERSION:-36.0.0}"
key_dir=".release-signing"
[[ -f "$key_dir/btcw-release.p12" && -f "$key_dir/release-password.txt" ]] || {
    echo 'Restore your existing release key and password into .release-signing. Never replace the key for an existing release.' >&2
    exit 1
}
./gradlew :app:assembleRelease --no-configuration-cache
mkdir -p dist
apk="dist/BTCW-1.0.apk"
# Refuse malformed artwork or known personal paths before signing.
"$JAVA_HOME/bin/java" tools/ApkPrivacyAudit.java app/build/outputs/apk/release/app-release-unsigned.apk
"$build_tools/zipalign" -f -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk dist/release-aligned.tmp.apk
"$build_tools/apksigner" sign --ks "$key_dir/btcw-release.p12" --ks-key-alias btcw-release \
    --ks-pass "file:$key_dir/release-password.txt" --v4-signing-enabled false \
    --out "$apk" dist/release-aligned.tmp.apk
rm dist/release-aligned.tmp.apk
"$build_tools/apksigner" verify --verbose --print-certs "$apk" > dist/SIGNATURE.txt
"$build_tools/zipalign" -c -P 16 4 "$apk"
"$JAVA_HOME/bin/java" tools/ApkPrivacyAudit.java "$apk" > dist/PRIVACY-SCAN.txt
"$build_tools/aapt2" dump badging "$apk" > dist/APK-INFO.txt
if rg -q 'application-debuggable|testOnly|ACCESS_(FINE|COARSE|BACKGROUND)_LOCATION' dist/APK-INFO.txt; then
    echo 'Release manifest privacy check failed' >&2
    exit 1
fi
(cd dist && shasum -a 256 BTCW-1.0.apk > SHA256SUMS.txt)
echo "Signed APK and audit output saved in dist. Share only BTCW-1.0.apk (plus checksum if desired)."
