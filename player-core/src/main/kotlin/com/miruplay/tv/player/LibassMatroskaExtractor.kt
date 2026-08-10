@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.util.Log
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor
import `is`.xyz.mpv.subtitle.NativeAssFont
import java.io.IOException

internal class LibassMatroskaExtractor(
    private val onFont: (NativeAssFont) -> Unit,
) : MatroskaExtractor(FLAG_EMIT_RAW_SUBTITLE_DATA) {
    private val attachments = LibassAttachmentCollector { font ->
        Log.i("MiruLibass", "Matroska font attachment name=${font.name} bytes=${font.data.size}")
        onFont(font)
    }

    override fun getElementType(id: Int): Int = when (id) {
        ID_ATTACHMENTS,
        ID_ATTACHED_FILE,
        -> EbmlProcessor.ELEMENT_TYPE_MASTER
        ID_FILE_NAME,
        ID_FILE_MIME_TYPE,
        -> EbmlProcessor.ELEMENT_TYPE_STRING
        ID_FILE_DATA -> EbmlProcessor.ELEMENT_TYPE_BINARY
        else -> super.getElementType(id)
    }

    override fun isLevel1Element(id: Int): Boolean =
        id == ID_ATTACHMENTS || super.isLevel1Element(id)

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        when (id) {
            ID_ATTACHMENTS -> Unit
            ID_ATTACHED_FILE -> attachments.startFile()
            else -> super.startMasterElement(id, contentPosition, contentSize)
        }
    }

    override fun endMasterElement(id: Int) {
        when (id) {
            ID_ATTACHMENTS -> Unit
            ID_ATTACHED_FILE -> attachments.endFile()
            else -> super.endMasterElement(id)
        }
    }

    override fun stringElement(id: Int, value: String) {
        when (id) {
            ID_FILE_NAME -> attachments.setName(value)
            ID_FILE_MIME_TYPE -> attachments.setMimeType(value)
            else -> super.stringElement(id, value)
        }
    }

    @Throws(IOException::class)
    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        if (id != ID_FILE_DATA) {
            super.binaryElement(id, contentSize, input)
            return
        }
        if (!attachments.canReadData(contentSize)) {
            input.skipFully(contentSize)
            attachments.rejectData()
            return
        }
        ByteArray(contentSize).also { data ->
            input.readFully(data, 0, contentSize)
            attachments.setData(data)
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        attachments.resetPartialFile()
        super.seek(position, timeUs)
    }

    private companion object {
        const val ID_ATTACHMENTS = 0x1941A469
        const val ID_ATTACHED_FILE = 0x61A7
        const val ID_FILE_NAME = 0x466E
        const val ID_FILE_MIME_TYPE = 0x4660
        const val ID_FILE_DATA = 0x465C
    }
}

internal class LibassAttachmentCollector(
    private val onFont: (NativeAssFont) -> Unit,
) {
    private var pending: PendingAttachment? = null
    private var fontCount = 0
    private var totalFontBytes = 0L

    fun startFile() {
        pending = PendingAttachment()
    }

    fun setName(name: String) {
        pending?.name = name
    }

    fun setMimeType(mimeType: String) {
        pending?.mimeType = mimeType
    }

    fun canReadData(size: Int): Boolean =
        pending != null && size in 1..MAX_FONT_BYTES

    fun setData(data: ByteArray) {
        pending?.data = data
    }

    fun rejectData() {
        pending?.rejected = true
    }

    fun endFile() {
        val attachment = pending ?: return
        pending = null
        val data = attachment.data ?: return
        if (
            attachment.rejected ||
            !isFont(attachment.name, attachment.mimeType) ||
            fontCount >= MAX_FONT_COUNT ||
            totalFontBytes + data.size > MAX_TOTAL_FONT_BYTES
        ) {
            return
        }
        val name = attachment.name.trim().ifBlank {
            "attachment-${fontCount + 1}.${extensionFor(attachment.mimeType)}"
        }
        fontCount++
        totalFontBytes += data.size
        onFont(NativeAssFont(name, data))
    }

    fun resetPartialFile() {
        pending = null
    }

    private fun isFont(name: String, mimeType: String): Boolean =
        mimeType.trim().lowercase() in FONT_MIME_TYPES ||
            name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in FONT_EXTENSIONS

    private fun extensionFor(mimeType: String): String = when (mimeType.trim().lowercase()) {
        "font/otf", "application/vnd.ms-opentype" -> "otf"
        "font/collection" -> "ttc"
        else -> "ttf"
    }

    private data class PendingAttachment(
        var name: String = "",
        var mimeType: String = "",
        var data: ByteArray? = null,
        var rejected: Boolean = false,
    )

    private companion object {
        const val MAX_FONT_BYTES = 32 * 1024 * 1024
        const val MAX_TOTAL_FONT_BYTES = 128L * 1024L * 1024L
        const val MAX_FONT_COUNT = 64
        val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc")
        val FONT_MIME_TYPES = setOf(
            "application/x-truetype-font",
            "application/x-font-ttf",
            "application/vnd.ms-opentype",
            "application/font-sfnt",
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "font/collection",
        )
    }
}
