# BTCW Android wallet

An Android wallet for BTCW (BitcoinPoW), with wallet creation and recovery,
receiving addresses, transaction sending, and direct peer synchronization.
Requires Android **8.0 or later** and an enrolled strong biometric, such as a
fingerprint.

## Features

- **Create or restore a wallet:** generate a 12-word recovery phrase, or restore
  an English BIP39 phrase with an optional passphrase.
- **Receive BTCW:** display a receiving address as a QR code or copy it to share.
- **Send BTCW:** enter an address, amount, and fee rate; review the destination,
  fee, total, and change before authenticating to sign.
- **Track transactions:** distinguish queued sends, delivery to a peer, and block
  confirmation. Pending sends and scan progress persist across app restarts.
- **Synchronize with BTCW peers:** download and verify headers and scan blocks
  while the app is open, with progress saved for the next session. Settings
  includes a rescan option starting at block 141000.
- **Protect wallet access:** encrypt the seed using Android Keystore and require
  strong biometric authentication. Backgrounding locks the wallet; screenshots
  and Android backup of wallet data are disabled.

Keep the recovery phrase and any passphrase backed up offline: the phrase cannot
be displayed again after setup, and there is no PIN fallback. Use a phrase
dedicated to BTCW. Address rotation, additional accounts, and `wallet.dat` import
are not implemented. Scans discover outputs from block 141000 onward; mining and
coinstake outputs cannot be spent by this app.

This is an experimental wallet with lightweight verification, not a full node or
an independently audited production wallet. Real-fund transfers and node mempool
acceptance have not been tested. See [wallet behavior and recovery](docs/WALLET.md)
and [synchronization limitations](docs/SPV.md) for details.

## Build after downloading from GitHub

Clone the repository or download its source ZIP and extract it first. Keep the
entire extracted project together, including `vendor/`. The **project root** is
the folder containing `settings.gradle.kts`, `gradlew`, `app/`, and `vendor/`.
Its name may depend on the repository and branch you downloaded.

### Prerequisites

Install these before building:

- **JDK 25.** On macOS, `./gradlew` automatically uses Android Studio's bundled
  JDK from `/Applications` or `~/Applications` when `JAVA_HOME` is unset. Check
  that the bundled JDK is version 25. On other systems, or with a different Studio
  installation location, set `JAVA_HOME` to a JDK 25 installation.
- **Android SDK platform 37** (`platforms;android-37`) and **Build Tools 36.0.0**
  (`build-tools;36.0.0`), with their SDK licenses accepted. Install these through
  Android Studio's SDK Manager. Let Studio configure the project's SDK location,
  or set `ANDROID_HOME` to your SDK directory for command-line builds.

Java and the Android SDK are not included in the repository. Automatic JDK and
SDK downloads by the build are disabled.

### Open and configure Android Studio

1. Choose **Open** and select the **project root**, not `app/` or its parent folder.
2. Open **Settings → Build, Execution, Deployment → Build Tools → Gradle**.
3. Select a **local Gradle installation**. Set **Gradle home** to the project's
   `vendor/gradle-9.6.0` folder. For example, if you extracted the project into
   `~/Downloads/AndroidWallet`, choose
   `~/Downloads/AndroidWallet/vendor/gradle-9.6.0`. Select that folder itself,
   not its `bin` or `lib` subfolder.
4. Set **Gradle JDK** separately to **JDK 25** and enable **Gradle offline mode**.
   This project uses a customized launcher, so do not select a downloading wrapper.
5. Choose **File → Sync Project with Gradle Files** and wait for sync to finish.

The SDK path in `local.properties` is specific to your computer and is not
committed. Android Studio is optional if you prefer command-line builds.

### Build a debug APK

Open Android Studio's **Terminal** or your system terminal. Change into the
project root first. For example, if you extracted it into `~/Downloads/AndroidWallet`:

```sh
cd ~/Downloads/AndroidWallet
./gradlew assembleDebug
```

Replace the example folder with your actual location. Copy only the commands,
not a terminal prompt such as `username@computer folder %` or previous error output.

On Windows Command Prompt, change into the extracted project folder and run:

```bat
gradlew.bat assembleDebug
```

Wait for **BUILD SUCCESSFUL**. The installable debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can run the terminal command even if Android Studio's Gradle panel only
lists `testDebugUnitTest` and does not show `assembleDebug`. To install and launch
from Studio, select the **app** run configuration and a connected Android device
or emulator, then click **Run ▶**. Wallet setup requires a strong biometric.

### Common build problems

- **`no such file or directory: ./gradlew`:** you are probably in the wrong
  directory. Change into the folder containing `gradlew`, then run the command again.
- **`command not found: username@...` or `command not found: zsh:`:** terminal
  prompts or error messages were pasted as commands. Enter only `./gradlew assembleDebug`.
- **`Unable to locate a Java Runtime`:** ensure JDK 25 is installed. The macOS
  launcher detects Android Studio in the locations above; otherwise set
  `JAVA_HOME` to your JDK directory. An explicitly set `JAVA_HOME` takes precedence.
- **`Permission denied: ./gradlew`:** on macOS/Linux, run `chmod +x gradlew`, then
  retry the build.
- **Sync or SDK errors:** check the local Gradle home, JDK 25, SDK platform 37,
  and Build Tools 36.0.0 settings above. If sync reports an unsupported Android
  Gradle Plugin version, use an Android Studio version supporting this project's
  Android Gradle Plugin **9.4.1**.

### Build a release APK

From the project root on macOS/Linux:

```sh
./gradlew assembleRelease
```

On Windows Command Prompt:

```bat
gradlew.bat assembleRelease
```

This produces `app/build/outputs/apk/release/app-release-unsigned.apk`. It must
be signed before people can install it. No private release key or password is
included in the GitHub source download. Use your own signing key for your builds
and keep it for future updates.

Maintainers with the existing release key can follow the
[release packaging instructions](docs/RELEASE.md) to create the signed
`dist/BTCW-1.0.apk` and its checksum for a GitHub Release attachment. Do not upload
the signing key or password. A debug APK is for development; `assembleRelease`
alone does not produce the signed distribution APK.

## Build without external repositories

This checkout includes Gradle 9.6.0 and an on-disk Maven repository in `vendor/`.
Build plugins, app libraries, test libraries, and their cached metadata are archived
as actual files, not Git LFS pointers or download links. The build does not use
Google Maven, Maven Central, the Gradle Plugin Portal, or a JDK download service.

These launchers run the included Gradle distribution directly and automatically
pass `--offline`. They are customized launchers, not the downloading Gradle wrapper.
The settings only permit the local `vendor/maven` repository. Automatic JDK and
SDK downloads are disabled. Missing prerequisites or archived artifacts cause a
build failure instead of a network download.

## Offline verification

The debug and release APKs, unit tests, and Android test APK were built on macOS
with an empty Gradle user home, an empty project cache, and build/configuration
caches disabled. No existing user dependency cache was needed. The Android test
APK was compiled; device tests are a separate step requiring a device or emulator.
Linux and Windows AAPT2 binaries are archived too, but builds on those operating
systems have not been tested here.

To repeat the verification on macOS/Linux, choose a new, empty directory:

```sh
GRADLE_USER_HOME="$PWD/.gradle-offline-check-new" ./gradlew \
  --no-daemon --no-build-cache --no-configuration-cache \
  --project-cache-dir .gradle-offline-check-new/project-cache \
  clean assembleDebug assembleRelease testDebugUnitTest assembleDebugAndroidTest
```

Disconnect networking during that check for an independent air-gap test. The
automated check above uses Gradle offline mode; it does not switch off the host's
network interface. The running wallet still needs peers to synchronize and
broadcast transactions.

## Publish and preserve

Read [the source privacy checklist](docs/SOURCE-PRIVACY.md) before publishing.

Commit **the entire `vendor/` directory** together with the source and build files.
It is approximately 676 MB unpacked. Every individual file is below 100 MiB, so
the archive can be stored as ordinary Git files. Avoid replacing it with Git LFS
pointers, submodules, or links to another hosting service: a complete clone or
GitHub source ZIP should contain the actual build inputs.

Keep `.gitignore` exclusions for SDK paths, build output, local Gradle caches,
and `.release-signing/`. Keep `.gitattributes` so checkout line-ending conversion
does not alter archived files. `vendor/SHA256SUMS` records archive checksums;
on macOS run `shasum -a 256 -c vendor/SHA256SUMS` from the project root.

When upgrading dependencies, archive their artifacts and metadata before removing
the old versions, update checksums, and repeat the empty-cache offline build.
The archive preserves compiled build inputs; it is not a guarantee that every
third-party library can itself be rebuilt from source. Preserve independent clones
or ZIP backups as well if you also want protection against GitHub disappearing.
