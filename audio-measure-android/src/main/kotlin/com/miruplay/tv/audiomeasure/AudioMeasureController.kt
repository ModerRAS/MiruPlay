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
        private const val SWEEP_F1_HZ = 10.0
        private const val SWEEP_F2_HZ = 20_000.0
        /** Dual sweep: different lengths ⇒ different drift-quantization lattices. */
        private val SWEEP_DURATIONS_S = listOf(4.0, 4.17)
        private const val ROOM_TAIL_S = 1.0
        /** Play the sweep at −6 dBFS; the mic gain takes care of absolute level. */
        private const val SWEEP_AMPLITUDE = 0.5
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
     * Play the dual reference sweep through the default output while capturing
     * the room with the probed microphone, then run the fail-closed pipeline.
     */
    suspend fun measureRoom(
        calibrationText: String? = null,
        onProgress: (String) -> Unit = {},
    ): MeasureOutcome = withContext(Dispatchers.IO) {
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
     * Analyze an imported WAV (16-bit PCM mono, our standard sweep). Only for
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
        val durationS = samples.size / fs.toDouble()
        val sweep = LogSweepGenerator.generate(SWEEP_F1_HZ, SWEEP_F2_HZ, durationS, fs)
        onProgress("分析房间响应…")
        val measurement = RoomMeasurer.measure(
            recordings = listOf(samples),
            sweeps = listOf(sweep),
            fs = fs,
            irLengthS = IR_LENGTH_S,
            assumeSharedClock = true,
            calibration = calibrationText?.let { MicCalibration.parse(it).calibration },
        )
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
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
            track.play()
            // Stereo: duplicate the mono sweep on both channels.
            val stereo = ShortArray(pcm.size * 2)
            for (i in pcm.indices) {
                stereo[2 * i] = pcm[i]
                stereo[2 * i + 1] = pcm[i]
            }
            var written = 0
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
        return DoubleArray(got) { recording[it] / 32768.0 }
    }
}
