plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.samsonic.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Recording skips the benchmark, but this phone reports the skip as a failure and
        // Gradle then keeps the profile out of the project. Run only the recorder.
        if (gradle.startParameter.taskNames.any { "generate" in it && "BaselineProfile" in it }) {
            testInstrumentationRunnerArguments["class"] =
                "com.example.samsonic.baselineprofile.BaselineProfileGenerator"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

// Runs on a connected phone: ./gradlew :app:generateBaselineProfile
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
