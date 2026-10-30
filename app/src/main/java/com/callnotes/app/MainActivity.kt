package com.callnotes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Setup screen plus the list of saved notes. */
class MainActivity : Activity() {

    private lateinit var setupBox: LinearLayout
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var notes: List<CallNote> = emptyList()

    private val neededPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        setupBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(setupBox)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "+ Add note"
            setOnClickListener { askForNumber() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            text = "Test popup"
            setOnClickListener { testPopup() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        root.addView(TextView(this).apply {
            text = "Saved notes"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        })

        empty = TextView(this).apply {
            text = "No notes yet. After your next answered call, you'll be asked to write one."
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(empty)

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(NoteActivity.intent(this, notes[position].number))
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(notes[position])
            true
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        refreshList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshSetup()
    }

    private fun hasPermissions() = neededPermissions.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshSetup() {
        setupBox.removeAllViews()
        val phoneOk = hasPermissions()
        val overlayOk = Settings.canDrawOverlays(this)

        if (!phoneOk) {
            setupBox.addView(Button(this).apply {
                text = "Step 1: Allow phone, call log & notifications"
                setOnClickListener { requestPermissions(neededPermissions, 1) }
            })
        }
        if (!overlayOk) {
            setupBox.addView(Button(this).apply {
                text = "Step 2: Allow display over other apps"
                setOnClickListener {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                }
            })
        }

        setupBox.addView(TextView(this).apply {
            text = if (phoneOk && overlayOk) {
                "✅ All set. When a saved number calls, its note pops up. " +
                    "After each answered call you'll be asked for a note.\n\n" +
                    "Samsung tip: Settings → Battery → Background usage limits → " +
                    "Never sleeping apps → add Call Notes, so it keeps working."
            } else {
                "Finish the steps above so Call Notes can see who's calling and show notes over the call screen."
            }
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun refreshList() {
        notes = NoteStore.all(this)
        val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val rows = notes.map {
            mapOf(
                "title" to "${it.number}  ·  ${fmt.format(Date(it.updated))}",
                "text" to it.text
            )
        }
        list.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("title", "text"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun askForNumber() {
        val field = EditText(this).apply {
            hint = "Phone number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
        }
        AlertDialog.Builder(this)
            .setTitle("Add a note")
            .setView(wrap)
            .setPositiveButton("Next") { _, _ ->
                val number = field.text.toString().trim()
                if (NoteStore.keyFor(number).isNotEmpty()) {
                    startActivity(NoteActivity.intent(this, number))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(note: CallNote) {
        AlertDialog.Builder(this)
            .setTitle("Delete this note?")
            .setMessage(note.number)
            .setPositiveButton("Delete") { _, _ ->
                NoteStore.deleteKey(this, note.key)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testPopup() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first", Toast.LENGTH_SHORT).show()
            return
        }
        Popup.show(applicationContext, "+971 50 000 0000", "This is how a note looks when a saved number calls you.")
    }
}
