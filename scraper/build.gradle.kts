plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.scraper"
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
    api(project(":media-source"))
    implementation(project(":data"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
}
