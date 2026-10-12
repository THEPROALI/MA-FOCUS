package com.mafocus.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val quotes = listOf(
        "Every reel you skip is a minute you give back to your goals.",
        "Discipline is choosing what you want most over what you want now.",
        "Your attention is your most valuable asset. Spend it on purpose.",
        "Small wins stack up. Close the app, open your task.",
        "Boredom is where your best ideas start. Let it happen.",
        "Future you is watching. Make them proud."
    )
    private val fg = 0xFFF1F2F7.toInt()
    private val grey = 0xFF8B90A3.toInt()
    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()

    private fun tx(s: String, sz: Float, c: Int = fg, b: Boolean = false) = TextView(this).apply {
        text = s; textSize = sz; setTextColor(c)
        if (b) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun btn(s: String, f: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { f() }
    }
    private fun field(h: String, t: Int) = EditText(this).apply {
        hint = h; inputType = t; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
    }

    override fun onResume() { super.onResume(); build() }

    private fun build() {
        val bg = Color.parseColor("#0E1017")
        window.statusBarColor = bg
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(l) })

        val c = Store.count(this); val lim = Store.limit(this); val h = Store.health(this)
        l.addView(tx("MA FOCUS", 26f, b = true))
        l.addView(tx(Store.face(h), 84f).apply { gravity = Gravity.CENTER; alpha = 0.4f + h / 100f * 0.6f })
        l.addView(tx("Brain health $h%", 20f, Store.color(h), true).apply { gravity = Gravity.CENTER })
        l.addView(tx("Reels today: $c / $lim", 16f).apply { gravity = Gravity.CENTER })

        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        l.addView(tx(if (on) "Screen access: ON" else "Screen access: OFF", 15f,
            if (on) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), true))
        if (!on) l.addView(btn("Enable screen access") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // ---- unproductive screen time ----
        var tt = 0L; var rt = 0L
        Store.APPS.keys.forEach { tt += Store.time(this, "t", it); rt += Store.time(this, "r", it) }
        l.addView(tx("Unproductive screen time today", 14f, grey).apply { setPadding(0, dp(24), 0, 0) })
        l.addView(tx(Store.fmt(tt), 32f, b = true))
        l.addView(tx("Reels / Shorts: ${Store.fmt(rt)}", 17f, Store.color(h), true))
        Store.APPS.forEach { (pkg, name) ->
            l.addView(tx("$name: ${Store.fmt(Store.time(this, "t", pkg))} total · ${Store.fmt(Store.time(this, "r", pkg))} reels", 14f))
        }

        l.addView(tx("Daily reel limit", 14f, grey).apply { setPadding(0, dp(24), 0, 0) })
        val lf = field("30", InputType.TYPE_CLASS_NUMBER).apply { setText(lim.toString()) }
        l.addView(lf)
        l.addView(btn("Save limit") {
            Store.setLimit(this, lf.text.toString().toIntOrNull() ?: 30); build()
        })
        l.addView(btn("Reset today's count") { Store.setCount(this, 0); build() })

        l.addView(tx("Tasks (the widget shows a new one for every reel past your limit)", 14f, grey))
        val tf = field("Add a task", InputType.TYPE_CLASS_TEXT)
        l.addView(tf)
        l.addView(btn("Add task") {
            val t = tf.text.toString().trim()
            if (t.isNotEmpty()) { Store.saveTasks(this, Store.tasks(this) + t); build() }
        })
        Store.tasks(this).forEachIndexed { i, t ->
            l.addView(btn("Done: $t") {
                val all = Store.tasks(this); all.removeAt(i); Store.saveTasks(this, all); build()
            })
        }

        l.addView(tx("\"${quotes.random()}\"", 17f).apply { setPadding(0, dp(24), 0, dp(12)) })

        // ---- fallback + diagnostics ----
        l.addView(btn(if (Store.countAll(this)) "Count ALL scrolling: ON" else "Count ALL scrolling: OFF") {
            Store.setCountAll(this, !Store.countAll(this)); build()
        })
        l.addView(tx("Use this if reels aren't being counted. Last event seen: ${Store.dbg(this)}", 12f, grey))
    }
}
