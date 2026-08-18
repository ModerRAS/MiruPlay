plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.miruplay.tv.translation"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(project(":repository-api"))
    implementation(project(":scraper-core"))
    implementation(project(":player-core"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}
