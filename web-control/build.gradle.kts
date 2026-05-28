plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.webcontrol"
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
    api(project(":web-control-core"))
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":repository-api"))
    implementation(project(":cloud-drive"))
    implementation(project(":player-core"))
    implementation(project(":scanner"))
    implementation(project(":media-source-api"))
    implementation(project(":scraper-core"))
    implementation(project(":sync-engine"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.dagger.hilt.android)
    api(libs.nanohttpd)
    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
}
