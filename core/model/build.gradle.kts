plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.miruplay.tv.core.model"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
}

project.extra.set("pureKotlin", true)

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
