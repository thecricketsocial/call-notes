package com.callnotes.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** A floating card drawn over the call screen, showing the caller's note. */
object Popup {
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, number: String, note: String) {
        hide()

        // Also post a notification, which can be read later or on the lock screen.
        Notifier.showNote(context, number, note)

        if (!Settings.canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = buildView(context, number, note)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        params.y = context.dp(90)

        try {
            wm.addView(card, params)
            view = card
        } catch (e: Exception) {
            return
        }

        card.setOnClickListener { hide() }
        handler.postDelayed({ hide() }, 60_000)
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val v = view ?: return
        view = null
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
            // Already gone.
        }
    }

    private fun buildView(context: Context, number: String, note: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(18)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xF2172234.toInt())
                cornerRadius = context.dp(18).toFloat()
            }
        }

        card.addView(TextView(context).apply {
            text = "📝  Note · $number"
            setTextColor(0xFFB8C7E0.toInt())
            textSize = 13f
        })

        card.addView(TextView(context).apply {
            text = note
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 8
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dp(6), 0, context.dp(6))
        })

        card.addView(TextView(context).apply {
            text = "Tap to close"
            setTextColor(0xFF8A98B0.toInt())
            textSize = 12f
        })

        return FrameLayout(context).apply {
            val side = context.dp(12)
            setPadding(side, 0, side, 0)
            addView(card)
        }
    }
}
