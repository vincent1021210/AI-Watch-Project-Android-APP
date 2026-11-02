package com.xiaoyi.watch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var steps: TextView
    private lateinit var deviceName: TextView
    private lateinit var scan: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)
        steps = findViewById(R.id.steps)
        deviceName = findViewById(R.id.deviceName)
        scan = findViewById(R.id.scan)
        WatchBle.attach(this)
        WatchBle.addListener { render() }

        scan.setOnClickListener {
            if (WatchBle.connected) {
                startWatchService("off")
            } else {
                if (askBlePerms(log)) return@setOnClickListener
                startWatchService("scan")
            }
        }
        findViewById<View>(R.id.rowNotice).setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }
        findViewById<View>(R.id.rowControl).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        deviceName.text = WatchBle.deviceName.ifBlank { "XiaoYi-Watch" }
        status.text = if (WatchBle.connected) "已連線" else "尚未連線"
        steps.text = if (WatchBle.steps >= 0) "今日步數 ${WatchBle.steps}" else "今日步數 --"
        scan.text = if (WatchBle.connected) "斷線" else "掃描並連線"
        log.text = WatchBle.lastLog
    }
}
