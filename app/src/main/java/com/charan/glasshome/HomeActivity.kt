package com.charan.glasshome

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The home screen — a private screening room: the room's light follows the selected app,
 * its name appears like a film's title card, and the apps sit on a glass shelf that tilts in 3D
 * as you move. Wi-Fi / Bluetooth / Sleep sit above the clock; a mini player appears while music plays.
 */
class HomeActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var content: View
    private lateinit var backdrop: BackdropView
    private lateinit var analogClock: AnalogClockView
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var greeting: TextView
    private lateinit var heroEyebrow: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroHint: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherHiLo: TextView
    private lateinit var appScroll: HorizontalScrollView
    private lateinit var appRow: LinearLayout
    private lateinit var glow: FocusGlowView
    private lateinit var statusRow: LinearLayout
    private lateinit var wifiButton: View
    private lateinit var btButton: View
    private lateinit var miniSlot: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val network = Executors.newSingleThreadExecutor()

    private var apps: Map<String, AppEntry> = emptyMap()
    private val arts = HashMap<String, ArtUtil.Art?>()
    private var palette = Themes.ALL[0]
    private var ownerName = ""
    private var city = ""
    private var shelf3d = true
    private var isOnScreen = false
    private var lastWeatherAt = 0L
    private var firstBuild = true
    private var shelfEmpty = false
    private var pendingFocusTag: Any? = null
    private var focusKey: String? = null
    private var accentNow = 0
    private var accentAnim: ValueAnimator? = null
    private var ticks = 0
    private var boosting = false

    private var intro: IntroOverlay? = null
    private var pendingIntro = false
    private var introDeadline = 0L

    private lateinit var nowPlaying: NowPlaying
    private var mini: MiniPlayerView? = null
    private var miniState: NowPlaying.State? = null
    private var miniAccent = 0
    private val coverColors = HashMap<String, Int>()

    private val time12 = SimpleDateFormat("h:mm", Locale.getDefault())
    private val time24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val amPm = SimpleDateFormat("a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            nowPlaying.tick()
            if (ticks++ % 5 == 0) updateStatus()
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    private val weatherTick = object : Runnable {
        override fun run() {
            refreshWeather()
            handler.postDelayed(this, WEATHER_REFRESH_MS)
        }
    }

    private val applyFocus = Runnable { applyFocusVisuals() }

    /** TV switched on from standby → come back to this home screen and play the welcome animation. */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            requestIntro()
            if (!isOnScreen) {
                runCatching {
                    startActivity(Intent(c, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            }
        }
    }

    /** An app was installed / removed / updated → refresh the shelf. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            AppRepository.invalidate()
            arts.clear()
            if (isOnScreen) loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        Ui.immersive(this)

        root = findViewById(R.id.root)
        content = findViewById(R.id.homeContent)
        backdrop = findViewById(R.id.backdrop)
        analogClock = findViewById(R.id.analogClock)
        timeText = findViewById(R.id.timeText)
        dateText = findViewById(R.id.dateText)
        greeting = findViewById(R.id.greeting)
        heroEyebrow = findViewById(R.id.heroEyebrow)
        heroTitle = findViewById(R.id.heroTitle)
        heroHint = findViewById(R.id.heroHint)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        weatherHiLo = findViewById(R.id.weatherHiLo)
        appScroll = findViewById(R.id.appScroll)
        appRow = findViewById(R.id.appRow)
        statusRow = findViewById(R.id.statusRow)
        miniSlot = findViewById(R.id.miniSlot)
        glow = FocusGlowView(this)
        findViewById<FrameLayout>(R.id.rowFrame).addView(glow, 0, FrameLayout.LayoutParams(1, 1))
        findViewById<View>(R.id.shelf).clipToOutline = true   // apps never leave the rounded shelf

        Fonts.apply(Fonts.LIGHT, timeText, weatherTemp)
        Fonts.apply(Fonts.LIGHT, heroHint, weatherDesc, weatherHiLo)
        Fonts.apply(Fonts.MEDIUM, greeting, dateText, heroEyebrow)
        heroTitle.typeface = Fonts.serif(this)
        heroTitle.setAutoSizeTextTypeUniformWithConfiguration(40, 80, 2, TypedValue.COMPLEX_UNIT_SP)

        analogClock.halo = true
        backdrop.glowX = 0.3f
        backdrop.glowY = 0.6f
        backdrop.scaleX = 1.08f      // room to drift sideways (parallax) without showing an edge
        backdrop.scaleY = 1.08f

        buildStatusButtons()
        nowPlaying = NowPlaying(this) { renderMini(it) }

        ownerName = Prefs.getName(this)
        city = Prefs.getCity(this)
        palette = Themes.get(Prefs.getTheme(this))
        backdrop.palette = palette
        miniAccent = palette.accent
        setAccent(palette.accent, animate = false)
        Prefs.loadWeather(this)?.let { (w, at) -> showWeather(w, at) }
        updateClock()
        showSkeleton()

        registerSystemReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerSystemReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })

        if (Prefs.claimBootIntro(this) || intent?.getBooleanExtra(EXTRA_PLAY_INTRO, false) == true) {
            content.alpha = 0f      // stay hidden until the welcome animation reveals it
            requestIntro()
        }
    }

    override fun onResume() {
        super.onResume()
        isOnScreen = true
        syncPrefs()
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
        loadApps()
        handler.removeCallbacks(weatherTick)
        val wait = (lastWeatherAt + WEATHER_REFRESH_MS - System.currentTimeMillis()).coerceAtLeast(0)
        handler.postDelayed(weatherTick, wait)
        if (Prefs.getMiniPlayer(this)) nowPlaying.start() else renderMini(null)
        maybePlayIntro()
    }

    override fun onPause() {
        super.onPause()
        isOnScreen = false
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(weatherTick)
        handler.removeCallbacks(applyFocus)
        nowPlaying.stop()
        intro?.skip()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOnReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        worker.shutdownNow()
        network.shutdownNow()
    }

    /** Pressing Home while already on the home screen jumps back to the first app. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intro != null) return
        if (intent?.getBooleanExtra(EXTRA_PLAY_INTRO, false) == true) {
            requestIntro()
            return
        }
        if (intent?.getBooleanExtra(EXTRA_BOOST, false) == true) {
            statusRow.getChildAt(2)?.let { boost(it) }
            return
        }
        intent?.getStringExtra(EXTRA_SHELF)?.let { list ->
            Prefs.setFavorites(this, list.split(',').map { it.trim() }.filter { it.isNotEmpty() })
            pendingFocusTag = null
            firstBuild = true
            loadApps()
            return
        }
        appScroll.smoothScrollTo(0, 0)
        appRow.getChildAt(0)?.requestFocus()
    }

    /** This is the home screen — Back has nowhere to go, so it returns to the first app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        appScroll.smoothScrollTo(0, 0)
        appRow.getChildAt(0)?.requestFocus()
    }

    /** While the welcome animation plays, any button skips it (and does nothing else). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val running = intro
        if (running != null) {
            if (event.action == KeyEvent.ACTION_DOWN) running.skip()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerSystemReceiver(r: BroadcastReceiver, f: IntentFilter) {
        // Only protected system broadcasts are used, so other apps can't fake them.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(r, f)
    }

    /** Open another page with the fade-and-settle transition (says so if the TV can't open it). */
    private fun go(intent: Intent): Boolean = try {
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.page_enter, R.anim.page_exit)
        true
    } catch (e: Exception) {
        notice("This TV can’t open that screen")
        false
    }

    /** Pick up changes made on the Personalize page. */
    private fun syncPrefs() {
        ownerName = Prefs.getName(this)
        analogClock.numbers = Prefs.getClockNumbers(this)
        val was3d = shelf3d
        shelf3d = Prefs.getShelf3d(this)
        if (was3d && !shelf3d) tilt(null)
        val newCity = Prefs.getCity(this)
        if (newCity != city) {
            city = newCity
            lastWeatherAt = 0L          // refetch for the new city right away
        }
        val p = Themes.get(Prefs.getTheme(this))
        if (p !== palette) {
            palette = p
            backdrop.palette = p
            for (i in 0 until appRow.childCount) {
                val t = appRow.getChildAt(i)
                Tiles.setGlow(t, arts[t.tag as? String]?.color ?: palette.accent)
            }
            applyFocusVisuals()
        }
        updateClock()
    }

    // ---------------------------------------------------------------- Welcome animation

    private fun requestIntro() {
        pendingIntro = true
        introDeadline = SystemClock.elapsedRealtime() + 20_000
        if (isOnScreen) maybePlayIntro()
    }

    private fun maybePlayIntro() {
        if (!pendingIntro || intro != null) return
        pendingIntro = false
        if (SystemClock.elapsedRealtime() > introDeadline) {
            content.alpha = 1f
            return
        }
        intro = IntroOverlay(this, root, content, ownerName, palette.accent) { intro = null }
            .also { it.play() }
    }

    // ---------------------------------------------------------------- Clock

    private fun updateClock() {
        val now = Date()
        if (DateFormat.is24HourFormat(this)) {
            timeText.text = time24.format(now)
        } else {
            val t = time12.format(now)
            val s = SpannableString("$t ${amPm.format(now)}")
            s.setSpan(RelativeSizeSpan(0.34f), t.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            timeText.text = s
        }
        dateText.text = dateFormat.format(now).uppercase()

        val part = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        greeting.text = (if (ownerName.isBlank()) part else "$part, $ownerName").uppercase()
    }

    // ---------------------------------------------------------------- Title card + light

    private fun setHero(eyebrow: String, title: String, hint: String, key: String) {
        heroEyebrow.text = eyebrow.uppercase()
        heroHint.text = hint
        if (heroTitle.text.toString() != title) {
            heroTitle.animate().cancel()
            if (heroTitle.text.isNullOrEmpty()) {
                heroTitle.text = title
                heroTitle.alpha = 1f
                heroTitle.translationY = 0f
            } else {
                // the old title lifts away, the new one rises into place
                heroTitle.animate().alpha(0f).translationY(-Ui.dpf(this, 8f)).setDuration(90)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        heroTitle.text = title
                        heroTitle.translationY = Ui.dpf(this, 18f)
                        heroTitle.animate().alpha(1f).translationY(0f).setDuration(340)
                            .setInterpolator(DecelerateInterpolator(2f)).start()
                    }.start()
            }
        } else if (heroTitle.alpha < 1f) {
            heroTitle.animate().alpha(1f).translationY(0f).setDuration(200).start()
        }
        focusKey = key
        handler.removeCallbacks(applyFocus)
        handler.postDelayed(applyFocus, 110)  // wait until focus settles (fast scrolling stays smooth)
    }

    private fun applyFocusVisuals() {
        val key = focusKey ?: return
        val art = arts[key]
        val color = if (key == KEY_MINI) miniAccent else art?.color ?: palette.accent
        backdrop.show(if (key == KEY_MINI) null else art?.backdrop, color)
        glow.setColor(color, animate = true)
        setAccent(color, animate = true)
    }

    /** Clock ring, second hand and title glow all take the selected app's colour. */
    private fun setAccent(color: Int, animate: Boolean) {
        accentAnim?.cancel()
        heroTitle.setShadowLayer(30f * resources.displayMetrics.density, 0f, 0f, Ui.withAlpha(color, 0x8C))
        if (!animate || accentNow == color) {
            accentNow = color
            analogClock.accent = color
            return
        }
        accentAnim = ValueAnimator.ofArgb(accentNow, color).apply {
            duration = 380
            addUpdateListener {
                val c = it.animatedValue as Int
                accentNow = c
                analogClock.accent = c
            }
            start()
        }
    }

    /**
     * 3D shelf: the selected tile faces you; its neighbours turn towards it like a gallery wall,
     * and the room drifts a little the other way (parallax). focused == null flattens everything.
     */
    private fun tilt(focused: View?) {
        val idx = if (focused != null) appRow.indexOfChild(focused) else -1
        val on = shelf3d && idx >= 0
        for (i in 0 until appRow.childCount) {
            val t = appRow.getChildAt(i)
            if (t is ShimmerView) continue
            val offset = if (on) (i - idx).coerceIn(-3, 3) else 0
            t.animate().rotationY(-offset * TILT_DEG).setDuration(480)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        val count = appRow.childCount
        val shift = if (on && count > 1) -(idx - (count - 1) / 2f) * Ui.dpf(this, 7f) else 0f
        backdrop.animate().translationX(shift).setDuration(900)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Focus moved off the shelf (to the status buttons or the mini player). */
    private fun leaveShelf() {
        tilt(null)
        glow.hide()
    }

    // ---------------------------------------------------------------- Wi-Fi / Bluetooth / Sleep

    private fun buildStatusButtons() {
        wifiButton = statusButton(R.drawable.ic_wifi, "Wi-Fi") {
            go(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        btButton = statusButton(R.drawable.ic_bluetooth, "Bluetooth") { showBluetoothMenu() }
        lateinit var boostButton: View
        boostButton = statusButton(R.drawable.ic_boost, "Boost") { boost(boostButton) }
        statusButton(R.drawable.ic_moon, "Sleep") { go(Intent(this, ScreensaverActivity::class.java)) }
    }

    private fun statusButton(icon: Int, name: String, onClick: () -> Unit): View {
        val b = FrameLayout(this).apply {
            background = getDrawable(R.drawable.ctrl_btn)
            isFocusable = true
            isClickable = true
            contentDescription = name
        }
        b.addView(ImageView(this).apply { runCatching { setImageResource(icon) } },
            FrameLayout.LayoutParams(Ui.dp(this, 17f), Ui.dp(this, 17f), Gravity.CENTER))
        b.setOnClickListener { onClick() }
        b.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.22f else 1f).scaleY(if (has) 1.22f else 1f)
                .setInterpolator(if (has) OvershootInterpolator(2f) else null).setDuration(200).start()
            if (has) {
                leaveShelf()
                when (name) {
                    "Wi-Fi" -> setHero("Network", "Wi-Fi", wifiInfo().second + "   ·   OK for Wi-Fi settings", KEY_STATUS)
                    "Bluetooth" -> setHero("Devices", "Bluetooth", btInfo().second + "   ·   OK for options", KEY_STATUS)
                    "Boost" -> setHero("Performance", "Boost",
                        "Close background apps in one click   ·   ${availMb()} MB free now", KEY_STATUS)
                    else -> setHero("Sleep mode", "Sleep", "Press OK to dim the room to a clock", KEY_STATUS)
                }
            }
        }
        statusRow.addView(b, LinearLayout.LayoutParams(Ui.dp(this, 32f), Ui.dp(this, 32f))
            .apply { marginStart = Ui.dp(this@HomeActivity, 12f) })
        return b
    }

    private fun updateStatus() {
        wifiButton.alpha = if (wifiInfo().first) 1f else 0.4f
        btButton.alpha = if (btInfo().first) 1f else 0.4f
    }

    private fun wifiInfo(): Pair<Boolean, String> {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        return when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> true to "Connected by cable"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                @Suppress("DEPRECATION")
                val info = runCatching { applicationContext.getSystemService(WifiManager::class.java).connectionInfo }.getOrNull()
                @Suppress("DEPRECATION")
                val level = info?.let { WifiManager.calculateSignalLevel(it.rssi, 4) } ?: 2
                val quality = arrayOf("Weak", "Fair", "Good", "Excellent")[level.coerceIn(0, 3)]
                val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                true to listOfNotNull("Connected", ssid, "$quality signal").joinToString("   ·   ")
            }
            else -> false to "Not connected"
        }
    }

    /**
     * Boost: closes background apps for real. Android 14 ignores the normal app API for this, so it
     * runs `am kill-all` + `am force-stop` through the TV's own debugging port (LocalAdb). Never
     * touches the music that is playing, the keyboard, the voice assistant, Chromecast or the Play Store.
     */
    private fun boost(button: View) {
        if (boosting) return
        boosting = true
        button.animate().rotationBy(720f).setDuration(1400).setInterpolator(DecelerateInterpolator()).start()
        val keep = setOfNotNull(packageName, miniState?.pkg, "com.google.android.katniss",
            "com.google.android.inputmethod.latin", "com.google.android.apps.mediashell",
            "com.android.vending", "com.google.android.gms")
        val targets = (apps.keys + NowPlaying.MUSIC_APPS).filter { it !in keep }.distinct()
            .filter { it.matches(Regex("[A-Za-z0-9._]+")) }
        val command = "am kill-all; for p in ${targets.joinToString(" ")}; do pidof \$p >/dev/null && " +
            "echo \$p && am force-stop \$p; done"
        worker.execute {
            val before = availMb()
            val result = runCatching {
                LocalAdb.run(this, command) {
                    runOnUiThread { notice("On the TV, tick “Always allow” and press OK — just this once") }
                }
            }
            Thread.sleep(1200)
            val after = availMb()
            runOnUiThread {
                boosting = false
                val err = result.exceptionOrNull()
                when {
                    err is LocalAdb.DebuggingOff ->
                        notice("Boost needs USB debugging on  ·  Settings › Device Preferences › Developer options")
                    err is LocalAdb.NotAllowed -> notice("Boost wasn’t allowed  ·  press it again and choose OK")
                    err != null -> notice("Boost couldn’t run this time  ·  try again")
                    else -> {
                        val closed = result.getOrNull().orEmpty().lines().count { it.isNotBlank() }
                        val freed = (after - before).coerceAtLeast(0)
                        notice(when {
                            closed > 0 -> "Boosted  ·  closed $closed ${if (closed == 1) "app" else "apps"}  ·  freed $freed MB  ·  $after MB free"
                            freed >= 10 -> "Boosted  ·  freed $freed MB  ·  $after MB free"
                            else -> "Already clean  ·  $after MB free"
                        })
                    }
                }
                if (button.isFocused) {
                    heroHint.text = "Close background apps in one click   ·   $after MB free now"
                }
            }
        }
    }

    private fun availMb(): Long {
        val mi = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem / 1_048_576L
    }

    /**
     * This TV has no "Bluetooth settings" screen that apps can open, so the button offers what it
     * does have: its own pair-a-device screen, turning Bluetooth on, and the full TV settings.
     */
    private fun showBluetoothMenu() {
        val (on, status) = btInfo()
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (!on) options += "Turn Bluetooth on" to { go(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); Unit }
        options += "Pair a remote or device" to { go(Intent(ACTION_CONNECT_INPUT)); Unit }
        options += "Open TV settings" to { go(Intent(Settings.ACTION_SETTINGS)); Unit }
        GlassMenu(this, "Bluetooth", status, options.map { it.first }, palette.accent) { options[it].second() }.show()
    }

    private fun btInfo(): Pair<Boolean, String> {
        val adapter = runCatching { getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
            ?: return false to "Not available"
        val on = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!on) return false to "Off"
        val names = runCatching { adapter.bondedDevices.mapNotNull { it.name } }.getOrNull()
        return true to when {
            names == null -> "On"
            names.isEmpty() -> "On   ·   No devices paired"
            else -> "On   ·   " + names.take(2).joinToString(", ")
        }
    }

    // ---------------------------------------------------------------- Mini player

    private fun renderMini(s: NowPlaying.State?) {
        if (s == null || !Prefs.getMiniPlayer(this)) {
            hideMini()
            return
        }
        val m = mini ?: MiniPlayerView(
            this,
            onPrev = { nowPlaying.previous() },
            onPlayPause = { nowPlaying.playPause() },
            onNext = { nowPlaying.next() },
            onButtonFocus = { _, has -> if (has) onMiniFocus() },
        ).also {
            mini = it
            it.visibility = View.GONE
            miniSlot.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 100f)))
        }
        val titleChanged = miniState?.title != s.title || miniState?.pkg != s.pkg
        miniState = s
        miniAccent = coverColor(s)
        val app = apps[s.pkg]
        m.bind(s, app?.banner ?: app?.icon, miniAccent)
        if (m.visibility != View.VISIBLE) {
            m.visibility = View.VISIBLE
            m.alpha = 0f
            m.translationY = -Ui.dpf(this, 24f)
            m.animate().alpha(1f).translationY(0f).setDuration(480)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        if (titleChanged && focusKey == KEY_MINI) onMiniFocus()
    }

    private fun hideMini() {
        val m = mini ?: return
        if (m.visibility != View.VISIBLE) return
        miniState = null
        if (m.hasFocus()) appRow.getChildAt(0)?.requestFocus()
        m.animate().alpha(0f).translationY(-Ui.dpf(this, 16f)).setDuration(260)
            .withEndAction { m.visibility = View.GONE }.start()
    }

    private fun onMiniFocus() {
        val s = miniState ?: return
        leaveShelf()
        val hint = if (s.full) s.subtitle else "Play, pause or skip from here"
        setHero("Now playing  ·  ${s.appName}", s.title, hint, KEY_MINI)
    }

    /** The colour of the album cover (or of the music app) — the room glows in it. */
    private fun coverColor(s: NowPlaying.State): Int {
        val key = s.pkg + "|" + s.title
        coverColors[key]?.let { return it }
        val bitmap = s.art
        if (bitmap != null) {
            worker.execute {
                val c = ArtUtil.make(BitmapDrawable(resources, bitmap), isBanner = false)?.color ?: return@execute
                runOnUiThread {
                    coverColors[key] = c
                    if (miniState?.title == s.title) {
                        miniAccent = c
                        if (focusKey == KEY_MINI) applyFocusVisuals()
                    }
                }
            }
        } else {
            apps[s.pkg]?.let { app ->
                worker.execute {
                    val c = AppRepository.art(app)?.color ?: return@execute
                    runOnUiThread { coverColors[key] = c }
                }
            }
        }
        return arts[s.pkg]?.color ?: palette.accent
    }

    // ---------------------------------------------------------------- Shelf

    /** Loading state: glass placeholders with a slow band of light. */
    private fun showSkeleton() {
        appRow.removeAllViews()
        repeat(5) {
            appRow.addView(ShimmerView(this), LinearLayout.LayoutParams(Tiles.dp(this, TILE_W), Tiles.dp(this, TILE_H))
                .apply { marginEnd = Tiles.dp(this@HomeActivity, 22f) })
        }
        heroEyebrow.text = ""
        heroTitle.text = ""
        heroHint.text = ""
    }

    private fun loadApps() {
        worker.execute {
            val list = AppRepository.loadAll(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps = list.associateBy { it.pkg }
                Prefs.ensureDefaults(this, apps.keys)
                buildRow()
                computeArt()
            }
        }
    }

    /** Work out each shelf app's light + colour in the background, one by one. */
    private fun computeArt() {
        val todo = Prefs.getFavorites(this).mapNotNull { apps[it] }.filter { !arts.containsKey(it.pkg) }
        if (todo.isEmpty()) return
        worker.execute {
            for (app in todo) {
                val art = AppRepository.art(app)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    arts[app.pkg] = art
                    appRow.findViewWithTag<View>(app.pkg)?.let { Tiles.setGlow(it, art?.color ?: palette.accent) }
                    if (focusKey == app.pkg) applyFocusVisuals()
                }
            }
        }
    }

    private fun buildRow() {
        val keepFocus = pendingFocusTag ?: currentFocus?.takeIf { it.parent == appRow }?.tag
        val rowHadFocus = currentFocus?.parent == appRow || pendingFocusTag != null
        pendingFocusTag = null

        appRow.removeAllViews()
        val favorites = Prefs.getFavorites(this).filter { it in apps }
        shelfEmpty = favorites.isEmpty()

        favorites.forEachIndexed { index, pkg ->
            val app = apps.getValue(pkg)
            val tile = Tiles.appTile(this, app, TILE_W, TILE_H)
            Tiles.setGlow(tile, arts[pkg]?.color ?: palette.accent)
            tile.setOnClickListener { open(app) }
            tile.setOnLongClickListener { showTileMenu(app); true }
            tile.setOnFocusChangeListener { v, has ->
                Tiles.focusZoom(v, has)
                if (has) {
                    glow.moveTo(v, FOCUS_SCALE)
                    tilt(v)
                    val chapter = String.format(Locale.US, "%02d  /  %02d", index + 1, favorites.size)
                    setHero(chapter, app.label, "Press OK to open   ·   Hold OK for options", app.pkg)
                }
            }
            addToRow(tile)
        }

        if (shelfEmpty) {
            addAction(ACTION_EDIT, R.drawable.ic_edit, "✏️", "Edit shelf",
                "Your shelf is empty", "Make it yours", "Press OK to choose the apps you love") { openLibrary(edit = true) }
        }
        addAction(ACTION_ALL, R.drawable.ic_apps, "🗂️", "All apps",
            "Library", "All apps", "Browse and search every app on your TV") { openLibrary(edit = false) }
        if (!shelfEmpty) {
            addAction(ACTION_EDIT, R.drawable.ic_edit, "✏️", "Edit shelf",
                "Your shelf", "Edit shelf", "Choose and arrange the apps on this shelf") { openLibrary(edit = true) }
        }
        addAction(ACTION_STYLE, R.drawable.ic_palette, "🎨", "Personalize",
            "Make it yours", "Personalize", "Your name, colours, clock and sleep mode") {
            go(Intent(this, SettingsActivity::class.java))
        }
        addAction(ACTION_SETTINGS, R.drawable.ic_settings, "⚙️", "TV settings",
            "System", "TV settings", "Wi-Fi, picture, sound and more") {
            go(Intent(Settings.ACTION_SETTINGS))
        }

        val target = (0 until appRow.childCount).map { appRow.getChildAt(it) }.firstOrNull { it.tag == keepFocus }
        when {
            // the very first time, leave any "touch mode" so a tile is selected straight away
            firstBuild -> (target ?: appRow.getChildAt(0))?.requestFocusFromTouch()
            target != null && rowHadFocus -> target.requestFocus()
            rowHadFocus -> appRow.getChildAt(0)?.requestFocus()
        }
        firstBuild = false
    }

    private fun addAction(
        tag: String, icon: Int, fallback: String, caption: String,
        eyebrow: String, title: String, hint: String, onClick: () -> Unit,
    ) {
        val tile = Tiles.actionTile(this, icon, fallback, caption, TILE_H)
        tile.tag = tag
        Tiles.setGlow(tile, palette.accent)
        tile.setOnClickListener { onClick() }
        tile.setOnFocusChangeListener { v, has ->
            Tiles.focusZoom(v, has)
            if (has) {
                glow.moveTo(v, FOCUS_SCALE)
                tilt(v)
                setHero(eyebrow, title, hint, tag)
            }
        }
        addToRow(tile)
    }

    private fun addToRow(tile: FrameLayout) {
        (tile.layoutParams as LinearLayout.LayoutParams).marginEnd = Tiles.dp(this, 22f)
        appRow.addView(tile)
    }

    private fun openLibrary(edit: Boolean) {
        go(Intent(this, AppPickerActivity::class.java)
            .putExtra(AppPickerActivity.EXTRA_MODE, if (edit) AppPickerActivity.MODE_EDIT else AppPickerActivity.MODE_LAUNCH))
    }

    private fun open(app: AppEntry) {
        if (!AppRepository.launch(this, app.pkg)) notice("Couldn’t open ${app.label}")
    }

    private fun notice(text: String) = Notice.show(this, text, bottomDp = 214f)

    private fun showTileMenu(app: AppEntry) {
        val favs = Prefs.getFavorites(this).filter { it in apps }
        val i = favs.indexOf(app.pkg)
        val chapter = String.format(Locale.US, "%02d of %02d on your shelf", i + 1, favs.size)
        GlassMenu(
            this, app.label, chapter,
            listOf("Open", "Move left", "Move right", "Remove from shelf", "App info"),
            arts[app.pkg]?.color ?: palette.accent,
        ) { which ->
            val list = favs.toMutableList()
            when (which) {
                0 -> open(app)
                1 -> if (i > 0) {
                    list.removeAt(i); list.add(i - 1, app.pkg); save(list, app.pkg)
                }
                2 -> if (i in 0 until list.size - 1) {
                    list.removeAt(i); list.add(i + 1, app.pkg); save(list, app.pkg)
                }
                3 -> {
                    list.remove(app.pkg)
                    save(list, list.getOrNull(i) ?: list.getOrNull(i - 1) ?: ACTION_EDIT)
                    notice("${app.label} removed from your shelf")
                }
                4 -> runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                }
            }
        }.show()
    }

    private fun save(favs: List<String>, focusTag: String) {
        Prefs.setFavorites(this, favs)
        pendingFocusTag = focusTag
        buildRow()
        computeArt()
    }

    // ---------------------------------------------------------------- Weather

    private fun refreshWeather() {
        val query = city
        if (lastWeatherAt == 0L) {
            weatherDesc.text = "Fetching the weather…"
            weatherHiLo.text = ""
        }
        network.execute {
            val result = runCatching { WeatherClient.fetch(query) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    Prefs.saveWeather(this, it)
                    showWeather(it, System.currentTimeMillis())
                }.onFailure {
                    if (lastWeatherAt == 0L) {
                        weatherIcon.text = "🌡️"
                        weatherTemp.text = "--°"
                        if (it is IllegalArgumentException) {
                            weatherDesc.text = "We couldn’t find “$query”"
                            weatherHiLo.text = "Change the city in Personalize"
                        } else {
                            weatherDesc.text = "Weather unavailable"
                            weatherHiLo.text = "Trying again in a moment"
                        }
                    }
                    // try again sooner than the normal 30 minutes (only while home is visible)
                    handler.removeCallbacks(weatherTick)
                    if (isOnScreen) handler.postDelayed(weatherTick, 2 * 60_000L)
                }
            }
        }
    }

    private fun showWeather(w: Weather, at: Long) {
        lastWeatherAt = at
        val (emoji, text) = WeatherClient.describe(w.code, w.isDay)
        weatherIcon.text = emoji
        weatherTemp.text = "${w.temp.roundToInt()}°"
        weatherDesc.text = "$text   ·   ${w.city}"
        weatherHiLo.text = "High ${w.max.roundToInt()}°   ·   Low ${w.min.roundToInt()}°"
    }

    companion object {
        /** `am start ... --ez play_intro true` replays the welcome animation (Personalize uses it too). */
        const val EXTRA_PLAY_INTRO = "play_intro"
        /** `am start ... --ez boost true` runs Boost (same as pressing the button). */
        const val EXTRA_BOOST = "boost"
        /** `am start ... --es shelf pkg1,pkg2` sets the apps on the shelf (handy for demos). */
        const val EXTRA_SHELF = "shelf"
        private const val TILE_W = 176f
        private const val TILE_H = 99f
        private const val FOCUS_SCALE = 1.12f
        private const val TILT_DEG = 11f
        private const val WEATHER_REFRESH_MS = 30 * 60_000L
        /** Android TV's own "pair a remote or accessory" screen. */
        private const val ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"
        private const val KEY_MINI = "now_playing"
        private const val KEY_STATUS = "status"
        private const val ACTION_ALL = "action_all"
        private const val ACTION_EDIT = "action_edit"
        private const val ACTION_STYLE = "action_style"
        private const val ACTION_SETTINGS = "action_settings"
    }
}
