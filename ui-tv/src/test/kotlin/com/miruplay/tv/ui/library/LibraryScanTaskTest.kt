package com.miruplay.tv.ui.library

import android.content.Context
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.scanner.LibraryScanStatus
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScanTaskTest {
    @Test
    fun `manual scan with no sources does not resolve scan coordinator`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val mediaRepository = mockk<MediaSourceRepository>()
            coEvery { mediaRepository.getSources() } returns Result.success(emptyList())
            var scanCoordinatorResolved = false
            val lazyScanCoordinator = Lazy<ScanCoordinator> {
                scanCoordinatorResolved = true
                error("ScanCoordinator should not be resolved before there is a source to scan")
            }

            val task = LibraryScanTask(
                context = mockk<Context>(relaxed = true),
                mediaRepository = mediaRepository,
                scanCoordinator = lazyScanCoordinator,
                scanPreferences = mockk<ScanPreferencesRepository>(relaxed = true),
                backgroundTasks = mockk<BackgroundTaskForegroundController>(relaxed = true),
                scanStatus = LibraryScanStatus(),
            )

            task.startManualScan()
            runCurrent()

            assertFalse(scanCoordinatorResolved)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancel stops active manual scan`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val source = MediaSourceInfo(
                id = 1L,
                name = "Test Source",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "/sdcard/Movies"),
            )
            val mediaRepository = mockk<MediaSourceRepository>()
            coEvery { mediaRepository.getSources() } returns Result.success(listOf(source))
            val coordinator = mockk<ScanCoordinator>(relaxed = true)
            coEvery { coordinator.scanSource(1L, false, any()) } coAnswers { awaitCancellation() }
            val context = mockk<Context>()
            every { context.cacheDir } returns java.io.File("build/tmp/library-scan-task-test")
            val status = LibraryScanStatus()
            val task = LibraryScanTask(
                context = context,
                mediaRepository = mediaRepository,
                scanCoordinator = Lazy { coordinator },
                scanPreferences = mockk<ScanPreferencesRepository>(relaxed = true),
                backgroundTasks = mockk<BackgroundTaskForegroundController>(relaxed = true),
                scanStatus = status,
            )

            task.startManualScan()
            runCurrent()

            assertTrue(status.state.value is LibraryScanState.Scanning)

            task.cancel()
            runCurrent()

            assertEquals(LibraryScanState.Cancelled, status.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
