package com.miruplay.tv.player.mpv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

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
) {
    val isPlayable: Boolean = "mpv.exe" !in missing && "portable_config/" !in missing
    val hasRife: Boolean = availableRifeBackends.isNotEmpty()
    val isComplete: Boolean = missing.isEmpty()

    fun message(): String = when {
        isComplete -> "Bundled mpv runtime is ready. RIFE: ${formatBackends(availableRifeBackends)}."
        isPlayable && !hasRife -> "mpv runtime is playable, but RIFE scripts are missing."
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
        if (manifest == null) this else "$this Manifest: present."
}

object MpvRuntimeVerifier {
    private val manifestJson = Json { ignoreUnknownKeys = true }

    fun verify(layout: MpvRuntimeLayout): MpvRuntimeVerification {
        val manifest = readManifest(layout)
        val requiredBackends = manifest
            ?.requiredRifeBackends
            ?.mapNotNull(::parseBackend)
            ?: RifeBackend.entries
        val missing = buildList {
            if (!Files.isRegularFile(layout.executable)) add("mpv.exe")
            if (!Files.isDirectory(layout.configDirectory)) add("portable_config/")
            if (requiredBackends.isNotEmpty() && !Files.isDirectory(layout.configDirectory.resolve("vs"))) {
                add("portable_config/vs/")
            }

            requiredBackends.forEach { backend ->
                if (!Files.isRegularFile(layout.rifeScript(backend))) {
                    add("portable_config/vs/${backend.scriptName}")
                }
            }
        }

        return MpvRuntimeVerification(
            layout = layout,
            missing = missing,
            availableRifeBackends = layout.availableRifeBackends(),
            manifest = manifest,
        )
    }

    fun verify(rootDirectory: Path): MpvRuntimeVerification =
        verify(MpvRuntimeDiscovery.layoutFor(rootDirectory))

    private fun readManifest(layout: MpvRuntimeLayout): MpvRuntimeManifest? {
        val manifestPath = layout.rootDirectory.resolve("runtime-manifest.json")
        if (!Files.isRegularFile(manifestPath)) return null
        return runCatching {
            manifestJson.decodeFromString<MpvRuntimeManifest>(Files.readString(manifestPath))
        }.getOrNull()
    }

    private fun parseBackend(value: String): RifeBackend? =
        runCatching { RifeBackend.valueOf(value.trim().uppercase()) }.getOrNull()
}
