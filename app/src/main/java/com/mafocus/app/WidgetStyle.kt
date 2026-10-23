package com.mafocus.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.widget.TextView

/** Look of the floating counter. Used by the real widget and by the live preview. */
object WidgetStyle {
    val STYLE_NAMES = listOf("Pill", "Neon", "Card", "Minimal")
    val COLOR_NAMES = listOf("Auto", "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink", "White")
    private val COLORS = intArrayOf(0, 0xFFFF4D4D.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD93D.toInt(), 0xFF2ECC71.toInt(),
        0xFF4C8DFF.toInt(), 0xFFA66CFF.toInt(), 0xFFFF5FA2.toInt(), 0xFFFFFFFF.toInt())
    val SIZES = floatArrayOf(11f, 14f, 18f, 26f)

    fun sizeSp(c: Context) = SIZES[Store.wsize(c).coerceIn(0, SIZES.size - 1)]

    fun base(c: Context, health: Int): Int {
        val i = Store.wcolor(c)
        return if (i in 1 until COLORS.size) COLORS[i] else Store.color(health)
    }

    private fun withA(col: Int, a: Int) = (col and 0x00FFFFFF) or (a shl 24)
    private fun dark(col: Int): Int {
        val h = FloatArray(3); Color.colorToHSV(col, h); h[2] *= 0.55f; return Color.HSVToColor(h)
    }
    private fun onColor(col: Int) = if (Color.luminance(col) > 0.6f) Color.BLACK else Color.WHITE

    fun apply(c: Context, t: TextView, health: Int) {
        val base = base(c, health)
        val a = Store.walpha(c).coerceIn(30, 100) * 255 / 100
        val d = c.resources.displayMetrics.density
        t.typeface = Typeface.DEFAULT_BOLD
        t.setShadowLayer(0f, 0f, 0f, 0)
        when (Store.wstyle(c)) {
            0 -> {
                t.background = GradientDrawable().apply { cornerRadius = 100 * d; setColor(withA(base, a)) }
                t.setTextColor(onColor(base))
            }
            1 -> {
                t.background = GradientDrawable().apply {
                    cornerRadius = 22 * d; setColor(withA(0xFF000000.toInt(), a)); setStroke((3 * d).toInt(), base)
                }
                t.setTextColor(base); t.setShadowLayer(14f, 0f, 0f, base)
            }
            2 -> {
                t.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(withA(base, a), withA(dark(base), a)))
                    .apply { cornerRadius = 20 * d; setStroke((2 * d).toInt(), withA(0xFFFFFFFF.toInt(), 120)) }
                t.setTextColor(onColor(base))
            }
            else -> {
                t.background = null
                t.setTextColor(base); t.setShadowLayer(10f, 0f, 0f, 0xFF000000.toInt())
            }
        }
    }

    /** First line (the count) is drawn bigger. */
    fun span(s: String): SpannableString {
        val sp = SpannableString(s)
        val e = s.indexOf('\n').let { if (it < 0) s.length else it }
        sp.setSpan(RelativeSizeSpan(1.3f), 0, e, 0)
        return sp
    }
}
