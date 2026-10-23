package com.mafocus.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

/** Real pause screen: a full activity that sits on top of the app, so the app underneath is paused. */
class BreatherActivity : Activity() {
    companion object {
        @Volatile var active = false
        var current: BreatherActivity? = null
    }
    private var pkg = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        current = this
        pkg = intent.getStringExtra("pkg") ?: ""
        val name = intent.getStringExtra("name") ?: "this app"
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val v = BreatherView(this, name)
        v.onContinue = {
            Store.setPass(this, pkg, System.currentTimeMillis() + Store.bPass(this) * 60_000L)
            finish()
        }
        v.onLeave = { goHome() }
        setContentView(v)
        v.start()
    }

    override fun onResume() { super.onResume(); active = true }
    override fun onPause() { super.onPause(); active = false }
    override fun onStop() { super.onStop(); if (!isFinishing) finish() }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    override fun onBackPressed() { goHome() }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
