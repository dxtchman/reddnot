package com.andy.reddnot

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val id: String,
    val kind: String, // "post" or "comment"
    val title: String,
    val subreddit: String,
    val author: String,
    val url: String,
    val thumb: String,
    val snippet: String,
    val savedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind).put("title", title).put("subreddit", subreddit)
        .put("author", author).put("url", url).put("thumb", thumb).put("snippet", snippet)
        .put("savedAt", savedAt)

    companion object {
        fun fromJson(o: JSONObject) = Bookmark(
            id = o.optString("id"),
            kind = o.optString("kind", "post"),
            title = o.optString("title"),
            subreddit = o.optString("subreddit"),
            author = o.optString("author"),
            url = o.optString("url"),
            thumb = o.optString("thumb"),
            snippet = o.optString("snippet"),
            savedAt = o.optLong("savedAt", System.currentTimeMillis()),
        )
    }
}

/** Everything the app keeps on the phone: bookmarks, subreddit shortcuts, hidden posts, settings. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("reddnot", Context.MODE_PRIVATE)

    val bookmarks = mutableStateListOf<Bookmark>()
    val subs = mutableStateListOf<String>()
    private val hidden = LinkedHashSet<String>()
    var hiddenCount by mutableStateOf(0)
        private set

    var bgKey by mutableStateOf(prefs.getString("bgKey", null) ?: "black")
        private set
    var accentKey by mutableStateOf(prefs.getString("accentKey", null) ?: "blue")
        private set
    /** "rainbow", "accent" or "off" */
    var threadMode by mutableStateOf(prefs.getString("threadMode", null) ?: "rainbow")
        private set
    var paletteKey by mutableStateOf(prefs.getString("paletteKey", null) ?: "classic")
        private set
    var textZoom by mutableIntStateOf(prefs.getInt("textZoom", 100))
        private set
    /** Thread line thickness in px; 0 keeps Reddit's. */
    var threadWidth by mutableIntStateOf(prefs.getInt("threadWidth", 2))
        private set
    /** Subreddits behind the News tab, joined with "+". */
    var newsSubs by mutableStateOf(prefs.getString("newsSubs", null) ?: DEFAULT_NEWS)
        private set
    /** Reddit's own News feed address, found in its site menu. */
    var newsHref by mutableStateOf(prefs.getString("newsHref", null))
        private set
    /** Swipe slot ("post.right.0") -> action id ("up"). */
    val swipes = mutableStateMapOf<String, String>()
    var hideHeader by mutableStateOf(prefs.getBoolean("hideHeader", true))
        private set
    var hideAds by mutableStateOf(prefs.getBoolean("hideAds", true))
        private set

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("bookmarks", "[]"))
            for (i in 0 until arr.length()) bookmarks.add(Bookmark.fromJson(arr.getJSONObject(i)))
        }
        runCatching {
            val arr = JSONArray(prefs.getString("subs", "[]"))
            for (i in 0 until arr.length()) subs.add(arr.getString(i))
        }
        runCatching {
            val arr = JSONArray(prefs.getString("hidden", "[]"))
            for (i in 0 until arr.length()) hidden.add(arr.getString(i))
        }
        hiddenCount = hidden.size
        val saved = runCatching { JSONObject(prefs.getString("swipes", "{}") ?: "{}") }.getOrNull()
        SwipeActions.defaults.forEach { (slot, action) ->
            swipes[slot] = saved?.optString(slot)?.ifBlank { null } ?: action
        }
    }

    // ---- bookmarks ----
    fun isBookmarked(id: String) = bookmarks.any { it.id == id }

    fun addBookmark(b: Bookmark) {
        if (b.id.isBlank()) return
        bookmarks.removeAll { it.id == b.id }
        bookmarks.add(0, b)
        saveBookmarks()
    }

    fun removeBookmark(id: String) {
        bookmarks.removeAll { it.id == id }
        saveBookmarks()
    }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    fun bookmarkIds(): JSONArray = JSONArray().also { a -> bookmarks.forEach { a.put(it.id) } }

    // ---- subreddit shortcuts ----
    fun addSub(raw: String): Boolean {
        val name = raw.trim().removePrefix("/").removePrefix("r/").removePrefix("R/").trim('/').trim()
        if (name.isEmpty() || !name.matches(Regex("[A-Za-z0-9_]{2,30}"))) return false
        if (subs.any { it.equals(name, ignoreCase = true) }) return true
        subs.add(name)
        subs.sortBy { it.lowercase() }
        saveSubs()
        return true
    }

    fun hasSub(name: String) = subs.any { it.equals(name, ignoreCase = true) }

    fun removeSub(name: String) {
        subs.removeAll { it.equals(name, ignoreCase = true) }
        saveSubs()
    }

    private fun saveSubs() {
        prefs.edit().putString("subs", JSONArray(subs.toList()).toString()).apply()
    }

    // ---- hidden posts ----
    fun hide(id: String) {
        if (id.isBlank()) return
        hidden.remove(id)
        hidden.add(id)
        while (hidden.size > 3000) hidden.remove(hidden.first())
        saveHidden()
    }

    fun unhide(id: String) {
        hidden.remove(id)
        saveHidden()
    }

    fun clearHidden() {
        hidden.clear()
        saveHidden()
    }

    fun hiddenIds(): JSONArray = JSONArray(hidden.toList())

    private fun saveHidden() {
        hiddenCount = hidden.size
        prefs.edit().putString("hidden", JSONArray(hidden.toList()).toString()).apply()
    }

    // ---- settings ----
    fun changeBg(key: String) { bgKey = key; prefs.edit().putString("bgKey", key).apply() }
    fun changeAccent(key: String) { accentKey = key; prefs.edit().putString("accentKey", key).apply() }
    fun changeThreadMode(mode: String) { threadMode = mode; prefs.edit().putString("threadMode", mode).apply() }
    fun changePalette(key: String) { paletteKey = key; prefs.edit().putString("paletteKey", key).apply() }
    fun changeThreadWidth(px: Int) { threadWidth = px; prefs.edit().putInt("threadWidth", px).apply() }
    fun changeTextZoom(zoom: Int) { textZoom = zoom; prefs.edit().putInt("textZoom", zoom).apply() }

    fun changeSwipe(slot: String, action: String) {
        swipes[slot] = action
        val o = JSONObject()
        swipes.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("swipes", o.toString()).apply()
    }

    /** Accepts "news worldnews", "r/news, r/worldnews" or "news+worldnews". */
    fun changeNewsSubs(raw: String): Boolean {
        val names = raw.split(Regex("[\\s,+]+"))
            .map { it.trim().removePrefix("/").removePrefix("r/").trim('/') }
            .filter { it.matches(Regex("[A-Za-z0-9_]{2,30}")) }
            .distinctBy { it.lowercase() }
        if (names.isEmpty()) return false
        newsSubs = names.joinToString("+")
        prefs.edit().putString("newsSubs", newsSubs).apply()
        return true
    }

    // Reddit no longer serves combined feeds (r/a+b sends you home), so the News tab
    // shows one news subreddit at a time with chips to switch between them.
    fun changeNewsHref(href: String) { newsHref = href; prefs.edit().putString("newsHref", href).apply() }

    fun newsList(): List<String> = newsSubs.split('+').filter { it.isNotBlank() }

    fun newsUrl(sub: String = newsList().firstOrNull() ?: "news") = "https://www.reddit.com/r/$sub/"

    fun isNews(url: String): Boolean {
        val sub = Links.subredditOf(url) ?: return false
        return newsList().any { it.equals(sub, ignoreCase = true) }
    }

    fun threadColors(): List<Long> =
        if (threadMode == "accent") List(8) { Themes.accent(accentKey).color }
        else Themes.palette(paletteKey).colors
    fun changeHideHeader(on: Boolean) { hideHeader = on; prefs.edit().putBoolean("hideHeader", on).apply() }
    fun changeHideAds(on: Boolean) { hideAds = on; prefs.edit().putBoolean("hideAds", on).apply() }

    fun settingsJson(): JSONObject {
        val bg = Themes.bg(bgKey)
        val theme: Any = if (bg.overridePage) {
            JSONObject().put("bg", Themes.hex(bg.bg)).put("bg2", Themes.hex(bg.bar)).put("bg3", Themes.hex(bg.card))
        } else {
            JSONObject.NULL
        }
        fun side(kind: String, dir: String) =
            JSONArray(listOf(swipes["$kind.$dir.0"] ?: "none", swipes["$kind.$dir.1"] ?: "none"))
        fun kind(k: String) = JSONObject().put("right", side(k, "right")).put("left", side(k, "left"))
        return JSONObject()
            .put("noHeader", hideHeader)
            .put("noAds", hideAds)
            .put("rainbow", threadMode != "off")
            .put("threadWidth", threadWidth)
            .put("theme", theme)
            .put("threads", JSONArray(threadColors().map { Themes.hex(it) }))
            .put("swipes", JSONObject().put("post", kind("post")).put("comment", kind("comment")))
    }

    companion object {
        const val DEFAULT_NEWS = "news+worldnews+technology+science+UpliftingNews"
    }
}
