package com.miruplay.tv.topping

import com.miruplay.tv.model.AudioDspPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level Topping DAC control: volume, gain, power, PEQ preset push, flat,
 * preamp. State is a write-cache only — the DAC cannot be queried over this
 * interface, so anything changed from the front panel/remote/vendor app is
 * invisible here (same caveat as toppingctl's show).
 */
class ToppingController(private val usb: ToppingUsb) {

    data class Status(
        val attached: Boolean,
        val model: String?,
        val deviceKey: String?,
        val confirmed: Boolean,
        val hidVolume: Boolean,
        val usbPermission: Boolean,
        val reason: String? = null,
        val bands: List<ToppingProtocol.PeqBand> = emptyList(),
        val preampDb: Double? = null,
        val volumeDb: Double? = null,
        val gainOn: Boolean? = null,
    )

    class ToppingCommandException(message: String) : Exception(message)

    private val lock = Any()
    private var lastState: Status? = null

    fun status(): Status {
        val attached = usb.findAttached()
        if (attached.isEmpty()) {
            return Status(false, null, null, false, false, false, reason = "未检测到已确认型号的 Topping DAC（需 DX5 II 或 D90 III Discrete）")
        }
        val a = attached.first()
        val cached = synchronized(lock) { lastState }
        return Status(
            attached = true, model = a.spec.name, deviceKey = a.spec.key,
            confirmed = a.spec.confirmed, hidVolume = a.spec.hidVolume,
            usbPermission = usb.hasPermission(a.device),
            bands = cached?.bands ?: emptyList(),
            preampDb = cached?.preampDb, volumeDb = cached?.volumeDb, gainOn = cached?.gainOn,
        )
    }

    private fun resolve(spec: ToppingProtocol.DeviceSpec? = null): ToppingUsb.AttachedDevice {
        val attached = usb.findAttached()
        val a = (if (spec == null) attached.firstOrNull() else attached.firstOrNull { it.spec.key == spec.key })
            ?: throw ToppingCommandException("未找到 ${spec?.name ?: "已确认型号的 Topping DAC"}")
        if (!a.spec.confirmed) {
            throw ToppingCommandException(
                "${a.spec.name} 的寄存器映射未在实机确认（多个 Topping 型号共用 PID，寄存器含义可能冲突），拒绝写入",
            )
        }
        return a
    }

    private fun sendAll(a: ToppingUsb.AttachedDevice, frames: List<ByteArray>, commit: Boolean = true) {
        for (f in frames) {
            usb.sendFrame(a, f)
            // The vendor app paces writes; don't flood the DSP.
            Thread.sleep(4)
        }
        if (commit) usb.sendFrame(a, ToppingProtocol.commitFrame())
    }

    suspend fun setVolume(db: Double, confirmed: Boolean = false, stepDb: Double = 0.5): Status = withContext(Dispatchers.IO) {
        require(db in ToppingProtocol.VOLUME_MIN_DB..ToppingProtocol.VOLUME_MAX_DB) { "音量 ${db} dB 超出范围（-99..0）" }
        if (db > ToppingProtocol.VOLUME_WARN_DB && !confirmed) {
            throw ToppingCommandException("${db} dB 音量过大，请二次确认后再执行")
        }
        val a = resolve()
        if (!a.spec.hidVolume) throw ToppingCommandException("${a.spec.name} 的音量不在 USB HID 接口上（仅面板/遥控可调）")
        val frames = ToppingProtocol.volumeFrames(db, stepDb)
        sendAll(a, frames)
        rememberVolume(db)
    }

    suspend fun setGain(on: Boolean): Status = withContext(Dispatchers.IO) {
        val a = resolve()
        sendAll(a, listOf(ToppingProtocol.gainFrame(on)))
        remember { copy(gainOn = on) }
    }

    suspend fun setPower(on: Boolean): Status = withContext(Dispatchers.IO) {
        val a = resolve()
        // Power answers the checksum oracle: no commit frame after it.
        usb.sendFrame(a, ToppingProtocol.powerFrame(on))
        status()
    }

    /** Push an in-app [AudioDspPreset]'s PEQ bands + preamp to the DAC hardware. */
    suspend fun applyPreset(preset: AudioDspPreset): ToppingProtocol.PeqMapping = withContext(Dispatchers.IO) {
        val a = resolve()
        val mapping = ToppingProtocol.mapPreset(preset)
        if (mapping.bands.none { it.on }) throw ToppingCommandException("预设中没有可写入设备的有效滤波")
        val frames = buildList {
            mapping.preampDb?.let { addAll(ToppingProtocol.preampFrames(it)) }
            mapping.bands.forEachIndexed { i, band -> addAll(ToppingProtocol.bandFrames(i, band)) }
        }
        sendAll(a, frames)
        synchronized(lock) {
            lastState = status().copy(bands = mapping.bands, preampDb = mapping.preampDb)
        }
        mapping
    }

    /** Disable every band (curve bypass), preamp untouched. */
    suspend fun flat(): Status = withContext(Dispatchers.IO) {
        val a = resolve()
        val frames = (0 until ToppingProtocol.PEQ_REGISTER_COUNT).flatMap { i ->
            ToppingProtocol.bandFrames(i, ToppingProtocol.DISABLED_BAND)
        }
        sendAll(a, frames)
        remember { copy(bands = List(ToppingProtocol.PEQ_REGISTER_COUNT) { ToppingProtocol.DISABLED_BAND }) }
    }

    suspend fun setPreamp(db: Double): Status = withContext(Dispatchers.IO) {
        require(db in ToppingProtocol.PREAMP_MIN_DB..ToppingProtocol.PREAMP_MAX_DB) { "preamp ${db} dB 超出范围（-40..+10）" }
        val a = resolve()
        sendAll(a, ToppingProtocol.preampFrames(db))
        remember { copy(preampDb = db) }
    }

    suspend fun requestUsbPermission(): Boolean = withContext(Dispatchers.IO) {
        val attached = usb.findAttached().firstOrNull() ?: return@withContext false
        usb.requestPermission(attached.device)
    }

    private fun rememberVolume(db: Double): Status = remember { copy(volumeDb = db) }

    private inline fun remember(block: Status.() -> Status): Status = synchronized(lock) {
        val next = block(status())
        lastState = next
        next
    }
}
