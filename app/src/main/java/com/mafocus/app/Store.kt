package com.mafocus.app

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** date = "" means every day; otherwise yyyyMMdd. before = start reminding this many days earlier. */
data class Task(val text: String, val date: String = "", val before: Int = 0)

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
    fun setCount(c: Context, n: Int) {
        count(c)
        p(c).edit().putInt("count", maxOf(0, n)).putInt("dc_${today()}", maxOf(0, n)).apply()
    }
    fun limit(c: Context) = p(c).getInt("limit", 30)
    fun setLimit(c: Context, n: Int) = p(c).edit().putInt("limit", maxOf(1, n)).apply()

    fun health(c: Context): Int {
        val n = count(c); val l = limit(c)
        return if (n <= l) (100 - n * 30f / l).toInt()
        else maxOf(0f, 70f - (n - l) * 70f / (l * 0.5f)).toInt()
    }
    fun color(h: Int): Int = Color.HSVToColor(floatArrayOf(h * 1.2f, 0.7f, 0.8f))
    fun face(h: Int) = when { h >= 70 -> "🧠"; h >= 40 -> "😵"; h > 0 -> "🤕"; else -> "💀" }

    fun allTasks(c: Context): MutableList<Task> {
        val raw = p(c).getString("tasks2", null)
        if (raw == null)
            return (p(c).getString("tasks", "") ?: "").split("\n").filter { it.isNotBlank() }.map { Task(it) }.toMutableList()
        val out = mutableListOf<Task>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Task(o.getString("t"), o.optString("d", ""), o.optInt("b", 0)))
            }
        } catch (_: Exception) {}
        return out
    }
    fun saveAllTasks(c: Context, l: List<Task>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.text).put("d", it.date).put("b", it.before)) }
        p(c).edit().putString("tasks2", a.toString()).apply()
    }
    fun shiftDay(day: String, delta: Int): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = java.util.Calendar.getInstance(); cal.time = f.parse(day) ?: Date()
        cal.add(java.util.Calendar.DAY_OF_YEAR, delta); return f.format(cal.time)
    }
    /** Tasks that should remind me today: every-day tasks + dated tasks inside their reminder window. */
    fun activeTasks(c: Context): List<Task> {
        val t = today()
        return allTasks(c).filter { it.date.isEmpty() || (t <= it.date && t >= shiftDay(it.date, -it.before)) }
            .sortedWith(compareBy<Task>({ it.date.isEmpty() }, { it.date }))
    }
    fun tasks(c: Context): List<String> = activeTasks(c).map { it.text }

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

    fun interval(c: Context) = p(c).getInt("iv", 5)
    fun setInterval(c: Context, n: Int) = p(c).edit().putInt("iv", maxOf(1, n)).apply()
    fun mode(c: Context) = p(c).getInt("mode", 0)          // 0 gentle, 1 medium (rude), 2 strict
    fun setMode(c: Context, m: Int) = p(c).edit().putInt("mode", m).apply()

    fun dayKey(daysAgo: Int): String {
        val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }
    fun countOn(c: Context, day: String) = p(c).getInt("dc_$day", 0)
    fun timeOn(c: Context, kind: String, pkg: String, day: String) = p(c).getLong("${kind}_${pkg}_$day", 0)

    fun theme(c: Context) = p(c).getInt("theme", 0)
    fun setTheme(c: Context, v: Int) = p(c).edit().putInt("theme", v).apply()
    fun wsize(c: Context) = p(c).getInt("wsize", 2)
    fun setWsize(c: Context, v: Int) = p(c).edit().putInt("wsize", v).apply()
    fun wx(c: Context) = p(c).getInt("wx", 1)
    fun setWx(c: Context, v: Int) = p(c).edit().putInt("wx", v).apply()
    fun wy(c: Context) = p(c).getInt("wy", 4)
    fun setWy(c: Context, v: Int) = p(c).edit().putInt("wy", v).apply()

    const val DEFAULT_MSG = "Take a breath. Do you really need to open {app} right now?"
    fun breatherOn(c: Context) = p(c).getBoolean("bon", false)
    fun setBreatherOn(c: Context, v: Boolean) = p(c).edit().putBoolean("bon", v).apply()
    fun bSecs(c: Context) = p(c).getInt("bsecs", 10)
    fun setBSecs(c: Context, n: Int) = p(c).edit().putInt("bsecs", n.coerceIn(3, 120)).apply()
    fun bMsg(c: Context) = p(c).getString("bmsg", DEFAULT_MSG) ?: DEFAULT_MSG
    fun setBMsg(c: Context, m: String) = p(c).edit().putString("bmsg", m).apply()
    fun bFont(c: Context) = p(c).getInt("bfont", 0)
    fun setBFont(c: Context, v: Int) = p(c).edit().putInt("bfont", v).apply()
    fun bTheme(c: Context) = p(c).getInt("btheme", 0)
    fun setBTheme(c: Context, v: Int) = p(c).edit().putInt("btheme", v).apply()
    fun bArt(c: Context) = p(c).getInt("bart", 0)
    fun setBArt(c: Context, v: Int) = p(c).edit().putInt("bart", v).apply()
    fun bPass(c: Context) = p(c).getInt("bpass", 5)
    fun setBPass(c: Context, v: Int) = p(c).edit().putInt("bpass", v).apply()
    fun breatherApps(c: Context): Set<String> {
        val s = p(c).getString("bapps", null) ?: return APPS.keys.toSet()
        return s.split(",").filter { it.isNotBlank() }.toSet()
    }
    fun setBreatherApps(c: Context, set: Set<String>) = p(c).edit().putString("bapps", set.joinToString(",")).apply()

    // floating counter look
    fun wstyle(c: Context) = p(c).getInt("wstyle", 2)
    fun setWstyle(c: Context, v: Int) = p(c).edit().putInt("wstyle", v).apply()
    fun wcolor(c: Context) = p(c).getInt("wcolor", -1)
    fun setWcolor(c: Context, v: Int) = p(c).edit().putInt("wcolor", v).apply()
    fun walpha(c: Context) = p(c).getInt("walpha", 95)
    fun setWalpha(c: Context, v: Int) = p(c).edit().putInt("walpha", v).apply()
    fun wanim(c: Context) = p(c).getInt("wanim", 1)
    fun setWanim(c: Context, v: Int) = p(c).edit().putInt("wanim", v).apply()
    fun wvib(c: Context) = p(c).getBoolean("wvib", true)
    fun setWvib(c: Context, v: Boolean) = p(c).edit().putBoolean("wvib", v).apply()
    fun wedge(c: Context) = p(c).getBoolean("wedge", true)
    fun setWedge(c: Context, v: Boolean) = p(c).edit().putBoolean("wedge", v).apply()

    // strict mode
    fun rlevel(c: Context) = p(c).getInt("rlevel", 2)          // 0 brutal, 1 extra brutal, 2 escalating
    fun setRlevel(c: Context, v: Int) = p(c).edit().putInt("rlevel", v).apply()
    fun userName(c: Context) = p(c).getString("uname", "") ?: ""
    fun setUserName(c: Context, v: String) = p(c).edit().putString("uname", v.trim()).apply()
    fun phrase(c: Context) = p(c).getString("phrase", "") ?: ""
    fun setPhrase(c: Context, v: String) = p(c).edit().putString("phrase", v).apply()

    // breather "don't ask again until"
    fun passUntil(c: Context, pkg: String) = p(c).getLong("pass_$pkg", 0L)
    fun setPass(c: Context, pkg: String, until: Long) = p(c).edit().putLong("pass_$pkg", until).apply()

    // home-screen box widget
    fun bwMain(c: Context) = p(c).getInt("bwmain", 0)
    fun setBwMain(c: Context, v: Int) = p(c).edit().putInt("bwmain", v).apply()
    fun bwExtra(c: Context) = p(c).getInt("bwextra", 22)       // brain, reel time, next task
    fun setBwExtra(c: Context, v: Int) = p(c).edit().putInt("bwextra", v).apply()
    fun bwTheme(c: Context) = p(c).getInt("bwtheme", 0)
    fun setBwTheme(c: Context, v: Int) = p(c).edit().putInt("bwtheme", v).apply()
    fun bwAlpha(c: Context) = p(c).getInt("bwalpha", 100)
    fun setBwAlpha(c: Context, v: Int) = p(c).edit().putInt("bwalpha", v).apply()
    fun bwSize(c: Context) = p(c).getInt("bwsize", 1)
    fun setBwSize(c: Context, v: Int) = p(c).edit().putInt("bwsize", v).apply()
    fun bwTitle(c: Context) = p(c).getString("bwtitle", "Exam") ?: "Exam"
    fun setBwTitle(c: Context, v: String) = p(c).edit().putString("bwtitle", v.trim()).apply()
    fun bwDate(c: Context) = p(c).getString("bwdate", "") ?: ""
    fun setBwDate(c: Context, v: String) = p(c).edit().putString("bwdate", v).apply()
}
