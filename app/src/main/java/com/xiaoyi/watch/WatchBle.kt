package com.xiaoyi.watch

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

object WatchBle {
    val SVC: UUID = UUID.fromString("a1a10001-5e11-4c11-9a11-7869616f7931")
    val STAT: UUID = UUID.fromString("a1a10002-5e11-4c11-9a11-7869616f7931")
    val CMD: UUID = UUID.fromString("a1a10003-5e11-4c11-9a11-7869616f7931")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val main = Handler(Looper.getMainLooper())
    private var adapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    var connected = false
        private set
    var deviceName = ""
        private set
    var lastStat = ""
        private set
    var lastLog = "請先掃描手錶"
        private set

    private val listeners = mutableListOf<() -> Unit>()
    private var volume = 30
    private var bright = 90

    fun addListener(fn: () -> Unit) {
        if (!listeners.contains(fn)) listeners.add(fn)
    }

    fun attach(context: Context) {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = mgr.adapter
    }

    @SuppressLint("MissingPermission")
    fun scanAndConnect(context: Context) {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            log("請先打開手機藍牙")
            return
        }
        log("掃描 XiaoYi-Watch…")
        val scanner = ad.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SVC))
            .build()
        val nameFilter = ScanFilter.Builder().setDeviceName("XiaoYi-Watch").build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (!name.startsWith("XiaoYi") && !name.startsWith("AI-Watch")) return
                scanner.stopScan(this)
                log("找到 $name，連線中")
                connect(context, result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                log("掃描失敗 $errorCode")
            }
        }
        scanner.startScan(listOf(filter, nameFilter), settings, cb)
        main.postDelayed({
            try {
                scanner.stopScan(cb)
            } catch (_: Exception) {
            }
            if (!connected) log("沒找到手錶，請確認錶上藍雅已開")
        }, 12000)
    }

    @SuppressLint("MissingPermission")
    private fun connect(context: Context, device: BluetoothDevice) {
        deviceName = device.name ?: "XiaoYi-Watch"
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected = true
                    log("已連線，讀取服務")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected = false
                    cmdChar = null
                    log("已斷線")
                    try {
                        g.close()
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val svc = g.getService(SVC)
                if (svc == null) {
                    log("手錶沒有小一服務")
                    return
                }
                cmdChar = svc.getCharacteristic(CMD)
                val stat = svc.getCharacteristic(STAT)
                if (stat != null) {
                    g.setCharacteristicNotification(stat, true)
                    val desc = stat.getDescriptor(CCCD)
                    if (desc != null) {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(desc)
                    }
                    g.readCharacteristic(stat)
                }
                log("可以控制手錶了")
                writeCmd("{\"t\":\"ping\"}")
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (ch.uuid == STAT) takeStat(ch.value)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                if (ch.uuid == STAT) takeStat(ch.value)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        cmdChar = null
        connected = false
        deviceName = ""
        log("已斷線")
    }

    @SuppressLint("MissingPermission")
    fun writeCmd(json: String) {
        val g = gatt
        val c = cmdChar
        if (g == null || c == null || !connected) {
            log("還沒連上手錶")
            return
        }
        c.value = json.toByteArray(Charsets.UTF_8)
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        g.writeCharacteristic(c)
        log("送出 $json")
    }

    fun vol(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        writeCmd("{\"t\":\"vol\",\"n\":$volume}")
    }

    fun bright(delta: Int) {
        bright = (bright + delta).coerceIn(0, 100)
        writeCmd("{\"t\":\"bright\",\"n\":$bright}")
    }

    private fun takeStat(bytes: ByteArray?) {
        if (bytes == null) return
        lastStat = bytes.toString(Charsets.UTF_8)
        log("手錶 $lastStat")
    }

    private fun log(msg: String) {
        lastLog = msg
        main.post { listeners.toList().forEach { it() } }
    }
}
