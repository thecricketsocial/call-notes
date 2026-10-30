package com.callnotes.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Small dialog to write or edit the note for one number. */
class NoteActivity : Activity() {

    companion object {
        private const val EXTRA_NUMBER = "number"

        fun intent(context: Context, number: String): Intent =
            Intent(context, NoteActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens the dialog, or posts a notification if Android won't allow that. */
        fun open(context: Context, number: String) {
            if (Settings.canDrawOverlays(context)) {
                try {
                    context.startActivity(intent(context, number))
                    return
                } catch (e: Exception) {
                    // Fall through to the notification.
                }
            }
            Notifier.askForNote(context, number)
        }
    }

    private lateinit var input: EditText
    private var number = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        setFinishOnTouchOutside(false)

        number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        if (number.isBlank()) {
            finish()
            return
        }

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(12))
        }

        root.addView(TextView(this).apply {
            text = "Note for $number"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
        })

        input = EditText(this).apply {
            hint = "What was this call about?"
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(NoteStore.get(this@NoteActivity, number)?.text.orEmpty())
            setSelection(text.length)
        }
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttons.addView(Button(this).apply {
            text = "Skip"
            setOnClickListener { finish() }
        })
        buttons.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { save() }
        })
        root.addView(buttons, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        setContentView(root)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.requestFocus()
    }

    private fun save() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            NoteStore.deleteKey(this, NoteStore.keyFor(number))
            Toast.makeText(this, "Note removed", Toast.LENGTH_SHORT).show()
        } else {
            NoteStore.save(this, number, text)
            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
