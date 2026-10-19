package com.mafocus.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class ReelService : AccessibilityService() {
    companion object { var instance: ReelService? = null }

    private var last = 0L
    private var lastEvt = 0L
    private var lockUntil = 0L
    private var lastDbg = 0L
    private var lastEval = 0L
    private var lastReel = 0L
    private var acc = 0f
    private var settlePkg = ""
    private var fgPkg: String? = null
    private var fgSince = 0L
    private var wm: WindowManager? = null
    private var tv: TextView? = null
    private var attached = false
    private lateinit var lp: WindowManager.LayoutParams
    private val ui = Handler(Looper.getMainLooper())
    private val evalRun = Runnable { evaluate() }
    private val settleRun = Runnable { settle() }
    private val closeRun = Runnable { performGlobalAction(GLOBAL_ACTION_HOME) }
    private var bView: BreatherView? = null
    private var bAttached = false
    private var bPkg = ""
    private val passUntil = HashMap<String, Long>()

    // Widget is shown for the whole app here; other apps only while reels/shorts are open
    private val always = setOf("com.instagram.android")

    // view-id fragments that mean "reels / shorts screen is open"
    private val keys = mapOf(
        "com.instagram.android" to listOf("clips"),
        "com.google.android.youtube" to listOf("reel", "shorts"),
        "com.facebook.katana" to listOf("reel"),
        "com.snapchat.android" to listOf("spotlight")
    )

    // shown after the last task of each loop; a different one every loop
    private val msgs = listOf(
        "Is more scrolling really required? Close the app.",
        "You set a limit. Keep that promise and close the app.",
        "Nothing in this feed will matter tomorrow. Your goals will.",
        "Stop here. Close the app and take a deep breath.",
        "Every extra reel costs time you can't get back. Close it now.",
        "You're stronger than the scroll. Close the app.",
        "Put the phone down and do one thing that moves your life forward.",
        "The feed has no limit. You do. Close the app.",
        "Future you is asking you to stop. Close the app.",
        "Pause. Is this really how you want to spend your day?",
        "Win this moment. Close the app and don't look back.",
        "Enough for now. Your focus is worth more than this feed."
    )

    // Medium mode: rude and sarcastic
    private val rude = listOf(
        "Still scrolling? Bold move for someone with goals.",
        "Your goals just unfollowed you.",
        "Wow. Another reel. Truly the best use of your life.",
        "Nobody ever said 'I wish I'd scrolled more.' Close it.",
        "You're not relaxing, you're avoiding. Close it.",
        "You blew past your limit. Not a flex. Close the app.",
        "This is exactly what you said you wouldn't do. Close it.",
        "Your future self is rolling their eyes. Close the app.",
        "Same reels, different day. Go do something real.",
        "The algorithm thanks you for your free time.",
        "You had one job: stop at your limit. One.",
        "Impressive dedication to doing absolutely nothing."
    )

    // Strict mode: brutal roasts
    private val brutal = listOf(
        "Your thumb has had a more productive day than you have.",
        "Your brain cells just filed a missing-person report.",
        "You've scrolled more today than you've worked. Commitment to nothing, truly inspiring.",
        "Your ancestors survived plagues so you could watch strangers lip-sync. Proud of you.",
        "That guy on screen has 3 million followers. You have a task list you keep ignoring.",
        "'Just five minutes.' You said it like it was a promise you'd ever keep.",
        "Somewhere there's a version of you who closed this app 20 reels ago and is winning.",
        "This isn't unwinding. It's rotting with extra steps.",
        "Even your phone battery is embarrassed for you.",
        "The algorithm knows you better than you know your own goals. Sit with that.",
        "Plot twist: nobody is coming to do your tasks for you. Put the phone down.",
        "Your screen-time report needs a therapist after reading what you did today.",
        "You outscrolled your own willpower. It didn't even put up a fight.",
        "Your thumb is getting more exercise than the rest of you."
    )

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { closeSession(); show(false); hideBreather() }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "widget") ui.post { if (Store.widgetOn(this)) evaluate() else show(false) }
        else if (k == "wsize" || k == "wx" || k == "wy") ui.post { applyLayout() }
    }

    // widget size and position come from Settings
    private fun applyLayout() {
        val t = tv ?: return
        if (!::lp.isInitialized) return
        val sz = floatArrayOf(11f, 14f, 18f)[Store.wsize(this).coerceIn(0, 2)]
        val k = sz / 14f
        t.textSize = sz
        t.setPadding((40 * k).toInt(), (16 * k).toInt(), (40 * k).toInt(), (16 * k).toInt())
        val dm = resources.displayMetrics
        val hz = when (Store.wx(this)) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        lp.gravity = Gravity.TOP or hz
        lp.x = if (Store.wx(this) == 1) 0 else (12 * dm.density).toInt()
        lp.y = (dm.heightPixels * 0.88f * Store.wy(this) / 100f).toInt()
        if (attached) try { wm?.updateViewLayout(t, lp) } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tv = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(40, 16, 40, 16)
            maxWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()
            setOnClickListener { openApp() }          // tap the widget -> open MA FOCUS
        }
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 90 }
        applyLayout()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        ui.postDelayed(evalRun, 300)   // show the widget straight away if Instagram is already open
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isNoise(pkg)) return
                if (pkg != packageName) { if (bAttached && pkg != bPkg) hideBreather() else maybeBreather(pkg) }
                ui.removeCallbacks(evalRun)
                ui.postDelayed(evalRun, 400)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (Store.APPS.containsKey(pkg)) onScroll(pkg, e)
        }
    }

    // Breather mode: a full-screen pause with a countdown before the chosen apps open
    private fun maybeBreather(pkg: String) {
        if (bAttached || pkg == packageName || isNoise(pkg)) return
        if (!Store.breatherOn(this) || pkg !in Store.breatherApps(this)) return
        if ((passUntil[pkg] ?: 0L) > SystemClock.elapsedRealtime()) return
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                   catch (_: Exception) { "this app" }
        val v = BreatherView(this, name)
        v.onContinue = {
            passUntil[pkg] = SystemClock.elapsedRealtime() + Store.bPass(this) * 60_000L
            hideBreather()
        }
        v.onLeave = { hideBreather(); performGlobalAction(GLOBAL_ACTION_HOME) }
        val p2 = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        try { wm?.addView(v, p2); bView = v; bAttached = true; bPkg = pkg; v.start() } catch (_: Exception) {}
    }

    private fun hideBreather() {
        bView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        bView = null; bAttached = false; bPkg = ""
    }

    private fun isNoise(p: String) =
        p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard")

    private fun evaluate() {
        val p = rootInActiveWindow?.packageName?.toString() ?: return
        if (isNoise(p)) return
        if (p != packageName) { if (bAttached && p != bPkg) hideBreather() else maybeBreather(p) }
        if (Store.APPS.containsKey(p)) {
            if (fgPkg != p) { closeSession(); openSession(p); show(p in always) }
            else if (p in always) show(true)
        } else if (fgPkg != null || attached) {
            closeSession(); show(false)
        }
    }

    // A reel is counted only when the pager has really moved by ~a page (sum of vertical scroll
    // distance). Half-swipes that snap back cancel out, and holding a finger down counts nothing.
    private fun onScroll(pkg: String, e: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now < lockUntil) return
        if (now - lastEvt > 3000) acc = 0f
        lastEvt = now
        if (fgPkg != pkg) { closeSession(); openSession(pkg) }
        val dy = if (Build.VERSION.SDK_INT >= 28) e.scrollDeltaY else 0
        if (now - lastDbg > 300) {
            lastDbg = now
            Store.setDbg(this, "${Store.APPS[pkg]} | ${e.source?.viewIdResourceName?.substringAfter(":id/")} | ${e.className?.toString()?.substringAfterLast('.')} | dy=$dy")
        }
        if (dy != 0) {
            acc += dy
            if (abs(acc) >= resources.displayMetrics.heightPixels * 0.6f) {
                acc = 0f; lockUntil = now + 500
                if (isReelsEvent(pkg, e)) countReel(pkg, now)
            }
        } else if (now - lastEval >= 250) {
            // This app doesn't report scroll distance: fall back to counting when scrolling settles.
            lastEval = now
            if (isReelsEvent(pkg, e)) { settlePkg = pkg; ui.removeCallbacks(settleRun); ui.postDelayed(settleRun, 450) }
        }
    }

    private fun settle() {
        val now = SystemClock.elapsedRealtime()
        if (now - last >= 700) countReel(settlePkg, now)
    }

    private fun countReel(pkg: String, now: Long) {
        last = now
        Store.addTime(this, "r", pkg, if (lastReel == 0L) 5000L else minOf(now - lastReel, 60000L))
        lastReel = now
        Store.setCount(this, Store.count(this) + 1)
        show(true)
        refresh()
        // Strict mode: from the 3rd reminder on, send you to the home screen (closes the reels app)
        val over = Store.count(this) - Store.limit(this)
        if (Store.mode(this) == 2 && over >= 3 * Store.interval(this)) {
            Toast.makeText(this, "Strict mode: closing the app", Toast.LENGTH_LONG).show()
            ui.removeCallbacks(closeRun); ui.postDelayed(closeRun, 1200)
        }
    }

    // Is the thing that scrolled a reels/shorts pager (and not e.g. a search box)?
    private fun isReelsEvent(pkg: String, e: AccessibilityEvent): Boolean {
        if (Store.countAll(this)) return true
        val ks = keys[pkg] ?: return true
        val cls = e.className?.toString() ?: ""
        if (cls.contains("Edit") || cls.contains("TextView") || cls.contains("Button")) return false
        var n: AccessibilityNodeInfo? = e.source
        var d = 0
        while (n != null && d < 6) {
            val id = n.viewIdResourceName
            if (id != null && ks.any { id.contains(it, true) }) return true
            n = n.parent; d++
        }
        return isReels(pkg)
    }

    private fun isReels(pkg: String): Boolean {
        val ks = keys[pkg] ?: return true
        val root = rootInActiveWindow ?: return false
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var n = 0
        while (q.isNotEmpty() && n < 1000) {
            val x = q.removeFirst(); n++
            val id = x.viewIdResourceName
            if (id != null && ks.any { id.contains(it, true) }) return true
            for (i in 0 until x.childCount) x.getChild(i)?.let { q.add(it) }
        }
        return false
    }

    private fun openSession(p: String) { fgPkg = p; fgSince = SystemClock.elapsedRealtime() }
    private fun closeSession() {
        fgPkg?.let { Store.addTime(this, "t", it, SystemClock.elapsedRealtime() - fgSince) }
        fgPkg = null; lastReel = 0L; acc = 0f
    }

    // The overlay window only exists while it is visible, so nothing is drawn over other apps.
    private fun attach(v: Boolean) {
        val t = tv ?: return
        try {
            if (v && !attached) { wm?.addView(t, lp); attached = true }
            else if (!v && attached) { wm?.removeView(t); attached = false }
        } catch (_: Exception) {}
    }

    private fun show(v: Boolean) {
        val want = v && Store.widgetOn(this)
        attach(want)
        if (want) refresh()
    }

    private fun refresh() {
        val c = Store.count(this); val l = Store.limit(this); val h = Store.health(this)
        var s = "${Store.face(h)}  $c / $l"
        val over = c - l
        // Every N-th reel past the limit (N is set in Settings): task 1, task 2, ... last task,
        // then a message (kind in Gentle, rude in Medium/Strict; a different one each loop).
        val iv = Store.interval(this)
        if (over > 0 && over % iv == 0) {
            val n = over / iv
            val t = Store.tasks(this)
            val len = t.size + 1
            val pos = (n - 1) % len
            val pool = when (Store.mode(this)) { 0 -> msgs; 1 -> rude; else -> brutal }
            s += if (pos < t.size) "\nTask ${pos + 1}: ${t[pos]}"
                 else "\n" + pool[((n - 1) / len) % pool.size]
        }
        tv?.apply {
            if (text.toString() != s) {
                text = s
                background = GradientDrawable().apply { cornerRadius = 60f; setColor(Store.color(h)) }
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        if (instance === this) instance = null
        ui.removeCallbacks(evalRun); ui.removeCallbacks(settleRun)
        hideBreather(); closeSession(); attach(false)
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        super.onDestroy()
    }
}
