package com.callnotes.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.Calendar
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
            text = "⏰ Remind me"
            setOnClickListener { chooseReminder() }
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

    /** Saves the note, then asks when to follow up and opens the calendar. */
    private fun chooseReminder() {
        val text = input.text.toString().trim()
        if (text.isNotEmpty()) NoteStore.save(this, number, text)

        val options = arrayOf(
            "In 1 hour",
            "Tomorrow, 10:00 AM",
            "In 3 days, 10:00 AM",
            "Next week, 10:00 AM",
            "Pick date & time…"
        )
        AlertDialog.Builder(this)
            .setTitle("Follow up when?")
            .setItems(options) { _, which ->
                val cal = Calendar.getInstance()
                when (which) {
                    0 -> {
                        cal.add(Calendar.HOUR_OF_DAY, 1)
                        cal.set(Calendar.SECOND, 0)
                        openCalendar(cal, text)
                    }
                    1 -> openCalendar(atTen(cal, 1), text)
                    2 -> openCalendar(atTen(cal, 3), text)
                    3 -> openCalendar(atTen(cal, 7), text)
                    else -> pickDateTime(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun atTen(cal: Calendar, daysAhead: Int): Calendar = cal.apply {
        add(Calendar.DAY_OF_YEAR, daysAhead)
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }

    private fun pickDateTime(text: String) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val cal = Calendar.getInstance().apply {
                    set(y, m, d, h, min, 0)
                }
                openCalendar(cal, text)
            }, 10, 0, DateFormat.is24HourFormat(this)).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    /** Opens the phone's calendar with a pre-filled follow-up event. The person taps Save there. */
    private fun openCalendar(start: Calendar, note: String) {
        val begin = start.timeInMillis
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Follow up: $number")
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                (if (note.isNotEmpty()) "$note\n\n" else "") + "Call back: $number"
            )
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 15 * 60 * 1000L)
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "No calendar app found", Toast.LENGTH_LONG).show()
        }
    }
}
