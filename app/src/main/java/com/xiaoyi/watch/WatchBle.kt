package com.xiaoyi.watch

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

object WatchBle {
    val SVC: UUID = UUID.fromString("a1a10001-5e11-4c11-9a11-7869616f7931")
    val STAT: UUID = UUID.fromString("a1a10002-5e11-4c11-9a11-7869616f7931")
    val CMD: UUID = UUID.fromString("a1a10003-5e11-4c11-9a11-7869616f7931")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val main = Handler(Looper.getMainLooper())
    private val ioThread = HandlerThread("xy-ble").apply { start() }
    private val io = Handler(ioThread.looper)
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
    var steps = -1
        private set
    var inbox = "還沒有訊息"
        private set

    private val listeners = mutableListOf<() -> Unit>()
    private var volume = 30
    private var bright = 90
    private var payload = 20
    private val writeLock = Object()
    private val ackLock = Object()
    private val acks = ArrayDeque<String>()
    private var writeDone = false

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
        val scanner = ad.bluetoothLeScanner ?: run {
            log("這支手機沒有低功耗藍牙")
            return
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SVC)).build()
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
            if (!connected) log("沒找到手錶，請確認錶上藍芽已開")
        }, 12000)
    }

    @SuppressLint("MissingPermission")
    private fun connect(context: Context, device: BluetoothDevice) {
        deviceName = device.name ?: "XiaoYi-Watch"
        payload = 20
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected = true
                    log("已連線，讀取服務")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected = false
                    cmdChar = null
                    payload = 20
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
                    } else {
                        g.requestMtu(247)
                    }
                } else {
                    g.requestMtu(247)
                }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                g.requestMtu(247)
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) payload = (mtu - 3).coerceAtLeast(20)
                log("可以控制手錶了")
                writeCmd("{\"t\":\"ping\"}")
                io.postDelayed({ writeCmd("{\"t\":\"steps\"}") }, 400)
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

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                status: Int
            ) {
                synchronized(writeLock) {
                    writeDone = true
                    writeLock.notifyAll()
                }
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
        payload = 20
        log("已斷線")
    }

    fun writeCmd(json: String) {
        io.post { writeNow(json) }
    }

    fun vol(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        writeCmd("{\"t\":\"vol\",\"n\":$volume}")
    }

    fun bright(delta: Int) {
        bright = (bright + delta).coerceIn(0, 100)
        writeCmd("{\"t\":\"bright\",\"n\":$bright}")
    }

    fun sendNotice(app: String, body: String) {
        val title = app.ifBlank { "訊息" }
        val text = body.ifBlank { title }
        io.post { sendNoticeNow(title, text) }
    }

    @SuppressLint("MissingPermission")
    private fun writeNow(json: String): Boolean {
        val g = gatt
        val c = cmdChar
        if (g == null || c == null || !connected) {
            log("還沒連上手錶")
            return false
        }
        if (json.toByteArray(Charsets.UTF_8).size > payload) {
            log("訊息太長")
            return false
        }
        synchronized(writeLock) { writeDone = false }
        c.value = json.toByteArray(Charsets.UTF_8)
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = g.writeCharacteristic(c)
        if (!ok) return false
        synchronized(writeLock) {
            val end = System.currentTimeMillis() + 1500
            while (!writeDone && System.currentTimeMillis() < end) {
                writeLock.wait(200)
            }
            return writeDone
        }
    }

    private fun awaitAck(pred: (String) -> Boolean, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        synchronized(ackLock) {
            while (System.currentTimeMillis() < end) {
                if (acks.any(pred)) return true
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                ackLock.wait(left)
            }
            return acks.any(pred)
        }
    }

    private fun sendNoticeNow(app: String, body: String) {
        if (!connected || cmdChar == null) {
            log("手錶未連線，沒送到")
            return
        }
        val readyAt = System.currentTimeMillis() + 2000
        while (payload < 80 && System.currentTimeMillis() < readyAt) {
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                return
            }
        }
        if (payload < 80) {
            log("藍牙傳輸太小，無法送訊息")
            return
        }
        val pic = NoticePic.pack(app, body)
        val overhead = 40
        var rawChunk = ((payload - overhead) / 4) * 3
        if (rawChunk > 150) rawChunk = 150
        if (rawChunk < 12) {
            log("藍牙傳輸太小，無法送訊息")
            return
        }
        val head = "{\"t\":\"mh\",\"w\":${pic.w},\"h\":${pic.h},\"z\":${pic.bytes.size}}"
        synchronized(ackLock) { acks.clear() }
        if (!writeNow(head) || !awaitAck({ it.contains("\"k\":\"mh\"") && it.contains("\"ok\"") }, 1200)) {
            log("手錶沒有接下這則訊息")
            return
        }
        var off = 0
        while (off < pic.bytes.size) {
            val n = minOf(rawChunk, pic.bytes.size - off)
            val slice = pic.bytes.copyOfRange(off, off + n)
            val b64 = android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)
            val chunk = "{\"t\":\"mc\",\"o\":$off,\"d\":\"$b64\"}"
            synchronized(ackLock) { acks.clear() }
            val want = "\"o\":$off"
            val ok = writeNow(chunk) && awaitAck({
                it.contains("\"k\":\"mc\"") && it.contains("\"ok\"") && it.contains(want)
            }, 1200)
            if (!ok) {
                log("訊息送到一半中斷")
                return
            }
            off += n
        }
        remember("$app  $body")
    }

    private fun takeStat(bytes: ByteArray?) {
        if (bytes == null) return
        lastStat = bytes.toString(Charsets.UTF_8)
        val i = lastStat.indexOf("\"n\":")
        if (lastStat.contains("\"steps\"") && i >= 0) {
            val n = lastStat.substring(i + 4).takeWhile { it.isDigit() }.toIntOrNull()
            if (n != null) steps = n
        }
        synchronized(ackLock) {
            acks.addLast(lastStat)
            while (acks.size > 8) acks.removeFirst()
            ackLock.notifyAll()
        }
        if (!lastStat.contains("\"k\":\"mc\"")) log("手錶 $lastStat")
    }

    private fun remember(line: String) {
        val clean = line.replace('\n', ' ').trim()
        val next = (clean + "\n" + inbox.lineSequence().filter { it != "還沒有訊息" }.joinToString("\n"))
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(6)
            .joinToString("\n")
        inbox = next.ifBlank { "還沒有訊息" }
        log("已送到手錶")
    }

    private fun log(msg: String) {
        lastLog = msg
        main.post { listeners.toList().forEach { it() } }
    }
}
