package com.example.notifsorter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Heads-up pop-up for notifications judged important, so you don't have to open this app. */
object Alerts {
    private const val CHANNEL = "important_alerts"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Important notifications", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Pop-up alerts for notifications that Notification Sorter judges important"
            nm.createNotificationChannel(ch)
        }
    }

    /** [open] is the original notification's tap action, so tapping the alert opens the original app. */
    fun post(ctx: Context, app: String, title: String, body: String, open: PendingIntent?) {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(ctx)
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (title.isBlank()) app else "$app: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (open != null) builder.setContentIntent(open)
        try {
            nmc.notify((System.nanoTime() and 0x7fffffff).toInt(), builder.build())
        } catch (e: SecurityException) {
            // notification permission not granted yet
        }
    }
}