package com.miruplay.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDebugOverridesTest {
    @Test
    fun `native snapshot label is consumed independently from ui capture label`() {
        val overrides = PlaybackDebugOverrides().apply {
            pendingGlFrameCaptureLabel = "ui_capture"
            pendingLibVlcNativeSnapshotLabel = "native_capture"
        }

        assertEquals("ui_capture", overrides.consumePendingGlFrameCaptureLabel())
        assertEquals("native_capture", overrides.consumePendingLibVlcNativeSnapshotLabel())
        assertNull(overrides.consumePendingGlFrameCaptureLabel())
        assertNull(overrides.consumePendingLibVlcNativeSnapshotLabel())
    }

    @Test
    fun `clearPendingLibVlcNativeSnapshotLabel only clears matching labels`() {
        val overrides = PlaybackDebugOverrides().apply {
            pendingLibVlcNativeSnapshotLabel = "expected"
        }

        overrides.clearPendingLibVlcNativeSnapshotLabel("other")
        assertEquals("expected", overrides.peekPendingLibVlcNativeSnapshotLabel())

        overrides.clearPendingLibVlcNativeSnapshotLabel("expected")
        assertNull(overrides.peekPendingLibVlcNativeSnapshotLabel())
    }

    @Test
    fun `skip libvlc startup probe defaults to false and can be toggled`() {
        val overrides = PlaybackDebugOverrides()

        assertEquals(false, overrides.skipLibVlcStartupProbe)

        overrides.skipLibVlcStartupProbe = true
        assertEquals(true, overrides.skipLibVlcStartupProbe)
    }

    @Test
    fun `skip libvlc startup options defaults to false and can be toggled`() {
        val overrides = PlaybackDebugOverrides()

        assertEquals(false, overrides.skipLibVlcStartupOptions)

        overrides.skipLibVlcStartupOptions = true
        assertEquals(true, overrides.skipLibVlcStartupOptions)
    }
}
