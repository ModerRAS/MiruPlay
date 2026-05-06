// Convention plugin for Android application modules
// Inherits common configuration from miruplay.android.library

// Since Gradle convention plugins can't inherit like class inheritance,
// we define the application plugin to apply the library plugin first,
// then add application-specific configuration.

plugins {
    id("com.android.application")
}

// Apply common Android configuration from library convention
// Note: modules using this plugin should also apply miruplay.android.library
// or include the common settings here directly.

android {
    compileSdk = 35
    namespace = "${rootProject.extra["PROJECT_NAMESPACE"] ?: "com.miruplay.tv"}"
    defaultConfig {
        applicationId = "com.miruplay.tv"
        versionCode = 1
        versionName = "0.1.0"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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