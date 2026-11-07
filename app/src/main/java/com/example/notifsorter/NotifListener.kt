package com.example.notifsorter

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotifListener : NotificationListenerService() {

    // Remember recent notifications so updates of the same one aren't logged twice.
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (t: Throwable) {
            Log.e("NotifSorter", "failed to handle notification", t)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification

        // Never touch: group summaries, ongoing/foreground, calls, alarms, navigation, media, etc.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0) return
        if (!sbn.isClearable) return
        if (n.category in PROTECTED_CATEGORIES) return
        if (sbn.packageName in PROTECTED_PACKAGES) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var body = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (body.isBlank()) body = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val dedupe = sbn.key + "|" + (title + body).hashCode()
        synchronized(seen) { if (seen.put(dedupe, true) != null) return }

        val store = Store.get(this)
        val res = Pipeline.decide(store, sbn.packageName, title, body)

        var hidden = false
        if (res.decision == Decision.SILENCE && Prefs.silenceEnabled(this)) {
            cancelNotification(sbn.key)
            hidden = true
        }

        store.insert(
            Rec(
                nkey = Pipeline.normKey(sbn.packageName, title),
                pkg = sbn.packageName,
                app = appLabel(sbn.packageName),
                title = title,
                body = body,
                ts = sbn.postTime,
                score = res.score,
                decision = res.decision,
                hidden = hidden,
            )
        )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    companion object {
        private val PROTECTED_CATEGORIES = setOf(
            Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_STATUS, Notification.CATEGORY_REMINDER,
        )
        private val PROTECTED_PACKAGES = setOf(
            "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom",
            "com.android.incallui", "com.google.android.deskclock", "com.android.deskclock",
        )
    }
}
