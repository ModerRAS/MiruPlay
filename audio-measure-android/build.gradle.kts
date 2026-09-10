plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miruplay.tv.audiomeasure"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":audio-measure-core"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
