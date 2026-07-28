plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miruplay.tv.player.ijk.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(files("libs/ijkplayer-classes.jar"))
    implementation(libs.androidx.core)
    testImplementation(libs.junit)
}
