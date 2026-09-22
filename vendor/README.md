# Archived build inputs

- `gradle-9.6.0/`: the complete installed Gradle binary distribution, copied from
  the distribution used by this project. Its `LICENSE`, `NOTICE`, and bundled
  license files are retained.
- `maven/`: Maven-layout snapshot of the developer machine's Gradle dependency
  artifact cache. This includes POM and Gradle module metadata, binaries, and
  cached sources/Javadoc. It may include extra versions beyond those selected by
  this build. Cached artifacts originated from the previously configured Google
  Maven, Maven Central, and Gradle Plugin Portal repositories.
- Linux and Windows AAPT2 9.4.1-15978811 JARs were additionally retrieved from
  `https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.4.1-15978811/`.
- `SHA256SUMS`: SHA-256 hashes of all archived files, relative to the project root
  (excluding this checksum file itself).

Gradle module metadata sometimes refers to a Maven URL filename different from
the cached filename. Both names are retained where necessary; these are copies
of the same artifact, not modified third-party binaries.

The original binaries and their embedded license/notice files are preserved.
POM files retain upstream licensing and project information. Each component
remains under its own license. Cached source archives are included where present;
this is not a complete source archive for all dependencies or a license audit.

Do not regenerate this directory by copying a whole Gradle user home: that can
include machine paths, credentials, logs, init scripts, and disposable caches.
Only public artifact files and the Gradle distribution belong here.
