package com.xiaoyi.watch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var steps: TextView
    private lateinit var inbox: TextView
    private lateinit var fwd: SwitchCompat
    private lateinit var allowNotice: Button
    private var updating = false
    private var wantFwd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)
        steps = findViewById(R.id.steps)
        inbox = findViewById(R.id.inbox)
        fwd = findViewById(R.id.fwd)
        allowNotice = findViewById(R.id.allowNotice)
        WatchBle.attach(this)
        WatchBle.addListener { render() }

        findViewById<Button>(R.id.scan).setOnClickListener {
            if (needPerms()) return@setOnClickListener
            startWatch("scan")
        }
        findViewById<Button>(R.id.disconnect).setOnClickListener {
            startWatch("off")
        }
        findViewById<Button>(R.id.volDown).setOnClickListener { WatchBle.vol(-10) }
        findViewById<Button>(R.id.volUp).setOnClickListener { WatchBle.vol(10) }
        findViewById<Button>(R.id.brightDown).setOnClickListener { WatchBle.bright(-10) }
        findViewById<Button>(R.id.brightUp).setOnClickListener { WatchBle.bright(10) }
        findViewById<Button>(R.id.flash).setOnClickListener { WatchBle.writeCmd("{\"t\":\"flash\"}") }
        findViewById<Button>(R.id.wake).setOnClickListener { WatchBle.writeCmd("{\"t\":\"wake\"}") }
        allowNotice.setOnClickListener {
            wantFwd = true
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.testMsg).setOnClickListener {
            if (needPerms()) return@setOnClickListener
            WatchBle.sendNotice("小一", "這是一則測試訊息")
        }
        fwd.setOnCheckedChangeListener { _, on ->
            if (updating) return@setOnCheckedChangeListener
            if (on && !NoticeGate.allowed(this)) {
                wantFwd = true
                updating = true
                fwd.isChecked = false
                updating = false
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                return@setOnCheckedChangeListener
            }
            NoticeGate.set(this, on)
            if (on) startWatch("hold")
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (wantFwd && NoticeGate.allowed(this)) {
            wantFwd = false
            NoticeGate.set(this, true)
            startWatch("hold")
        } else if (!NoticeGate.allowed(this) && NoticeGate.enabled(this)) {
            NoticeGate.set(this, false)
        }
        val on = NoticeGate.enabled(this) && NoticeGate.allowed(this)
        updating = true
        fwd.isChecked = on
        updating = false
        allowNotice.visibility = if (NoticeGate.allowed(this)) View.GONE else View.VISIBLE
        render()
    }

    private fun render() {
        status.text = if (WatchBle.connected) "已連線 ${WatchBle.deviceName}" else "尚未連線"
        steps.text = if (WatchBle.steps >= 0) "今日步數 ${WatchBle.steps}" else "今日步數 --"
        inbox.text = WatchBle.inbox
        log.text = WatchBle.lastLog
    }

    private fun startWatch(action: String) {
        val it = Intent(this, WatchService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it)
        if (action == "scan") WatchBle.scanAndConnect(this)
    }

    private fun needPerms(): Boolean {
        val ask = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            ask += Manifest.permission.BLUETOOTH_SCAN
            ask += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            ask += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) ask += Manifest.permission.POST_NOTIFICATIONS
        val miss = ask.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (miss.isEmpty()) return false
        ActivityCompat.requestPermissions(this, miss.toTypedArray(), 8)
        log.text = "請允許藍牙權限後再掃描"
        return true
    }
}
