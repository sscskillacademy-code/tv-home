package com.charan.glasshome

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * What music is playing (Spotify, YouTube Music, …) plus its remote controls.
 *  - Full mode: with notification-listener access, reads the track, artist, cover and position.
 *  - Basic mode: Android blocks that access on low-memory TVs, so we only know that music is playing
 *    and which music app was used last. Play / pause / next / previous still work, via media keys.
 */
class NowPlaying(private val ctx: Context, private val onChange: (State?) -> Unit) {

    class State(
        val pkg: String,
        val appName: String,
        val title: String,
        val subtitle: String,
        val art: Bitmap?,
        val playing: Boolean,
        val position: Long,
        val duration: Long,
        val full: Boolean,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val sessions = ctx.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(ctx, MediaListener::class.java)
    private var controller: MediaController? = null
    private var hasAccess = false
    private var started = false
    private var lastMusicAt = 0L
    private var lastApp: String? = null
    private var lastAppCheck = 0L

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }

    fun start() {
        if (started) return
        started = true
        hasAccess = try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            true
        } catch (e: SecurityException) {
            false
        }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        if (hasAccess) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** Called every second by the home screen (progress, and basic-mode detection). */
    fun tick() {
        if (!started) return
        if (hasAccess) publish() else publishBasic()
    }

    private fun refresh() {
        if (!hasAccess) {
            publishBasic()
            return
        }
        val list = runCatching { sessions.getActiveSessions(listener) }.getOrNull().orEmpty()
        val pick = list.filter { it.packageName in MUSIC_APPS }
            .maxByOrNull { if (it.playbackState?.state == PlaybackState.STATE_PLAYING) 1 else 0 }
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val st = c?.playbackState
        val active = st != null && (st.state == PlaybackState.STATE_PLAYING ||
            st.state == PlaybackState.STATE_PAUSED || st.state == PlaybackState.STATE_BUFFERING)
        if (c == null || st == null || !active) {
            onChange(null)
            return
        }
        val md = c.metadata
        val playing = st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_BUFFERING
        var pos = st.position
        if (playing && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        val app = label(c.packageName)
        onChange(State(
            pkg = c.packageName,
            appName = app,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: app,
            subtitle = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: app,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = playing,
            position = pos.coerceAtLeast(0L),
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            full = true,
        ))
    }

    private fun publishBasic() {
        val now = SystemClock.elapsedRealtime()
        val playing = audio.isMusicActive
        if (playing) lastMusicAt = now
        if (lastMusicAt == 0L || now - lastMusicAt > BASIC_LINGER_MS) {
            onChange(null)
            return
        }
        val pkg = lastMusicApp()
        if (pkg == null) {
            onChange(null)
            return
        }
        val app = label(pkg)
        onChange(State(pkg, app, app, if (playing) "Playing now" else "Paused", null, playing, 0L, 0L, false))
    }

    /** The music app used most recently (with usage access); otherwise the first one installed. */
    private fun lastMusicApp(): String? {
        val now = System.currentTimeMillis()
        if (lastApp != null && now - lastAppCheck < 30_000) return lastApp
        lastAppCheck = now
        var found: String? = null
        runCatching {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            val events = usm.queryEvents(now - 12 * 3_600_000L, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName in MUSIC_APPS) found = e.packageName
            }
        }
        lastApp = found ?: MUSIC_APPS.firstOrNull { installed(it) }
        return lastApp
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        handler.postDelayed({ tick() }, 500)
    }

    fun next() {
        val c = controller
        if (c != null) c.transportControls.skipToNext() else key(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        val c = controller
        if (c != null) c.transportControls.skipToPrevious() else key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun key(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun label(pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("Music")

    private fun installed(pkg: String): Boolean =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    companion object {
        val MUSIC_APPS = listOf(
            "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic",
            "com.amazon.music.tv", "com.jio.media.jiobeats", "com.gaana", "com.bsbportal.music",
            "com.apple.android.music",
        )
        /** In basic mode, keep the player on screen this long after the music stops (it may be paused). */
        private const val BASIC_LINGER_MS = 90_000L
    }
}
