package com.mafocus.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var lastReel = 0L
    private var fgPkg: String? = null
    private var fgSince = 0L
    private var wm: WindowManager? = null
    private var tv: TextView? = null
    private val ui = Handler(Looper.getMainLooper())
    private val evalRun = Runnable { evaluate() }
    private val countRun = Runnable { commit() }
    private var bursting = false
    private var burstReels = false
    private var burstPkg = ""

    // Widget is shown for the whole app here; other apps only while reels/shorts are open
    private val always = setOf("com.instagram.android")

    // view-id fragments that mean "reels / shorts screen is open"
    private val keys = mapOf(
        "com.instagram.android" to listOf("clips"),
        "com.google.android.youtube" to listOf("reel_", "shorts"),
        "com.facebook.katana" to listOf("reel"),
        "com.snapchat.android" to listOf("spotlight")
    )

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { closeSession(); show(false) }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val t = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(40, 16, 40, 16); visibility = View.GONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 90 }
        wm?.addView(t, lp); tv = t
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
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

    // A swipe fires many scroll events. We decide once at the start of the swipe whether it is
    // a reel/short, then count it once the swipe has settled (450 ms of quiet).
    private fun onScroll(pkg: String, e: AccessibilityEvent) {
        if (!bursting) {
            bursting = true; burstPkg = pkg
            if (fgPkg != pkg) { closeSession(); openSession(pkg) }
            burstReels = isReelsEvent(pkg, e)
            Store.setDbg(this, "${pkg.substringAfterLast('.')} / ${e.className?.toString()?.substringAfterLast('.')} / reels=$burstReels")
            show(burstReels || pkg in always)
        }
        ui.removeCallbacks(countRun)
        ui.postDelayed(countRun, 450)
    }

    private fun commit() {
        bursting = false
        if (!burstReels) return
        val now = SystemClock.elapsedRealtime()
        if (now - last < 600) return
        last = now
        Store.addTime(this, "r", burstPkg, if (lastReel == 0L) 5000L else minOf(now - lastReel, 60000L))
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
        val want = if (v) View.VISIBLE else View.GONE
        if (t.visibility != want) t.visibility = want
        if (v) refresh()
    }

    private fun refresh() {
        val c = Store.count(this); val l = Store.limit(this); val h = Store.health(this)
        var s = "${Store.face(h)}  $c / $l"
        if (c > l) {
            val t = Store.tasks(this)
            // a different task for every extra reel past the limit
            if (t.isNotEmpty()) s += "\nDo this instead: ${t[(c - l - 1) % t.size]}"
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
        ui.removeCallbacks(evalRun); ui.removeCallbacks(countRun)
        closeSession()
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        tv?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
