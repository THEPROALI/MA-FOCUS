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
