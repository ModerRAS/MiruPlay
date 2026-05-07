plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.scanner"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":media-source"))
    api(project(":data"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
}
