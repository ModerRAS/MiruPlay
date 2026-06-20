plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miruplay.tv.player.mpv.android"
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core)
}
