plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.scanner"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":media-source"))
    api(project(":repository-api"))
    api(project(":metadata"))
    implementation(project(":scraper"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
}
