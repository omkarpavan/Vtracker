plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.vtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vtracker"
        minSdk = 26
        targetSdk = 35
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
    implementation(libs.swiperefreshlayout)
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

    // Security-Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Core-KTX
    implementation("androidx.core:core-ktx:1.13.1")

    androidTestImplementation(libs.espresso.core)
}