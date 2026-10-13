plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")   // reads app/google-services.json
}

android {
    namespace = "com.tele.child"           // code package (R class, view binding); independent of applicationId
    compileSdk = 34

    defaultConfig {
        // Must match the package_name in app/google-services.json.
        applicationId = "sudifenty.com.calculetor"
        minSdk = 26            // Android 8.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Keep only the ABIs used by real phones (smaller download and install).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Release signing. The key lives outside the repo; the build reads it from environment variables
    // (see the release build script). Without them, the release APK is built unsigned.
    signingConfigs {
        create("release") {
            System.getenv("TELE_KEYSTORE")?.let { ks ->
                storeFile = file(ks)
                storePassword = System.getenv("TELE_KS_PASS")
                keyAlias = System.getenv("TELE_KEY_ALIAS")
                keyPassword = System.getenv("TELE_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("TELE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true      // R8 code shrinking (release builds only)
            isShrinkResources = true    // drop unused resources (requires minify)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
