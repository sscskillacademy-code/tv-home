# Glass Home 3 — "Screening Room"

A one-of-a-kind home screen for the Realme TV Stick (Android TV 14, 1 GB RAM), designed like a private
screening room: a velvet-dark room, warm ivory type, light that spills from whatever is selected, and a
whisper of film grain.

## Screens

| Screen | What it does |
|---|---|
| **Home** | Floating neon analog clock with time and date, greeting and weather, a serif "title card" for the selected app (chapter marker `01 / 05`), and a glass shelf of your apps. The room's light, clock ring and a sliding halo take the selected app's colour. |
| **Library** (All apps) | Every app with names under the tiles, live **search** (OK on the search field opens the TV keyboard), hold OK for Open / Add to shelf / App info. |
| **Edit shelf** | Same grid; OK adds or removes an app from the shelf (✓ badge). Hold OK on a shelf app at Home to move or remove it. |
| **Personalize** | Your name (greeting + welcome), weather city, colour of the room (the room re-lights live as you move across the swatches), preview of the welcome animation and the screensaver. |
| **Welcome animation** | Plays when the TV starts or wakes: the clock draws itself, "Welcome home" in italic serif, your name. Any button skips it. |
| **Screensaver** | Clock, serif date and weather floating in the dark; glides every minute so nothing burns in. |
| **States** | Shimmering placeholders while apps load, "Make it yours" when the shelf is empty, "Nothing found" for an empty search, weather loading / unavailable / city-not-found, and a glass notice if an app can't open. |

Glass Home is a launcher, so it has no film pages, player or viewer profiles of its own — Netflix, YouTube
and the other apps keep their own. The "profile" is your name on the Personalize page.

## Design system (`Ui.kt`, `Fonts.kt`)

- **Type:** Instrument Serif for titles (like a film's title card), Urbanist for everything read at a glance.
  Sized for ~3 m viewing: titles 60–80 sp, body 18 sp, nothing smaller than 13 sp.
- **Colour:** ivory `#F4EFE6` on near-black; each app lends its own colour as light, kept low so the room
  stays dark. Five room colours: Midnight, Ember, Ocean, Emerald, Pure Black.
- **Focus:** the selected tile grows, gets a thin ivory ring and a coloured halo that glides between tiles;
  everything else dims. Up/Down always leave text fields (no cursor traps).
- **Layout:** 960 × 540 dp canvas (what 720p, 1080p and 4K Android TVs all report), 48 dp side and ~27 dp
  top/bottom safe margins for overscan.
- **Performance (1 GB RAM):** nothing animates while idle; the clock redraws once a second; grain and the
  room's light are drawn once; app colours are worked out once in the background.

## Build and run

No Android Studio needed — `build-apk.ps1` uses the Android SDK tools + the Kotlin compiler
(`kotlin-compiler-2.0.21.zip` from the JetBrains GitHub releases):

```powershell
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -KotlinHome C:\path\to\kotlinc
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 10.204.207.212:5555
& $adb install -r GlassHome.apk
& $adb shell input keyevent KEYCODE_HOME                                                  # open it
& $adb shell am start -n com.charan.glasshome/.HomeActivity --ez play_intro true          # replay the welcome
```

## What was changed on the TV

| Change | Command used |
|---|---|
| Installed Glass Home | `adb install -r GlassHome.apk` |
| Old home screen switched off (not deleted) | `pm disable-user --user 0 com.google.android.tvlauncher` |
| Glass Home is the default home | `cmd package set-home-activity com.charan.glasshome/.HomeActivity` |
| Glass Clock screensaver | `settings put secure screensaver_components com.charan.glasshome/.ClockDream` |

The state before the changes is saved in [`tv-backup/`](tv-backup/).

## Undo everything

```powershell
powershell -ExecutionPolicy Bypass -File restore-tv.ps1                    # old home screen back
powershell -ExecutionPolicy Bypass -File restore-tv.ps1 -RemoveGlassHome   # ...and remove Glass Home
```

Without a computer: Settings → Apps → See all apps → Show system apps → **Android TV Home** → Enable.

## Credits

Cleanup approach and per-device notes follow [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT).
Fonts: Urbanist and Instrument Serif (SIL OFL 1.1 — see `FONT-LICENSE-OFL.txt`,
`FONT-LICENSE-OFL-InstrumentSerif.txt`). Icons: Material Icons paths (Apache 2.0).
