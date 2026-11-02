package com.xiaoyi.watch

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class NoticeActivity : AppCompatActivity() {
    private lateinit var log: TextView
    private lateinit var inbox: TextView
    private lateinit var fwd: SwitchCompat
    private lateinit var allowNotice: Button
    private var updating = false
    private var wantFwd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notice)
        log = findViewById(R.id.log)
        inbox = findViewById(R.id.inbox)
        fwd = findViewById(R.id.fwd)
        allowNotice = findViewById(R.id.allowNotice)
        WatchBle.attach(this)
        WatchBle.addListener { render() }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        allowNotice.setOnClickListener {
            wantFwd = true
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.testMsg).setOnClickListener {
            if (askBlePerms(log)) return@setOnClickListener
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
            if (on) startWatchService("hold")
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (wantFwd && NoticeGate.allowed(this)) {
            wantFwd = false
            NoticeGate.set(this, true)
            startWatchService("hold")
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
        inbox.text = WatchBle.inbox
        log.text = WatchBle.lastLog
    }
}
