plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.player.core"
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
    implementation(project(":core:common"))
    implementation(project(":repository-api"))
    implementation(project(":player-mpv-android"))
    api(project(":media-source"))
    api(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.effect)
    api(libs.androidx.media3.ui)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(libs.robolectric)
}
