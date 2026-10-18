plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
