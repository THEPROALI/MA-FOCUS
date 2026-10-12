package com.mafocus.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class ReelService : AccessibilityService() {
    private var last = 0L
    private var wm: WindowManager? = null
    private var tv: TextView? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val t = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(40, 16, 40, 16)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 90 }
        wm?.addView(t, lp); tv = t
        refresh()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val now = SystemClock.elapsedRealtime()
        if (now - last < 900) return   // one swipe = many scroll events; count once
        last = now
        Store.setCount(this, Store.count(this) + 1)
        refresh()
    }

    private fun refresh() {
        val c = Store.count(this); val l = Store.limit(this); val h = Store.health(this)
        var s = "${Store.face(h)}  $c / $l"
        if (c > l) Store.tasks(this).firstOrNull()?.let { s += "\nDo this instead: $it" }
        tv?.apply {
            text = s
            background = GradientDrawable().apply { cornerRadius = 60f; setColor(Store.color(h)) }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        tv?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
