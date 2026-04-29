plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "brut.androlib.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        // Only ship ABIs we actually build aapt2 for.
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

    packaging {
        // Desktop aapt2 prebuilts come in via apktool-lib resources but are
        // useless on Android. Strip them so the AAR isn't bloated by ~6 MB.
        resources.excludes += setOf(
            "prebuilt/linux/**",
            "prebuilt/macosx/**",
            "prebuilt/windows/**"
        )

        // libaapt2.so is an executable native binary, not a normal .so. Make
        // sure AGP keeps it in the AAR and doesn't strip it into a debug-only
        // bundle.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    api(project(":brut.apktool:apktool-lib"))
}

tasks.register<Copy>("collectRuntimeJars") {
    group = "build"
    description = "Copies the AAR's full runtime classpath jars into build/libs-bundle/ for fileTree consumption by Android host apps."
    dependsOn("assembleRelease")
    from(configurations.named("releaseRuntimeClasspath")) {
        include("*.jar")
        // Android already provides org.xmlpull.v1.* via android.jar; bundling
        // xmlpull again triggers duplicate-class dexer errors.
        exclude("xmlpull-*.jar")
    }
    into(layout.buildDirectory.dir("libs-bundle"))
}
