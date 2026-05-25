plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 支持通过 -PVERSION_NAME 和 -PVERSION_CODE 传入版本信息
val appVersionName = if (project.hasProperty("VERSION_NAME")) project.property("VERSION_NAME") as String else "0.1.0"
val appVersionCode = if (project.hasProperty("VERSION_CODE")) (project.property("VERSION_CODE") as String).toInt() else 1
val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD")

android {
    namespace = "com.miruplay.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.miruplay.tv"
        versionCode = appVersionCode
        versionName = appVersionName
        minSdk = 28
        targetSdk = 35

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    lint {
        disable += "Instantiatable"
        checkReleaseBuilds = true
    }
    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile.orNull?.let { rootProject.file(it) }
            storePassword = releaseStorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":ui-design"))
    implementation(project(":ui-tv"))
    implementation(project(":repository-api"))
    implementation(project(":media-source"))
    implementation(project(":data"))
    implementation(project(":player-core"))
    implementation(project(":scraper"))
    implementation(project(":sync-engine"))
    implementation(project(":web-control"))
    
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    
    testImplementation(libs.junit)
}
