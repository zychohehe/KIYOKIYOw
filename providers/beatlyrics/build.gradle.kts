// Lyrics sources ported from Beatcord (GPL-3.0). See NOTICE.md in this folder for the credit.
plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.beatlyrics"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // these two bring OkHttp, kotlinx.coroutines and kotlinx.serialization along, same as the other providers
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    // Genius, Musixmatch and Megalobiz read web pages
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))

    coreLibraryDesugaring(libs.desugaring)
}
