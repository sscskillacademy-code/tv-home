package com.charan.glasshome

import android.service.dreams.DreamService

/** The TV's screensaver: shows [ClockScene]. */
class ClockDream : DreamService() {

    private var scene: ClockScene? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        scene = ClockScene(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scene?.start()
    }

    override fun onDreamingStopped() {
        scene?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scene?.stop()
        super.onDetachedFromWindow()
    }
}
