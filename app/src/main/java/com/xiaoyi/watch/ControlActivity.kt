package com.xiaoyi.watch

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        log = findViewById(R.id.log)
        WatchBle.attach(this)
        WatchBle.addListener { log.text = WatchBle.lastLog }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.volDown).setOnClickListener { WatchBle.vol(-10) }
        findViewById<Button>(R.id.volUp).setOnClickListener { WatchBle.vol(10) }
        findViewById<Button>(R.id.brightDown).setOnClickListener { WatchBle.bright(-10) }
        findViewById<Button>(R.id.brightUp).setOnClickListener { WatchBle.bright(10) }
        findViewById<Button>(R.id.flash).setOnClickListener { WatchBle.writeCmd("{\"t\":\"flash\"}") }
        findViewById<Button>(R.id.wake).setOnClickListener { WatchBle.writeCmd("{\"t\":\"wake\"}") }
        log.text = WatchBle.lastLog
    }
}
