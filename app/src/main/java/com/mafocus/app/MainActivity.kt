package com.mafocus.app

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var tab = 0
    private var introDone = false
    private var calMonth: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selDay = ""
    private var remIdx = 0
    private val remVals = listOf(0, 1, 3, 7)
    private var draft = ""
    private var draft2 = ""
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val quotes = listOf(
        "Every reel you skip is a minute you give back to your goals.",
        "Discipline is choosing what you want most over what you want now.",
        "Your attention is your most valuable asset. Spend it on purpose.",
        "Small wins stack up. Close the app, open your task.",
        "Boredom is where your best ideas start. Let it happen.",
        "Future you is watching. Make them proud."
    )
    private var BG = 0; private var CARD = 0; private var LINE = 0; private var TX = 0
    private var MU = 0; private var AC = 0; private var BTN2 = 0; private var FIELD = 0
    private var LIGHT = false
    private val GOOD = 0xFF2ECC71.toInt()
    private val BAD = 0xFFE74C3C.toInt()
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun applyTheme() {
        val t = THEMES[Store.theme(this).coerceIn(0, THEMES.size - 1)]
        BG = Color.parseColor(t.bg); CARD = Color.parseColor(t.card); LINE = Color.parseColor(t.line)
        TX = Color.parseColor(t.tx); MU = Color.parseColor(t.mu); AC = Color.parseColor(t.ac)
        BTN2 = Color.parseColor(t.btn2); FIELD = Color.parseColor(t.field); LIGHT = t.light
    }

    // ---------- helpers ----------
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
            background = round(if (primary) AC else BTN2, 12)
            stateListAnimator = null
            setOnClickListener { f() }
        }, lp(MP, dp(46)).apply { topMargin = dp(8) })
    }
    private fun field(h: String, t: Int) = EditText(this).apply {
        hint = h; inputType = t; setTextColor(TX); setHintTextColor(MU); textSize = 15f
        background = round(FIELD, 12, LINE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    private fun draftBox(hint: String, get: () -> String, put: (String) -> Unit) =
        field(hint, InputType.TYPE_CLASS_TEXT).apply {
            setText(get())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { put(s?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
    private fun row(p: LinearLayout, a: String, b: String, bc: Int = MU) {
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6))
            addView(tx(a, 14f), lp(0, WC, 1f)); addView(tx(b, 14f, bc))
        })
    }
    private fun seg(p: LinearLayout, items: List<String>, sel: Int, pick: (Int) -> Unit) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { i, s ->
            r.addView(Button(this).apply {
                text = s; isAllCaps = false; textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (i == sel) Color.WHITE else TX)
                background = round(if (i == sel) AC else BTN2, 10)
                stateListAnimator = null
                setOnClickListener { pick(i) }
            }, lp(0, dp(42), 1f).apply { if (i > 0) leftMargin = dp(6) })
        }
        p.addView(r, lp(MP, WC).apply { topMargin = dp(8) })
    }
    private fun accessOn() = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        .contains(packageName)
    private fun go(i: Intent) {
        try { startActivity(i) } catch (_: Exception) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- skeleton ----------
    override fun onResume() {
        super.onResume()
        if (Store.auto(this) && Auto.hasUsage(this) && Auto.hasSecure(this)) Auto.start(this)
        build()
        if (!introDone) { introDone = true; playIntro() }
    }

    private fun build() {
        applyTheme()
        window.statusBarColor = BG; window.navigationBarColor = CARD
        var f = window.decorView.systemUiVisibility
        val lf = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        f = if (LIGHT) f or lf else f and lf.inv()
        window.decorView.systemUiVisibility = f

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(30), dp(18), dp(20))
        }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        outer.addView(ScrollView(this).apply { addView(root) }, lp(MP, 0, 1f))
        outer.addView(tabBar(), lp(MP, WC))
        setContentView(outer)

        header(root)
        when (tab) { 0 -> home(root); 1 -> stats(root); 2 -> tasksPage(root); 3 -> breatherPage(root); else -> settings(root) }
    }

    private fun tabBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setBackgroundColor(CARD)
        listOf("Home", "Stats", "Tasks", "Breather", "Settings").forEachIndexed { i, n ->
            addView(TextView(this@MainActivity).apply {
                text = n.uppercase(); textSize = 10f; letterSpacing = 0.05f; gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (i == tab) AC else MU)
                setPadding(0, dp(16), 0, dp(16))
                setOnClickListener { tab = i; build() }
            }, lp(0, WC, 1f))
        }
    }

    private fun header(root: LinearLayout) {
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
    }

    // ---------- HOME ----------
    private fun home(root: LinearLayout) {
        val c = Store.count(this); val lim = Store.limit(this); val h = Store.health(this); val hc = Store.color(h)

        if (Store.auto(this)) {
            root.addView(tx("●  Automatic mode on  ·  screen access runs only in Instagram & YouTube", 13f, GOOD, "sans-serif-medium"),
                lp(MP, WC).apply { bottomMargin = dp(12) })
        } else if (accessOn()) {
            root.addView(tx("●  Screen access active", 13f, GOOD, "sans-serif-medium"), lp(WC, WC).apply { bottomMargin = dp(12) })
        } else {
            val a = card(root)
            a.addView(tx("Screen access is off", 15f, BAD, "sans-serif-medium"))
            btn(a, "Enable screen access") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val ns = card(root)
        ns.addView(label("Next step"))
        val over = c - lim
        val nextTask = Store.tasks(this).firstOrNull()
        ns.addView(tx(when { over > 0 -> "You're $over reels past your limit"; c >= lim * 0.8 -> "Close to your limit"; else -> "${lim - c} reels left today" },
            17f, if (over > 0) BAD else TX, "sans-serif-medium"))
        ns.addView(tx(when {
            over > 0 && nextTask != null -> "Close the reels app now, then do: $nextTask"
            over > 0 -> "Close the reels app now. Add a task so you know what to do instead."
            nextTask != null -> "Next up when you're done scrolling: $nextTask"
            else -> "Add a task in the Tasks tab so you have something better to do."
        }, 14f, MU).apply { setPadding(0, dp(6), 0, 0) })

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
        b.addView(bar, lp(MP, dp(8)).apply { topMargin = dp(14) })

        var tt = 0L; var rt = 0L
        Store.APPS.keys.forEach { tt += Store.time(this, "t", it); rt += Store.time(this, "r", it) }
        val s = card(root)
        s.addView(label("Today"))
        row(s, "Reels scrolled", "$c / $lim", TX)
        row(s, "Unproductive screen time", Store.fmt(tt), TX)
        row(s, "Time on reels / Shorts", Store.fmt(rt), hc)
    }

    // ---------- STATS ----------
    private fun stats(root: LinearLayout) {
        val lim = Store.limit(this); val c = Store.count(this)
        val keys = (6 downTo 0).map { Store.dayKey(it) }
        val labels = (6 downTo 0).map { off ->
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -off)
            SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
        }
        val counts = keys.mapIndexed { i, k -> if (i == 6) c.toFloat() else Store.countOn(this, k).toFloat() }
        val mins = keys.map { k -> Store.APPS.keys.sumOf { Store.timeOn(this, "r", it, k) } / 60000f }

        val a = card(root)
        a.addView(label("Reels per day"))
        a.addView(BarChart(this, counts, labels, lim.toFloat(), AC, BAD, TX, MU), lp(MP, dp(190)))
        a.addView(tx("Dashed line = your daily limit. Red bars went over it.", 12f, MU).apply { setPadding(0, dp(8), 0, 0) })

        val m = card(root)
        m.addView(label("Minutes on reels per day"))
        m.addView(BarChart(this, mins, labels, 0f, AC, BAD, TX, MU), lp(MP, dp(190)))

        val s = card(root)
        s.addView(label("Summary · last 7 days"))
        row(s, "Today", "${counts[6].toInt()} reels", TX)
        row(s, "Yesterday", "${counts[5].toInt()} reels", TX)
        row(s, "Daily average", "%.1f reels".format(counts.sum() / 7f), TX)
        row(s, "Days within your limit", "${counts.count { it <= lim }} of 7", GOOD)
        row(s, "Total time on reels", Store.fmt((mins.sum() * 60000f).toLong()), TX)

        val p = card(root)
        p.addView(label("Today by app"))
        Store.APPS.forEach { (pkg, name) ->
            row(p, name, "${Store.fmt(Store.time(this, "t", pkg))}  ·  ${Store.fmt(Store.time(this, "r", pkg))} reels")
        }
    }

    // ---------- TASKS + CALENDAR ----------
    private fun taskRow(p: LinearLayout, t: Task, all: MutableList<Task>) {
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tx(t.text, 15f))
            if (t.date.isNotEmpty() && t.before > 0) col.addView(tx("reminds from ${t.before} day(s) before", 11f, MU))
            addView(col, lp(0, WC, 1f))
            addView(tx("Done", 14f, AC, "sans-serif-medium").apply {
                setPadding(dp(12), dp(6), 0, dp(6))
                setOnClickListener { all.remove(t); Store.saveAllTasks(this@MainActivity, all); build() }
            })
        })
    }

    private fun tasksPage(root: LinearLayout) {
        val all = Store.allTasks(this)
        val todayKey = Store.dayKey(0)
        if (selDay.isEmpty()) selDay = todayKey
        val byDay = all.filter { it.date.isNotEmpty() }.groupBy { it.date }

        // --- calendar ---
        val cc = card(root)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(tx("‹", 26f, AC, "sans-serif-medium").apply {
            setPadding(dp(14), 0, dp(14), 0); setOnClickListener { calMonth.add(Calendar.MONTH, -1); build() }
        })
        nav.addView(tx(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calMonth.time), 16f, TX, "sans-serif-medium")
            .apply { gravity = Gravity.CENTER }, lp(0, WC, 1f))
        nav.addView(tx("›", 26f, AC, "sans-serif-medium").apply {
            setPadding(dp(14), 0, dp(14), 0); setOnClickListener { calMonth.add(Calendar.MONTH, 1); build() }
        })
        cc.addView(nav)
        val wk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            wk.addView(tx(it, 11f, MU, "sans-serif-medium").apply { gravity = Gravity.CENTER }, lp(0, WC, 1f))
        }
        cc.addView(wk)
        val first = calMonth.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val dim = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val prefix = SimpleDateFormat("yyyyMM", Locale.US).format(first.time)
        var d = 1 - offset
        while (d <= dim) {
            val rw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 7) {
                if (d < 1 || d > dim) rw.addView(View(this), lp(0, dp(46), 1f))
                else {
                    val key = prefix + "%02d".format(d)
                    val sel = key == selDay; val isToday = key == todayKey
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        background = when { sel -> round(AC, 10); isToday -> round(Color.TRANSPARENT, 10, AC); else -> null }
                        setOnClickListener { selDay = key; build() }
                    }
                    cell.addView(tx(d.toString(), 14f, if (sel) Color.WHITE else TX, "sans-serif-medium"))
                    cell.addView(View(this).apply {
                        background = if (byDay.containsKey(key)) round(if (sel) Color.WHITE else AC, 3) else null
                    }, lp(dp(5), dp(5)).apply { topMargin = dp(2) })
                    rw.addView(cell, lp(0, dp(46), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                d++
            }
            cc.addView(rw)
        }
        cc.addView(tx("Dots mark days with tasks.", 11f, MU).apply { setPadding(0, dp(8), 0, 0) })

        // --- selected day ---
        val sd = card(root)
        sd.addView(label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(dayFmt.parse(selDay) ?: Date())))
        val dayTasks = byDay[selDay].orEmpty()
        if (dayTasks.isEmpty()) sd.addView(tx("Nothing planned for this day.", 13f, MU))
        dayTasks.forEach { taskRow(sd, it, all) }
        sd.addView(draftBox("Task for this day", { draft }, { draft = it }), lp(MP, WC).apply { topMargin = dp(12) })
        sd.addView(tx("Start reminding me", 12f, MU).apply { setPadding(0, dp(10), 0, 0) })
        seg(sd, listOf("That day", "1 day before", "3 days", "7 days"), remIdx) { remIdx = it; build() }
        btn(sd, "Add to this day") {
            val v = draft.trim()
            if (v.isNotEmpty()) { all.add(Task(v, selDay, remVals[remIdx])); Store.saveAllTasks(this, all); draft = ""; build() }
        }
        sd.addView(tx("Reminders for this task appear on the widget only from the start day up to this day.", 11f, MU)
            .apply { setPadding(0, dp(8), 0, 0) })

        // --- overdue ---
        val overdue = all.filter { it.date.isNotEmpty() && it.date < todayKey }
        if (overdue.isNotEmpty()) {
            val od = card(root)
            od.addView(label("Overdue"))
            overdue.forEach { taskRow(od, it, all) }
        }

        // --- every day ---
        val ed = card(root)
        ed.addView(label("Every day"))
        ed.addView(tx("These remind you every day.", 13f, MU))
        all.filter { it.date.isEmpty() }.forEach { taskRow(ed, it, all) }
        ed.addView(draftBox("Add an every-day task", { draft2 }, { draft2 = it }), lp(MP, WC).apply { topMargin = dp(10) })
        btn(ed, "Add every-day task", false) {
            val v = draft2.trim()
            if (v.isNotEmpty()) { all.add(Task(v)); Store.saveAllTasks(this, all); draft2 = ""; build() }
        }

        val q = card(root)
        q.addView(label("Motivation"))
        q.addView(tx("\u201C${quotes.random()}\u201D", 17f, TX, "serif").apply {
            typeface = Typeface.create("serif", Typeface.ITALIC); setLineSpacing(0f, 1.2f)
        })
    }

    // ---------- BREATHER ----------
    private fun chips(p: LinearLayout, n: Int, make: (Int, TextView) -> Unit, pick: (Int) -> Unit) {
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until n) {
            val t = TextView(this).apply { setPadding(dp(16), dp(12), dp(16), dp(12)); setOnClickListener { pick(i) } }
            make(i, t)
            r.addView(t, lp(WC, WC).apply { rightMargin = dp(8) })
        }
        hs.addView(r); p.addView(hs, lp(MP, WC).apply { topMargin = dp(8) })
    }

    private fun breatherPage(root: LinearLayout) {
        val on = Store.breatherOn(this)
        val a = card(root)
        a.addView(label("Breather mode"))
        a.addView(tx("A calm pause screen with a countdown appears before the apps you choose open. When the timer ends you can continue, or go back.", 13f, MU))
        btn(a, if (on) "Turn breather OFF" else "Turn breather ON", !on) { Store.setBreatherOn(this, !on); build() }
        btn(a, "Preview full screen", false) { openBreatherPreview() }
        if (on && !accessOn() && !Store.auto(this))
            a.addView(tx("Screen access is off, so the pause can't appear yet.", 12f, BAD).apply { setPadding(0, dp(8), 0, 0) })

        val ap = card(root)
        ap.addView(label("Apps"))
        val n = Store.breatherApps(this).size
        ap.addView(tx("$n app(s) will show the pause before opening.", 13f, MU))
        btn(ap, "Choose apps", false) { chooseApps() }

        val m = card(root)
        m.addView(label("Your message"))
        val cur = Store.bMsg(this)
        val mf = field("Message", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 3; gravity = Gravity.TOP or Gravity.START; setText(cur)
        }
        m.addView(mf)
        m.addView(tx("Type {app} to show the app's name.", 12f, MU).apply { setPadding(0, dp(6), 0, 0) })
        btn(m, "Save message") { Store.setBMsg(this, mf.text.toString().ifBlank { Store.DEFAULT_MSG }); build() }
        btn(m, "Reset to default", false) { Store.setBMsg(this, Store.DEFAULT_MSG); build() }

        val t = card(root)
        t.addView(label("Timer"))
        t.addView(tx("Pause length in seconds:", 13f, MU))
        val secs = Store.bSecs(this)
        val sf = field("10", InputType.TYPE_CLASS_NUMBER).apply { setText(secs.toString()) }
        t.addView(sf, lp(MP, WC).apply { topMargin = dp(8) })
        btn(t, "Save pause length") { Store.setBSecs(this, sf.text.toString().toIntOrNull() ?: 10); build() }
        t.addView(tx("After I continue, don't pause that app again for:", 13f, MU).apply { setPadding(0, dp(14), 0, 0) })
        val passVals = listOf(1, 5, 15, 30)
        seg(t, listOf("1 min", "5 min", "15 min", "30 min"), passVals.indexOf(Store.bPass(this)).coerceAtLeast(0)) {
            Store.setBPass(this, passVals[it]); build()
        }

        val lk = card(root)
        lk.addView(label("Look"))
        lk.addView(tx("Font", 12f, MU))
        val cf = Store.bFont(this)
        chips(lk, Fonts.names.size, { i, tv ->
            tv.text = Fonts.names[i]; tv.textSize = 14f; tv.typeface = Fonts.tf(i)
            tv.setTextColor(if (i == cf) Color.WHITE else TX); tv.background = round(if (i == cf) AC else BTN2, 12)
        }) { Store.setBFont(this, it); build() }
        lk.addView(tx("Theme", 12f, MU).apply { setPadding(0, dp(14), 0, 0) })
        val ct = Store.bTheme(this)
        chips(lk, THEMES.size, { i, tv ->
            val th = THEMES[i]
            tv.text = (if (i == ct) "✓  " else "") + th.name; tv.textSize = 13f
            tv.setTextColor(Color.parseColor(th.tx)); tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            tv.background = round(Color.parseColor(th.card), 12, Color.parseColor(if (i == ct) th.ac else th.line))
        }) { Store.setBTheme(this, it); build() }
        lk.addView(tx("Picture", 12f, MU).apply { setPadding(0, dp(14), 0, 0) })
        val art = Store.bArt(this)
        seg(lk, listOf("Breathe", "Sunrise", "Night", "My photo"), art) { Store.setBArt(this, it); build() }
        if (art == 3) btn(lk, "Choose photo", false) {
            try {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 77)
            } catch (_: Exception) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show() }
        }
        btn(lk, "Preview full screen") { openBreatherPreview() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 77 || res != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 1200) s *= 2
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
            if (bmp != null) java.io.FileOutputStream(java.io.File(filesDir, "breather.jpg")).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            Store.setBArt(this, 3); build()
        } catch (_: Exception) { Toast.makeText(this, "Couldn't use that photo", Toast.LENGTH_SHORT).show() }
    }

    private fun chooseApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        val sel = Store.breatherApps(this).toMutableSet()
        val checked = BooleanArray(apps.size) { sel.contains(apps[it].first) }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Pause before opening")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                if (on) sel.add(apps[i].first) else sel.remove(apps[i].first)
            }
            .setPositiveButton("Save") { _, _ -> Store.setBreatherApps(this, sel); build() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openBreatherPreview() {
        val dlg = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val v = BreatherView(this, "Instagram")
        v.onContinue = { dlg.dismiss() }; v.onLeave = { dlg.dismiss() }
        dlg.setContentView(v); dlg.show(); v.start()
    }

    // Full-screen live preview of the real widget while you change its size and position
    private fun openWidgetPreview() {
        val dlg = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }
        frame.addView(tx("Reels preview", 14f, Color.parseColor("#555555")), FrameLayout.LayoutParams(WC, WC, Gravity.CENTER))
        val pill = TextView(this).apply {
            text = "🧠  12 / 30"; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 60f; setColor(Store.color(80)) }
        }
        frame.addView(pill, FrameLayout.LayoutParams(WC, WC))
        fun layoutPill() {
            val sz = floatArrayOf(11f, 14f, 18f)[Store.wsize(this).coerceIn(0, 2)]; val k = sz / 14f
            pill.textSize = sz
            pill.setPadding((40 * k).toInt(), (16 * k).toInt(), (40 * k).toInt(), (16 * k).toInt())
            val x = Store.wx(this)
            val l = pill.layoutParams as FrameLayout.LayoutParams
            l.gravity = Gravity.TOP or (when (x) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL })
            l.leftMargin = if (x == 0) dp(12) else 0; l.rightMargin = if (x == 2) dp(12) else 0
            l.topMargin = (frame.height * 0.88f * Store.wy(this) / 100f).toInt()
            pill.layoutParams = l
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(CARD, 20, LINE)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        fun fill() {
            panel.removeAllViews()
            panel.addView(tx("Size", 12f, MU))
            seg(panel, listOf("Small", "Medium", "Large"), Store.wsize(this)) { Store.setWsize(this, it); fill(); layoutPill() }
            panel.addView(tx("Horizontal position", 12f, MU).apply { setPadding(0, dp(10), 0, 0) })
            seg(panel, listOf("Left", "Center", "Right"), Store.wx(this)) { Store.setWx(this, it); fill(); layoutPill() }
            panel.addView(tx("Vertical position", 12f, MU).apply { setPadding(0, dp(10), 0, 0) })
            panel.addView(SeekBar(this).apply {
                max = 100; progress = Store.wy(this@MainActivity)
                progressTintList = ColorStateList.valueOf(AC); thumbTintList = ColorStateList.valueOf(AC)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { Store.setWy(this@MainActivity, v); layoutPill() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }, lp(MP, WC))
            btn(panel, "Done") { dlg.dismiss() }
        }
        fill()
        frame.addView(panel, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        dlg.setContentView(frame); dlg.show()
        frame.post { layoutPill() }
    }

    // ---------- opening animation ----------
    private fun playIntro() {
        val fl = FrameLayout(this).apply { setBackgroundColor(BG); isClickable = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7C6CF0.toInt(), 0xFF2A1B66.toInt())).apply { cornerRadius = dp(28).toFloat() }
            alpha = 0f; scaleX = 0.4f; scaleY = 0.4f
        }
        val title = tx("MA FOCUS", 28f, TX, "sans-serif-black").apply { letterSpacing = 0.6f; alpha = 0f }
        val tag = tx("TAKE YOUR ATTENTION BACK", 11f, MU, "sans-serif-medium").apply { letterSpacing = 0.2f; alpha = 0f }
        col.addView(logo, lp(dp(112), dp(112)))
        col.addView(title, lp(WC, WC).apply { topMargin = dp(24) })
        col.addView(tag, lp(WC, WC).apply { topMargin = dp(6) })
        fl.addView(col, FrameLayout.LayoutParams(WC, WC, Gravity.CENTER))
        addContentView(fl, ViewGroup.LayoutParams(MP, MP))
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).setInterpolator(OvershootInterpolator(1.4f)).start()
        title.animate().alpha(1f).setStartDelay(250).setDuration(450).start()
        ValueAnimator.ofFloat(0.6f, 0.16f).apply {
            duration = 800; startDelay = 250
            addUpdateListener { title.letterSpacing = it.animatedValue as Float }
            start()
        }
        tag.animate().alpha(1f).setStartDelay(500).setDuration(400).start()
        fl.animate().alpha(0f).setStartDelay(1500).setDuration(380)
            .withEndAction { (fl.parent as? ViewGroup)?.removeView(fl) }.start()
    }

    // ---------- SETTINGS ----------
    private fun settings(root: LinearLayout) {
        val lim = Store.limit(this); val iv = Store.interval(this); val mode = Store.mode(this)

        // appearance
        val th = card(root)
        th.addView(label("Theme"))
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cur = Store.theme(this)
        THEMES.forEachIndexed { i, t ->
            tr.addView(TextView(this).apply {
                text = (if (i == cur) "✓  " else "") + t.name; textSize = 13f
                setTextColor(Color.parseColor(t.tx)); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = round(Color.parseColor(t.card), 12, Color.parseColor(if (i == cur) t.ac else t.line))
                setOnClickListener { Store.setTheme(this@MainActivity, i); build() }
            }, lp(WC, WC).apply { rightMargin = dp(8) })
        }
        hs.addView(tr); th.addView(hs)

        val l = card(root)
        l.addView(label("Daily reel limit"))
        val lf = field("30", InputType.TYPE_CLASS_NUMBER).apply { setText(lim.toString()) }
        l.addView(lf)
        btn(l, "Save limit") { Store.setLimit(this, lf.text.toString().toIntOrNull() ?: 30); build() }
        btn(l, "Reset today's count", false) { Store.setCount(this, 0); build() }

        val r = card(root)
        r.addView(label("Reminders"))
        r.addView(tx("Remind me every this many reels once I'm past my limit:", 13f, MU))
        val rf = field("5", InputType.TYPE_CLASS_NUMBER).apply { setText(iv.toString()) }
        r.addView(rf, lp(MP, WC).apply { topMargin = dp(8) })
        btn(r, "Save reminder interval") { Store.setInterval(this, rf.text.toString().toIntOrNull() ?: 5); build() }
        r.addView(tx("Reminder style", 13f, MU).apply { setPadding(0, dp(16), 0, 0) })
        seg(r, listOf("Gentle", "Medium", "Strict"), mode) { Store.setMode(this, it); build() }
        r.addView(tx(listOf(
            "Gentle: your tasks one by one, then kind messages asking you to close the app.",
            "Medium: your tasks one by one, then rude, sarcastic messages.",
            "Strict: your tasks, then brutal roasts. From the 3rd reminder on, the app is closed for you (sent to the home screen), and again on every reel after that today."
        )[mode], 12f, MU).apply { setPadding(0, dp(8), 0, 0) })

        // widget
        val w = card(root)
        w.addView(label("Floating widget"))
        w.addView(tx(if (Store.widgetOn(this)) "Shown while Instagram or Shorts are open. Tap it to open MA FOCUS." else "Hidden. Reels are still counted.", 13f, MU))
        btn(w, if (Store.widgetOn(this)) "Turn widget off" else "Turn widget on", !Store.widgetOn(this)) {
            Store.setWidgetOn(this, !Store.widgetOn(this)); build()
        }
        btn(w, "Customize size & position (live preview)", false) { openWidgetPreview() }

        // automatic mode
        val am = card(root)
        am.addView(label("Automatic mode"))
        am.addView(tx("Turns screen access on only while Instagram or YouTube is open and off everywhere else, so UPI apps work normally.", 13f, MU))
        val okU = Auto.hasUsage(this); val okS = Auto.hasSecure(this)
        am.addView(View(this), lp(MP, dp(8)))
        row(am, "1. Usage access", if (okU) "Granted" else "Needed", if (okU) GOOD else BAD)
        row(am, "2. Secure settings (one-time, computer)", if (okS) "Granted" else "Needed", if (okS) GOOD else BAD)
        if (!okU) btn(am, "Grant usage access", false) {
            go(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        }
        if (!okS) {
            am.addView(tx("Run this once on a computer with your phone plugged in and USB debugging on:", 12f, MU).apply { setPadding(0, dp(8), 0, dp(4)) })
            am.addView(tx("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 12f, TX, "monospace").apply { setTextIsSelectable(true) })
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            btn(am, "Allow notifications (status icon)", false) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        btn(am, "Allow background running (battery)", false) { go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        if (okU && okS) btn(am, if (Store.auto(this)) "Turn automatic mode OFF" else "Turn automatic mode ON", !Store.auto(this)) {
            val v = !Store.auto(this); Store.setAuto(this, v)
            if (v) Auto.start(this) else Auto.stop(this)
            build()
        }

        val up = card(root)
        up.addView(label("Using UPI or banking apps?"))
        up.addView(tx("Payment apps refuse to run while any screen-access app is on.", 13f, MU))
        if (Store.auto(this)) up.addView(tx("Automatic mode is on: screen access stays off outside Instagram and YouTube.", 13f, TX).apply { setPadding(0, dp(8), 0, 0) })
        else if (accessOn()) btn(up, "Pause screen access", false) {
            ReelService.instance?.disableSelf()
            root.postDelayed({ build() }, 700)
        } else btn(up, "Turn screen access back on") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        val d = card(root)
        d.addView(label("Troubleshooting"))
        btn(d, if (Store.countAll(this)) "Count ALL scrolling: ON" else "Count ALL scrolling: OFF", false) {
            Store.setCountAll(this, !Store.countAll(this)); build()
        }
        d.addView(tx("Turn on if reels aren't counted. Last event: ${Store.dbg(this)}", 11f, MU).apply { setPadding(0, dp(10), 0, 0) })
        d.addView(tx("Works on Android 8.0 and newer (phones and tablets). Install the same APK on each device and enable screen access there.", 11f, MU).apply { setPadding(0, dp(10), 0, 0) })
        d.addView(tx("Play Protect warning? Tap More details, then Install anyway. If it keeps blocking the app: Play Store, profile icon, Play Protect, settings, turn off Scan apps with Play Protect.", 11f, MU).apply { setPadding(0, dp(10), 0, 0) })
    }
}
