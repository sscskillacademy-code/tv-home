package com.charan.glasshome

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent

/** Full-screen preview of the screensaver (opened from Personalize). Any button closes it. */
class ScreensaverActivity : Activity() {

    private lateinit var scene: ClockScene

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scene = ClockScene(this)
        setContentView(scene)
        Ui.immersive(this)
        scene.alpha = 0f
        scene.animate().alpha(1f).setDuration(600).start()
    }

    override fun onResume() {
        super.onResume()
        scene.start()
    }

    override fun onPause() {
        scene.stop()
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.page_return_enter, R.anim.page_return_exit)
    }
}
