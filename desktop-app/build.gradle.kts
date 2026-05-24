import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import groovy.json.JsonSlurper
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Tar
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

sourceSets {
    main {
        resources.srcDir(project(":web-control").projectDir.resolve("src/main/assets"))
    }
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
val jpackageInstallerOutputDir = layout.buildDirectory.dir("jpackage/installer")
val jpackageAppContentRuntimeDir = jpackageAppContentDir.map { it.dir("runtime") }
val jpackageEntrySmokeReport = layout.buildDirectory.file("jpackage/smoke/native-entry-smoke.json")
val jpackageInstallerSmokeReport = layout.buildDirectory.file("jpackage/smoke/windows-installer-smoke.json")
val desktopEntrySmokeArg = "--miruplay-desktop-smoke"
val desktopEntrySmokeReportArgPrefix = "--miruplay-desktop-smoke-report="
val mainJarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val runtimeClasspath = configurations.named("runtimeClasspath")
val windowsPackageVersion = providers.gradleProperty("windowsPackageVersion")
    .orElse("0.1.0")
val windowsInstallerType = providers.gradleProperty("windowsInstallerType")
    .orElse("msi")
val requireWindowsInstallerToolchain = providers.gradleProperty("requireWindowsInstallerToolchain")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val signWindowsInstaller = providers.gradleProperty("signWindowsInstaller")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val windowsInstallerSignTool = providers.gradleProperty("windowsInstallerSignTool")
val windowsInstallerCertPath = providers.gradleProperty("windowsInstallerCertPath")
val windowsInstallerCertPassword = providers.gradleProperty("windowsInstallerCertPassword")
val windowsInstallerTimestampUrl = providers.gradleProperty("windowsInstallerTimestampUrl")
    .orElse("http://timestamp.digicert.com")
val windowsInstallerUpgradeUuid = providers.gradleProperty("windowsInstallerUpgradeUuid")
    .orElse("8b677436-92f3-4b59-83bb-4e6ad9f8f22a")

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

fun hasWindowsDrivePrefix(value: String): Boolean =
    value.length >= 2 && value[1] == ':' && value[0].isLetter()

fun normalizeRuntimeManifestEntry(entry: String): String? {
    val normalized = entry.trim().replace('\\', '/')
    if (normalized.isBlank() || normalized.startsWith("/") || hasWindowsDrivePrefix(normalized)) {
        return null
    }
    val directory = normalized.endsWith("/")
    val segments = normalized
        .trimEnd('/')
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
        return null
    }
    return segments.joinToString("/").let { if (directory) "$it/" else it }
}

fun parseRuntimeManifestText(
    text: String,
    context: String,
    problems: MutableList<String>,
): Map<*, *>? =
    try {
        JsonSlurper().parseText(text) as? Map<*, *>
            ?: run {
                problems += "$context must contain a JSON object"
                null
            }
    } catch (error: Exception) {
        problems += "$context could not be parsed: ${error.message ?: error::class.simpleName}"
        null
    }

fun runtimeManifestStringList(
    manifest: Map<*, *>,
    key: String,
    context: String,
    problems: MutableList<String>,
): List<String> {
    val value = manifest[key] ?: return emptyList()
    if (value !is Iterable<*>) {
        problems += "$context $key must be an array"
        return emptyList()
    }
    return value.mapNotNull { entry ->
        entry as? String ?: run {
            problems += "$context $key contains a non-string entry: $entry"
            null
        }
    }
}

fun validateRuntimeManifestEvidence(
    manifest: Map<*, *>,
    context: String,
    problems: MutableList<String>,
    exists: (relativePath: String, directory: Boolean) -> Boolean,
) {
    val manifestBackends = runtimeManifestStringList(
        manifest = manifest,
        key = "requiredRifeBackends",
        context = context,
        problems = problems,
    )
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }

    val unknownBackends = manifestBackends.filterNot { it in backendScripts.keys }
    unknownBackends.forEach { backend ->
        problems += "$context requiredRifeBackends contains unknown backend: $backend"
    }
    manifestBackends
        .mapNotNull { backendScripts[it] }
        .forEach { scriptName ->
            val relativePath = "portable_config/vs/$scriptName"
            if (!exists(relativePath, false)) {
                problems += "$context required backend file is missing: $relativePath"
            }
        }

    runtimeManifestStringList(
        manifest = manifest,
        key = "files",
        context = context,
        problems = problems,
    ).forEach { entry ->
        val manifestPath = normalizeRuntimeManifestEntry(entry)
        if (manifestPath == null) {
            problems += "$context files contains invalid package-relative entry: ${entry.ifBlank { "<blank>" }}"
            return@forEach
        }
        val directory = manifestPath.endsWith("/")
        val relativePath = manifestPath.removeSuffix("/")
        if (!exists(relativePath, directory)) {
            problems += "$context files entry is missing: $manifestPath"
        }
    }
}

fun validateRuntimeManifestFile(root: File, problems: MutableList<String>) {
    val manifestFile = root.resolve("runtime-manifest.json")
    if (!manifestFile.isFile) return
    val context = "runtime-manifest.json"
    val manifest = parseRuntimeManifestText(manifestFile.readText(), context, problems) ?: return
    validateRuntimeManifestEvidence(
        manifest = manifest,
        context = context,
        problems = problems,
    ) { relativePath, directory ->
        val candidate = root.resolve(relativePath)
        if (directory) candidate.isDirectory else candidate.isFile
    }
}

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

fun normalizeWindowsInstallerType(value: String): String =
    value.trim().lowercase().also { normalized ->
        if (normalized !in setOf("msi", "exe")) {
            throw GradleException("windowsInstallerType must be msi or exe, but was: $value")
        }
    }

fun commandExistsOnPath(commandName: String): Boolean {
    val path = System.getenv("PATH").orEmpty()
    val pathExt = System.getenv("PATHEXT")
        ?.split(';')
        ?.filter { it.isNotBlank() }
        ?: listOf(".exe", ".cmd", ".bat")
    val candidates = path.split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .flatMap { directory ->
            val root = File(directory)
            if (commandName.contains('.')) {
                listOf(root.resolve(commandName))
            } else {
                pathExt.map { extension -> root.resolve("$commandName$extension") }
            }
        }
    return candidates.any { it.isFile }
}

fun windowsInstallerToolchainAvailable(): Boolean =
    commandExistsOnPath("candle.exe") && commandExistsOnPath("light.exe")

fun signtoolExecutable(): File {
    val explicit = windowsInstallerSignTool.orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it) }
    if (explicit != null) {
        if (explicit.isFile) return explicit
        throw GradleException("windowsInstallerSignTool does not point to a file: $explicit")
    }
    val path = System.getenv("PATH").orEmpty()
    val candidates = path.split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .map { File(it, "signtool.exe") }
    return candidates.firstOrNull { it.isFile }
        ?: throw GradleException(
            "signtool.exe was not found. Set -PwindowsInstallerSignTool=<path> or disable -PsignWindowsInstaller."
        )
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val verifyWindowsInstallerToolchain by tasks.registering {
    group = "verification"
    description = "Verify WiX and optional signing inputs for Windows installer packaging."
    onlyIf { System.getProperty("os.name").contains("Windows", ignoreCase = true) }
    inputs.property("windowsInstallerType", windowsInstallerType)
    inputs.property("signWindowsInstaller", signWindowsInstaller)
    inputs.property("windowsInstallerSignTool", windowsInstallerSignTool.orElse(""))
    inputs.property("windowsInstallerCertPath", windowsInstallerCertPath.orElse(""))

    doLast {
        val installerType = normalizeWindowsInstallerType(windowsInstallerType.get())
        if (!windowsInstallerToolchainAvailable()) {
            throw GradleException(
                "Windows installer toolchain was not found for $installerType packaging. " +
                    "Install WiX Toolset and ensure candle.exe and light.exe are on PATH."
            )
        }
        if (signWindowsInstaller.get()) {
            signtoolExecutable()
            val certPath = windowsInstallerCertPath.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?: throw GradleException("signWindowsInstaller=true requires -PwindowsInstallerCertPath=<pfx>.")
            if (!certPath.isFile) {
                throw GradleException("windowsInstallerCertPath does not point to a file: $certPath")
            }
        }
        logger.lifecycle(
            "Windows installer toolchain verified for $installerType packaging" +
                if (signWindowsInstaller.get()) " with signing enabled." else " without signing."
        )
    }
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
        validateRuntimeManifestFile(root, missing)

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

tasks.withType<Zip>().configureEach {
    if (name == "distZip") {
        archiveVersion.set(windowsPackageVersion)
    }
}

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
    inputs.property("windowsPackageVersion", windowsPackageVersion)
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
            "--app-version", windowsPackageVersion.get(),
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

val packageWindowsInstaller by tasks.registering {
    group = "distribution"
    description = "Build a Windows MSI/EXE installer from the verified jpackage app image."
    if (windowsInstallerToolchainAvailable() || requireWindowsInstallerToolchain.get()) {
        dependsOn(verifyWindowsInstallerToolchain)
    }
    if (windowsInstallerToolchainAvailable()) {
        dependsOn("smokeNativeAppImageRuntime")
    }
    onlyIf {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
            bundleMpvRuntime.get() &&
            hasMpvRuntimeSource() &&
            (windowsInstallerToolchainAvailable() || requireWindowsInstallerToolchain.get())
    }
    inputs.dir(jpackageAppImageRoot).optional()
    inputs.property("windowsPackageVersion", windowsPackageVersion)
    inputs.property("windowsInstallerType", windowsInstallerType)
    inputs.property("windowsInstallerUpgradeUuid", windowsInstallerUpgradeUuid)
    outputs.dir(jpackageInstallerOutputDir)

    doFirst {
        if (requireWindowsInstallerToolchain.get() && !windowsInstallerToolchainAvailable()) {
            throw GradleException(
                "Windows installer toolchain was not found. Install WiX Toolset and ensure " +
                    "candle.exe and light.exe are on PATH."
            )
        }
    }

    doLast {
        val installerType = normalizeWindowsInstallerType(windowsInstallerType.get())
        val appImageRoot = jpackageAppImageRoot.get()
        if (!appImageRoot.isDirectory) {
            throw GradleException("Verified app image was not found: $appImageRoot")
        }

        val outputDir = jpackageInstallerOutputDir.get().asFile
        delete(outputDir)
        outputDir.mkdirs()

        val command = mutableListOf(
            jpackageExecutable().absolutePath,
            "--type", installerType,
            "--name", "MiruPlay",
            "--app-image", appImageRoot.absolutePath,
            "--dest", outputDir.absolutePath,
            "--vendor", "MiruPlay",
            "--app-version", windowsPackageVersion.get(),
            "--description", "MiruPlay Windows desktop anime media manager",
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-upgrade-uuid", windowsInstallerUpgradeUuid.get(),
        )
        exec {
            commandLine(command)
        }
    }
}

val smokeWindowsInstaller by tasks.registering {
    group = "verification"
    description = "Build the Windows installer and verify installer artifact metadata."
    dependsOn(packageWindowsInstaller)
    onlyIf {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
            bundleMpvRuntime.get() &&
            hasMpvRuntimeSource() &&
            (windowsInstallerToolchainAvailable() || requireWindowsInstallerToolchain.get())
    }
    inputs.dir(jpackageInstallerOutputDir).optional()
    inputs.property("windowsInstallerType", windowsInstallerType)
    inputs.property("windowsPackageVersion", windowsPackageVersion)
    inputs.property("windowsInstallerUpgradeUuid", windowsInstallerUpgradeUuid)
    inputs.property("signWindowsInstaller", signWindowsInstaller)
    outputs.file(jpackageInstallerSmokeReport)
    outputs.upToDateWhen { false }

    doLast {
        val installerType = normalizeWindowsInstallerType(windowsInstallerType.get())
        val outputDir = jpackageInstallerOutputDir.get().asFile
        if (!outputDir.isDirectory) {
            throw GradleException("Windows installer output directory was not created: $outputDir")
        }
        val installer = outputDir.listFiles { file ->
            file.isFile &&
                file.extension.equals(installerType, ignoreCase = true) &&
                file.name.startsWith("MiruPlay", ignoreCase = true)
        }
            ?.maxByOrNull { it.lastModified() }
            ?: throw GradleException("Windows $installerType installer was not created under $outputDir")
        if (installer.length() <= 0L) {
            throw GradleException("Windows installer is empty: $installer")
        }

        val signatureMode = if (signWindowsInstaller.get()) "signed" else "unsigned"
        if (signWindowsInstaller.get()) {
            val signTool = signtoolExecutable()
            val certPath = windowsInstallerCertPath.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?: throw GradleException("signWindowsInstaller=true requires -PwindowsInstallerCertPath=<pfx>.")
            if (!certPath.isFile) {
                throw GradleException("windowsInstallerCertPath does not point to a file: $certPath")
            }
            val signCommand = mutableListOf(
                signTool.absolutePath,
                "sign",
                "/fd", "SHA256",
                "/f", certPath.absolutePath,
                "/tr", windowsInstallerTimestampUrl.get(),
                "/td", "SHA256",
            )
            windowsInstallerCertPassword.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { password ->
                    signCommand += listOf("/p", password)
                }
            signCommand += installer.absolutePath
            exec {
                commandLine(signCommand)
            }
            exec {
                commandLine(signTool.absolutePath, "verify", "/pa", installer.absolutePath)
            }
        }

        val report = """
            {
              "status": "ok",
              "installerType": ${installerType.jsonString()},
              "appVersion": ${windowsPackageVersion.get().jsonString()},
              "signatureMode": ${signatureMode.jsonString()},
              "installerPath": ${installer.toPath().toAbsolutePath().normalize().toString().jsonString()},
              "sizeBytes": ${installer.length()},
              "sha256": ${sha256(installer).jsonString()}
            }
        """.trimIndent()
        val reportFile = jpackageInstallerSmokeReport.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(report)
        logger.lifecycle(
            "Windows $installerType installer verified: " +
                "${installer.toPath().toAbsolutePath().normalize()} (${installer.length()} bytes, $signatureMode), " +
                "smoke report=${reportFile.toPath().toAbsolutePath().normalize()}"
        )
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
            val entries = zip.entries().asSequence()
                .map { it.name.replace('\\', '/') to it }
                .toMap()
            val entryNames = entries.keys
            val runtimeEntryPrefix = entryNames
                .firstOrNull { it.endsWith("/runtime/mpv/runtime-manifest.json") }
                ?.removeSuffix("runtime-manifest.json")
                ?: entryNames
                    .firstOrNull { it.endsWith("/runtime/mpv/mpv.exe") }
                    ?.removeSuffix("mpv.exe")
                ?: ""

            fun requireZipRuntimeFile(relativePath: String) {
                val expectedSuffix = "/runtime/mpv/${relativePath.replace('\\', '/')}"
                if (entryNames.none { it.endsWith(expectedSuffix) }) {
                    missingZipEntries += relativePath
                }
            }

            fun zipRuntimeFileEntry(relativePath: String): String? {
                val normalized = relativePath.replace('\\', '/')
                if (runtimeEntryPrefix.isNotEmpty()) {
                    val candidate = "$runtimeEntryPrefix$normalized"
                    if (candidate in entryNames) return candidate
                }
                val expectedSuffix = "/runtime/mpv/$normalized"
                return entryNames.firstOrNull { it.endsWith(expectedSuffix) }
            }

            requireZipRuntimeFile("mpv.exe")
            requireZipRuntimeFile("runtime-manifest.json")
            requestedBackends.forEach { backend ->
                requireZipRuntimeFile("portable_config/vs/${backendScripts.getValue(backend)}")
            }
            zipRuntimeFileEntry("runtime-manifest.json")?.let { manifestEntryName ->
                val manifestText = zip.getInputStream(entries.getValue(manifestEntryName)).bufferedReader().use { it.readText() }
                parseRuntimeManifestText(
                    text = manifestText,
                    context = "packaged runtime-manifest.json",
                    problems = missingZipEntries,
                )?.let { manifest ->
                    validateRuntimeManifestEvidence(
                        manifest = manifest,
                        context = "packaged runtime-manifest.json",
                        problems = missingZipEntries,
                    ) { relativePath, directory ->
                        if (directory) {
                            val prefix = "$runtimeEntryPrefix${relativePath.trimEnd('/')}/"
                            entryNames.any { it.startsWith(prefix) }
                        } else {
                            zipRuntimeFileEntry(relativePath) != null
                        }
                    }
                }
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
    outputs.file(jpackageEntrySmokeReport)
    outputs.upToDateWhen { false }

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

        val appDir = appRoot.resolve("app")
        val launcherConfig = appDir.resolve("MiruPlay.cfg")
        if (!launcherConfig.isFile) {
            throw GradleException("Native app image launcher config was not found: $launcherConfig")
        }
        val configLines = launcherConfig.readLines()
        val expectedMainClass = application.mainClass.get()
        val actualMainClass = configLines
            .firstOrNull { it.startsWith("app.mainclass=") }
            ?.substringAfter('=')
        if (actualMainClass != expectedMainClass) {
            throw GradleException(
                "Native app image launcher config has wrong main class. " +
                    "Expected $expectedMainClass but found ${actualMainClass ?: "<missing>"} in $launcherConfig"
            )
        }

        val classpathPrefix = "app.classpath=\$APPDIR\\"
        val configuredClasspathJars = configLines
            .asSequence()
            .filter { it.startsWith(classpathPrefix) }
            .map { it.removePrefix(classpathPrefix).replace('\\', '/') }
            .map { it.substringAfterLast('/') }
            .filter { it.endsWith(".jar", ignoreCase = true) }
            .toSet()
        val appJars = appDir.listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        val missingClasspathEntries = appJars - configuredClasspathJars
        val missingClasspathFiles = configuredClasspathJars - appJars
        if (mainJarFile.get().asFile.name !in configuredClasspathJars ||
            missingClasspathEntries.isNotEmpty() ||
            missingClasspathFiles.isNotEmpty()
        ) {
            throw GradleException(
                buildString {
                    appendLine("Native app image launcher classpath is inconsistent in $launcherConfig.")
                    if (mainJarFile.get().asFile.name !in configuredClasspathJars) {
                        appendLine("Missing main jar classpath entry: ${mainJarFile.get().asFile.name}")
                    }
                    if (missingClasspathEntries.isNotEmpty()) {
                        appendLine("Jars missing from launcher classpath:")
                        missingClasspathEntries.sorted().forEach { appendLine(" - $it") }
                    }
                    if (missingClasspathFiles.isNotEmpty()) {
                        appendLine("Launcher classpath entries without jar files:")
                        missingClasspathFiles.sorted().forEach { appendLine(" - $it") }
                    }
                }
            )
        }

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
        validateRuntimeManifestFile(runtimeRoot, missing)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Native app image runtime is incomplete at $runtimeRoot. Missing:\n" +
                    missing.joinToString(separator = "\n") { " - $it" }
            )
        }

        val entrySmokeReportFile = jpackageEntrySmokeReport.get().asFile
        val entrySmokeOutputFile = entrySmokeReportFile.resolveSibling("native-entry-smoke-output.log")
        delete(entrySmokeReportFile)
        delete(entrySmokeOutputFile)
        entrySmokeReportFile.parentFile.mkdirs()
        val entrySmokeProcess = ProcessBuilder(
            launcher.absolutePath,
            desktopEntrySmokeArg,
            "$desktopEntrySmokeReportArgPrefix${entrySmokeReportFile.absolutePath}",
        )
            .directory(appRoot)
            .redirectErrorStream(true)
            .redirectOutput(entrySmokeOutputFile)
            .apply {
                environment()["MIRUPLAY_DESKTOP_START_SECTION"] = "library"
                environment()["MIRUPLAY_MPV_RUNTIME"] = ""
            }
            .start()
        val entrySmokeFinished = entrySmokeProcess.waitFor(30, TimeUnit.SECONDS)
        val entrySmokeText = entrySmokeOutputFile.takeIf { it.isFile }?.readText().orEmpty().trim()
        if (!entrySmokeFinished) {
            entrySmokeProcess.destroyForcibly()
            throw GradleException(
                "Native app image launcher smoke timed out after 30 seconds.\n$entrySmokeText"
            )
        }
        if (entrySmokeProcess.exitValue() != 0) {
            throw GradleException(
                "Native app image launcher smoke failed with exit code ${entrySmokeProcess.exitValue()}.\n" +
                    entrySmokeText
            )
        }
        if (!entrySmokeReportFile.isFile) {
            throw GradleException(
                "Native app image launcher smoke did not write report: $entrySmokeReportFile\n$entrySmokeText"
            )
        }
        val entrySmokeReport = entrySmokeReportFile.readText()
        val expectedRuntimeRoot = runtimeRoot.toPath().toAbsolutePath().normalize().toString()
        val expectedMpvExecutable = runtimeRoot.resolve("mpv.exe").toPath().toAbsolutePath().normalize().toString()
        val expectedConfigDirectory = runtimeRoot.resolve("portable_config").toPath().toAbsolutePath().normalize().toString()
        val expectedWindowTitle = "MiruPlay \u684c\u9762\u7248"
        val missingReportFields = buildList {
            if (!entrySmokeReport.contains("\"status\": \"ok\"")) add("status=ok")
            if (!entrySmokeReport.contains("\"entryPoint\": ${expectedMainClass.jsonString()}")) {
                add("entryPoint=$expectedMainClass")
            }
            if (!entrySmokeReport.contains("\"windowTitle\": ${expectedWindowTitle.jsonString()}")) {
                add("windowTitle=$expectedWindowTitle")
            }
            if (!entrySmokeReport.contains("\"initialSection\": \"library\"")) add("initialSection=library")
            if (!entrySmokeReport.contains("\"runtimeRoot\": ${expectedRuntimeRoot.jsonString()}")) {
                add("runtimeRoot=$expectedRuntimeRoot")
            }
            if (!entrySmokeReport.contains("\"mpvExecutable\": ${expectedMpvExecutable.jsonString()}")) {
                add("mpvExecutable=$expectedMpvExecutable")
            }
            if (!entrySmokeReport.contains("\"configDirectory\": ${expectedConfigDirectory.jsonString()}")) {
                add("configDirectory=$expectedConfigDirectory")
            }
        }
        if (missingReportFields.isNotEmpty()) {
            throw GradleException(
                "Native app image launcher smoke report is missing expected fields in $entrySmokeReportFile:\n" +
                    missingReportFields.joinToString(separator = "\n") { " - $it" } +
                    "\nReport:\n$entrySmokeReport"
            )
        }

        logger.lifecycle(
            "native app image verified: launcher=${launcher.toPath().toAbsolutePath().normalize()}, " +
                "runtime=${runtimeRoot.toPath().toAbsolutePath().normalize()}, " +
                "classpath jars=${configuredClasspathJars.size}, " +
                "RIFE backends=${requestedBackends.ifEmpty { listOf("none") }.joinToString(", ")}, " +
                "entry smoke report=${entrySmokeReportFile.toPath().toAbsolutePath().normalize()}"
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

tasks.named<Tar>("distTar") {
    inputs.property("windowsPackageVersion", windowsPackageVersion)
    dependsOn(verifyMpvRuntimePayload)
    dependsOn(generateMpvRuntimeManifest)
    archiveVersion.set(windowsPackageVersion)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":ui-design"))
    implementation(project(":web-control-core"))
    implementation(project(":cloud-drive-desktop"))
    implementation(project(":media-source-desktop"))
    implementation(project(":player-mpv"))
    implementation(project(":repository-desktop"))
    implementation(project(":scanner-desktop"))
    implementation(project(":scraper-desktop"))
    implementation(project(":sync-engine-desktop"))
    implementation(project(":sync-engine-shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
