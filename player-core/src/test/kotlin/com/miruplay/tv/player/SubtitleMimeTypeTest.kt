package com.miruplay.tv.player

import androidx.media3.common.MimeTypes
import com.miruplay.tv.model.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMimeTypeTest {
    @Test
    fun `ass and ssa use the Media3 SSA mime type`() {
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeTypeForFormat(SubtitleFormat.ASS))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeTypeForFormat(SubtitleFormat.SSA))
    }
}
