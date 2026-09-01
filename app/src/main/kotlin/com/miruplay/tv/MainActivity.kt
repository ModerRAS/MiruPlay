package com.miruplay.tv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.OneShotPreDrawListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.AppModePreferencesManager
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.normalizeSupportedBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.buildToneMappingPreset
import com.miruplay.tv.model.persistenceLocation
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.EpisodePlaybackSourceResolver
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.navigation.NavRoutes
import com.miruplay.tv.ui.detail.AnimeDetailScreen
import com.miruplay.tv.ui.detail.DramaDetailScreen
import com.miruplay.tv.ui.library.LibraryScreen
import com.miruplay.tv.ui.mode.AppModeSelectionScreen
import com.miruplay.tv.ui.music.MusicAlbumDetailPlaceholder
import com.miruplay.tv.ui.music.MusicAlbumDetailScreen
import com.miruplay.tv.ui.music.MusicLibraryPlaceholder
import com.miruplay.tv.ui.music.MusicLibraryScreen
import com.miruplay.tv.ui.music.MusicPlayerPlaceholder
import com.miruplay.tv.ui.music.MusicPlayerScreen
import com.miruplay.tv.ui.mode.DramaLibraryScreen
import com.miruplay.tv.ui.player.PlayerScreen
import com.miruplay.tv.ui.settings.AddSourceScreen
import com.miruplay.tv.ui.theme.MiruPlayTheme
import com.miruplay.tv.webcontrol.WebControlNavigator
import com.miruplay.tv.webcontrol.WebPlaybackSource
import com.miruplay.tv.player.PlaybackDebugOverrides
import com.miruplay.tv.player.LibVlcDebugConfig
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode
import com.miruplay.tv.player.forcedVideoSignalDescriptorFor
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

internal const val APP_RESTART_LAUNCH_DELAY_MS = 2500L
internal const val APP_SHUTDOWN_DELAY_MS = 750L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var mediaRepository: Lazy<MediaSourceRepository>
    @Inject lateinit var progressRepository: Lazy<PlaybackProgressRepository>
    @Inject lateinit var appModePreferencesManager: AppModePreferencesManager
    @Inject lateinit var playbackPreferencesManager: PlaybackPreferencesManager
    @Inject lateinit var appCredentials: Lazy<AppCredentialStore>
    @Inject lateinit var webControlNavigator: Lazy<WebControlNavigator>
    @Inject lateinit var scanCoordinator: Lazy<ScanCoordinator>
    @Inject lateinit var playbackDebugOverrides: PlaybackDebugOverrides

    private var restoreLaunchTmdbOverrides: (() -> Unit)? = null
    private var launchDirectPlaybackRequest: LaunchDirectPlaybackRequest? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val onCreateStartUptimeMs = SystemClock.uptimeMillis()
        logStartupTrace(
            stage = "onCreate_enter",
            startUptimeMs = onCreateStartUptimeMs,
            attributes = mapOf("has_saved_state" to (savedInstanceState != null).toString()),
        )
        super.onCreate(savedInstanceState)
        logStartupTrace("onCreate_after_super", onCreateStartUptimeMs)
        val launchIntentSnapshot = captureLaunchIntentSnapshot(intent)
        logStartupTrace("onCreate_after_snapshot", onCreateStartUptimeMs)
        val initialSelectionState = appModePreferencesManager.getSelectionStateSync()
        logStartupTrace(
            stage = "onCreate_after_selection_state",
            startUptimeMs = onCreateStartUptimeMs,
            attributes = mapOf(
                "app_mode_selected" to initialSelectionState.hasCompletedModeSelection.toString(),
            ),
        )
        val launchBootstrapPlan = buildLaunchBootstrapPlan(
            snapshot = launchIntentSnapshot,
            selectionState = initialSelectionState,
            debugBuild = BuildConfig.DEBUG,
        )
        logStartupTrace(
            stage = "onCreate_after_bootstrap",
            startUptimeMs = onCreateStartUptimeMs,
            attributes = mapOf(
                "has_direct_playback" to (launchBootstrapPlan.directPlaybackRequest != null).toString(),
            ),
        )
        MiruLog.i(
            "MainActivity",
            "Main activity created",
            mapOf(
                "has_saved_state" to (savedInstanceState != null).toString(),
                "intent_action" to intent?.action.orEmpty(),
                "has_test_source" to launchIntentSnapshot.hasTestSourceIntent().toString(),
            )
        )
        logLaunchIntentSummary(launchIntentSnapshot)
        applyLaunchPlaybackOverrides(launchBootstrapPlan.playbackOverrides)
        applyLaunchPlaybackDebugOverrides(launchBootstrapPlan.playbackDebugOverrides)
        launchDirectPlaybackRequest = launchBootstrapPlan.directPlaybackRequest
        logStartupTrace(
            stage = "onCreate_before_render",
            startUptimeMs = onCreateStartUptimeMs,
            attributes = mapOf(
                "has_direct_playback" to (launchDirectPlaybackRequest != null).toString(),
            ),
        )
        if (launchBootstrapPlan.directPlaybackRequest != null) {
            renderDirectPlaybackBootstrapPlaceholder(
                initialAppModeSelectionState = launchBootstrapPlan.initialSelectionState,
                startUptimeMs = onCreateStartUptimeMs,
            )
        } else {
            renderContent(launchBootstrapPlan.initialSelectionState)
        }
        logStartupTrace("onCreate_after_render", onCreateStartUptimeMs)
        lifecycleScope.launch {
            applyLaunchTestTmdbOverrides(launchBootstrapPlan.tmdbOverrides)
            launchBootstrapPlan.deferredSourceRequest?.let { request ->
                addLaunchTestSource(request)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        val onNewIntentStartUptimeMs = SystemClock.uptimeMillis()
        logStartupTrace("onNewIntent_enter", onNewIntentStartUptimeMs)
        super.onNewIntent(intent)
        setIntent(intent)
        logStartupTrace("onNewIntent_after_setIntent", onNewIntentStartUptimeMs)
        val launchIntentSnapshot = captureLaunchIntentSnapshot(intent)
        logStartupTrace("onNewIntent_after_snapshot", onNewIntentStartUptimeMs)
        val launchBootstrapPlan = buildLaunchBootstrapPlan(
            snapshot = launchIntentSnapshot,
            selectionState = appModePreferencesManager.getSelectionStateSync(),
            debugBuild = BuildConfig.DEBUG,
        )
        logStartupTrace(
            stage = "onNewIntent_after_bootstrap",
            startUptimeMs = onNewIntentStartUptimeMs,
            attributes = mapOf(
                "has_direct_playback" to (launchBootstrapPlan.directPlaybackRequest != null).toString(),
            ),
        )
        logLaunchIntentSummary(launchIntentSnapshot)
        applyLaunchPlaybackOverrides(launchBootstrapPlan.playbackOverrides)
        applyLaunchPlaybackDebugOverrides(launchBootstrapPlan.playbackDebugOverrides)
        launchDirectPlaybackRequest = launchBootstrapPlan.directPlaybackRequest
        logStartupTrace(
            stage = "onNewIntent_after_state_update",
            startUptimeMs = onNewIntentStartUptimeMs,
            attributes = mapOf(
                "has_direct_playback" to (launchDirectPlaybackRequest != null).toString(),
            ),
        )
        lifecycleScope.launch {
            applyLaunchTestTmdbOverrides(launchBootstrapPlan.tmdbOverrides)
            launchBootstrapPlan.deferredSourceRequest?.let { request ->
                addLaunchTestSource(request)
            }
        }
    }

    private fun applyLaunchTestTmdbOverrides(
        overrides: LaunchTestTmdbOverrides,
    ) {
        restoreLaunchTmdbOverrides?.invoke()
        restoreLaunchTmdbOverrides = null
        if (!BuildConfig.DEBUG) return
        val token = overrides.token
        val baseUrlOverride = overrides.baseUrlOverride
        if (token == null && baseUrlOverride == null) return
        MiruLog.i(
            "MainActivity",
            "Applying launch TMDB overrides",
            mapOf(
                "has_token_override" to (token != null).toString(),
                "has_base_url_override" to (baseUrlOverride != null).toString(),
            ),
        )
        Log.i(
            "MainActivity",
            "Applying launch TMDB overrides (token=${token != null}, baseUrl=${baseUrlOverride != null})",
        )
        val credentials = appCredentials.get()
        val previousToken = credentials.tmdbAccessToken
        val previousBaseUrlOverride = credentials.tmdbApiBaseUrlOverride
        restoreLaunchTmdbOverrides = {
            credentials.tmdbAccessToken = previousToken
            credentials.tmdbApiBaseUrlOverride = previousBaseUrlOverride
        }
        token?.let { credentials.tmdbAccessToken = it }
        baseUrlOverride?.let { credentials.tmdbApiBaseUrlOverride = it }
    }

    private fun applyLaunchPlaybackOverrides(
        overrides: LaunchPlaybackOverrides,
    ) {
        if (!BuildConfig.DEBUG) return
        if (
            overrides.backend == null &&
            overrides.ruleKey == null &&
            overrides.preset == null
        ) {
            return
        }
        val current = playbackPreferencesManager.formatAwareToneMappingPreferences.normalized()
        val updated = current.copy(
            defaultBackend = (overrides.backend ?: current.defaultBackend).normalizeSupportedBackend(),
            rules = if (overrides.ruleKey != null && overrides.preset != null) {
                current.rules + (
                    overrides.ruleKey to buildToneMappingPreset(overrides.ruleKey, overrides.preset)
                )
            } else {
                current.rules
            }
        )
        playbackPreferencesManager.formatAwareToneMappingPreferences = updated
        MiruLog.i(
            "MainActivity",
            "Applied playback launch overrides",
            mapOf(
                "backend" to (overrides.backend?.name ?: "default"),
                "rule_key" to (overrides.ruleKey?.name ?: "none"),
                "preset" to (overrides.preset?.name ?: "none"),
            )
        )
    }

    private fun applyLaunchPlaybackDebugOverrides(
        overrides: LaunchPlaybackDebugOverrides,
    ) {
        if (BuildConfig.DEBUG) {
            playbackDebugOverrides.forcedVideoSignalDescriptor =
                forcedVideoSignalDescriptorFor(overrides.forcedSignalKind)
            val captureLabel = overrides.captureGlFrameLabel?.trim()?.takeIf { it.isNotBlank() }
            playbackDebugOverrides.pendingGlFrameCaptureLabel = captureLabel
            val initialNativeSnapshotLabel = initialPendingLibVlcNativeSnapshotLabelFor(overrides)
            playbackDebugOverrides.pendingLibVlcNativeSnapshotLabel = initialNativeSnapshotLabel
            playbackDebugOverrides.libVlcDebugConfig = LibVlcDebugConfig(
                hwMode = overrides.libVlcHardwareMode ?: LibVlcHardwareAccelerationMode.FULL,
                voutMode = overrides.libVlcVoutMode ?: LibVlcVoutMode.DEFAULT,
                displayChroma = overrides.libVlcDisplayChroma,
            )
            overrides.libassSubtitleMonitorEnabled?.let {
                playbackDebugOverrides.libassSubtitleMonitorEnabled = it
            }
            Log.i(
                "MainActivity",
                "applyLaunchPlaybackDebugOverrides " +
                    "captureLabel=${captureLabel.orEmpty()} " +
                    "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode} " +
                    "initialNativeSnapshotLabel=${initialNativeSnapshotLabel.orEmpty()}",
            )
        } else {
            playbackDebugOverrides.forcedVideoSignalDescriptor = null
            playbackDebugOverrides.pendingGlFrameCaptureLabel = null
            playbackDebugOverrides.pendingLibVlcNativeSnapshotLabel = null
            playbackDebugOverrides.libVlcDebugConfig = LibVlcDebugConfig(
                voutMode = LibVlcVoutMode.GL_SURFACE,
            )
            playbackDebugOverrides.skipLibVlcStartupProbe = true
        }
    }

    override fun onDestroy() {
        if (restoreLaunchTmdbOverrides != null) {
            MiruLog.i("MainActivity", "Restoring launch TMDB overrides")
            Log.i("MainActivity", "Restoring launch TMDB overrides")
        }
        restoreLaunchTmdbOverrides?.invoke()
        restoreLaunchTmdbOverrides = null
        super.onDestroy()
    }

    private fun scheduleAppRestart() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        val triggerAtMillis = SystemClock.elapsedRealtime() + APP_RESTART_LAUNCH_DELAY_MS
        MiruLog.i(
            "MainActivity",
            "App restart scheduled",
            mapOf(
                "launch_delay_ms" to APP_RESTART_LAUNCH_DELAY_MS.toString(),
                "alarm_type" to "ELAPSED_REALTIME_WAKEUP",
            )
        )
        Log.i("MainActivity", "App restart scheduled")
        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    internal fun requestAppShutdown(restart: Boolean) {
        if (restart) {
            MiruLog.i(
                "MainActivity",
                "App restart requested",
                mapOf(
                    "shutdown_delay_ms" to APP_SHUTDOWN_DELAY_MS.toString(),
                    "launch_delay_ms" to APP_RESTART_LAUNCH_DELAY_MS.toString(),
                )
            )
            Log.i("MainActivity", "App restart requested")
            scheduleAppRestart()
        } else {
            MiruLog.i("MainActivity", "App exit requested")
            Log.i("MainActivity", "App exit requested")
        }
        lifecycleScope.launch {
            delay(APP_SHUTDOWN_DELAY_MS)
            finishAffinity()
            finishAndRemoveTask()
            Process.killProcess(Process.myPid())
            exitProcess(if (restart) 0 else 11)
        }
    }

    private suspend fun addLaunchTestSource(
        request: LaunchTestSourceRequest,
    ) {
        val normalizedLocation = normalizeLaunchTestSourceLocation(request.type, request.location)
        MiruLog.i(
            "MainActivity",
            "Adding test source from launch extra",
            mapOf(
                "source_name" to request.name,
                "source_type" to request.type.name,
                "content_mode" to request.contentMode.name,
                "recognition_mode" to request.recognitionMode.name,
                "scan_after_add" to request.scanAfterAdd.toString(),
            )
        )
        Log.i("MainActivity", "Adding test source from launch extra")
        val source = MediaSourceInfo(
            name = request.name,
            type = request.type,
            contentMode = request.contentMode,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = request.type,
                location = normalizedLocation,
                displayName = request.displayName,
                username = request.username,
                password = request.password,
                recognitionMode = request.recognitionMode,
            ).let { connectionInfo ->
                if (request.disableOnlineMetadata) {
                    connectionInfo + ("disableOnlineMetadata" to "true")
                } else {
                    connectionInfo
                }
            },
        )
        val mediaRepo = mediaRepository.get()
        val existingSource = mediaRepo.getSources()
            .getOrNull()
            .orEmpty()
            .firstOrNull { existing ->
                existing.type == request.type &&
                    existing.persistenceLocation() == normalizedLocation
            }
        val persistedSourceId = if (existingSource != null) {
            val updateResult = mediaRepo.updateSource(
                source.copy(
                    id = existingSource.id,
                    isConnected = existingSource.isConnected,
                    lastScanned = existingSource.lastScanned,
                )
            )
            updateResult.onSuccess {
                MiruLog.i(
                    "MainActivity",
                    "Test source updated",
                    mapOf(
                        "source_id" to existingSource.id.toString(),
                        "source_name" to request.name,
                    )
                )
                Log.i("MainActivity", "Test source updated")
            }.onError { error ->
                MiruLog.w(
                    "MainActivity",
                    "Test source update failed",
                    attributes = mapOf(
                        "source_name" to request.name,
                        "error" to error.toUserMessage(),
                    )
                )
                Log.w("MainActivity", "Test source update failed: ${error.toUserMessage()}")
            }
            updateResult.getOrNull()?.let { existingSource.id }
        } else {
            val addResult = mediaRepo.addSource(source)
            addResult.onSuccess { id ->
                MiruLog.i(
                    "MainActivity",
                    "Test source added",
                    mapOf(
                        "source_id" to id.toString(),
                        "source_name" to request.name,
                    )
                )
                Log.i("MainActivity", "Test source added")
            }.onError { error ->
                MiruLog.w(
                    "MainActivity",
                    "Test source add failed",
                    attributes = mapOf(
                        "source_name" to request.name,
                        "error" to error.toUserMessage(),
                    )
                )
                Log.w("MainActivity", "Test source add failed: ${error.toUserMessage()}")
            }
            addResult.getOrNull()
        }

        if (request.scanAfterAdd && persistedSourceId != null) {
            scanCoordinator.get().scanSource(
                persistedSourceId,
                posterCacheDirectory = File(cacheDir, "miruplay_image_cache"),
            )
                .onSuccess { result ->
                    MiruLog.i(
                        "MainActivity",
                        "Test source scan completed",
                        mapOf(
                            "source_id" to persistedSourceId.toString(),
                            "episodes_found" to result.episodesFound.toString(),
                            "new_episodes" to result.newEpisodes.toString(),
                        )
                    )
                    Log.i("MainActivity", "Test source scan completed")
                }
                .onError { error ->
                    MiruLog.w(
                        "MainActivity",
                        "Test source scan failed",
                        attributes = mapOf(
                            "source_id" to persistedSourceId.toString(),
                            "error" to error.toUserMessage(),
                        )
                    )
                    Log.w("MainActivity", "Test source scan failed: ${error.toUserMessage()}")
                }
        }
    }

    private fun renderContent(initialAppModeSelectionState: AppModeSelectionState) {
        setContent {
            MiruPlayTheme {
                val directPlaybackRequest = launchDirectPlaybackRequest
                if (directPlaybackRequest != null) {
                    DirectPlaybackEntry(
                        request = directPlaybackRequest,
                        onNavigateBack = { launchDirectPlaybackRequest = null },
                    )
                } else {
                    val resolvedMediaRepository = remember { mediaRepository.get() }
                    val resolvedProgressRepository = remember { progressRepository.get() }
                    val resolvedWebControlNavigator = remember { webControlNavigator.get() }
                    MiruPlayNavigation(
                        initialAppModeSelectionState = initialAppModeSelectionState,
                        appModePreferencesManager = appModePreferencesManager,
                        mediaRepository = resolvedMediaRepository,
                        progressRepository = resolvedProgressRepository,
                        webControlNavigator = resolvedWebControlNavigator,
                        launchDirectPlaybackRequest = launchDirectPlaybackRequest,
                        onDirectPlaybackRequestConsumed = { consumed ->
                            if (launchDirectPlaybackRequest == consumed) {
                                launchDirectPlaybackRequest = null
                            }
                        },
                    )
                }
            }
        }
    }

    private fun renderDirectPlaybackBootstrapPlaceholder(
        initialAppModeSelectionState: AppModeSelectionState,
        startUptimeMs: Long,
    ) {
        val placeholderView = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(placeholderView)
        logStartupTrace("direct_placeholder_attached", startUptimeMs)
        if (shouldSwitchDirectPlaybackPlaceholderToComposeImmediately()) {
            if (isDestroyed || isFinishing) return
            logStartupTrace("direct_placeholder_switch_to_compose_immediate", startUptimeMs)
            renderContent(initialAppModeSelectionState)
            return
        }
        OneShotPreDrawListener.add(placeholderView) {
            logStartupTrace("direct_placeholder_predraw", startUptimeMs)
            placeholderView.post {
                if (isDestroyed || isFinishing) return@post
                logStartupTrace("direct_placeholder_switch_to_compose", startUptimeMs)
                renderContent(initialAppModeSelectionState)
            }
        }
    }

    private fun logStartupTrace(
        stage: String,
        startUptimeMs: Long,
        attributes: Map<String, String> = emptyMap(),
    ) {
        val elapsedMs = (SystemClock.uptimeMillis() - startUptimeMs).coerceAtLeast(0L)
        val message = "Startup trace: $stage"
        val payload = linkedMapOf(
            "stage" to stage,
            "elapsed_ms" to elapsedMs.toString(),
            "has_direct_playback" to (launchDirectPlaybackRequest != null).toString(),
        )
        payload.putAll(attributes)
        MiruLog.i("MainActivity", message, payload)
        Log.i(
            "MainActivity",
            "$message elapsedMs=$elapsedMs hasDirectPlayback=${launchDirectPlaybackRequest != null} attributes=$attributes",
        )
    }

    private fun logLaunchIntentSummary(snapshot: LaunchIntentSnapshot) {
        if (!BuildConfig.DEBUG) return
        if (!snapshot.hasAnyLaunchTestData()) return
        MiruLog.i(
            "MainActivity",
            "Launch test extras captured",
            mapOf(
                "has_test_source" to snapshot.hasTestSourceIntent().toString(),
                "has_tmdb_token_extra" to snapshot.tmdbOverrides.hasTokenExtra.toString(),
                "has_tmdb_token_value" to (snapshot.tmdbOverrides.token != null).toString(),
                "has_tmdb_base_url_extra" to snapshot.tmdbOverrides.hasBaseUrlExtra.toString(),
                "has_tmdb_base_url_value" to (snapshot.tmdbOverrides.baseUrlOverride != null).toString(),
                "scan_after_add" to snapshot.scanAfterAdd.toString(),
                "has_direct_playback" to (snapshot.directPlaybackRequest != null).toString(),
            ),
        )
        Log.i(
            "MainActivity",
            "Launch extras summary (testSource=${snapshot.hasTestSourceIntent()}, tmdbTokenExtra=${snapshot.tmdbOverrides.hasTokenExtra}, tmdbTokenValue=${snapshot.tmdbOverrides.token != null}, tmdbBaseUrlExtra=${snapshot.tmdbOverrides.hasBaseUrlExtra}, tmdbBaseUrlValue=${snapshot.tmdbOverrides.baseUrlOverride != null}, scanAfterAdd=${snapshot.scanAfterAdd}, directPlayback=${snapshot.directPlaybackRequest != null})",
        )
    }
}

internal data class LaunchTestSourceRequest(
    val name: String,
    val type: MediaSourceType,
    val location: String,
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    val contentMode: MediaContentMode,
    val recognitionMode: MediaRecognitionMode = MediaRecognitionMode.DIRECTORY,
    val disableOnlineMetadata: Boolean,
    val scanAfterAdd: Boolean,
)

internal data class LaunchIntentSnapshot(
    val legacyLocalPath: String?,
    val legacyLocalName: String?,
    val rawType: String?,
    val rawLocation: String?,
    val rawName: String?,
    val rawDisplayName: String?,
    val rawUsername: String?,
    val rawPassword: String?,
    val rawContentMode: String?,
    val rawRecognitionMode: String?,
    val disableOnlineMetadata: Boolean,
    val scanAfterAdd: Boolean,
    val tmdbOverrides: LaunchTestTmdbOverrides,
    val playbackOverrides: LaunchPlaybackOverrides,
    val playbackDebugOverrides: LaunchPlaybackDebugOverrides,
    val directPlaybackRequest: LaunchDirectPlaybackRequest?,
    val subtitleUri: String? = null,
    val subtitleFormat: String? = null,
)

internal data class LaunchTestTmdbOverrides(
    val token: String?,
    val baseUrlOverride: String?,
    val hasTokenExtra: Boolean,
    val hasBaseUrlExtra: Boolean,
)

internal data class LaunchPlaybackOverrides(
    val backend: PlaybackRenderBackend?,
    val ruleKey: VideoRenderRuleKey?,
    val preset: ToneMappingProfilePreset?,
)

internal data class LaunchPlaybackDebugOverrides(
    val forcedSignalKind: VideoSignalKind?,
    val captureGlFrameLabel: String?,
    val libVlcHardwareMode: LibVlcHardwareAccelerationMode?,
    val libVlcVoutMode: LibVlcVoutMode?,
    val libVlcDisplayChroma: String?,
    val libassSubtitleMonitorEnabled: Boolean? = null,
)

internal data class LaunchDirectPlaybackRequest(
    val uri: String,
    val mediaSourceId: String,
    val startPositionMs: Long,
    val episodeId: String?,
    val subtitleUri: String? = null,
    val subtitleFormat: SubtitleFormat = SubtitleFormat.ASS,
)

internal fun directPlaybackSourceFor(
    request: LaunchDirectPlaybackRequest,
): PlaybackSource {
    val subtitleTracks = if (request.subtitleUri != null) {
        listOf(
            SubtitleTrack(
                language = "und",
                title = "external-test",
                isExternal = true,
                path = request.subtitleUri,
                format = request.subtitleFormat,
            ),
        )
    } else {
        emptyList()
    }
    return PlaybackSource(
        uri = request.uri,
        mediaSourceId = request.mediaSourceId,
        startPosition = request.startPositionMs,
        subtitleTracks = subtitleTracks,
        episodeId = request.episodeId,
    )
}

internal data class LaunchBootstrapPlan(
    val initialSelectionState: AppModeSelectionState,
    val tmdbOverrides: LaunchTestTmdbOverrides,
    val playbackOverrides: LaunchPlaybackOverrides,
    val playbackDebugOverrides: LaunchPlaybackDebugOverrides,
    val directPlaybackRequest: LaunchDirectPlaybackRequest?,
    val deferredSourceRequest: LaunchTestSourceRequest?,
)

internal fun buildLaunchBootstrapPlan(
    snapshot: LaunchIntentSnapshot,
    selectionState: AppModeSelectionState,
    debugBuild: Boolean,
): LaunchBootstrapPlan =
    LaunchBootstrapPlan(
        initialSelectionState = selectionState,
        tmdbOverrides = snapshot.tmdbOverrides,
        playbackOverrides = snapshot.playbackOverrides,
        playbackDebugOverrides = snapshot.playbackDebugOverrides,
        directPlaybackRequest = snapshot.directPlaybackRequest
            ?.takeIf { debugBuild },
        deferredSourceRequest = resolveLaunchTestSourceRequest(
            legacyLocalPath = snapshot.legacyLocalPath,
            legacyLocalName = snapshot.legacyLocalName,
            rawType = snapshot.rawType,
            rawLocation = snapshot.rawLocation,
            rawName = snapshot.rawName,
            rawDisplayName = snapshot.rawDisplayName,
            rawUsername = snapshot.rawUsername,
            rawPassword = snapshot.rawPassword,
            rawContentMode = snapshot.rawContentMode,
            rawRecognitionMode = snapshot.rawRecognitionMode,
            disableOnlineMetadata = snapshot.disableOnlineMetadata,
            scanAfterAdd = snapshot.scanAfterAdd,
            fallbackMode = selectionState.currentAppMode,
        ),
    )

internal fun captureLaunchIntentSnapshot(intent: Intent?): LaunchIntentSnapshot {
    val extras = intent?.extras
    return LaunchIntentSnapshot(
        legacyLocalPath = intent?.getStringExtra("test_local_path"),
        legacyLocalName = intent?.getStringExtra("test_local_name"),
        rawType = intent?.getStringExtra("test_source_type"),
        rawLocation = intent?.getStringExtra("test_source_location"),
        rawName = intent?.getStringExtra("test_source_name"),
        rawDisplayName = intent?.getStringExtra("test_source_display_name"),
        rawUsername = intent?.getStringExtra("test_source_username"),
        rawPassword = intent?.getStringExtra("test_source_password"),
        rawContentMode = intent?.getStringExtra("test_content_mode"),
        rawRecognitionMode = intent?.getStringExtra("test_source_recognition_mode"),
        disableOnlineMetadata = intent?.getBooleanExtra("test_disable_online_metadata", false) == true,
        scanAfterAdd = intent?.getBooleanExtra("test_scan_after_add", false) == true,
        tmdbOverrides = resolveLaunchTestTmdbOverrides(
            rawToken = intent?.getStringExtra("test_tmdb_token"),
            rawBaseUrlOverride = intent?.getStringExtra("test_tmdb_base_url"),
            hasTokenExtra = extras?.containsKey("test_tmdb_token") == true,
            hasBaseUrlExtra = extras?.containsKey("test_tmdb_base_url") == true,
        ),
        playbackOverrides = resolveLaunchPlaybackOverrides(
            rawBackend = intent?.getStringExtra("test_playback_backend"),
            rawRuleKey = intent?.getStringExtra("test_tone_mapping_rule"),
            rawPreset = intent?.getStringExtra("test_tone_mapping_preset"),
        ),
        playbackDebugOverrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = intent?.getStringExtra("test_force_signal_kind"),
            rawCaptureGlFrameLabel = intent?.getStringExtra("test_capture_gl_frame"),
            rawLibVlcHardwareMode = intent?.getStringExtra("test_libvlc_hw_mode"),
            rawLibVlcVoutMode = intent?.getStringExtra("test_libvlc_vout_mode"),
            rawLibVlcDisplayChroma = intent?.getStringExtra("test_libvlc_display_chroma"),
            rawLibassMonitorEnabled = intent?.getBooleanExtra("test_enable_libass_monitor", false)
                .takeIf { it == true },
        ),
        directPlaybackRequest = resolveLaunchDirectPlaybackRequest(
            rawUri = intent?.getStringExtra("test_playback_uri"),
            rawMediaSourceId = intent?.getStringExtra("test_playback_media_source_id"),
            rawStartPositionMs = intent?.getStringExtra("test_playback_start_position_ms"),
            rawEpisodeId = intent?.getStringExtra("test_playback_episode_id"),
            rawSubtitleUri = intent?.getStringExtra("test_subtitle_uri"),
            rawSubtitleFormat = intent?.getStringExtra("test_subtitle_format"),
        ),
        subtitleUri = intent?.getStringExtra("test_subtitle_uri"),
        subtitleFormat = intent?.getStringExtra("test_subtitle_format"),
    )
}

internal fun resolveLaunchTestTmdbOverrides(
    rawToken: String?,
    rawBaseUrlOverride: String?,
    hasTokenExtra: Boolean,
    hasBaseUrlExtra: Boolean,
): LaunchTestTmdbOverrides =
    LaunchTestTmdbOverrides(
        token = normalizeLaunchTmdbToken(rawToken),
        baseUrlOverride = normalizeLaunchTmdbOverride(rawBaseUrlOverride),
        hasTokenExtra = hasTokenExtra,
        hasBaseUrlExtra = hasBaseUrlExtra,
    )

internal fun resolveLaunchPlaybackOverrides(
    rawBackend: String?,
    rawRuleKey: String?,
    rawPreset: String?,
): LaunchPlaybackOverrides =
    LaunchPlaybackOverrides(
        backend = PlaybackRenderBackend.entries.firstOrNull {
            it.name.equals(rawBackend?.trim(), ignoreCase = true)
        }?.normalizeSupportedBackend(),
        ruleKey = VideoRenderRuleKey.entries.firstOrNull {
            it.name.equals(rawRuleKey?.trim(), ignoreCase = true)
        },
        preset = ToneMappingProfilePreset.entries.firstOrNull {
            it.name.equals(rawPreset?.trim(), ignoreCase = true)
        },
    )

internal fun resolveLaunchPlaybackDebugOverrides(
    rawForcedSignalKind: String?,
    rawCaptureGlFrameLabel: String?,
    rawLibVlcHardwareMode: String?,
    rawLibVlcVoutMode: String?,
    rawLibVlcDisplayChroma: String?,
    rawLibassMonitorEnabled: Boolean? = null,
): LaunchPlaybackDebugOverrides =
    LaunchPlaybackDebugOverrides(
        forcedSignalKind = VideoSignalKind.entries.firstOrNull {
            it.name.equals(rawForcedSignalKind?.trim(), ignoreCase = true)
        },
        captureGlFrameLabel = rawCaptureGlFrameLabel?.trim()?.takeIf { it.isNotBlank() },
        libVlcHardwareMode = LibVlcHardwareAccelerationMode.entries.firstOrNull {
            it.name.equals(rawLibVlcHardwareMode?.trim(), ignoreCase = true)
        },
        libVlcVoutMode = LibVlcVoutMode.entries.firstOrNull {
            it.name.equals(rawLibVlcVoutMode?.trim(), ignoreCase = true)
        },
        libVlcDisplayChroma = normalizeLaunchLibVlcDisplayChroma(rawLibVlcDisplayChroma),
        libassSubtitleMonitorEnabled = rawLibassMonitorEnabled,
    )

internal fun normalizeLaunchLibVlcDisplayChroma(rawValue: String?): String? =
    rawValue
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("[A-Z0-9]{4}")) }

internal fun initialPendingLibVlcNativeSnapshotLabelFor(
    overrides: LaunchPlaybackDebugOverrides,
): String? =
    overrides.captureGlFrameLabel
        ?.takeIf { overrides.libVlcVoutMode != LibVlcVoutMode.VMEM_STREAM }

internal fun shouldSwitchDirectPlaybackPlaceholderToComposeImmediately(): Boolean = true

internal fun resolveLaunchDirectPlaybackRequest(
    rawUri: String?,
    rawMediaSourceId: String?,
    rawStartPositionMs: String?,
    rawEpisodeId: String?,
    rawSubtitleUri: String?,
    rawSubtitleFormat: String?,
): LaunchDirectPlaybackRequest? {
    val uri = rawUri?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val startPositionMs = rawStartPositionMs
        ?.trim()
        ?.toLongOrNull()
        ?.coerceAtLeast(0L)
        ?: 0L
    val mediaSourceId = rawMediaSourceId?.trim()?.takeIf { it.isNotBlank() }
        ?: uri
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.', missingDelimiterValue = uri.substringBefore('?').substringAfterLast('/').substringAfterLast('\\'))
            .takeIf { it.isNotBlank() }
        ?: "media"
    return LaunchDirectPlaybackRequest(
        uri = uri,
        mediaSourceId = mediaSourceId,
        startPositionMs = startPositionMs,
        episodeId = rawEpisodeId?.trim()?.takeIf { it.isNotBlank() },
        subtitleUri = rawSubtitleUri?.trim()?.takeIf { it.isNotBlank() },
        subtitleFormat = when {
            rawSubtitleFormat?.equals("SRT", ignoreCase = true) == true -> SubtitleFormat.SRT
            rawSubtitleFormat?.equals("VTT", ignoreCase = true) == true -> SubtitleFormat.VTT
            rawSubtitleFormat?.equals("SSA", ignoreCase = true) == true -> SubtitleFormat.SSA
            else -> SubtitleFormat.ASS
        },
    )
}

internal fun LaunchIntentSnapshot.hasTestSourceIntent(): Boolean =
    hasLaunchTestSourceIntent(
        legacyLocalPath = legacyLocalPath,
        sourceLocation = rawLocation,
    )

internal fun LaunchIntentSnapshot.hasAnyLaunchTestData(): Boolean =
    hasTestSourceIntent() ||
        rawContentMode?.isNotBlank() == true ||
        rawRecognitionMode?.isNotBlank() == true ||
        disableOnlineMetadata ||
        scanAfterAdd ||
        tmdbOverrides.hasTokenExtra ||
        tmdbOverrides.hasBaseUrlExtra ||
        playbackOverrides.backend != null ||
        playbackOverrides.ruleKey != null ||
        playbackOverrides.preset != null ||
        playbackDebugOverrides.forcedSignalKind != null ||
        playbackDebugOverrides.captureGlFrameLabel != null ||
        directPlaybackRequest != null

internal fun resolveLaunchTestSourceContentMode(
    rawValue: String?,
    fallbackMode: AppMode?,
): MediaContentMode {
    val normalized = rawValue?.trim()?.uppercase()
    return when (normalized) {
        MediaContentMode.DRAMA.name -> MediaContentMode.DRAMA
        MediaContentMode.ANIME.name -> MediaContentMode.ANIME
        else -> when (fallbackMode) {
            AppMode.DRAMA -> MediaContentMode.DRAMA
            else -> MediaContentMode.ANIME
        }
    }
}

internal fun resolveLaunchTestSourceRecognitionMode(rawValue: String?): MediaRecognitionMode =
    MediaRecognitionMode.entries.firstOrNull { it.name.equals(rawValue?.trim(), ignoreCase = true) }
        ?: MediaRecognitionMode.DIRECTORY

internal fun normalizeLaunchTmdbOverride(rawValue: String?): String? =
    rawValue?.trim()?.takeIf { it.isNotBlank() }

internal fun normalizeLaunchTmdbToken(rawValue: String?): String? =
    rawValue?.trim()?.takeIf { it.isNotBlank() }

internal fun hasLaunchTestSourceIntent(
    legacyLocalPath: String?,
    sourceLocation: String?,
): Boolean =
    !legacyLocalPath.isNullOrBlank() || !sourceLocation.isNullOrBlank()

internal fun resolveLaunchTestSourceType(
    rawValue: String?,
    location: String?,
): MediaSourceType {
    val normalized = rawValue?.trim()?.uppercase()
    return when (normalized) {
        MediaSourceType.LOCAL.name -> MediaSourceType.LOCAL
        MediaSourceType.WEBDAV.name -> MediaSourceType.WEBDAV
        MediaSourceType.SMB.name -> MediaSourceType.SMB
        else -> {
            val normalizedLocation = location?.trim().orEmpty()
            when {
                normalizedLocation.startsWith("http://", ignoreCase = true) ||
                    normalizedLocation.startsWith("https://", ignoreCase = true) -> MediaSourceType.WEBDAV
                normalizedLocation.startsWith("smb://", ignoreCase = true) ||
                    normalizedLocation.startsWith("\\\\") -> MediaSourceType.SMB
                else -> MediaSourceType.LOCAL
            }
        }
    }
}

internal fun normalizeLaunchTestSourceLocation(
    type: MediaSourceType,
    location: String,
): String =
    when (type) {
        MediaSourceType.SMB -> MediaSourceInfoConventions.normalizeSmbRoot(location)
        MediaSourceType.LOCAL,
        MediaSourceType.WEBDAV -> location.trim()
    }

internal fun resolveLaunchTestSourceRequest(
    legacyLocalPath: String?,
    legacyLocalName: String?,
    rawType: String?,
    rawLocation: String?,
    rawName: String?,
    rawDisplayName: String?,
    rawUsername: String?,
    rawPassword: String?,
    rawContentMode: String?,
    rawRecognitionMode: String?,
    disableOnlineMetadata: Boolean,
    scanAfterAdd: Boolean,
    fallbackMode: AppMode?,
): LaunchTestSourceRequest? {
    val legacyPath = legacyLocalPath?.trim()?.takeIf { it.isNotBlank() }
    if (legacyPath != null) {
        return LaunchTestSourceRequest(
            name = legacyLocalName?.trim()?.takeIf { it.isNotBlank() } ?: "Test Local",
            type = MediaSourceType.LOCAL,
            location = legacyPath,
            contentMode = resolveLaunchTestSourceContentMode(rawContentMode, fallbackMode),
            recognitionMode = MediaRecognitionMode.DIRECTORY,
            disableOnlineMetadata = true,
            scanAfterAdd = scanAfterAdd,
        )
    }

    val location = rawLocation?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val sourceType = resolveLaunchTestSourceType(rawType, location)
    return LaunchTestSourceRequest(
        name = rawName?.trim()?.takeIf { it.isNotBlank() } ?: when (sourceType) {
            MediaSourceType.LOCAL -> "ADB Test Local"
            MediaSourceType.WEBDAV -> "ADB Test WebDAV"
            MediaSourceType.SMB -> "ADB Test SMB"
        },
        type = sourceType,
        location = location,
        displayName = rawDisplayName?.trim().orEmpty(),
        username = rawUsername?.trim().orEmpty(),
        password = rawPassword.orEmpty(),
        contentMode = resolveLaunchTestSourceContentMode(rawContentMode, fallbackMode),
        recognitionMode = resolveLaunchTestSourceRecognitionMode(rawRecognitionMode),
        disableOnlineMetadata = disableOnlineMetadata,
        scanAfterAdd = scanAfterAdd,
    )
}

@Composable
private fun MiruPlayNavigation(
    initialAppModeSelectionState: AppModeSelectionState,
    appModePreferencesManager: AppModePreferencesManager,
    mediaRepository: MediaSourceRepository,
    progressRepository: PlaybackProgressRepository,
    webControlNavigator: WebControlNavigator,
    launchDirectPlaybackRequest: LaunchDirectPlaybackRequest?,
    onDirectPlaybackRequestConsumed: (LaunchDirectPlaybackRequest) -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var appModeSelectionState by remember {
        mutableStateOf(initialAppModeSelectionState)
    }
    val episodePlaybackSourceResolver = remember(progressRepository, mediaRepository) {
        EpisodePlaybackSourceResolver(
            progress = progressRepository,
            mediaSources = mediaRepository,
        )
    }

    androidx.compose.runtime.LaunchedEffect(webControlNavigator, navController) {
        webControlNavigator.commands.collect { command ->
            val payload = command.payload
            runCatching {
                when (command.type) {
                    WebControlNavigator.TYPE_OPEN_PLAYER -> {
                        val source = payload?.let { Json.decodeFromJsonElement<WebPlaybackSource>(it) }
                            ?: return@runCatching
                        MiruLog.i(
                            "MiruPlayNavigation",
                            "Web control requested player navigation",
                            mapOf(
                                "media_source_id" to source.mediaSourceId,
                                "has_episode_id" to (!source.episodeId.isNullOrBlank()).toString(),
                                "start_position_ms" to source.startPositionMs.toString(),
                            )
                        )
                        val encodedPath = Uri.encode(source.uri)
                        val encodedSource = Uri.encode(source.mediaSourceId)
                        val encodedEpisode = Uri.encode(source.episodeId ?: "")
                        val encodedProgress = Uri.encode(source.progressId ?: "")
                        navigateToPlayerRoute(
                            navController = navController,
                            route = NavRoutes.player(
                                uri = encodedPath,
                                mediaSourceId = encodedSource,
                                startPosition = source.startPositionMs,
                                episodeId = encodedEpisode,
                                progressId = encodedProgress,
                            ),
                        )
                    }
                    WebControlNavigator.TYPE_CLOSE_PLAYER -> {
                        if (shouldReplaceExistingPlayerRoute(navController.currentDestination?.route)) {
                            MiruLog.i("MiruPlayNavigation", "Web control requested player close")
                            navController.popBackStack()
                        }
                    }
                    WebControlNavigator.TYPE_APP_RESTART -> {
                        MiruLog.i("MiruPlayNavigation", "Web control requested app restart")
                        (navController.context as? MainActivity)?.requestAppShutdown(restart = true)
                    }
                    WebControlNavigator.TYPE_APP_EXIT -> {
                        MiruLog.i("MiruPlayNavigation", "Web control requested app exit")
                        (navController.context as? MainActivity)?.requestAppShutdown(restart = false)
                    }
                }
            }.onFailure { error ->
                MiruLog.e(
                    "MiruPlayNavigation",
                    "Failed to handle web control navigation command",
                    error,
                    mapOf("command_type" to command.type)
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(launchDirectPlaybackRequest, navController) {
        val request = launchDirectPlaybackRequest ?: return@LaunchedEffect
        MiruLog.i(
            "MiruPlayNavigation",
            "Launch extras requested direct player navigation",
            mapOf(
                "media_source_id" to request.mediaSourceId,
                "has_episode_id" to (!request.episodeId.isNullOrBlank()).toString(),
                "start_position_ms" to request.startPositionMs.toString(),
            )
        )
        val encodedPath = Uri.encode(request.uri)
        val encodedSource = Uri.encode(request.mediaSourceId)
        val encodedEpisode = Uri.encode(request.episodeId ?: "")
        navigateToPlayerRoute(
            navController = navController,
            route = NavRoutes.player(
                uri = encodedPath,
                mediaSourceId = encodedSource,
                startPosition = request.startPositionMs,
                episodeId = encodedEpisode,
            ),
        )
        onDirectPlaybackRequestConsumed(request)
    }
    NavHost(
        navController = navController,
        startDestination = NavRoutes.launchDestinationFor(appModeSelectionState)
    ) {
        composable(NavRoutes.MODE_SELECTION) {
            AppModeSelectionScreen(
                onSelectMode = { mode ->
                    scope.launch {
                        appModePreferencesManager.completeModeSelection(mode)
                        appModeSelectionState = AppModeSelectionState(
                            currentAppMode = mode,
                            hasCompletedModeSelection = true,
                        )
                        navController.navigate(NavRoutes.homeFor(mode)) {
                            popUpTo(NavRoutes.MODE_SELECTION) {
                                inclusive = true
                            }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.LIBRARY) {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDetail = { animeId ->
                    runCatching {
                        navController.navigate(NavRoutes.animeDetail(animeId))
                    }.onFailure { error ->
                        MiruLog.e(
                            "MiruPlayNavigation",
                            "Failed to open anime detail",
                            error,
                            mapOf("anime_id" to animeId)
                        )
                    }
                }
            )
        }

        composable(NavRoutes.DRAMA_HOME) {
            DramaLibraryScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDetail = { seriesId ->
                    navController.navigate(NavRoutes.dramaDetail(seriesId))
                }
            )
        }

        composable(
            route = NavRoutes.DRAMA_DETAIL,
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId")
                ?.takeIf { it.isNotBlank() }
                ?: return@composable
            DramaDetailScreen(
                seriesId = seriesId,
                onNavigateBack = { navController.popBackStack() },
                onPlayEpisode = { episode ->
                    scope.launch {
                        runCatching {
                            val playbackEpisode = com.miruplay.tv.model.Episode(
                                id = episode.id,
                                animeId = episode.seriesId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                title = episode.title,
                                filePath = episode.filePath,
                                fileName = episode.fileName,
                            )
                            val source = episodePlaybackSourceResolver.build(playbackEpisode)
                            val encodedPath = Uri.encode(source.uri)
                            val encodedSource = Uri.encode(source.mediaSourceId)
                            val encodedEpisode = Uri.encode(source.episodeId ?: "")
                            navController.navigate(
                                NavRoutes.player(
                                    uri = encodedPath,
                                    mediaSourceId = encodedSource,
                                    startPosition = source.startPosition,
                                    episodeId = encodedEpisode,
                                )
                            )
                        }.onFailure { error ->
                            MiruLog.e(
                                "MiruPlayNavigation",
                                "Failed to open drama episode playback",
                                error,
                                mapOf(
                                    "episode_id" to episode.id,
                                    "series_id" to episode.seriesId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                        }
                    }
                }
            )
        }

        composable(NavRoutes.MUSIC_HOME) {
            MusicLibraryScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDetail = { albumId -> navController.navigate(NavRoutes.musicDetail(albumId)) }
            )
        }

        composable(
            route = NavRoutes.MUSIC_DETAIL,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) {
            MusicAlbumDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlayTrack = { trackId -> navController.navigate(NavRoutes.musicPlayer(trackId)) }
            )
        }

        composable(
            route = NavRoutes.MUSIC_PLAYER,
            arguments = listOf(navArgument("trackId") { type = NavType.StringType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId")?.takeIf { it.isNotBlank() } ?: return@composable
            MusicPlayerScreen(trackId = trackId, onNavigateBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SETTINGS) {
            AddSourceScreen(
                onNavigateBack = { navController.popBackStack() },
                onRestartApp = { (navController.context as? MainActivity)?.requestAppShutdown(restart = true) },
                onExitApp = { (navController.context as? MainActivity)?.requestAppShutdown(restart = false) },
            )
        }

        composable(
            route = NavRoutes.ANIME_DETAIL,
            arguments = listOf(navArgument("animeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId")
                ?.takeIf { it.isNotBlank() }
                ?: return@composable
            AnimeDetailScreen(
                animeId = animeId,
                onNavigateBack = { navController.popBackStack() },
                onPlayEpisode = { episode ->
                    scope.launch {
                        runCatching {
                            MiruLog.i(
                                "MiruPlayNavigation",
                                "Episode playback requested",
                                mapOf(
                                    "episode_id" to episode.id,
                                    "anime_id" to episode.animeId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                            val source = episodePlaybackSourceResolver.build(episode)
                            val encodedPath = Uri.encode(source.uri)
                            val encodedSource = Uri.encode(source.mediaSourceId)
                            val encodedEpisode = Uri.encode(source.episodeId ?: "")
                            val encodedProgress = Uri.encode(source.progressId ?: "")
                            navController.navigate(
                                NavRoutes.player(
                                    uri = encodedPath,
                                    mediaSourceId = encodedSource,
                                    startPosition = source.startPosition,
                                    episodeId = encodedEpisode,
                                    progressId = encodedProgress,
                                )
                            )
                        }.onFailure { error ->
                            MiruLog.e(
                                "MiruPlayNavigation",
                                "Failed to open episode playback",
                                error,
                                mapOf(
                                    "episode_id" to episode.id,
                                    "anime_id" to episode.animeId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                        }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.PLAYER_WITH_OPTIONS,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("mediaSourceId") {
                    type = NavType.StringType
                    defaultValue = "media"
                },
                navArgument("startPosition") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("episodeId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("progressId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val source = playbackSourceFromPlayerRouteArguments(
                uri = backStackEntry.arguments?.getString("uri") ?: return@composable,
                mediaSourceId = backStackEntry.arguments?.getString("mediaSourceId") ?: "media",
                startPosition = backStackEntry.arguments?.getLong("startPosition") ?: 0L,
                episodeId = backStackEntry.arguments?.getString("episodeId"),
                progressId = backStackEntry.arguments?.getString("progressId"),
            )
            PlayerScreen(
                playbackSource = source,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

internal fun playbackSourceFromPlayerRouteArguments(
    uri: String,
    mediaSourceId: String,
    startPosition: Long,
    episodeId: String?,
    progressId: String?,
): PlaybackSource {
    val resolvedEpisodeId = episodeId?.takeIf(String::isNotBlank)
    return PlaybackSource(
        uri = uri,
        mediaSourceId = mediaSourceId,
        startPosition = startPosition,
        subtitleTracks = emptyList(),
        episodeId = resolvedEpisodeId,
        progressId = progressId?.takeIf(String::isNotBlank) ?: resolvedEpisodeId,
    )
}

internal fun navigateToPlayerRoute(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    if (shouldReplaceExistingPlayerRoute(navController.currentDestination?.route)) {
        navController.popBackStack()
    }
    navController.navigate(route) {
        launchSingleTop = true
    }
}

internal fun shouldReplaceExistingPlayerRoute(currentRoute: String?): Boolean =
    currentRoute == NavRoutes.PLAYER_WITH_OPTIONS

@Composable
private fun DirectPlaybackEntry(
    request: LaunchDirectPlaybackRequest,
    onNavigateBack: () -> Unit,
) {
    remember(request) {
        Log.i(
            "MainActivity",
            "Startup trace: direct_playback_entry mediaSourceId=${request.mediaSourceId} uri=${request.uri}",
        )
        true
    }
    val playbackSource = remember(request) {
        directPlaybackSourceFor(request)
    }
    PlayerScreen(
        playbackSource = playbackSource,
        onNavigateBack = onNavigateBack,
    )
}
