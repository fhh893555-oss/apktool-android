# apktool-android

[![Android CI](https://github.com/anirudhmalik/apktool-android/actions/workflows/android.yml/badge.svg)](https://github.com/anirudhmalik/apktool-android/actions/workflows/android.yml)
[![Release](https://github.com/anirudhmalik/apktool-android/actions/workflows/release.yml/badge.svg)](https://github.com/anirudhmalik/apktool-android/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](LICENSE.md)

**apktool-android** is an Android library (AAR) that runs Apktool’s decode → edit project tree → rebuild flow **inside your app process**. You get a normal resource/smali workspace on disk and a rebuilt APK without relying on a desktop CLI, Termux, `adb shell`, or root. The AAR ships per-ABI **`libaapt2.so`** (fully static, NDK-built) and a small entry point—`ApktoolAndroid.configure(Context)`—that points Apktool at executable-safe paths on Android (`nativeLibraryDir`, app `filesDir` / `cacheDir`).

## Scope

| In scope | Out of scope |
|----------|----------------|
| In-app decode/build using `ApkDecoder` / `ApkBuilder` with Android-safe `Config` | Installing or documenting the desktop **apktool** JAR/CLI workflow |
| Bundled `aapt2` as `libaapt2.so` + optional self-build docs under `aapt2-build/` | Fork maintenance of upstream Apktool feature roadmaps |

## Documentation

| Doc | Purpose |
|-----|---------|
| **[USAGE.md](USAGE.md)** | Android integration: Gradle, manifest, `ApktoolAndroid`, full `Config` reference, threading, heap, releases |
| **[aapt2-build/README.md](aapt2-build/README.md)** | Rebuild and install `libaapt2.so` for `jniLibs/`; output contract; licence notes |
| **[aapt2-build/SELF_BUILD_PLAN.md](aapt2-build/SELF_BUILD_PLAN.md)** | Self-build strategy, patch stack, and historical runbook |
| **[aapt2-build/patches/README.md](aapt2-build/patches/README.md)** | Canonical vs recipe-local patches; re-export procedures |

## Build (library AAR)

```sh
./gradlew :brut.apktool:apktool-android:assembleRelease
# Output: brut.apktool/apktool-android/build/outputs/aar/apktool-android-release.aar
```

> **Note:** If you drop only that AAR into another app, you must also vendor the runtime JARs from `./gradlew :brut.apktool:apktool-android:collectRuntimeJars` — see [USAGE.md — Gradle integration](USAGE.md#gradle-integration).

For JVM/unit tests on `apktool-lib` and instrumented tests, see [Building from source in USAGE.md](USAGE.md#building-from-source).

## Acknowledgments

- **[iBotPeaches/Apktool](https://github.com/iBotPeaches/Apktool)** — The resource table, smali/DEX, and rebuild pipeline live in `apktool-lib` and come from the main Apktool project. **apktool-android** only adds the Android packaging, paths, and `aapt2` binary story.
- **[ReVanced/aapt2](https://github.com/ReVanced/aapt2)** — The cross-compile recipe (CMake, AOSP submodule pin, patch flow) is vendored under `aapt2-build/recipe/` and extended as documented in `aapt2-build/README.md`.
- **Critical `aapt2` patches (authors)** — We keep canonical diffs under `aapt2-build/patches/` for audit and attribution:
  - [**0001** — Apktool port changes for custom `aapt2` usage](aapt2-build/patches/0001-aapt2-port-changes-for-apktool.patch) — **[Connor Tumbleson](https://github.com/iBotPeaches)**. Documents the baseline leniency/linking work for Apktool-driven `aapt2`; the recipe build applies ReVanced’s `apktool_ibotpeaches.patch`, which is a **superset** of this file (see [patches/README.md](aapt2-build/patches/README.md)).
  - [**0002** — Unrestrict value format for `<item>` tags](aapt2-build/patches/0002-aapt2-unrestrict-item-tag-value-format.patch) — **[Igor Eisberg](https://github.com/IgorEisberg)** (patch author; merged on the Apktool side as **PR #3130**). Lets `aapt2` accept modern `<item type="layout" format="…">` patterns so Play-Store–style APKs rebuild without `invalid value for type 'layout'. Expected a reference.` A re-rooted copy is applied on top of ReVanced’s stack in the live build ([patch stack](aapt2-build/README.md#patch-stack)).

## License

Apache-2.0 — see [LICENSE.md](LICENSE.md). The vendored ReVanced build scripts under `aapt2-build/recipe/` are GPL-3.0 (their `LICENSE` is preserved). The shipped `libaapt2.so` binaries and the AAR are **not** GPL-3.0; see [Licence story in aapt2-build/README.md](aapt2-build/README.md#licence-story).
