package com.mafocus.app

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/** Switches the screen-access service on/off by itself (needs the one-time WRITE_SECURE_SETTINGS grant). */
object Auto {
    fun hasUsage(c: Context): Boolean {
        val ao = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }
    fun hasSecure(c: Context) =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private fun me(c: Context) = ComponentName(c, ReelService::class.java)
    private fun list(c: Context): List<String> =
        (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(":").filter { it.isNotBlank() }

    fun isEnabled(c: Context) = list(c).any { ComponentName.unflattenFromString(it) == me(c) }

    fun setEnabled(c: Context, on: Boolean) {
        if (!hasSecure(c) || isEnabled(c) == on) return
        val rest = list(c).filter { ComponentName.unflattenFromString(it) != me(c) }.toMutableList()
        if (on) rest.add(me(c).flattenToString())
        try {
            val cr = c.contentResolver
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, rest.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, if (rest.isEmpty()) 0 else 1)
        } catch (_: Exception) {}
    }

    fun start(c: Context) { try { c.startForegroundService(Intent(c, GuardService::class.java)) } catch (_: Exception) {} }
    fun stop(c: Context) { c.stopService(Intent(c, GuardService::class.java)) }
}

/** Watches which app is in front (every 0.6 s, only while the screen is on). */
class GuardService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var cur = ""
    private var since = 0L
    private var offVotes = 0
    private var reg = false
    private val tick = object : Runnable { override fun run() { poll(); h.postDelayed(this, 600) } }
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            h.removeCallbacks(tick)
            if (i?.action == Intent.ACTION_SCREEN_OFF) Auto.setEnabled(this@GuardService, false)
            else h.post(tick)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startFg()
        since = System.currentTimeMillis() - 10 * 60 * 1000
        if (!reg) {
            registerReceiver(rx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
            reg = true
        }
        h.removeCallbacks(tick)
        if ((getSystemService(POWER_SERVICE) as PowerManager).isInteractive) h.post(tick)
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("guard", "MA FOCUS", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "guard")
            .setContentTitle("MA FOCUS is on guard")
            .setContentText("Screen access is on only in Instagram and YouTube.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun poll() {
        if (!Auto.hasUsage(this)) return
        val um = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(since, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) cur = e.packageName
        }
        since = now - 2000
        val want = Store.APPS.containsKey(cur) || (Store.breatherOn(this) && Store.breatherApps(this).contains(cur))
        if (want) { offVotes = 0; Auto.setEnabled(this, true) }
        else if (++offVotes >= 2) Auto.setEnabled(this, false)   // 2 polls in a row, avoids flapping
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        if (reg) try { unregisterReceiver(rx) } catch (_: Exception) {}
        super.onDestroy()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { if (Store.auto(c)) Auto.start(c) }
}
