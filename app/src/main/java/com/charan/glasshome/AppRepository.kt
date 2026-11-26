package com.charan.glasshome

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class AppEntry(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
    val banner: Drawable?,
)

/** Finds the installed apps that can be opened (cached), and opens them. */
object AppRepository {

    @Volatile private var cached: List<AppEntry>? = null
    private val artCache = HashMap<String, ArtUtil.Art?>()

    /** Forget the cached list (an app was installed, removed or updated). */
    fun invalidate() {
        cached = null
        synchronized(artCache) { artCache.clear() }
    }

    /** Slow the first time (loads icons) — call it off the main thread. */
    fun loadAll(ctx: Context): List<AppEntry> {
        cached?.let { return it }
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, AppEntry>()
        // TV apps first, so we prefer their TV banner over a phone icon.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName
                if (pkg == ctx.packageName || found.containsKey(pkg)) continue
                val banner = runCatching { info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                found[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), icon, banner)
            }
        }
        val list = found.values.sortedBy { it.label.lowercase() }
        cached = list
        return list
    }

    /** Blurred backdrop + glow colour for an app (cached). Call off the main thread. */
    fun art(app: AppEntry): ArtUtil.Art? {
        synchronized(artCache) { if (artCache.containsKey(app.pkg)) return artCache[app.pkg] }
        val art = runCatching {
            if (app.banner != null) ArtUtil.make(app.banner, isBanner = true) else ArtUtil.make(app.icon, isBanner = false)
        }.getOrNull()
        synchronized(artCache) { artCache[app.pkg] = art }
        return art
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
