plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.ecohome.viewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.ecohome.viewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("com.google.android.filament:filament-android:1.57.1")
    implementation("com.google.android.filament:gltfio-android:1.57.1")
    implementation("com.google.android.filament:filament-utils-android:1.57.1")
}
