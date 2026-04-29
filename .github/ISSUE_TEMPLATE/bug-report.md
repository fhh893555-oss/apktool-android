---
name: Bug report
about: Report a problem with apktool-android (AAR / Android integration)
title: "[BUG] "
labels: ''
assignees: ''

---

### Environment
1. **apktool-android version** (release tag, AAR name, or `git rev-parse HEAD` if built from source) –
2. **Integration** — how you consume the library:
   - [ ] AAR from GitHub Releases + `collectRuntimeJars` bundle ([USAGE.md](https://github.com/anirudhmalik/apktool-android/blob/main/USAGE.md#gradle-integration))
   - [ ] Maven / internal artifact with transitives
   - [ ] Composite build / `project(":brut.apktool:apktool-android")`
3. **Device / emulator** — model (or AVD image), **API level**, **ABI** (`arm64-v8a`, `armeabi-v7a`, `x86_64`) –
4. **Host app** — `minSdk`, `compileSdk`, AGP version if relevant –
5. **APK under test** — source (Play Store, sideload, fixture); **size** approximate –

### What happened
Short description of incorrect behaviour vs what you expected.

### Logcat / stack trace

```
Paste logcat or exception (decode/build phases, aapt2 errors, OOM, etc.)
```

### Steps to reproduce
1. …
2. …

Minimal **Kotlin/Java** snippet if the bug is in configuration or calling `ApkDecoder` / `ApkBuilder` / `ApktoolAndroid.configure` helps.

### Attachments (if applicable)
- **APK** — link or attach only if redistribution is allowed.
- **Framework files** — for OEM/system APK issues: relevant `framework-res.apk` / `-apks` from `/system/framework` (if you can share).
- **Decoded tree** — e.g. `apktool.yml` + failing resource snippet (no need for full dump).

### Before submitting
1. Does the same APK **decode/rebuild on desktop** [iBotPeaches/Apktool](https://github.com/iBotPeaches/Apktool) CLI with a recent 3.x build? If yes, say so; if no, it may be an **upstream Apktool** issue.
2. Are you on the **latest release** / matching **runtime JAR bundle** for that AAR (see [USAGE.md](https://github.com/anirudhmalik/apktool-android/blob/main/USAGE.md))?
3. For large APKs: did you set **`android:largeHeap="true"`** and run work **off the main thread**?
4. For `libaapt2.so` / exec failures: does the host use **`packaging { jniLibs { useLegacyPackaging = true } }`**?
