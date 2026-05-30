plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.sync.engine"
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
    implementation(project(":background-task"))
    api(project(":repository-api"))
    api(project(":metadata"))
    api(project(":sync-engine-shared"))
    implementation(project(":cloud-drive-api"))
    implementation(project(":cloud-drive"))
    implementation(project(":scanner"))
    implementation(project(":scraper"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
}
