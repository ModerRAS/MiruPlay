plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.miruplay.tv.metadata"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
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
    api(project(":data"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
