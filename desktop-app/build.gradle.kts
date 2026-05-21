import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.zip.ZipFile
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

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

val generatedMpvRuntimeManifest = layout.buildDirectory.file("generated/mpv-runtime/runtime-manifest.json")
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
val backendScripts = mapOf(
    "NVIDIA" to "MEMC_RIFE_NV.vpy",
    "DIRECTML" to "MEMC_RIFE_DML.vpy",
    "STANDARD" to "MEMC_RIFE_STD.vpy",
)
val jpackageAppContentDir = layout.buildDirectory.dir("jpackage/app-content")
val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val jpackageOutputDir = layout.buildDirectory.dir("jpackage/output")
val jpackageAppImageRoot = jpackageOutputDir.map { it.dir("MiruPlay").asFile }
val jpackageAppContentRuntimeDir = jpackageAppContentDir.map { it.dir("runtime") }
val mainJarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val runtimeClasspath = configurations.named("runtimeClasspath")

fun requestedRifeBackends(): List<String> =
    requiredRifeBackends.get()
        .split(',', ';', ' ', '\n', '\t')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }

fun validateRifeBackends(backends: List<String>) {
    val unknownBackends = backends.filterNot { it in backendScripts.keys }
    if (unknownBackends.isNotEmpty()) {
        throw GradleException(
            "Unknown requiredRifeBackends value(s): ${unknownBackends.joinToString(", ")}. " +
                "Use NVIDIA, DIRECTML, STANDARD, or an empty value."
        )
    }
}

fun String.jsonString(): String =
    buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

fun List<String>.jsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.jsonString() }

fun hasMpvRuntimeSource(): Boolean =
    mpvRuntimeSource.isPresent || bundledMpvRuntime.asFile.exists()

fun mpvRuntimeSourceHasManifest(): Boolean =
    hasMpvRuntimeSource() && effectiveMpvRuntimeRoot.get().resolve("runtime-manifest.json").isFile

fun jpackageExecutable(): File {
    val executableName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "jpackage.exe"
    } else {
        "jpackage"
    }
    val candidates = listOfNotNull(
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { File(it, "bin/$executableName") },
        File(System.getProperty("java.home"), "bin/$executableName"),
        File(System.getProperty("java.home"), "../bin/$executableName"),
    )
    return candidates.firstOrNull { it.isFile }
        ?: throw GradleException(
            "jpackage was not found. Use a full JDK 21 and set JAVA_HOME before running packageWindowsAppImage."
        )
}

val verifyMpvRuntimePayload by tasks.registering {
    group = "verification"
    description = "Verify the mpv runtime payload before creating a RIFE-capable Windows distribution."
    onlyIf { requireMpvRuntime.get() || mpvRuntimeSource.isPresent }
    inputs.dir(effectiveMpvRuntimeRoot).optional()
    inputs.property("requiredRifeBackends", requiredRifeBackends)

    doLast {
        val root = effectiveMpvRuntimeRoot.get().toPath().toAbsolutePath().normalize().toFile()
        val requestedBackends = requestedRifeBackends()
        validateRifeBackends(requestedBackends)

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

val distZipTask = tasks.named<Zip>("distZip")

val prepareJpackageAppContent by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Prepare bundled runtime content for JDK jpackage app images."
    into(jpackageAppContentDir)
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(generateMpvRuntimeManifest)

    if (bundleMpvRuntime.get() && hasMpvRuntimeSource()) {
        from(effectiveMpvRuntimeRoot) {
            into("runtime/mpv")
        }
        if (!mpvRuntimeSourceHasManifest()) {
            from(generatedMpvRuntimeManifest) {
                into("runtime/mpv")
            }
        }
    }

    doFirst {
        jpackageAppContentDir.get().asFile.mkdirs()
    }
}

val prepareJpackageInput by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Prepare application jars for JDK jpackage app images."
    dependsOn(tasks.named("jar"))
    from(mainJarFile)
    from(runtimeClasspath)
    into(jpackageInputDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val packageWindowsAppImage by tasks.registering {
    group = "distribution"
    description = "Build a Windows JDK jpackage app image with bundled mpv/RIFE runtime content."
    dependsOn(prepareJpackageInput)
    dependsOn(prepareJpackageAppContent)
    onlyIf { System.getProperty("os.name").contains("Windows", ignoreCase = true) }
    inputs.dir(jpackageInputDir)
    inputs.dir(jpackageAppContentDir).optional()
    outputs.dir(jpackageAppImageRoot)

    doLast {
        val appContentRuntime = jpackageAppContentRuntimeDir.get().asFile
        val outputDir = jpackageOutputDir.get().asFile
        delete(outputDir)
        outputDir.mkdirs()

        val command = mutableListOf(
            jpackageExecutable().absolutePath,
            "--type", "app-image",
            "--name", "MiruPlay",
            "--input", jpackageInputDir.get().asFile.absolutePath,
            "--main-jar", mainJarFile.get().asFile.name,
            "--main-class", application.mainClass.get(),
            "--dest", outputDir.absolutePath,
            "--vendor", "MiruPlay",
            "--app-version", "0.1.0",
            "--description", "MiruPlay Windows desktop anime media manager",
        )
        if (appContentRuntime.isDirectory) {
            command += listOf("--app-content", appContentRuntime.absolutePath)
        }
        exec {
            commandLine(command)
        }
    }
}

val smokePackagedMpvRuntime by tasks.registering {
    group = "verification"
    description = "Build the distribution zip, verify packaged mpv/RIFE entries, and smoke-check the runtime used for packaging."
    dependsOn(distZipTask)
    onlyIf { bundleMpvRuntime.get() && hasMpvRuntimeSource() }
    inputs.file(distZipTask.flatMap { it.archiveFile })
    inputs.file(effectiveMpvRuntimeRoot.map { it.resolve("mpv.exe") }).optional()
    inputs.property("requiredRifeBackends", requiredRifeBackends)

    doLast {
        val archive = distZipTask.get().archiveFile.get().asFile
        if (!archive.isFile) {
            throw GradleException("Distribution zip was not created: $archive")
        }
        val requestedBackends = requestedRifeBackends()
        validateRifeBackends(requestedBackends)

        val runtimeRoot = effectiveMpvRuntimeRoot.get().toPath().toAbsolutePath().normalize().toFile()
        val executable = runtimeRoot.resolve("mpv.exe")
        if (!executable.isFile) {
            throw GradleException("mpv executable not found for packaged runtime: $executable")
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
                "Packaged mpv runtime smoke check failed with exit code ${result.exitValue}.\n$text"
            )
        }

        val missingZipEntries = mutableListOf<String>()
        ZipFile(archive).use { zip ->
            val entryNames = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name.replace('\\', '/') }
                .toSet()

            fun requireZipRuntimeFile(relativePath: String) {
                val expectedSuffix = "/runtime/mpv/${relativePath.replace('\\', '/')}"
                if (entryNames.none { it.endsWith(expectedSuffix) }) {
                    missingZipEntries += relativePath
                }
            }

            requireZipRuntimeFile("mpv.exe")
            requireZipRuntimeFile("runtime-manifest.json")
            requestedBackends.forEach { backend ->
                requireZipRuntimeFile("portable_config/vs/${backendScripts.getValue(backend)}")
            }
        }
        if (missingZipEntries.isNotEmpty()) {
            throw GradleException(
                "Distribution zip is missing packaged mpv runtime entries in $archive:\n" +
                    missingZipEntries.joinToString(separator = "\n") { " - $it" }
            )
        }
        logger.lifecycle(
            "packaged mpv runtime smoke check passed: ${text.lineSequence().firstOrNull().orEmpty()}"
        )
        logger.lifecycle(
            "packaged mpv runtime entries verified in ${archive.toPath().toAbsolutePath().normalize()} " +
                "with RIFE backends: ${requestedBackends.ifEmpty { listOf("none") }.joinToString(", ")}"
        )
    }
}

val smokeNativeAppImageRuntime by tasks.registering {
    group = "verification"
    description = "Build the Windows app image and verify the bundled mpv/RIFE runtime resources."
    dependsOn(packageWindowsAppImage)
    onlyIf {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
            bundleMpvRuntime.get() &&
            hasMpvRuntimeSource()
    }
    inputs.dir(jpackageAppImageRoot).optional()
    inputs.property("requiredRifeBackends", requiredRifeBackends)

    doLast {
        val appRoot = jpackageAppImageRoot.get()
        if (!appRoot.isDirectory) {
            throw GradleException("Native app image output was not created: $appRoot")
        }
        val requestedBackends = requestedRifeBackends()
        validateRifeBackends(requestedBackends)

        val launcher = appRoot.walkTopDown()
            .filter { it.isFile }
            .firstOrNull { file ->
                file.name.equals("MiruPlay.exe", ignoreCase = true) ||
                    file.name == "MiruPlay"
            }
            ?: throw GradleException("Native app image launcher was not found under $appRoot")

        val runtimeRoot = appRoot.walkTopDown()
            .filter { it.isDirectory && it.name == "mpv" }
            .firstOrNull { candidate ->
                candidate.resolve("mpv.exe").isFile &&
                    candidate.resolve("portable_config").isDirectory
            }
            ?: throw GradleException("Native app image is missing bundled runtime/mpv under $appRoot")

        val missing = mutableListOf<String>()
        fun requireRuntimeFile(relativePath: String) {
            if (!runtimeRoot.resolve(relativePath).isFile) missing += relativePath
        }
        requireRuntimeFile("mpv.exe")
        requireRuntimeFile("runtime-manifest.json")
        requestedBackends.forEach { backend ->
            requireRuntimeFile("portable_config/vs/${backendScripts.getValue(backend)}")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Native app image runtime is incomplete at $runtimeRoot. Missing:\n" +
                    missing.joinToString(separator = "\n") { " - $it" }
            )
        }

        logger.lifecycle(
            "native app image verified: launcher=${launcher.toPath().toAbsolutePath().normalize()}, " +
                "runtime=${runtimeRoot.toPath().toAbsolutePath().normalize()}, " +
                "RIFE backends=${requestedBackends.ifEmpty { listOf("none") }.joinToString(", ")}"
        )
    }
}

val generateMpvRuntimeManifest by tasks.registering {
    group = "distribution"
    description = "Generate metadata for the bundled mpv/RIFE runtime."
    outputs.file(generatedMpvRuntimeManifest)
    inputs.dir(effectiveMpvRuntimeRoot).optional()
    inputs.property("requiredRifeBackends", requiredRifeBackends)

    onlyIf {
        bundleMpvRuntime.get() &&
            hasMpvRuntimeSource() &&
            !mpvRuntimeSourceHasManifest()
    }
    doFirst {
        val source = effectiveMpvRuntimeRoot.get()
        if (!source.exists()) {
            throw GradleException("mpvRuntimeSource does not exist: $source")
        }
        validateRifeBackends(requestedRifeBackends())
    }
    doLast {
        val requestedBackends = requestedRifeBackends()
        val runtimeRoot = effectiveMpvRuntimeRoot.get().toPath().toAbsolutePath().normalize()
        val manifestFiles = buildList {
            add("mpv.exe")
            add("portable_config/")
            if (requestedBackends.isNotEmpty()) {
                add("portable_config/vs/")
                requestedBackends.forEach { backend ->
                    add("portable_config/vs/${backendScripts.getValue(backend)}")
                }
            }
        }
        val manifest = """
            {
              "source": ${runtimeRoot.toString().jsonString()},
              "runtimeRoot": ${runtimeRoot.toString().jsonString()},
              "requiredRifeBackends": ${requestedBackends.jsonArray()},
              "verifiedAt": ${OffsetDateTime.now().toString().jsonString()},
              "files": ${manifestFiles.jsonArray()}
            }
        """.trimIndent()
        val manifestFile = generatedMpvRuntimeManifest.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(manifest)
    }
}

distributions {
    main {
        contents {
            if (bundleMpvRuntime.get() && hasMpvRuntimeSource()) {
                from(effectiveMpvRuntimeRoot) {
                    into("runtime/mpv")
                }
                if (!mpvRuntimeSourceHasManifest()) {
                    from(generatedMpvRuntimeManifest) {
                        into("runtime/mpv")
                    }
                }
            }
        }
    }
}

tasks.named("installDist") {
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(generateMpvRuntimeManifest)
}

distZipTask {
    if (runMpvSmoke.get()) {
        dependsOn(smokeMpvRuntime)
    }
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(generateMpvRuntimeManifest)
}

tasks.named("distTar") {
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(generateMpvRuntimeManifest)
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
