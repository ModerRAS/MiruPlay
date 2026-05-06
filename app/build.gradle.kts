plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.miruplay.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.miruplay.tv"
        versionCode = 1
        versionName = "0.1.0"
        minSdk = 24
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":ui-tv"))
    implementation(project(":data"))
    implementation(project(":player-core"))
    implementation(project(":scraper"))
    
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    
    testImplementation(libs.junit)
}
