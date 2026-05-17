import java.io.ByteArrayOutputStream

plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt")
}

val preparedMpvRuntime = layout.buildDirectory.dir("prepared-mpv-runtime")
val mpvRuntimeSource = providers.gradleProperty("mpvRuntimeSource")
val requireMpvRuntime = providers.gradleProperty("requireMpvRuntime")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val runMpvSmoke = providers.gradleProperty("runMpvSmoke")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val bundleMpvRuntime = providers.gradleProperty("bundleMpvRuntime")
    .map { !it.equals("false", ignoreCase = true) }
    .orElse(true)
val requiredRifeBackends = providers.gradleProperty("requiredRifeBackends")
    .orElse("NVIDIA,DIRECTML")
val bundledMpvRuntime = rootProject.layout.projectDirectory.dir("runtime/mpv")
val effectiveMpvRuntimeRoot = mpvRuntimeSource
    .map { rootProject.file(it) }
    .orElse(providers.provider { bundledMpvRuntime.asFile })

val verifyMpvRuntimePayload by tasks.registering {
    group = "verification"
    description = "Verify the mpv runtime payload before creating a RIFE-capable Windows distribution."
    onlyIf { requireMpvRuntime.get() || mpvRuntimeSource.isPresent }
    inputs.dir(effectiveMpvRuntimeRoot).optional()
    inputs.property("requiredRifeBackends", requiredRifeBackends)

    doLast {
        val root = effectiveMpvRuntimeRoot.get().toPath().toAbsolutePath().normalize().toFile()
        val backendScripts = mapOf(
            "NVIDIA" to "MEMC_RIFE_NV.vpy",
            "DIRECTML" to "MEMC_RIFE_DML.vpy",
            "STANDARD" to "MEMC_RIFE_STD.vpy",
        )
        val requestedBackends = requiredRifeBackends.get()
            .split(',', ';', ' ', '\n', '\t')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        val unknownBackends = requestedBackends.filterNot { it in backendScripts.keys }
        if (unknownBackends.isNotEmpty()) {
            throw GradleException(
                "Unknown requiredRifeBackends value(s): ${unknownBackends.joinToString(", ")}. " +
                    "Use NVIDIA, DIRECTML, STANDARD, or an empty value."
            )
        }

        val missing = mutableListOf<String>()
        fun requireFile(relativePath: String) {
            if (!root.resolve(relativePath).isFile) missing += relativePath
        }
        fun requireDirectory(relativePath: String) {
            if (!root.resolve(relativePath).isDirectory) missing += "$relativePath/"
        }

        requireDirectory(".")
        requireFile("mpv.exe")
        requireDirectory("portable_config")
        if (requestedBackends.isNotEmpty()) {
            requireDirectory("portable_config/vs")
            requestedBackends.forEach { backend ->
                requireFile("portable_config/vs/${backendScripts.getValue(backend)}")
            }
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "mpv runtime payload is incomplete at $root. Missing:\n" +
                    missing.joinToString(separator = "\n") { " - $it" }
            )
        }

        logger.lifecycle(
            "Verified mpv runtime payload at $root with RIFE backends: " +
                requestedBackends.ifEmpty { listOf("none") }.joinToString(", ")
        )
    }
}

val smokeMpvRuntime by tasks.registering {
    group = "verification"
    description = "Launch the configured mpv.exe with --version to smoke-check the runtime payload."
    dependsOn(verifyMpvRuntimePayload)
    onlyIf { requireMpvRuntime.get() || mpvRuntimeSource.isPresent }
    inputs.file(effectiveMpvRuntimeRoot.map { it.resolve("mpv.exe") }).optional()

    doLast {
        val root = effectiveMpvRuntimeRoot.get().toPath().toAbsolutePath().normalize().toFile()
        val executable = root.resolve("mpv.exe")
        if (!executable.isFile) {
            throw GradleException("mpv executable not found: $executable")
        }

        val output = ByteArrayOutputStream()
        val result = exec {
            commandLine(executable.absolutePath, "--version")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8.name()).trim()
        if (result.exitValue != 0) {
            throw GradleException(
                "mpv runtime smoke check failed with exit code ${result.exitValue}.\n$text"
            )
        }
        logger.lifecycle("mpv runtime smoke check passed: ${text.lineSequence().firstOrNull().orEmpty()}")
    }
}

val prepareMpvRuntime by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Prepare a local mpv_PlayKit-style runtime for the desktop distribution."
    into(preparedMpvRuntime)

    val sourcePath = mpvRuntimeSource.map { rootProject.file(it) }
    onlyIf { bundleMpvRuntime.get() && mpvRuntimeSource.isPresent }
    doFirst {
        val source = sourcePath.get()
        if (!source.exists()) {
            throw GradleException("mpvRuntimeSource does not exist: $source")
        }
    }
    from(sourcePath) {
        includeEmptyDirs = true
    }
}

distributions {
    main {
        contents {
            if (bundleMpvRuntime.get() && mpvRuntimeSource.isPresent) {
                from(preparedMpvRuntime) {
                    into("runtime/mpv")
                }
            } else if (bundleMpvRuntime.get()) {
                from(rootProject.layout.projectDirectory.dir("runtime/mpv")) {
                    into("runtime/mpv")
                }
            }
        }
    }
}

tasks.named("installDist") {
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(prepareMpvRuntime)
}

tasks.named("distZip") {
    if (runMpvSmoke.get()) {
        dependsOn(smokeMpvRuntime)
    }
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(prepareMpvRuntime)
}

tasks.named("distTar") {
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(prepareMpvRuntime)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":ui-design"))
    implementation(project(":cloud-drive-desktop"))
    implementation(project(":media-source-desktop"))
    implementation(project(":player-mpv"))
    implementation(project(":repository-desktop"))
    implementation(project(":scanner-desktop"))
    implementation(project(":scraper-desktop"))
    implementation(project(":sync-engine-desktop"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
