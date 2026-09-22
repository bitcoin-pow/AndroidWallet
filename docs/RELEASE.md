# Sharing the Android release

Share `dist/BTCW-1.0.apk`, optionally with `dist/SHA256SUMS.txt`. Do not share the
entire working directory, debug/test APKs, local build logs, or `.release-signing`.
To publish the source, use the curated file set described in
[SOURCE-PRIVACY.md](SOURCE-PRIVACY.md).
The signed release has package ID `com.wallet.btcw`, version 1.0 (code 1), and
requires Android 8.0 or later.

## Privacy review

The release was checked after signing, including decompressed ZIP entries and
raw container/signing bytes in ASCII-compatible and UTF-16 encodings. No local
username/home-directory matches, developer filesystem paths, private-key PEMs,
private/config file entries, or app test-wallet classes were found. Packaged
PNG/WebP metadata checks also passed. Source inspection found no personal account,
address, location or hard-coded wallet secret in production app sources.

The supplied wordmark contained two EXIF chunks; these were removed. A timestamp
chunk was removed from the coin image. Original image pixels were compared and
remain identical. APK source-control and dependency metadata are disabled.
The manifest is non-debuggable, has no location permission, and disables backup.
Wallet data is created on each device; it is not copied from the emulator into
this APK. BIP39's public word dictionary is not a private recovery phrase.

The release certificate's only identity field is `CN=BTCW Wallet`. Its public
certificate and public-key fingerprint are necessarily included in a signed APK;
they identify this publisher's key, not a personal name or location. File-system
ownership on the development Mac is not part of the APK contents.

This is a scoped artifact privacy review, not proof of anonymity or an independent
wallet security/consensus audit. It does not certify the wallet for production
funds. Existing SPV, chain-selection, recovery and testing limitations are listed
in SPV.md and WALLET.md. Network peers can observe clients' connecting IP addresses;
that is runtime network behavior, not a developer IP embedded in the APK.

## Preserve the signing identity

The locally generated key is `.release-signing/btcw-release.p12`; its random
password is in `.release-signing/release-password.txt`. Both have owner-only file
permissions and are excluded from Git. Back up both privately and securely. Never
publish them. Future updates must use the same key and a higher versionCode.
Losing the key can prevent updates to this installed app.

This release uses a different key from Android's development debug certificate.
Do not uninstall a saved debug wallet to replace it without a recovery backup.
The release artifact was not installed over the existing emulator wallet.

## Repeat the build and audit

Set JAVA_HOME to a compatible JDK and ANDROID_HOME to the Android SDK, then run:

```
tools/package-release.sh
```

The script builds with release lint enabled, audits, aligns, signs with the existing
key, verifies the signature, audits the signed APK, and writes the checksum and
review output under `dist/`. It does not upload or publish anything.
Update the version in app/build.gradle.kts and the output filename in the script
before producing a later release. Review newly added code/assets and extend the
privacy checks as needed; string matching cannot identify every possible secret.
