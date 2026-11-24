package com.andy.reddnot

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

    var trueBlack by mutableStateOf(prefs.getBoolean("trueBlack", true))
        private set
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
    fun changeTrueBlack(on: Boolean) { trueBlack = on; prefs.edit().putBoolean("trueBlack", on).apply() }
    fun changeHideHeader(on: Boolean) { hideHeader = on; prefs.edit().putBoolean("hideHeader", on).apply() }
    fun changeHideAds(on: Boolean) { hideAds = on; prefs.edit().putBoolean("hideAds", on).apply() }

    fun settingsJson(): JSONObject = JSONObject()
        .put("black", trueBlack)
        .put("noHeader", hideHeader)
        .put("noAds", hideAds)
}
