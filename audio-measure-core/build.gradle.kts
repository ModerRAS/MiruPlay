plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Reuse BiquadDesigner so fitted bands need zero coefficient translation.
    api(project(":audio-dsp-core"))
    testImplementation(libs.junit)
}
