# BTCW

## Build without external repositories

This checkout includes Gradle 9.6.0 and an on-disk Maven repository in `vendor/`.
Build plugins, app libraries, test libraries, and their cached metadata are archived
as actual files, not Git LFS pointers or download links. The build does not use
Google Maven, Maven Central, the Gradle Plugin Portal, or a JDK download service.

Install these prerequisites yourself before disconnecting:

- JDK **25**; set `JAVA_HOME` to its installation directory.
- Android SDK with **platforms;android-37** and **build-tools;36.0.0** installed
  and the applicable SDK licenses accepted; set `ANDROID_HOME` to the SDK directory.
- A supported host OS and its normal command-line environment. Android Studio is
  optional for command-line builds.

From the project directory on macOS or Linux:

```sh
./gradlew clean assembleDebug assembleRelease
```

On Windows Command Prompt:

```bat
gradlew.bat clean assembleDebug assembleRelease
```

These launchers run the included Gradle distribution directly and automatically
pass `--offline`. They are customized launchers, not the downloading Gradle wrapper.
The settings only permit the local `vendor/maven` repository. Automatic JDK and
SDK downloads are disabled. Missing prerequisites or archived artifacts cause a
build failure instead of a network download.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The release APK is
`app/build/outputs/apk/release/app-release-unsigned.apk`; signing your own release
requires your own signing key. No private release key is included.

For Android Studio, select a **local Gradle installation** at
`vendor/gradle-9.6.0` in the project's Gradle settings, choose JDK 25, and enable
Gradle offline mode. Do not configure a downloading wrapper. A developer's
`local.properties` may specify their SDK location; it is not committed.

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

There is no Git repository initialized in this supplied workspace. To publish,
create an empty GitHub repository, initialize Git here, add and review the files,
commit, add your GitHub repository as `origin`, and push. A repository URL and
credentials are needed for that final publishing step.

When upgrading dependencies, archive their artifacts and metadata before removing
the old versions, update checksums, and repeat the empty-cache offline build.
The archive preserves compiled build inputs; it is not a guarantee that every
third-party library can itself be rebuilt from source. Preserve independent clones
or ZIP backups as well if you also want protection against GitHub disappearing.
