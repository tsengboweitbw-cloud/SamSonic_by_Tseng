plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}
android {
    namespace = "com.example.samsonic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.samsonic"
        minSdk = 31
        targetSdk = 37
        versionCode = 15
        versionName = "2.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The USB DAC driver is native code; every phone it targets is 64-bit ARM.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    // -PnativeSanitize=hwasan builds the native code with HWAddressSanitizer, for a debug
    // app on a phone with Android 14 or later (see app/src/hwasan). Off unless asked for.
    if (project.findProperty("nativeSanitize") == "hwasan") {
        defaultConfig.externalNativeBuild.cmake.arguments += "-DSAMSONIC_HWASAN=ON"
        sourceSets.getByName("debug").resources.srcDir("src/hwasan/resources")
        // wrap.sh runs from the extracted libraries.
        packaging.jniLibs.useLegacyPackaging = true
    }
    buildTypes {
        release {
            optimization {
                keepRules {
                    files.add(getDefaultProguardFile("proguard-android-optimize.txt"))
                    files.add(file("proguard-rules.pro"))
                }
                enable = true
            }
        }
        // Its own app id, so it installs beside the release app (signed with another key)
        // instead of over it, and keeps its own servers and settings. Named "Samsonic Dev".
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Non-debuggable, debug-signed build for judging animation smoothness on a
        // device: debug builds run Compose largely interpreted, so they stutter
        // the first time each screen is opened. Installs over the debug build.
        create("perfTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-perfTest"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Lists the app's languages (English, Traditional Chinese) for Android 13+'s per-app
    // language setting, from the values-* folders and res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }
    // Lint's existing findings are in the baseline, so only new ones fail the build.
    lint {
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        // Media3's exception classes read the clock, which android.jar stubs out.
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        // Settings' About row shows versionName from it.
        buildConfig = true
    }
}

androidComponents {
    // The baseline profile plugin's build for recording copies release, which shrinks with
    // the optimization setting above. Its names must stay readable, so turn that off.
    beforeVariants(selector().withBuildType("nonMinifiedRelease")) {
        it.isMinifyEnabled = false
        it.shrinkResources = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}