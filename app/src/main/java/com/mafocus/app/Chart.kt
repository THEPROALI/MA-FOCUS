package com.mafocus.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Simple bar chart with an optional dashed limit line. */
class BarChart(
    c: Context,
    private val vals: List<Float>,
    private val labels: List<String>,
    private val limit: Float,
    private val good: Int,
    private val bad: Int,
    private val txt: Int,
    private val mute: Int
) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val top = 22 * d; val bottom = h - 24 * d
        val mx = maxOf(vals.maxOrNull() ?: 0f, limit, 1f) * 1.1f
        val slot = w / vals.size; val bw = slot * 0.5f
        p.textAlign = Paint.Align.CENTER; p.textSize = 11 * d
        vals.forEachIndexed { i, v ->
            val x = slot * i + (slot - bw) / 2
            val bh = (bottom - top) * v / mx
            p.style = Paint.Style.FILL
            p.color = if (limit > 0f && v > limit) bad else good
            cv.drawRoundRect(RectF(x, bottom - maxOf(bh, 2 * d), x + bw, bottom), 5 * d, 5 * d, p)
            if (v > 0f) {
                p.color = txt
                val s = if (v >= 10f || v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
                cv.drawText(s, x + bw / 2, bottom - bh - 5 * d, p)
            }
            p.color = mute
            cv.drawText(labels[i], x + bw / 2, h - 7 * d, p)
        }
        if (limit > 0f) {
            val y = bottom - (bottom - top) * limit / mx
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = mute
            p.pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f)
            cv.drawLine(0f, y, w, y, p)
            p.pathEffect = null
        }
    }
}

/** Small phone-shaped preview showing where the floating widget will sit. */
class WidgetPreview(
    c: Context,
    var px: Int, var py: Int, var psize: Int,
    private val frame: Int, private val pill: Int, private val txt: Int
) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = frame
        cv.drawRoundRect(RectF(2 * d, 2 * d, w - 2 * d, h - 2 * d), 18 * d, 18 * d, p)
        val pw = w * (0.46f + 0.14f * psize); val ph = pw * 0.28f
        val m = 8 * d
        val left = when (px) { 0 -> m + 4 * d; 2 -> w - pw - m - 4 * d; else -> (w - pw) / 2 }
        val top = (h * 0.88f * py / 100f).coerceIn(m + 4 * d, h - ph - m - 4 * d)
        p.style = Paint.Style.FILL; p.color = pill
        cv.drawRoundRect(RectF(left, top, left + pw, top + ph), ph / 2, ph / 2, p)
        p.color = txt; p.textAlign = Paint.Align.CENTER; p.textSize = ph * 0.45f
        cv.drawText("12 / 30", left + pw / 2, top + ph * 0.64f, p)
    }
}
