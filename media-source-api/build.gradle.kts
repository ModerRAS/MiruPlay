plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

project.extra.set("pureKotlin", true)

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
}
