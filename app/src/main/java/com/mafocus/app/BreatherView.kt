package com.mafocus.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

object Fonts {
    val names = listOf("Clean", "Elegant", "Bold", "Light", "Mono", "Handwritten", "Condensed")
    fun tf(i: Int): Typeface = when (i) {
        0 -> Typeface.create("sans-serif", Typeface.NORMAL)
        1 -> Typeface.create("serif", Typeface.ITALIC)
        2 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        3 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        4 -> Typeface.MONOSPACE
        5 -> Typeface.create("cursive", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
}

/** Picture on the pause screen: 0 breathing rings (animated), 1 sunrise, 2 night, 3 your own photo. */
class BreatherArt(c: Context, private val style: Int, private val ac: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private val photo: Bitmap? = if (style == 3) {
        try { BitmapFactory.decodeFile(File(c.filesDir, "breather.jpg").path) } catch (_: Exception) { null }
    } else null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (style == 0) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    private fun al(col: Int, a: Int) = (col and 0x00FFFFFF) or (a shl 24)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h / 2
        val r = minOf(w, h) / 2
        p.style = Paint.Style.FILL
        if (style == 0) {
            val base = r * (0.45f + 0.5f * phase)
            p.color = al(ac, 40); cv.drawCircle(cx, cy, base, p)
            p.color = al(ac, 80); cv.drawCircle(cx, cy, base * 0.72f, p)
            p.color = al(ac, 150); cv.drawCircle(cx, cy, base * 0.45f, p)
            return
        }
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 28 * d, 28 * d, Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)
        when (style) {
            1 -> {
                p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF5B4FD6.toInt(), 0xFFFFB36B.toInt(), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, p); p.shader = null
                p.color = 0xFFFFE08A.toInt(); cv.drawCircle(cx, h * 0.62f, r * 0.38f, p)
                p.color = 0xFF2B3A67.toInt(); cv.drawOval(RectF(-w * 0.2f, h * 0.70f, w * 0.75f, h * 1.5f), p)
                p.color = 0xFF1E2A4A.toInt(); cv.drawOval(RectF(w * 0.25f, h * 0.78f, w * 1.3f, h * 1.6f), p)
            }
            2 -> {
                p.color = 0xFF0B1030.toInt(); cv.drawRect(0f, 0f, w, h, p)
                p.color = 0xFFFFFFFF.toInt()
                listOf(0.15f to 0.2f, 0.8f to 0.15f, 0.3f to 0.75f, 0.85f to 0.6f, 0.55f to 0.1f, 0.12f to 0.5f, 0.7f to 0.88f)
                    .forEach { cv.drawCircle(w * it.first, h * it.second, 2.2f * d, p) }
                p.color = 0xFFF4E9C9.toInt(); cv.drawCircle(cx, cy, r * 0.42f, p)
                p.color = 0xFF0B1030.toInt(); cv.drawCircle(cx + r * 0.2f, cy - r * 0.08f, r * 0.38f, p)
            }
            else -> {
                p.color = al(ac, 40); cv.drawRect(0f, 0f, w, h, p)
                photo?.let { b ->
                    val s = maxOf(w / b.width, h / b.height)
                    val bw = b.width * s; val bh = b.height * s
                    cv.drawBitmap(b, null, RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), null)
                }
            }
        }
        cv.restore()
    }
}

/** Bar that shrinks from full to empty. */
class ShrinkBar(c: Context, private val track: Int, private val fill: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var fraction = 1f
        set(v) { field = v; invalidate() }
    override fun onDraw(cv: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        p.color = track; cv.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, p)
        p.color = fill; cv.drawRoundRect(RectF(0f, 0f, w * fraction, h), h / 2, h / 2, p)
    }
}

/** The full-screen pause: picture, your message, countdown bar, Not now / Continue. */
class BreatherView(c: Context, appName: String) : FrameLayout(c) {
    var onContinue: () -> Unit = {}
    var onLeave: () -> Unit = {}
    private var anim: ValueAnimator? = null
    private val d = c.resources.displayMetrics.density
    private val secs = Store.bSecs(c)
    private val bar: ShrinkBar
    private val count: TextView
    private val cont: Button

    private fun dp(i: Int) = (i * d).toInt()
    private fun mk(c: Context, label: String, fill: Int, txt: Int, font: Typeface) = Button(c).apply {
        text = label; isAllCaps = false; textSize = 15f; typeface = font; setTextColor(txt); stateListAnimator = null
        background = GradientDrawable().apply { setColor(fill); cornerRadius = 14 * d }
    }

    init {
        val th = THEMES[Store.bTheme(c).coerceIn(0, THEMES.size - 1)]
        val bg = Color.parseColor(th.bg); val tx = Color.parseColor(th.tx)
        val ac = Color.parseColor(th.ac); val b2 = Color.parseColor(th.btn2)
        val font = Fonts.tf(Store.bFont(c))
        isClickable = true
        setBackgroundColor(bg)

        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(40))
        }
        col.addView(BreatherArt(c, Store.bArt(c), ac), LinearLayout.LayoutParams(dp(210), dp(210)))
        col.addView(TextView(c).apply {
            text = Store.bMsg(c).replace("{app}", appName); textSize = 24f; setTextColor(tx); typeface = font
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        count = TextView(c).apply { text = "$secs"; textSize = 44f; setTextColor(ac); typeface = font; gravity = Gravity.CENTER }
        col.addView(count, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        bar = ShrinkBar(c, b2, ac)
        col.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(6); leftMargin = dp(12); rightMargin = dp(12) })
        val leave = mk(c, "Not now", b2, tx, font).apply { setOnClickListener { onLeave() } }
        cont = mk(c, "Continue", ac, Color.WHITE, font).apply {
            isEnabled = false; alpha = 0.4f; setOnClickListener { onContinue() }
        }
        col.addView(leave, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(32) })
        col.addView(cont, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })

        val sv = ScrollView(c).apply { isFillViewport = true; addView(col, LayoutParams(-1, -1)) }
        addView(sv, LayoutParams(-1, -1))

        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = secs * 1000L; interpolator = LinearInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                bar.fraction = f; count.text = kotlin.math.ceil(f * secs).toInt().toString()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { count.text = "Ready"; cont.isEnabled = true; cont.alpha = 1f }
            })
        }
    }

    fun start() { anim?.start() }
    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
}
