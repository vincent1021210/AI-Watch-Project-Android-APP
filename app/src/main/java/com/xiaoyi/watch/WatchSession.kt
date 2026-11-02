package com.xiaoyi.watch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun Context.startWatchService(action: String) {
    val it = Intent(this, WatchService::class.java).setAction(action)
    if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it)
    if (action == "scan") WatchBle.scanAndConnect(this)
}

internal fun Activity.askBlePerms(log: TextView): Boolean {
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
