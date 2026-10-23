package com.mafocus.app

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class ReelService : AccessibilityService() {
    companion object { var instance: ReelService? = null }

    // counting state
    private var last = 0L
    private var lastEvt = 0L
    private var lockUntil = 0L
    private var lastDbg = 0L
    private var lastEval = 0L
    private var lastReel = 0L
    private var acc = 0f
    private var lastIdx = -1
    private var pagerKey = ""
    private var settlePkg = ""
    private var fgPkg: String? = null
    private var fgSince = 0L

    // floating counter
    private var wm: WindowManager? = null
    private var box: FrameLayout? = null
    private var tv: TextView? = null
    private var attached = false
    private lateinit var lp: WindowManager.LayoutParams
    private var pulse: ValueAnimator? = null
    private var edge: View? = null
    private var edgeAnim: ValueAnimator? = null
    private var holdUntil = 0L
    private var hits = 0
    private var banner: TextView? = null
    private var pendingRoast = ""

    // breather
    private var bView: BreatherView? = null
    private var bAttached = false
    private var bPkg = ""

    private val ui = Handler(Looper.getMainLooper())
    private val evalRun = Runnable { evaluate() }
    private val settleRun = Runnable { settle() }
    private val closeRun = Runnable { performGlobalAction(GLOBAL_ACTION_HOME); showBanner(pendingRoast) }
    private val bannerRun = Runnable { hideBanner() }

    // Widget is shown for the whole app here; other apps only while reels/shorts are open
    private val always = setOf("com.instagram.android")
    private val YT = "com.google.android.youtube"

    // view-id fragments that mean "reels / shorts screen is open"
    private val keys = mapOf(
        "com.instagram.android" to listOf("clips"),
        YT to listOf("reel", "shorts"),
        "com.facebook.katana" to listOf("reel"),
        "com.snapchat.android" to listOf("spotlight")
    )

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

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { closeSession(); show(false); hideBreather() }
    }
    private val lookKeys = setOf("wsize", "wx", "wy", "wstyle", "wcolor", "walpha", "wanim", "wedge")
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "widget") ui.post { if (Store.widgetOn(this)) evaluate() else show(false) }
        else if (k in lookKeys) ui.post { applyLayout(); refresh() }
    }

    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val t = TextView(this).apply {
            gravity = Gravity.CENTER
            maxWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()
            setOnClickListener { openApp() }          // tap the widget -> open MA FOCUS
        }
        tv = t
        // extra padding around the counter leaves room for the pop animation
        box = FrameLayout(this).apply {
            setPadding(dp(40), dp(26), dp(40), dp(26)); clipChildren = false; clipToPadding = false
            addView(t, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
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

    // size and position come from Settings
    private fun applyLayout() {
        val t = tv ?: return
        if (!::lp.isInitialized) return
        val sz = WidgetStyle.sizeSp(this)
        val k = sz / 14f
        t.textSize = sz
        t.setPadding((36 * k).toInt(), (14 * k).toInt(), (36 * k).toInt(), (14 * k).toInt())
        val dm = resources.displayMetrics
        val hz = when (Store.wx(this)) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        lp.gravity = Gravity.TOP or hz
        lp.x = if (Store.wx(this) == 1) 0 else (12 * dm.density).toInt()
        lp.y = (dm.heightPixels * 0.88f * Store.wy(this) / 100f).toInt()
        val b = box
        if (attached && b != null) try { wm?.updateViewLayout(b, lp) } catch (_: Exception) {}
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

    // ---------------- breather: a real pause screen ----------------
    private fun maybeBreather(pkg: String) {
        if (bAttached || pkg == packageName || isNoise(pkg)) return
        if (!Store.breatherOn(this) || pkg !in Store.breatherApps(this)) return
        if (Store.passUntil(this, pkg) > System.currentTimeMillis()) return
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                   catch (_: Exception) { "this app" }
        // 1) instant cover (in case the activity is slow to start)
        val v = BreatherView(this, name)
        v.onContinue = {
            Store.setPass(this, pkg, System.currentTimeMillis() + Store.bPass(this) * 60_000L)
            hideBreather(); BreatherActivity.current?.finish()
        }
        v.onLeave = { hideBreather(); BreatherActivity.current?.finish(); performGlobalAction(GLOBAL_ACTION_HOME) }
        val p2 = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        try { wm?.addView(v, p2); bView = v; bAttached = true; bPkg = pkg; v.start() } catch (_: Exception) {}
        // 2) full-screen activity on top: this is what actually pauses the app underneath
        try {
            startActivity(Intent(this, BreatherActivity::class.java).putExtra("pkg", pkg).putExtra("name", name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        } catch (_: Exception) {}
        ui.postDelayed({ if (BreatherActivity.active) hideBreather() }, 900)
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

    // ---------------- counting ----------------
    private fun onScroll(pkg: String, e: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()
        if (fgPkg != pkg) { closeSession(); openSession(pkg); lastIdx = -1; pagerKey = "" }
        val from = e.fromIndex; val to = e.toIndex
        val dy = if (Build.VERSION.SDK_INT >= 28) e.scrollDeltaY else 0
        if (now - lastDbg > 300) {
            lastDbg = now
            Store.setDbg(this, "${Store.APPS[pkg]} | ${e.source?.viewIdResourceName?.substringAfter(":id/")} | ${e.className?.toString()?.substringAfterLast('.')} | idx=$from..$to dy=$dy")
        }

        // Method 1: list position. A reels pager shows 1-2 items; a normal feed/list shows many.
        // A reel is counted only when the pager has settled on the NEXT item (half-swipes never count).
        if (from >= 0 && to >= from) {
            if (to - from > 1) { lastIdx = -1; pagerKey = ""; return }      // normal scrolling: ignore
            val key = "$pkg|${e.source?.viewIdResourceName}|${e.className}"
            if (key != pagerKey) { pagerKey = key; lastIdx = from; return }
            if (lastIdx in 0 until from) {
                val steps = minOf(from - lastIdx, 5)
                lastIdx = from
                if (isReelsEvent(pkg, e, true)) repeat(steps) { countReel(pkg, SystemClock.elapsedRealtime()) }
            } else lastIdx = from
            return
        }

        // Method 2 (only if this app reports no list positions): total scroll distance
        if (now < lockUntil) return
        if (now - lastEvt > 3000) acc = 0f
        lastEvt = now
        if (dy != 0) {
            acc += dy
            if (abs(acc) >= resources.displayMetrics.heightPixels * 0.6f) {
                acc = 0f; lockUntil = now + 500
                if (isReelsEvent(pkg, e, pkg != YT)) countReel(pkg, now)
            }
        } else if (now - lastEval >= 250) {
            lastEval = now
            if (isReelsEvent(pkg, e, pkg != YT)) { settlePkg = pkg; ui.removeCallbacks(settleRun); ui.postDelayed(settleRun, 450) }
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
        BoxWidget.refresh(this)
        show(true)
        refresh()
        pop()
        val over = Store.count(this) - Store.limit(this)
        if (over > 0 && Store.wvib(this)) buzz(if (Store.wanim(this) == 2) 220L else 90L)
        // Strict mode: from the 3rd reminder on, show a roast, then send you to the home screen
        if (Store.mode(this) == 2 && over >= 3 * Store.interval(this)) strictClose()
    }

    private fun strictClose() {
        if (SystemClock.elapsedRealtime() < holdUntil) return
        val roast = Roasts.pick(this, hits, hits); hits++
        pendingRoast = roast
        holdUntil = SystemClock.elapsedRealtime() + 3800
        tv?.text = WidgetStyle.span("🔥  STRICT MODE\n$roast")
        ui.removeCallbacks(closeRun); ui.postDelayed(closeRun, 3600)
    }

    // The roast stays on the home screen after the app is closed, so you actually read it
    private fun showBanner(text: String) {
        hideBanner()
        val t = TextView(this).apply {
            this.text = "🔥  STRICT MODE\n$text"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(22), dp(18), dp(22), dp(18))
            background = GradientDrawable().apply { setColor(0xEE8B0000.toInt()); cornerRadius = dp(18).toFloat() }
        }
        val p3 = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = dp(110); horizontalMargin = 0.04f }
        try { wm?.addView(t, p3); banner = t; ui.postDelayed(bannerRun, 8000) } catch (_: Exception) {}
    }
    private fun hideBanner() {
        ui.removeCallbacks(bannerRun)
        banner?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        banner = null
    }

    // Best signal: the view that scrolled (or its parents) has a reels/shorts id.
    private fun isReelsEvent(pkg: String, e: AccessibilityEvent, treeOk: Boolean): Boolean {
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
        return treeOk && isReels(pkg)
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
        fgPkg = null; lastReel = 0L; acc = 0f; lastIdx = -1; pagerKey = ""
    }

    // ---------------- the floating counter ----------------
    private fun attach(v: Boolean) {
        val b = box ?: return
        try {
            if (v && !attached) { wm?.addView(b, lp); attached = true }
            else if (!v && attached) {
                wm?.removeView(b); attached = false
                pulse?.cancel(); pulse = null; tv?.alpha = 1f
                showEdge(false)
            }
        } catch (_: Exception) {}
    }

    private fun show(v: Boolean) {
        val want = v && Store.widgetOn(this)
        attach(want)
        if (want) refresh()
    }

    private fun refresh() {
        if (SystemClock.elapsedRealtime() < holdUntil) return      // a strict-mode roast is on screen
        val c = Store.count(this); val l = Store.limit(this); val h = Store.health(this)
        var s = "${Store.face(h)}  $c / $l"
        val over = c - l
        // Every N-th reel past the limit (N is set in Settings): task 1, task 2, ... last task,
        // then a message that depends on the style: kind, rude or a brutal roast.
        val iv = Store.interval(this)
        if (over > 0 && over % iv == 0) {
            val n = over / iv
            val t = Store.tasks(this)
            val len = t.size + 1
            val pos = (n - 1) % len
            val k = (n - 1) / len
            s += if (pos < t.size) "\nTask ${pos + 1}: ${t[pos]}"
                 else "\n" + when (Store.mode(this)) { 0 -> msgs[k % msgs.size]; 1 -> rude[k % rude.size]; else -> Roasts.pick(this, k, k) }
        }
        tv?.let {
            if (it.text.toString() != s) it.text = WidgetStyle.span(s)
            WidgetStyle.apply(this, it, h)
        }
        updatePulse(over > 0)
        showEdge(attached && over > 0 && Store.wedge(this))
    }

    // pop every time a reel is counted
    private fun pop() {
        val lvl = Store.wanim(this); val t = tv ?: return
        if (lvl == 0 || !attached) return
        val w = t.width.coerceAtLeast(1)
        val s = minOf(if (lvl == 1) 1.25f else 1.6f, 1f + 2f * dp(40) / w)
        t.animate().cancel()
        t.animate().scaleX(s).scaleY(s).rotation(if (lvl == 2) 5f else 0f).setDuration(110).withEndAction {
            t.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    // keep blinking while you are over the limit
    private fun updatePulse(on: Boolean) {
        val lvl = Store.wanim(this)
        if (on && lvl > 0 && attached) {
            if (pulse == null) pulse = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (lvl == 2) 420L else 900L
                repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { tv?.alpha = 1f - (if (lvl == 2) 0.7f else 0.4f) * (it.animatedValue as Float) }
                start()
            }
        } else { pulse?.cancel(); pulse = null; tv?.alpha = 1f }
    }

    // red glowing border around the whole screen while you're over the limit
    private fun showEdge(on: Boolean) {
        if (on && edge == null) {
            val v = View(this).apply {
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(10), 0xFFFF1744.toInt()) }
            }
            val p4 = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                wm?.addView(v, p4); edge = v
                edgeAnim = ValueAnimator.ofFloat(0.25f, 1f).apply {
                    duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { v.alpha = it.animatedValue as Float }
                    start()
                }
            } catch (_: Exception) {}
        } else if (!on && edge != null) {
            edgeAnim?.cancel(); edgeAnim = null
            try { wm?.removeView(edge) } catch (_: Exception) {}
            edge = null
        }
    }

    private fun buzz(ms: Long) {
        try { getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
        catch (_: Exception) {}
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        if (instance === this) instance = null
        ui.removeCallbacks(evalRun); ui.removeCallbacks(settleRun); ui.removeCallbacks(closeRun)
        hideBreather(); hideBanner(); closeSession(); attach(false); showEdge(false)
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        super.onDestroy()
    }
}
