package com.callnotes.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.TelephonyManager

/**
 * Listens for phone call state changes.
 *  - Ringing: if there is a saved note for the caller, show it on screen.
 *  - Call ended (after being answered or dialled): ask for a note.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val app = context.applicationContext
        val prefs = app.getSharedPreferences("call_state", Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!number.isNullOrBlank()) {
                    prefs.edit().putString("number", number).apply()
                    val note = NoteStore.get(app, number)
                    if (note != null && note.text.isNotBlank()) {
                        Popup.show(app, number, note.text)
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val edit = prefs.edit().putBoolean("answered", true)
                if (!number.isNullOrBlank()) edit.putString("number", number)
                edit.apply()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Popup.hide()
                val answered = prefs.getBoolean("answered", false)
                val savedNumber = prefs.getString("number", null)
                prefs.edit().clear().apply()

                // Missed or rejected calls don't ask for a note.
                if (!answered) return

                // Give the phone a moment to write the call log, then ask for a note.
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val callNumber = lastCallNumber(app) ?: savedNumber
                        if (!callNumber.isNullOrBlank()) NoteActivity.open(app, callNumber)
                    } finally {
                        pending.finish()
                    }
                }, 1500)
            }
        }
    }

    /** Number of the most recent call (incoming or outgoing) from the call log. */
    private fun lastCallNumber(context: Context): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (c.moveToFirst()) {
                    val started = c.getLong(1)
                    val recent = System.currentTimeMillis() - started < 3 * 60 * 60 * 1000L
                    if (recent) c.getString(0) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
