plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "brut.androlib.android.test"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }

    androidResources {
        // Test fixtures (sample.apk, modern.apk) are already-compressed ZIPs.
        // Letting AGP's CompressAssetsTask DEFLATE them again is pure waste --
        // it can't make them smaller and pushes a multi-MB byte[] through
        // zipflinger, which OOMs the worker on 100 MB+ fixtures. STORE them.
        noCompress += setOf("apk")
    }

    packaging {
        // Strip desktop aapt2 prebuilts that come transitively from apktool-lib;
        // they are dead weight on the test APK.
        resources.excludes += setOf(
            "prebuilt/linux/**",
            "prebuilt/macosx/**",
            "prebuilt/windows/**"
        )

        // libaapt2.so is an executable, not a JNI library. Force legacy
        // packaging so AGP emits android:extractNativeLibs="true" and the
        // installer materialises the .so on disk under nativeLibraryDir,
        // where ProcessBuilder can exec() it. Without this AGP stores it
        // uncompressed and maps it directly from the APK -- fine for
        // System.loadLibrary, fatal for exec().
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":brut.apktool:apktool-android"))

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
