plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miruplay.tv.background"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(libs.dagger.hilt.android)

    testImplementation(libs.junit)
}
