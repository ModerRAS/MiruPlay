package com.miruplay.tv.player

import java.io.File
import com.miruplay.tv.model.VideoSignalDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcFrameProbeBridgeTest {
    @Test
    fun `armFirstFrameProbe prepares files and forwards probe attachment`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 91L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)

        val armResult = bridge.armFirstFrameProbe(
            playerInstance = 77L,
            outputDir = tempDir,
            label = "hdr proof",
            windowWidth = 1920,
            windowHeight = 1080,
        )

        assertTrue(armResult.success)
        assertNotNull(armResult.session)
        assertEquals(77L, armResult.session!!.playerInstance)
        assertEquals(91L, armResult.session!!.probeHandle)
        assertEquals(File(tempDir, "hdr_proof_vmem.txt"), armResult.session!!.metadataFile)
        assertEquals(File(tempDir, "hdr_proof_vmem_preview.ppm"), armResult.session!!.previewFile)
        assertEquals(File(tempDir, "hdr_proof_vmem_luma.pgm"), armResult.session!!.lumaFile)
        assertEquals(File(tempDir, "hdr_proof_vmem.raw"), armResult.session!!.rawFrameFile)
        assertEquals(77L, invoker.attachedPlayerInstance)
        assertEquals(91L, invoker.attachedProbeHandle)
        assertEquals(1920, invoker.attachedWindowWidth)
        assertEquals(1080, invoker.attachedWindowHeight)
    }

    @Test
    fun `awaitFirstFrameProbe reports success when native wait completes and metadata exists`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 19L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)
        val session = bridge.armFirstFrameProbe(
            playerInstance = 11L,
            outputDir = tempDir,
            label = "hdr",
            windowWidth = 1920,
            windowHeight = 1080,
        ).session!!
        Thread {
            Thread.sleep(75L)
            session.metadataFile.writeText("chroma=I0AL")
            session.rawFrameFile.writeBytes(byteArrayOf(1, 2, 3))
        }.start()

        val result = bridge.awaitFirstFrameProbe(session, timeoutMs = 2_000)

        assertTrue(result.success)
        assertEquals(0, result.resultCode)
        assertTrue(result.metadataFile.isFile)
        assertTrue(result.rawFrameFile.isFile)
    }

    @Test
    fun `awaitFirstFrameProbe reports failure when metadata is missing after native success`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 31L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)
        val session = bridge.armFirstFrameProbe(
            playerInstance = 11L,
            outputDir = tempDir,
            label = "hdr",
            windowWidth = 1920,
            windowHeight = 1080,
        ).session!!

        val result = bridge.awaitFirstFrameProbe(session, timeoutMs = 150)

        assertFalse(result.success)
        assertEquals(LibVlcFrameProbeBridge.WAIT_TIMEOUT, result.resultCode)
    }

    @Test
    fun `releaseProbe forwards player and probe handles`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 64L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)
        val session = bridge.armFirstFrameProbe(
            playerInstance = 42L,
            outputDir = tempDir,
            label = "hdr",
            windowWidth = 1920,
            windowHeight = 1080,
        ).session!!

        bridge.releaseProbe(session)

        assertEquals(42L, invoker.releasedPlayerInstance)
        assertEquals(64L, invoker.releasedProbeHandle)
    }

    @Test
    fun `preferredLibVlcProbeOutputChroma leaves hdr sources on libvlc native chroma path`() {
        assertNull(
            preferredLibVlcProbeOutputChroma(
                VideoSignalDescriptor(bitDepth = 10),
            ),
        )
        assertNull(
            preferredLibVlcProbeOutputChroma(
                VideoSignalDescriptor(bitDepth = 8),
            ),
        )
    }

    @Test
    fun `resolvePreferredLibVlcProbeOutputChroma honors explicit debug override`() {
        assertEquals(
            "RV32",
            resolvePreferredLibVlcProbeOutputChroma(
                signalDescriptor = VideoSignalDescriptor(bitDepth = 10),
                debugConfig = LibVlcDebugConfig(displayChroma = "RV32"),
            ),
        )
    }

    @Test
    fun `armFirstFrameProbe forwards preferred output chroma hint to native invoker`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 51L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)

        val armResult = bridge.armFirstFrameProbe(
            playerInstance = 21L,
            outputDir = tempDir,
            label = "hdr",
            preferredOutputChroma = "P010",
            windowWidth = 1920,
            windowHeight = 1080,
        )

        assertTrue(armResult.success)
        assertEquals("P010", invoker.createProbePreferredOutputChroma)
    }

    @Test
    fun `armFirstFrameProbe clamps invalid probe window bounds before native attach`() {
        val tempDir = createTempDir(prefix = "libvlc-frame-probe-")
        val invoker = FakeLibVlcFrameProbeInvoker(
            createProbeAction = { _, _, _, _, _ -> 88L },
            attachProbeAction = { _, _, _, _ -> 0 },
        )
        val bridge = LibVlcFrameProbeBridge(invoker)

        val armResult = bridge.armFirstFrameProbe(
            playerInstance = 33L,
            outputDir = tempDir,
            label = "hdr",
            windowWidth = 0,
            windowHeight = -12,
        )

        assertTrue(armResult.success)
        assertEquals(1, invoker.attachedWindowWidth)
        assertEquals(1, invoker.attachedWindowHeight)
    }

    @Test
    fun `default frame probe constants stay aligned with VMEM stream attach expectations`() {
        assertEquals(
            LibVlcVmemStreamBridge.DEFAULT_VIDEO_OUTPUT_MODULE,
            LibVlcFrameProbeBridge.DEFAULT_VIDEO_OUTPUT_MODULE,
        )
        assertEquals(
            LibVlcVmemStreamBridge.DEFAULT_WINDOW_MODULE,
            LibVlcFrameProbeBridge.DEFAULT_WINDOW_MODULE,
        )
        assertEquals(
            LibVlcVmemStreamBridge.DEFAULT_DECODER_DEVICE,
            LibVlcFrameProbeBridge.DEFAULT_DECODER_DEVICE,
        )
    }
}

private class FakeLibVlcFrameProbeInvoker(
    private val createProbeAction: (File, File, File, File, String?) -> Long,
    private val attachProbeAction: (Long, Long, Int, Int) -> Int,
) : LibVlcFrameProbeInvoker {
    var attachedPlayerInstance: Long = 0L
    var attachedProbeHandle: Long = 0L
    var attachedWindowWidth: Int = 0
    var attachedWindowHeight: Int = 0
    var releasedPlayerInstance: Long = 0L
    var releasedProbeHandle: Long = 0L
    var createProbePreferredOutputChroma: String? = null

    override fun createProbe(
        metadataFile: File,
        previewFile: File,
        lumaFile: File,
        rawFrameFile: File,
        preferredOutputChroma: String?,
    ): Long {
        createProbePreferredOutputChroma = preferredOutputChroma
        return createProbeAction(
            metadataFile,
            previewFile,
            lumaFile,
            rawFrameFile,
            preferredOutputChroma,
        )
    }

    override fun attachProbe(
        playerInstance: Long,
        probeHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int {
        attachedPlayerInstance = playerInstance
        attachedProbeHandle = probeHandle
        attachedWindowWidth = windowWidth
        attachedWindowHeight = windowHeight
        return attachProbeAction(playerInstance, probeHandle, windowWidth, windowHeight)
    }

    override fun releaseProbe(
        playerInstance: Long,
        probeHandle: Long,
    ) {
        releasedPlayerInstance = playerInstance
        releasedProbeHandle = probeHandle
    }
}
