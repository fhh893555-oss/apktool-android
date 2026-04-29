# androidTest assets

The instrumented tests in
[`ApktoolInstrumentedTest`](../java/brut/androlib/android/ApktoolInstrumentedTest.java)
read two APKs from this directory:

| Asset        | Used by                                                  | How to produce                                                                                          |
|--------------|----------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `sample.apk` | `aapt2VersionExecutes()`, `decodeAndRebuild()`           | Build the test fixture in `brut.apktool/apktool-lib/src/test/resources/testapp` using a desktop `aapt2` and copy the resulting APK here. |
| `modern.apk` | `featureFlagsBuild()` (test is a no-op if absent)         | Copy any current Play-Store APK that uses sparse-encoded `resources.arsc` and feature-flag metadata.    |

## Producing `sample.apk`

The fastest path is to let the existing JVM tests build the fixture once
and reuse the artifact:

```sh
# from repo root
./gradlew :brut.apktool:apktool-lib:test --tests brut.androlib.BuildAndDecodeApkTest

# the fixture is rebuilt under build/test/testapp.tmp/<...>.apk -- copy it.
cp brut.apktool/apktool-lib/build/test/.../testapp.apk \
   brut.apktool/apktool-android-test/src/androidTest/assets/sample.apk
```

If you'd rather avoid the testapp dependency, any small APK that aapt2 will
both decode and rebuild works (Apktool's own historical sample APKs are
fine).

## Why these aren't checked in

Sample APKs under MIT/AOSP licensing are fine to vendor, but Play-Store APKs
(`modern.apk`) are not. Keeping them out of the tree also stops the working
copy from bloating with binaries that have to be rebuilt every time the
testapp fixture changes.

The CI workflow in [.github/workflows/android.yml](../../../../../../.github/workflows/android.yml)
generates `sample.apk` on the fly and skips `featureFlagsBuild()` unless
the workflow is invoked with `modern.apk` provided as an artifact input.
