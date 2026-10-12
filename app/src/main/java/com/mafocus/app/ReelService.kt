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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class ReelService : AccessibilityService() {
    private var last = 0L
    private var lastEval = 0L
    private var lastReel = 0L
    private var fgPkg: String? = null
    private var fgSince = 0L
    private var wm: WindowManager? = null
    private var tv: TextView? = null
    private val ui = Handler(Looper.getMainLooper())
    private val evalRun = Runnable { evaluate() }

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

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { closeSession(); show(false) }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "widget") ui.post { if (Store.widgetOn(this)) evaluate() else show(false) }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val t = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(40, 16, 40, 16); visibility = View.GONE
            maxWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 90 }
        wm?.addView(t, lp); tv = t
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isNoise(pkg)) return
                // Don't trust a single event (popups, our own overlay, system dialogs cause flicker).
                // Wait a moment, then check which app is really on screen.
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
        } else if (fgPkg != null || tv?.visibility == View.VISIBLE) {
            closeSession(); show(false)
        }
    }

    // Counts at the START of a swipe (so a swipe is never lost), then ignores the rest of
    // that swipe's scroll events for 650 ms. Detection is retried every 120 ms until it succeeds.
    private fun onScroll(pkg: String, e: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 650 || now - lastEval < 120) return
        lastEval = now
        if (fgPkg != pkg) { closeSession(); openSession(pkg) }
        val reels = isReelsEvent(pkg, e)
        Store.setDbg(this, "${Store.APPS[pkg]} | ${e.source?.viewIdResourceName?.substringAfter(":id/")} | reels=$reels")
        show(reels || pkg in always)
        if (!reels) return
        last = now
        Store.addTime(this, "r", pkg, if (lastReel == 0L) 5000L else minOf(now - lastReel, 60000L))
        lastReel = now
        Store.setCount(this, Store.count(this) + 1)
        refresh()
    }

    // Best signal: the view that scrolled (or its parents) has a reels/shorts id.
    private fun isReelsEvent(pkg: String, e: AccessibilityEvent): Boolean {
        if (Store.countAll(this)) return true
        val ks = keys[pkg] ?: return true
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
        fgPkg = null; lastReel = 0L
    }

    private fun show(v: Boolean) {
        val t = tv ?: return
        val want = if (v && Store.widgetOn(this)) View.VISIBLE else View.GONE
        if (t.visibility != want) t.visibility = want
        if (want == View.VISIBLE) refresh()
    }

    private fun refresh() {
        val c = Store.count(this); val l = Store.limit(this); val h = Store.health(this)
        var s = "${Store.face(h)}  $c / $l"
        val over = c - l
        // Every 5th reel past the limit: task 1, task 2, ... last task, then a motivational
        // message (a different one each loop), then the loop starts again.
        if (over > 0 && over % 5 == 0) {
            val n = over / 5
            val t = Store.tasks(this)
            val len = t.size + 1
            val pos = (n - 1) % len
            s += if (pos < t.size) "\nTask ${pos + 1}: ${t[pos]}"
                 else "\n" + msgs[((n - 1) / len) % msgs.size]
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
        ui.removeCallbacks(evalRun)
        closeSession()
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        tv?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
