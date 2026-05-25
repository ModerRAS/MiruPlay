plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

project.extra.set("pureKotlin", true)

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":cloud-drive-api"))
    api(project(":repository-api"))
    api(project(":media-source-api"))
    api(project(":sync-engine-shared"))
    api(libs.nanohttpd)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
