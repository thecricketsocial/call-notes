package com.callnotes.app

import android.content.Context
import org.json.JSONObject

data class CallNote(val key: String, val number: String, val text: String, val updated: Long)

/**
 * Stores one note per phone number on the device only.
 * Numbers are matched on their last 9 digits, so "+971 56 408 7348",
 * "+971564087348" and "0564087348" all count as the same caller.
 */
object NoteStore {
    private const val PREFS = "call_notes"

    fun keyFor(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    fun get(context: Context, number: String): CallNote? {
        val key = keyFor(number)
        if (key.isEmpty()) return null
        val raw = prefs(context).getString(key, null) ?: return null
        return parse(key, raw)
    }

    fun save(context: Context, number: String, text: String) {
        val key = keyFor(number)
        if (key.isEmpty()) return
        val json = JSONObject()
            .put("number", number)
            .put("text", text)
            .put("updated", System.currentTimeMillis())
        prefs(context).edit().putString(key, json.toString()).apply()
    }

    fun deleteKey(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    fun all(context: Context): List<CallNote> =
        prefs(context).all
            .mapNotNull { (key, value) -> (value as? String)?.let { parse(key, it) } }
            .sortedByDescending { it.updated }

    private fun parse(key: String, raw: String): CallNote? = try {
        val o = JSONObject(raw)
        CallNote(key, o.optString("number", key), o.optString("text", ""), o.optLong("updated", 0L))
    } catch (e: Exception) {
        null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
