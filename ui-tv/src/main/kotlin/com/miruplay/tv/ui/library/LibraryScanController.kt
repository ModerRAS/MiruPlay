package com.miruplay.tv.ui.library

import com.miruplay.tv.scanner.LibraryScanState
import kotlinx.coroutines.flow.StateFlow

interface LibraryScanController {
    val state: StateFlow<LibraryScanState>

    fun startManualScan()

    fun startAutoScanIfDue()

    fun cancel()
}
