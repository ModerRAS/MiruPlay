package com.miruplay.tv.ui.library

import android.content.Context
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scanner.LibraryScanStatus
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
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
}
