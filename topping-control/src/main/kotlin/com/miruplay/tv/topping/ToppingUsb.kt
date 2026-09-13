package com.miruplay.tv.topping

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * USB Host access to the Topping DAC's HID control interface. No root, no
 * dependency: the same 16-byte frames toppingctl sends are written here via
 * interrupt-OUT (preferred — exact byte stream on the wire) or SET_REPORT.
 */
class ToppingUsb(private val context: Context) {

    data class AttachedDevice(val device: UsbDevice, val spec: ToppingProtocol.DeviceSpec, val usbInterface: UsbInterface, val outEndpoint: UsbEndpoint?)

    class ToppingUsbException(message: String) : Exception(message)

    private val usbManager get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Enumerate Thesycon-VID HID interfaces and match on the USB PRODUCT STRING
     *  — the PID (0x8750) is shared across Topping models whose registers collide. */
    fun findAttached(): List<AttachedDevice> {
        val found = mutableListOf<AttachedDevice>()
        for (device in usbManager.deviceList.values) {
            if (device.vendorId != ToppingProtocol.VENDOR_ID) continue
            val spec = ToppingProtocol.DEVICES.firstOrNull { s ->
                val product = device.productName?.uppercase()?.filter { it.isLetterOrDigit() }
                product != null && s.productMatch.any { p -> product.startsWith(p) || p in product }
            } ?: continue
            // One DAC can expose several HID interfaces; any HID (class 03) interface
            // with an interrupt-OUT endpoint speaks the control protocol.
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_HID) continue
                var out: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_OUT) out = ep
                }
                found += AttachedDevice(device, spec, iface, out)
            }
        }
        return found
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /** Fire the system USB-permission dialog; suspends until the user answers. */
    suspend fun requestPermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { cont ->
        val action = "com.miruplay.topping.USB_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != action) return
                context.unregisterReceiver(this)
                cont.resume(usbManager.hasPermission(device))
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(action))
        }
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(action),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usbManager.requestPermission(device, pi)
    }

    /** Send one frame. Interrupt-OUT keeps the exact toppingctl byte stream;
     *  SET_REPORT (class request 0x09, output report, id 0) is the fallback. */
    fun sendFrame(attached: AttachedDevice, frame: ByteArray) {
        val wire = ToppingProtocol.wireBytes(attached.spec, frame)
        if (!usbManager.hasPermission(attached.device)) {
            throw ToppingUsbException("没有 USB 权限，请在设备弹窗中允许访问")
        }
        val connection: UsbDeviceConnection = usbManager.openDevice(attached.device)
            ?: throw ToppingUsbException("无法打开 USB 设备")
        try {
            if (!connection.claimInterface(attached.usbInterface, true)) {
                throw ToppingUsbException("无法占用 USB HID 接口")
            }
            val ep = attached.outEndpoint
            val sent = if (ep != null) {
                connection.bulkTransfer(ep, wire, wire.size, 200)
            } else {
                connection.controlTransfer(
                    /* requestType = */ 0x21,   // Host-to-device | Class | Interface
                    /* request = */ 0x09,       // SET_REPORT
                    /* value = */ 0x0200,       // Output report, report id 0
                    /* index = */ attached.usbInterface.id,
                    wire, wire.size, 200,
                )
            }
            if (sent < 0) throw ToppingUsbException("USB 写入失败（帧未送达）")
        } finally {
            connection.close()
        }
    }
}
