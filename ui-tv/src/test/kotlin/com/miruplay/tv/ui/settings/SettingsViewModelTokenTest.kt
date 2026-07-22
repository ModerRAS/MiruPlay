package com.miruplay.tv.ui.settings

import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.OtlpLogUploadConfig
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.scraper.core.BangumiArchiveSnapshot
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTokenTest {
    @MockK lateinit var mediaRepository: MediaSourceRepository
    @MockK lateinit var mediaSourceFactory: MediaSourceFactory
    @MockK lateinit var securePrefs: AppCredentialStore
    @MockK lateinit var appModePreferences: AppModePreferencesRepository
    @MockK lateinit var scanPreferences: ScanPreferencesManager
    @MockK lateinit var playbackPreferences: PlaybackPreferencesManager
    @MockK lateinit var webControlPreferences: WebControlAccessManager
    @MockK lateinit var cloudDriveRepository: CloudDriveAutomationRepository
    @MockK lateinit var logUploadRepository: LogUploadRepository
    @MockK lateinit var appUpdateRepository: AppUpdateRepository
    @MockK lateinit var cloudDriveClient: CloudDriveClient
    @MockK lateinit var cloudDriveEngine: CloudDriveRssAutomationEngine
    @MockK lateinit var cloudDriveScheduler: CloudDriveRssScheduler
    @MockK lateinit var bangumiArchiveStore: BangumiArchiveStore
    @MockK lateinit var backgroundTasks: BackgroundTaskForegroundController

    private val logStatus = MutableStateFlow(LogUploadStatus())

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        coEvery { mediaRepository.getSources() } returns Result.success(emptyList())
        coEvery { appModePreferences.getSelectionState() } returns
            com.miruplay.tv.repository.AppModeSelectionState(
                currentAppMode = AppMode.ANIME,
                hasCompletedModeSelection = true,
            )

        every { securePrefs.bangumiAccessToken } returns null
        every { securePrefs.tmdbAccessToken } returns null
        every { securePrefs.cloudDriveToken } returns null
        every { securePrefs.cloudDrivePassword } returns null

        every { scanPreferences.autoScanEnabled } returns false
        every { scanPreferences.autoScanIntervalMs } returns 6 * 60 * 60 * 1000L
        every { scanPreferences.lastScanAt } returns 0L
        every { scanPreferences.mergeSameAnimeEnabled } returns false
        every { scanPreferences.posterWallArrangement } returns PosterWallArrangement.TITLE

        every { playbackPreferences.endAction } returns PlaybackEndAction.RETURN_TO_DETAIL
        every { playbackPreferences.episodeVersionSelectionPolicy } returns EpisodeVersionSelectionPolicy.AUTO_NEAREST
        every { playbackPreferences.preferredSubtitleLanguage } returns SubtitleLanguagePreference.AUTO
        every { playbackPreferences.formatAwareToneMappingPreferences } returns FormatAwareToneMappingPreferences()

        every { webControlPreferences.webControlEnabled } returns false
        every { webControlPreferences.accessToken } returns "test-token"

        every { cloudDriveRepository.observeConfig() } returns emptyFlow()
        every { cloudDriveRepository.observeSubscriptions() } returns emptyFlow()

        every { logUploadRepository.status } returns logStatus
        every { logUploadRepository.observeConfig() } returns emptyFlow()
        every { logUploadRepository.getConfig() } returns OtlpLogUploadConfig()
        every { logUploadRepository.isTokenConfigured() } returns false

        every { bangumiArchiveStore.snapshot() } returns BangumiArchiveSnapshot(
            latest = null,
            subjectFile = File("build/tmp/empty-subject.jsonlines"),
            subjectFileSizeBytes = 0L,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveTmdbToken updates secure prefs and state flow`() = runTest {
        every { securePrefs.tmdbAccessToken = any() } answers {
            every { securePrefs.tmdbAccessToken } returns firstArg<String>()
        }
        val viewModel = createViewModel()

        viewModel.saveTmdbToken("  tmdb-token  ")
        advanceUntilIdle()

        assertEquals("tmdb-token", viewModel.tmdbToken.value)
        assertEquals("tmdb-token", securePrefs.tmdbAccessToken)
    }

    @Test
    fun `clearTmdbToken clears secure prefs and state flow`() = runTest {
        every { securePrefs.tmdbAccessToken } returns "saved-token"
        every { securePrefs.tmdbAccessToken = any() } answers {
            every { securePrefs.tmdbAccessToken } returns firstArg<String>()
        }
        every { securePrefs.clearTmdbToken() } answers {
            every { securePrefs.tmdbAccessToken } returns null
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearTmdbToken()
        advanceUntilIdle()

        assertEquals("", viewModel.tmdbToken.value)
        assertEquals(null, securePrefs.tmdbAccessToken)
    }

    @Test
    fun `setCurrentAppMode persists next launch mode and updates state flow`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCurrentAppMode(AppMode.DRAMA)
        advanceUntilIdle()

        coVerify(exactly = 1) { appModePreferences.setCurrentAppMode(AppMode.DRAMA) }
        assertEquals(AppMode.DRAMA, viewModel.currentAppMode.value)
        assertEquals(EpisodeVersionSelectionPolicy.AUTO_NEAREST, viewModel.episodeVersionSelectionPolicy.value)
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            mediaRepository = mediaRepository,
            mediaSourceFactory = mediaSourceFactory,
            securePrefs = securePrefs,
            appModePreferences = appModePreferences,
            scanPreferences = scanPreferences,
            playbackPreferences = playbackPreferences,
            webControlPreferences = webControlPreferences,
            cloudDriveRepository = cloudDriveRepository,
            logUploadRepository = logUploadRepository,
            appUpdateRepository = appUpdateRepository,
            cloudDriveClient = cloudDriveClient,
            cloudDriveEngine = cloudDriveEngine,
            cloudDriveScheduler = cloudDriveScheduler,
            bangumiArchiveStore = bangumiArchiveStore,
            backgroundTasks = backgroundTasks,
        )
}
