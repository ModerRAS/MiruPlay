package com.miruplay.tv.audiomeasure

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.miruplay.tv.measure.LogSweep
import com.miruplay.tv.measure.LogSweepGenerator
import com.miruplay.tv.measure.MicCalibration
import com.miruplay.tv.measure.RoomMeasurer
import com.miruplay.tv.measure.MeasurementOps
import com.miruplay.tv.measure.WavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Android orchestration for sweep measurement (Phase 2/3):
 *  - capability probe (USB-mic input enumeration + RECORD_AUDIO gate)
 *  - sweep playback via [AudioTrack] and room capture via [AudioRecord],
 *    running concurrently per sweep (dual sweep for the fail-closed drift gate)
 *  - offline WAV import for captures made over a shared clock (loopback)
 *
 * DSP math is delegated to audio-measure-core; this class owns audio I/O only
 * and never touches the playback DSP path.
 */
class AudioMeasureController(private val context: Context) {

    companion object {
        const val SAMPLE_RATE_HZ = 48_000
        private const val SWEEP_F1_HZ = 20.0
        private const val SWEEP_F2_HZ = 20_000.0
        /**
         * Start at 20 Hz, not 10 Hz: 10–20 Hz is subsonic and only generates
         * huge driver excursion (overload/distortion) that pollutes the sweep
         * before anything audible. REW-level guidance: keep sweeps away from
         * subsonic unless measuring a sub specifically.
         */
        /** Dual sweep: different lengths ⇒ different drift-quantization lattices. */
        private val SWEEP_DURATIONS_S = listOf(4.0, 4.17)
        private const val ROOM_TAIL_S = 1.0
        /** Play the sweep at −12 dBFS like REW: protects drivers and mic headroom. */
        private const val SWEEP_AMPLITUDE = 0.25
        /** Pink noise at −20 dBFS: comfortable level for matching TV volume. */
        private const val NOISE_AMPLITUDE = 0.1
        private const val IR_LENGTH_S = 1.0
    }

    data class Capabilities(
        val available: Boolean,
        val reason: String?,
        val inputDeviceName: String?,
    )

    data class MeasureOutcome(
        val measurement: RoomMeasurer.Measurement,
        val capabilities: Capabilities,
    )

    class MeasureException(message: String) : Exception(message)

    /**
     * Download a UMIK-1 calibration file from miniDSP by serial number,
     * validate it, and return it ready to persist. No network stack
     * dependency: HttpURLConnection keeps this module plain.
     */
    suspend fun downloadUmikCalibration(
        serial: String,
        incidence: com.miruplay.tv.measure.MicCalibration.Incidence,
    ): com.miruplay.tv.repository.MicCalibrationSettings = withContext(Dispatchers.IO) {
        val url = java.net.URL(UmikCalibrationDownloader.buildUrl(serial, incidence))
        val text = url.openConnection().let { connection ->
            (connection as java.net.HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            try {
                if (connection.responseCode !in 200..299) {
                    throw MeasureException("下载校准文件失败（HTTP ${connection.responseCode}）")
                }
                connection.inputStream.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8).also {
                        if (it.length > com.miruplay.tv.measure.MicCalibration.MAX_TEXT_CHARS) {
                            throw MeasureException("校准文件过大")
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (UmikCalibrationDownloader.isErrorBody(text)) {
            throw MeasureException("miniDSP 上没有找到该序列号（$serial）的校准数据，请核对序列号")
        }
        val settings = UmikCalibrationDownloader.buildSettings(serial, incidence, text)
        // Guard against truncated/hiccup downloads: the official file always
        // reaches 20 kHz, so a short coverage means something went wrong.
        val coverage = com.miruplay.tv.measure.MicCalibration.parse(settings.data).calibration.coverageWarning()
        if (coverage != null) {
            throw MeasureException(coverage)
        }
        settings
    }

    /** Enumerate usable microphone inputs and gate on the RECORD_AUDIO permission. */
    fun probe(): Capabilities {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val mics = inputs.filter {
            it.type in intArrayOf(
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
            ) && it.isSource
        }
        if (mics.isEmpty()) {
            return Capabilities(false, "未找到可用的麦克风输入设备（Android TV 需要支持 USB 音频输入）", null)
        }
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val name = mics.firstOrNull()?.let { describe(it) }
            return Capabilities(false, "缺少 RECORD_AUDIO 运行时权限", name)
        }
        val preferred = mics.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: mics.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: mics.first()
        return Capabilities(true, null, describe(preferred))
    }

    private fun describe(device: AudioDeviceInfo): String =
        "id=${device.id} type=${device.type} ${device.productName}"

    /**
     * Name of the output the sweep will play through (HDMI preferred, then
     * built-in speaker, then the first sink). For the wizard's device-check
     * step; playback itself always uses the default routing.
     */
    fun describeDefaultOutput(): String? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sinks = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val preferred = sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_HDMI }
            ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: sinks.firstOrNull()
        return preferred?.let { describe(it) }
    }

    private val noiseRunning = AtomicBoolean(false)
    @Volatile private var noiseTrack: AudioTrack? = null

    /**
     * Loop pink noise (Paul Kellet approximation) at −20 dBFS for the
     * wizard's volume-matching step: user raises/lowers TV volume until the
     * noise matches normal listening loudness. Returns true when playing.
     */
    fun startPinkNoise(): Boolean {
        if (noiseRunning.getAndSet(true)) return true
        val fs = SAMPLE_RATE_HZ
        val minBuf = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            noiseRunning.set(false)
            return false
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(fs)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, 16_384) * 2)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            noiseRunning.set(false)
            return false
        }
        noiseTrack = track
        kotlin.concurrent.thread(name = "pink-noise", isDaemon = true) {
            try {
                track.play()
                val rnd = java.util.Random()
                val chunk = ShortArray(4096)
                var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
                var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
                while (noiseRunning.get()) {
                    for (i in chunk.indices step 2) {
                        val white = rnd.nextDouble() * 2.0 - 1.0
                        b0 = 0.99886 * b0 + white * 0.0555179
                        b1 = 0.99332 * b1 + white * 0.0750759
                        b2 = 0.96900 * b2 + white * 0.1538520
                        b3 = 0.86650 * b3 + white * 0.3104856
                        b4 = 0.55000 * b4 + white * 0.5329522
                        b5 = -0.7616 * b5 - white * 0.0168980
                        val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                        b6 = white * 0.115926
                        val v = (pink * NOISE_AMPLITUDE * Short.MAX_VALUE)
                            .roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        chunk[i] = v
                        chunk[i + 1] = v
                    }
                    if (track.write(chunk, 0, chunk.size) <= 0) break
                }
            } finally {
                runCatching { track.stop() }
                track.release()
                noiseTrack = null
            }
        }
        return true
    }

    fun stopPinkNoise() {
        noiseRunning.set(false)
        noiseTrack?.let { runCatching { it.pause() } }
    }

    /**
     * Play the dual reference sweep through the default output while capturing
     * the room with the probed microphone, then run the fail-closed pipeline.
     */
    suspend fun measureRoom(
        calibrationText: String? = null,
        onProgress: (String) -> Unit = {},
    ): MeasureOutcome = withContext(Dispatchers.IO) {
        stopPinkNoise() // noise from the volume-check step must not pollute capture
        val capabilities = probe()
        if (!capabilities.available) throw MeasureException(capabilities.reason ?: "microphone unavailable")
        val calibration = calibrationText?.let { MicCalibration.parse(it).calibration }
        val sweeps = SWEEP_DURATIONS_S.map {
            LogSweepGenerator.generate(SWEEP_F1_HZ, SWEEP_F2_HZ, it, SAMPLE_RATE_HZ)
        }
        val recordings = mutableListOf<DoubleArray>()
        sweeps.forEachIndexed { index, sweep ->
            onProgress("播放扫频 ${index + 1}/${sweeps.size}（${SWEEP_DURATIONS_S[index]}s）…")
            recordings += playAndCapture(sweep)
        }
        onProgress("分析房间响应…")
        val measurement = RoomMeasurer.measure(
            recordings = recordings,
            sweeps = sweeps,
            fs = SAMPLE_RATE_HZ,
            irLengthS = IR_LENGTH_S,
            calibration = calibration,
        )
        MeasureOutcome(measurement, capabilities)
    }

    /**
     * Analyze an imported WAV (16-bit PCM mono). The recording is a standard
     * sweep + room tail; the reference sweep length is unknown from the WAV
     * alone, so probe our two standard durations and keep the one whose IR is
     * sharpest (a wrong-length reference shatters the IR peak). Only for
     * captures over a shared clock (loopback); mic captures must use
     * [measureRoom] so the drift gate can run.
     */
    suspend fun importWav(
        wavBytes: ByteArray,
        calibrationText: String? = null,
        onProgress: (String) -> Unit = {},
    ): MeasureOutcome = withContext(Dispatchers.IO) {
        onProgress("解析 WAV…")
        val (samples, fs) = WavFile.read16bitMono(wavBytes)
        val calibration = calibrationText?.let { MicCalibration.parse(it).calibration }
        // 录音至少要容纳扫频 + 半秒 IR 余量，否则无法定参考长度。
        val candidates = SWEEP_DURATIONS_S.filter { samples.size >= (it + 0.5) * fs }
        if (candidates.isEmpty()) {
            throw MeasureException("WAV 太短：需要 ≥ ${SWEEP_DURATIONS_S.first().toInt() + 1} 秒的标准扫频录音（含房间尾音）")
        }
        onProgress("分析房间响应…")
        var best: RoomMeasurer.Measurement? = null
        var bestSharpness = -1.0
        for (durationS in candidates) {
            val sweep = LogSweepGenerator.generate(SWEEP_F1_HZ, SWEEP_F2_HZ, durationS, fs)
            val measurement = RoomMeasurer.measure(
                recordings = listOf(samples),
                sweeps = listOf(sweep),
                fs = fs,
                irLengthS = IR_LENGTH_S,
                assumeSharedClock = true,
                calibration = calibration,
            )
            val sharpness = MeasurementOps.irSharpness(measurement.ir)
            if (sharpness > bestSharpness) {
                bestSharpness = sharpness
                best = measurement
            }
        }
        val measurement = best ?: throw MeasureException("WAV 分析失败")
        MeasureOutcome(measurement, Capabilities(true, null, "shared-clock import"))
    }

    /** Concurrent play + record for one sweep; returns the captured mono samples. */
    @SuppressLint("MissingPermission")
    private fun playAndCapture(sweep: LogSweep): DoubleArray {
        val fs = SAMPLE_RATE_HZ
        val tailSamples = (ROOM_TAIL_S * fs).toInt()
        val expectedSamples = sweep.sweep.size + tailSamples
        val recording = ShortArray(expectedSamples)
        val collected = java.util.concurrent.atomic.AtomicInteger(0)
        val running = AtomicBoolean(true)

        val minBuf = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw MeasureException("AudioRecord 最小缓冲区无效（$minBuf）")
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, fs,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 16_384) * 2,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw MeasureException("AudioRecord 初始化失败（检查 RECORD_AUDIO 权限与 USB 麦克风路由）")
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(fs)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(
                AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                16_384,
            ) * 2)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            audioRecord.release()
            track.release()
            throw MeasureException("AudioTrack 初始化失败")
        }

        val recorder = Thread {
            audioRecord.startRecording()
            val chunk = ShortArray(4_096)
            while (running.get() && collected.get() < expectedSamples) {
                val read = audioRecord.read(chunk, 0, chunk.size)
                if (read <= 0) break
                val toCopy = minOf(read, expectedSamples - collected.get())
                System.arraycopy(chunk, 0, recording, collected.get(), toCopy)
                collected.addAndGet(toCopy)
            }
        }
        recorder.start()
        try {
            val pcm = ShortArray(sweep.sweep.size)
            for (i in pcm.indices) {
                pcm[i] = (sweep.sweep[i] * SWEEP_AMPLITUDE * Short.MAX_VALUE).roundToInt().toShort()
            }
            // Stereo: duplicate the mono sweep on both channels.
            val stereo = ShortArray(pcm.size * 2)
            for (i in pcm.indices) {
                stereo[2 * i] = pcm[i]
                stereo[2 * i + 1] = pcm[i]
            }
            // Prefill the track buffer BEFORE play(): play() on an empty buffer
            // underruns immediately and produces a start-of-measurement pop.
            val trackBufferShorts = maxOf(
                AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                16_384,
            ) * 2 / 2
            var written = track.write(stereo, 0, minOf(trackBufferShorts, stereo.size))
            track.play()
            while (written < stereo.size) {
                written += track.write(stereo, written, stereo.size - written)
            }
            track.stop()
            // Room tail continues after the sweep; capture it.
            val deadline = System.nanoTime() + (ROOM_TAIL_S * 2).toLong() * 1_000_000_000L
            while (running.get() && collected.get() < expectedSamples && System.nanoTime() < deadline) {
                Thread.sleep(20)
            }
        } finally {
            running.set(false)
            try {
                recorder.join(2_000)
            } catch (_: InterruptedException) {
            }
            audioRecord.stop()
            audioRecord.release()
            track.release()
        }
        val got = collected.get()
        if (got < expectedSamples * 9 / 10) {
            throw MeasureException("麦克风采集样本不足（$got / $expectedSamples），请检查输入路由")
        }
        val clipLimit = (0.995 * Short.MAX_VALUE).toInt()
        var clipped = 0
        for (i in 0 until got) {
            val s = recording[i].toInt()
            if (s >= clipLimit || s <= -clipLimit) clipped++
        }
        if (clipped * 1000 > got) {
            throw MeasureException("麦克风输入过载（$clipped 个样本削波），请调低设备音量或麦克风增益后重试")
        }
        return DoubleArray(got) { recording[it] / 32768.0 }
    }
}
