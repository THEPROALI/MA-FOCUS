package com.mafocus.app

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
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
}
