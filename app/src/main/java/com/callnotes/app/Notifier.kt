package com.callnotes.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifier {
    private const val CHANNEL = "call_notes"

    fun showNote(context: Context, number: String, note: String) {
        post(context, number, "Note for $number", note)
    }

    fun askForNote(context: Context, number: String) {
        post(context, number, "Add a note for $number", "Tap to write what this call was about.")
    }

    private fun post(context: Context, number: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Caller notes", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val id = NoteStore.keyFor(number).hashCode()
        val open: Intent = NoteActivity.intent(context, number)
        val pi = PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60 * 1000L)
            .build()

        nm.notify(id, notification)
    }
}
