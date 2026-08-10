plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 支持通过 -PVERSION_NAME / -PVERSION_CODE 显式传入版本信息。
// 未显式传入 VERSION_NAME 时，默认把最后一段 patch 替换为 BUILD_NUMBER。
val baseAppVersionName = "2.6.0"

fun String?.nonBlankOrNull(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

fun String?.numericBuildNumberOrNull(): String? =
    nonBlankOrNull()?.takeIf { it.all(Char::isDigit) }

fun versionNameWithBuildNumber(baseVersionName: String, buildNumber: String): String {
    val parts = baseVersionName.trim().ifBlank { "0.1.0" }.split(".")
    val normalizedParts = if (parts.size >= 3) parts.dropLast(1) else parts
    return (normalizedParts + buildNumber).joinToString(".")
}

val appBuildNumber = providers.gradleProperty("BUILD_NUMBER").orNull.numericBuildNumberOrNull()
    ?: providers.environmentVariable("BUILD_NUMBER").orNull.numericBuildNumberOrNull()
    ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull.numericBuildNumberOrNull()
    ?: "0"
val appVersionName = providers.gradleProperty("VERSION_NAME").orNull.nonBlankOrNull()
    ?: providers.environmentVariable("VERSION_NAME").orNull.nonBlankOrNull()
    ?: versionNameWithBuildNumber(baseAppVersionName, appBuildNumber)
val appVersionCode = (
    providers.gradleProperty("VERSION_CODE").orNull.numericBuildNumberOrNull()
        ?: providers.environmentVariable("VERSION_CODE").orNull.numericBuildNumberOrNull()
        ?: appBuildNumber
    ).toIntOrNull()
    ?.takeIf { it > 0 }
    ?: 1
val appApplicationId = providers.gradleProperty("APPLICATION_ID").orNull.nonBlankOrNull()
    ?: "com.miruplay.tv"
val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD")

android {
    namespace = "com.miruplay.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = appApplicationId
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
        debug {
        }
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
    implementation(project(":background-task"))
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
    implementation(libs.androidx.work.runtime.ktx)
    
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
}
