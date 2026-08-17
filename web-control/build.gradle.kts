plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val webControlFrontendDir = layout.projectDirectory.dir("frontend")
val webControlFrontendAssetsRoot = layout.buildDirectory.dir("generated/web-control-assets")
val bunExecutable = providers.gradleProperty("miruplay.bunExecutable").orElse("bun")

val installWebControlFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs WebControl frontend dependencies."

    workingDir = webControlFrontendDir.asFile
    executable = bunExecutable.get()
    args("install", "--frozen-lockfile")

    inputs.file(webControlFrontendDir.file("package.json"))
    inputs.file(webControlFrontendDir.file("bun.lock"))
    outputs.dir(webControlFrontendDir.dir("node_modules"))
}

val buildWebControlFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds WebControl frontend assets for Android packaging."

    dependsOn(installWebControlFrontend)
    workingDir = webControlFrontendDir.asFile
    executable = bunExecutable.get()
    args("run", "build")

    inputs.file(webControlFrontendDir.file("package.json"))
    inputs.file(webControlFrontendDir.file("bun.lock"))
    inputs.file(webControlFrontendDir.file("index.html"))
    inputs.file(webControlFrontendDir.file("vite.config.js"))
    inputs.dir(webControlFrontendDir.dir("src"))
    outputs.dir(webControlFrontendAssetsRoot)
}

android {
    namespace = "com.miruplay.tv.webcontrol"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(webControlFrontendAssetsRoot)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(buildWebControlFrontend)
}

tasks.matching { it.name.startsWith("package") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(buildWebControlFrontend)
}

tasks.matching { it.name.startsWith("lint") }.configureEach {
    dependsOn(buildWebControlFrontend)
}

dependencies {
    api(project(":web-control-core"))
    implementation(project(":audio-dsp-core"))
    implementation(project(":background-task"))
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":repository-api"))
    implementation(project(":cloud-drive"))
    implementation(project(":player-core"))
    implementation(project(":scanner"))
    implementation(project(":media-source-api"))
    implementation(project(":scraper-core"))
    implementation(project(":sync-engine"))
    implementation(project(":translation"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.dagger.hilt.android)
    api(libs.nanohttpd)
    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
}
