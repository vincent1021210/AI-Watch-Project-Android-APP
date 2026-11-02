package com.xiaoyi.watch

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object NoticeGate {
    private const val PREF = "xy_notice"
    private const val KEY = "fwd"

    fun enabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    fun allowed(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.contains(context.packageName)
    }
}

class NotifyBridge : NotificationListenerService() {
    private var lastKey = ""
    private var lastAt = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NoticeGate.enabled(this)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val flags = n.flags
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val key = sbn.packageName + "|" + title + "|" + text
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 5000) return
        lastKey = key
        lastAt = now
        val app = appLabel(sbn.packageName)
        val who = if (title.isBlank() || title == app) app else "$app · $title"
        WatchBle.sendNotice(who, text.ifBlank { title })
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }
}
