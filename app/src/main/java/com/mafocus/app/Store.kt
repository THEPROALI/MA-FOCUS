package com.mafocus.app

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    val APPS = linkedMapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat"
    )
    private fun p(c: Context) = c.getSharedPreferences("mf", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun count(c: Context): Int {
        val s = p(c)
        if (s.getString("day", "") != today()) s.edit().putString("day", today()).putInt("count", 0).apply()
        return s.getInt("count", 0)
    }
    fun setCount(c: Context, n: Int) { count(c); p(c).edit().putInt("count", maxOf(0, n)).apply() }
    fun limit(c: Context) = p(c).getInt("limit", 30)
    fun setLimit(c: Context, n: Int) = p(c).edit().putInt("limit", maxOf(1, n)).apply()

    fun health(c: Context): Int {
        val n = count(c); val l = limit(c)
        return if (n <= l) (100 - n * 30f / l).toInt()
        else maxOf(0f, 70f - (n - l) * 70f / (l * 0.5f)).toInt()
    }
    fun color(h: Int): Int = Color.HSVToColor(floatArrayOf(h * 1.2f, 0.7f, 0.8f))
    fun face(h: Int) = when { h >= 70 -> "🧠"; h >= 40 -> "😵"; h > 0 -> "🤕"; else -> "💀" }

    fun tasks(c: Context): MutableList<String> =
        (p(c).getString("tasks", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
    fun saveTasks(c: Context, t: List<String>) = p(c).edit().putString("tasks", t.joinToString("\n")).apply()

    // kind: "t" = total time in app, "r" = time spent on reels/shorts
    fun addTime(c: Context, kind: String, pkg: String, ms: Long) {
        if (ms <= 0) return
        val k = "${kind}_${pkg}_${today()}"
        p(c).edit().putLong(k, p(c).getLong(k, 0) + ms).apply()
    }
    fun time(c: Context, kind: String, pkg: String) = p(c).getLong("${kind}_${pkg}_${today()}", 0)
    fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }

    fun countAll(c: Context) = p(c).getBoolean("all", false)
    fun setCountAll(c: Context, v: Boolean) = p(c).edit().putBoolean("all", v).apply()
    fun dbg(c: Context) = p(c).getString("dbg", "none yet") ?: "none yet"
    fun setDbg(c: Context, s: String) = p(c).edit().putString("dbg", s).apply()

    fun prefs(c: Context) = p(c)
    fun widgetOn(c: Context) = p(c).getBoolean("widget", true)
    fun setWidgetOn(c: Context, v: Boolean) = p(c).edit().putBoolean("widget", v).apply()
    fun auto(c: Context) = p(c).getBoolean("auto", false)
    fun setAuto(c: Context, v: Boolean) = p(c).edit().putBoolean("auto", v).apply()
}
