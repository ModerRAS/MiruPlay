package com.miruplay.tv.player.mpv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class MpvRuntimeManifest(
    val source: String? = null,
    val overlaySource: String? = null,
    val runtimeRoot: String? = null,
    val requiredRifeBackends: List<String> = emptyList(),
    val verifiedAt: String? = null,
    val files: List<String> = emptyList(),
)

data class MpvRuntimeVerification(
    val layout: MpvRuntimeLayout,
    val missing: List<String>,
    val availableRifeBackends: Set<RifeBackend>,
    val manifest: MpvRuntimeManifest? = null,
    val manifestPresent: Boolean = manifest != null,
) {
    val isPlayable: Boolean = "mpv.exe" !in missing && "portable_config/" !in missing
    val hasRife: Boolean = availableRifeBackends.isNotEmpty()
    val isComplete: Boolean = missing.isEmpty()

    fun message(): String = when {
        isComplete -> "Bundled mpv runtime is ready. RIFE: ${formatBackends(availableRifeBackends)}."
        isPlayable && manifestMissingEntries.isNotEmpty() ->
            "mpv runtime is playable. Runtime manifest entries are missing or invalid: ${manifestMissingEntries.joinToString(", ")}."
        isPlayable && !hasRife -> "mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend."
        isPlayable -> "mpv runtime is playable. Missing optional files: ${missing.joinToString(", ")}."
        else -> "mpv runtime is incomplete. Missing: ${missing.joinToString(", ")}."
    }.withManifestMarker()

    fun detailMessage(): String = buildString {
        appendLine(message())
        manifest?.let { manifest ->
            appendLine()
            appendLine("Runtime manifest")
            manifest.verifiedAt?.let { appendLine("Verified at: $it") }
            manifest.source?.let { appendLine("Source: $it") }
            manifest.overlaySource?.let { appendLine("Overlay source: $it") }
            manifest.runtimeRoot?.let { appendLine("Runtime root: $it") }
            if (manifest.requiredRifeBackends.isNotEmpty()) {
                appendLine("Required RIFE: ${manifest.requiredRifeBackends.joinToString(", ")}")
            }
            if (manifest.files.isNotEmpty()) {
                appendLine("Manifest files: ${manifest.files.joinToString(", ")}")
            }
        }
    }.trimEnd()

    private fun formatBackends(backends: Set<RifeBackend>): String =
        backends.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: "none"

    private fun String.withManifestMarker(): String =
        if (!manifestPresent) this else "$this Manifest: present."

    private val manifestMissingEntries: List<String> =
        missing.filter { it.startsWith(MANIFEST_MISSING_PREFIX) }
            .map { it.removePrefix(MANIFEST_MISSING_PREFIX) }
            .map { it.trimStart() }
}

object MpvRuntimeVerifier {
    private val manifestJson = Json { ignoreUnknownKeys = true }

    fun statusFromInputs(mpvPath: String, configDir: String): String =
        runCatching {
            val root = MpvRuntimeDiscovery.inferRootFromInputs(mpvPath, configDir)
            verify(root).detailMessage()
        }.getOrElse { error ->
            "Runtime check failed: ${error.message ?: error::class.simpleName}"
        }

    fun verify(layout: MpvRuntimeLayout): MpvRuntimeVerification {
        val manifestResult = readManifest(layout)
        val manifest = manifestResult.manifest
        val declaredRequiredBackends = manifest
            ?.requiredRifeBackends
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
        val requiredBackends = declaredRequiredBackends
            ?.mapNotNull(::parseBackend)
            ?: RifeBackend.entries
        val missing = linkedSetOf<String>()
        fun addMissing(value: String) {
            missing += value
        }

        if (!Files.isRegularFile(layout.executable)) addMissing("mpv.exe")
        if (!Files.isDirectory(layout.configDirectory)) addMissing("portable_config/")
        manifestResult.problems.forEach(::addMissing)
        declaredRequiredBackends
            ?.filter { parseBackend(it) == null }
            ?.forEach { addMissing("$MANIFEST_MISSING_PREFIX requiredRifeBackends=$it") }
        if (requiredBackends.isNotEmpty() && !Files.isDirectory(layout.configDirectory.resolve("vs"))) {
            addMissing("portable_config/vs/")
        }

        requiredBackends.forEach { backend ->
            if (!Files.isRegularFile(layout.rifeScript(backend))) {
                addMissing("portable_config/vs/${backend.scriptName}")
            }
        }
        manifest?.files.orEmpty().forEach { entry ->
            missingManifestEntry(layout, entry)?.let(::addMissing)
        }

        return MpvRuntimeVerification(
            layout = layout,
            missing = missing.toList(),
            availableRifeBackends = layout.availableRifeBackends(),
            manifest = manifest,
            manifestPresent = manifestResult.present,
        )
    }

    fun verify(rootDirectory: Path): MpvRuntimeVerification =
        verify(MpvRuntimeDiscovery.layoutFor(rootDirectory))

    private fun readManifest(layout: MpvRuntimeLayout): ManifestReadResult {
        val manifestPath = layout.rootDirectory.resolve("runtime-manifest.json")
        if (!Files.isRegularFile(manifestPath)) return ManifestReadResult()
        return runCatching {
            ManifestReadResult(
                manifest = manifestJson.decodeFromString<MpvRuntimeManifest>(Files.readString(manifestPath)),
                present = true,
            )
        }.getOrElse { error ->
            ManifestReadResult(
                present = true,
                problems = listOf(
                    "$MANIFEST_MISSING_PREFIX runtime-manifest.json could not be parsed: " +
                        (error.message ?: error::class.simpleName.orEmpty())
                ),
            )
        }
    }

    private fun parseBackend(value: String): RifeBackend? =
        runCatching { RifeBackend.valueOf(value.trim().uppercase()) }.getOrNull()

    private fun missingManifestEntry(layout: MpvRuntimeLayout, entry: String): String? {
        val manifestPath = normalizeManifestFileEntry(entry)
            ?: return "$MANIFEST_MISSING_PREFIX ${entry.ifBlank { "<blank>" }}"
        val relativePath = manifestPath.removeSuffix("/")
        val candidate = layout.rootDirectory.resolve(Paths.get(relativePath)).normalize()
        if (!candidate.startsWith(layout.rootDirectory)) {
            return "$MANIFEST_MISSING_PREFIX $manifestPath"
        }

        val exists = if (manifestPath.endsWith("/")) {
            Files.isDirectory(candidate)
        } else {
            Files.isRegularFile(candidate)
        }
        return if (exists) null else "$MANIFEST_MISSING_PREFIX $manifestPath"
    }

    private fun normalizeManifestFileEntry(entry: String): String? {
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

    private fun hasWindowsDrivePrefix(value: String): Boolean =
        value.length >= 2 && value[1] == ':' && value[0].isLetter()
}

private data class ManifestReadResult(
    val manifest: MpvRuntimeManifest? = null,
    val present: Boolean = false,
    val problems: List<String> = emptyList(),
)

private const val MANIFEST_MISSING_PREFIX = "runtime-manifest:"
