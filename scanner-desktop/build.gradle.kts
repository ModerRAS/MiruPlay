plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":repository-api"))
    api(project(":media-source-api"))
    api(project(":metadata-core"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":media-source-desktop"))
    testImplementation(libs.junit)
}
