plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":media-source-api"))
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.jcifs.ng)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    listOf(
        "miruplay.smbLiveUrl",
        "miruplay.smbLiveUsername",
        "miruplay.smbLivePassword",
        "miruplay.smbLiveDomain",
        "miruplay.smbLiveExpectedName",
    ).forEach { propertyName ->
        System.getProperty(propertyName)?.let { value ->
            systemProperty(propertyName, value)
        }
    }
}
