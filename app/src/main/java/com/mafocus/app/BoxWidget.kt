package com.mafocus.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the home-screen box can show. */
object BoxData {
    val NAMES = listOf("Reels today", "Brain health", "Time on reels", "Screen time", "Next task", "Countdown")

    fun text(c: Context, id: Int): String = when (id) {
        0 -> "${Store.count(c)} / ${Store.limit(c)} reels"
        1 -> "Brain ${Store.health(c)}%"
        2 -> { var rt = 0L; Store.APPS.keys.forEach { rt += Store.time(c, "r", it) }; "${Store.fmt(rt)} on reels" }
        3 -> { var tt = 0L; Store.APPS.keys.forEach { tt += Store.time(c, "t", it) }; "${Store.fmt(tt)} screen time" }
        4 -> "Next: " + (Store.tasks(c).firstOrNull() ?: "nothing planned")
        else -> countdown(c)
    }

    fun countdown(c: Context): String {
        val d = Store.bwDate(c); val title = Store.bwTitle(c).ifBlank { "Countdown" }
        if (d.isEmpty()) return "$title: set a date"
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val target = f.parse(d) ?: return title
        val today = f.parse(f.format(Date())) ?: return title
        val days = ((target.time - today.time) / 86_400_000L).toInt()
        return when { days > 1 -> "$title: $days days"; days == 1 -> "$title: tomorrow"; days == 0 -> "$title: today!"; else -> "$title: passed" }
    }

    fun bar(c: Context): String {
        val n = Store.count(c); val l = Store.limit(c).coerceAtLeast(1)
        val f = (n * 10 / l).coerceIn(0, 10)
        return "█".repeat(f) + "░".repeat(10 - f)
    }

    fun extras(c: Context): List<Int> {
        val main = Store.bwMain(c); val mask = Store.bwExtra(c)
        return (0..5).filter { it != main && ((mask shr it) and 1) == 1 }
    }

    fun views(c: Context): RemoteViews {
        val th = THEMES[Store.bwTheme(c).coerceIn(0, THEMES.size - 1)]
        val k = floatArrayOf(0.85f, 1f, 1.2f)[Store.bwSize(c).coerceIn(0, 2)]
        val rv = RemoteViews(c.packageName, R.layout.widget_box)
        rv.setInt(R.id.w_bg, "setColorFilter", Color.parseColor(th.card))
        rv.setInt(R.id.w_bg, "setImageAlpha", Store.bwAlpha(c) * 255 / 100)
        rv.setTextViewText(R.id.w_title, "MA FOCUS")
        rv.setTextColor(R.id.w_title, Color.parseColor(th.mu))
        rv.setTextViewText(R.id.w_main, text(c, Store.bwMain(c)))
        rv.setTextColor(R.id.w_main, Color.parseColor(th.ac))
        rv.setTextViewTextSize(R.id.w_main, TypedValue.COMPLEX_UNIT_SP, 22f * k)
        val showBar = Store.bwMain(c) == 0
        rv.setViewVisibility(R.id.w_bar, if (showBar) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.w_bar, bar(c)); rv.setTextColor(R.id.w_bar, Color.parseColor(th.ac))
        val lines = intArrayOf(R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4, R.id.w_l5)
        val ex = extras(c)
        lines.forEachIndexed { i, id ->
            if (i < ex.size) {
                rv.setViewVisibility(id, View.VISIBLE)
                rv.setTextViewText(id, text(c, ex[i])); rv.setTextColor(id, Color.parseColor(th.tx))
                rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, 12f * k)
            } else rv.setViewVisibility(id, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.w_root,
            PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        return rv
    }
}

class BoxWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, BoxData.views(c)) }
    }
    companion object {
        fun refresh(c: Context) {
            try {
                val m = AppWidgetManager.getInstance(c)
                m.getAppWidgetIds(ComponentName(c, BoxWidget::class.java)).forEach { m.updateAppWidget(it, BoxData.views(c)) }
            } catch (_: Exception) {}
        }
    }
}
