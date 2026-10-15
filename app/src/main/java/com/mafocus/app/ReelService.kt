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

    // Medium and Strict mode: blunt, rude, meant to shame you into closing the app
    private val rude = listOf(
        "Still scrolling? Bold move for someone with goals.",
        "Your goals just unfollowed you.",
        "Wow. Another reel. Truly the best use of your life.",
        "Nobody ever said 'I wish I'd scrolled more.' Close it.",
        "Strangers dancing are doing better than you right now. Close the app.",
        "You're not relaxing, you're avoiding. Close it.",
        "You blew past your limit. Not a flex. Close the app.",
        "This is exactly what you said you wouldn't do. Close it.",
        "Your future self is rolling their eyes. Close the app.",
        "Same reels, different day. Go do something real.",
        "The algorithm thanks you for your free time. Close the app.",
        "Embarrassing. You had one job: stop at your limit."
    )

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { closeSession(); show(false) }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "widget") ui.post { if (Store.widgetOn(this)) evaluate() else show(false) }
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
                ui.removeCallbacks(evalRun)
                ui.postDelayed(evalRun, 400)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (Store.APPS.containsKey(pkg)) onScroll(pkg, e)
        }
    }

    private fun isNoise(p: String) =
        p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard")

    private fun evaluate() {
        val p = rootInActiveWindow?.packageName?.toString() ?: return
        if (isNoise(p)) return
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
            val pool = if (Store.mode(this) == 0) msgs else rude
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
        closeSession(); attach(false)
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        super.onDestroy()
    }
}
