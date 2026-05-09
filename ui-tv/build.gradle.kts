plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miruplay.tv.ui.tv"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    buildFeatures {
        compose = true
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
    api(project(":player-core"))
    api(project(":media-source"))
    api(project(":scanner"))
    implementation(project(":data"))
    
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    
    implementation(libs.dagger.hilt.android)
    implementation(libs.dagger.hilt.navigation.compose)
    ksp(libs.dagger.hilt.compiler)
    
    testImplementation(libs.junit)
    testImplementation(libs.androidx.testing.compose)
}
