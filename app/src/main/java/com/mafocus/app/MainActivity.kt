package com.mafocus.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private val BG = Color.parseColor("#0B0D12")
    private val CARD = Color.parseColor("#141821")
    private val LINE = Color.parseColor("#232938")
    private val TX = Color.parseColor("#E8EAF0")
    private val MU = Color.parseColor("#7A8194")
    private val AC = Color.parseColor("#4C8DFF")
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun round(fill: Int, r: Int = 14, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun tx(s: String, sz: Float, c: Int = TX, font: String = "sans-serif") = TextView(this).apply {
        text = s; textSize = sz; setTextColor(c); typeface = Typeface.create(font, Typeface.NORMAL)
    }
    private fun label(s: String) = tx(s.uppercase(), 11f, MU, "sans-serif-medium").apply {
        letterSpacing = 0.14f; setPadding(0, 0, 0, dp(10))
    }
    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = round(CARD, 16, LINE)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        parent.addView(this, lp(MP, WC).apply { bottomMargin = dp(12) })
    }
    private fun btn(p: LinearLayout, s: String, primary: Boolean = true, f: () -> Unit) {
        p.addView(Button(this).apply {
            text = s; isAllCaps = false; textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else TX)
            background = round(if (primary) AC else Color.parseColor("#1C2130"), 12)
            stateListAnimator = null
            setOnClickListener { f() }
        }, lp(MP, dp(46)).apply { topMargin = dp(8) })
    }
    private fun field(h: String, t: Int) = EditText(this).apply {
        hint = h; inputType = t; setTextColor(TX); setHintTextColor(MU); textSize = 15f
        background = round(Color.parseColor("#0E1118"), 12, LINE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    private fun row(p: LinearLayout, a: String, b: String, bc: Int = MU) {
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6))
            addView(tx(a, 14f), lp(0, WC, 1f)); addView(tx(b, 14f, bc))
        })
    }

    override fun onResume() { super.onResume(); build() }

    private fun build() {
        window.statusBarColor = BG; window.navigationBarColor = BG
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(36), dp(18), dp(36))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })

        val c = Store.count(this); val lim = Store.limit(this); val h = Store.health(this)
        val hc = Store.color(h)

        // ---- header / logo ----
        val hd = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hd.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7C6CF0.toInt(), 0xFF2A1B66.toInt())).apply { cornerRadius = dp(14).toFloat() }
        }, lp(dp(54), dp(54)))
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        ht.addView(tx("MA FOCUS", 25f, TX, "sans-serif-black").apply { letterSpacing = 0.16f })
        ht.addView(tx("TAKE YOUR ATTENTION BACK", 10.5f, MU, "sans-serif-medium").apply { letterSpacing = 0.2f })
        hd.addView(ht)
        root.addView(hd, lp(MP, WC).apply { bottomMargin = dp(18) })

        // ---- screen access ----
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        if (on) {
            root.addView(tx("●  Screen access active", 13f, 0xFF2ECC71.toInt(), "sans-serif-medium"),
                lp(WC, WC).apply { bottomMargin = dp(12) })
        } else {
            val a = card(root)
            a.addView(tx("Screen access is off", 15f, 0xFFE74C3C.toInt(), "sans-serif-medium"))
            btn(a, "Enable screen access") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // ---- brain ----
        val b = card(root)
        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        br.addView(tx(Store.face(h), 54f).apply { alpha = 0.45f + h / 100f * 0.55f; setPadding(0, 0, dp(16), 0) })
        val bc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bc.addView(tx("BRAIN HEALTH", 11f, MU, "sans-serif-medium").apply { letterSpacing = 0.14f })
        bc.addView(tx("$h%", 36f, hc, "sans-serif-black"))
        bc.addView(tx(when { h >= 70 -> "Sharp and focused"; h >= 40 -> "Getting tired"; h > 0 -> "Under strain"; else -> "Burnt out. Stop scrolling." }, 13f, MU))
        br.addView(bc); b.addView(br)
        val bar = LinearLayout(this).apply { background = round(LINE, 4); weightSum = 100f }
        bar.addView(View(this).apply { background = round(hc, 4) }, lp(0, dp(8), h.coerceAtLeast(1).toFloat()))
        bar.addView(View(this), lp(0, dp(8), (100 - h.coerceAtLeast(1)).toFloat()))
        b.addView(bar, lp(MP, dp(8)).apply { topMargin = dp(14); bottomMargin = dp(10) })
        row(b, "Reels today", "$c / $lim", TX)

        // ---- screen time ----
        var tt = 0L; var rt = 0L
        Store.APPS.keys.forEach { tt += Store.time(this, "t", it); rt += Store.time(this, "r", it) }
        val s = card(root)
        s.addView(label("Unproductive screen time today"))
        s.addView(tx(Store.fmt(tt), 32f, TX, "sans-serif-black"))
        s.addView(tx("Reels / Shorts  ·  ${Store.fmt(rt)}", 15f, hc, "sans-serif-medium"))
        s.addView(View(this).apply { setBackgroundColor(LINE) }, lp(MP, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(6) })
        Store.APPS.forEach { (pkg, name) ->
            row(s, name, "${Store.fmt(Store.time(this, "t", pkg))}  ·  ${Store.fmt(Store.time(this, "r", pkg))} reels")
        }

        // ---- limit ----
        val l = card(root)
        l.addView(label("Daily reel limit"))
        val lf = field("30", InputType.TYPE_CLASS_NUMBER).apply { setText(lim.toString()) }
        l.addView(lf)
        l.addView(tx("Past your limit you get a reminder every 5 reels.", 12f, MU).apply { setPadding(0, dp(8), 0, 0) })
        btn(l, "Save limit") { Store.setLimit(this, lf.text.toString().toIntOrNull() ?: 30); build() }
        btn(l, "Reset today's count", false) { Store.setCount(this, 0); build() }

        // ---- tasks ----
        val t = card(root)
        t.addView(label("Tasks"))
        val tf = field("What should you be doing instead?", InputType.TYPE_CLASS_TEXT)
        t.addView(tf)
        btn(t, "Add task") {
            val v = tf.text.toString().trim()
            if (v.isNotEmpty()) { Store.saveTasks(this, Store.tasks(this) + v); build() }
        }
        val tasks = Store.tasks(this)
        if (tasks.isEmpty()) t.addView(tx("No tasks yet.", 13f, MU).apply { setPadding(0, dp(10), 0, 0) })
        tasks.forEachIndexed { i, task ->
            t.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(2))
                addView(tx("${i + 1}.  $task", 15f), lp(0, WC, 1f))
                addView(tx("Done", 14f, AC, "sans-serif-medium").apply {
                    setPadding(dp(12), dp(6), 0, dp(6)); setOnClickListener {
                        val all = Store.tasks(this@MainActivity); all.removeAt(i); Store.saveTasks(this@MainActivity, all); build()
                    }
                })
            })
        }

        // ---- quote ----
        val q = card(root)
        q.addView(tx("\u201C${quotes.random()}\u201D", 17f, TX, "serif").apply {
            typeface = Typeface.create("serif", Typeface.ITALIC); setLineSpacing(0f, 1.2f)
        })

        // ---- fallback + diagnostics ----
        val d = card(root)
        d.addView(label("Troubleshooting"))
        btn(d, if (Store.countAll(this)) "Count ALL scrolling: ON" else "Count ALL scrolling: OFF", false) {
            Store.setCountAll(this, !Store.countAll(this)); build()
        }
        d.addView(tx("Turn on if reels aren't counted. Last event: ${Store.dbg(this)}", 11f, MU).apply { setPadding(0, dp(10), 0, 0) })
    }
}
