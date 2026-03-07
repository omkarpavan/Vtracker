plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.vtracker"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.vtracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    implementation("com.airbnb.android:lottie:6.4.1")
    implementation("com.android.volley:volley:1.2.1")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // GIF support
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")

    // FIX 6: Updated Security-Crypto to the stable version
    implementation("androidx.security:security-crypto:1.0.0")

    // FIX 7: Updated Core-KTX to a recent stable version
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.0.0")

    androidTestImplementation(libs.espresso.core)
}