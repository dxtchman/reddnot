package com.andy.reddnot

import android.net.Uri

const val HOME = "https://www.reddit.com/"

object Links {
    private val redditHosts = listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com")

    fun isReddit(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return redditHosts.any { host == it || host.endsWith(".$it") }
    }

    /** old./np./m. reddit links open on the normal site. */
    fun normalize(url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return url
        val swap = host == "reddit.com" || host == "old.reddit.com" || host == "np.reddit.com" ||
            host == "m.reddit.com" || host == "new.reddit.com" || host == "i.reddit.com"
        return if (swap) uri.buildUpon().scheme("https").authority("www.reddit.com").build().toString() else url
    }

    /** First reddit link in shared text, if any. */
    fun findIn(text: String): String? =
        Regex("""https?://\S+""").findAll(text).map { it.value.trimEnd(')', '.', ',') }
            .firstOrNull { isReddit(Uri.parse(it)) }

    fun path(url: String): String = Uri.parse(url).path ?: "/"

    fun subredditOf(url: String): String? =
        Regex("^/r/([A-Za-z0-9_+]+)").find(path(url))?.groupValues?.get(1)

    fun isPost(url: String) = path(url).contains("/comments/")

    /** "t3_abc123" for a comments page, matching the id Reddit puts on the post. */
    fun postIdOf(url: String): String? =
        Regex("/comments/([A-Za-z0-9]+)").find(path(url))?.groupValues?.get(1)?.let { "t3_$it" }

    fun isFeed(url: String): Boolean {
        val p = path(url).trimEnd('/')
        if (p.isEmpty()) return true
        return Regex("^(/r/[A-Za-z0-9_+]+)?(/(best|hot|new|top|rising|controversial))?$").matches(p)
    }

    /** Reddit's home page, where its For You / Following tabs live. */
    fun isHomeFeed(url: String): Boolean =
        isReddit(Uri.parse(url)) && Regex("^/((best|hot|new|top|rising)/?)?$").matches(path(url))

    fun title(url: String, pageTitle: String): String {
        val p = path(url)
        val sub = subredditOf(url)
        return when {
            isPost(url) -> if (sub != null) "r/$sub" else "Post"
            sub != null -> "r/$sub"
            p.startsWith("/user/") || p.startsWith("/u/") ->
                p.split('/').getOrNull(2)?.let { if (it == "me") "Profile" else "u/$it" } ?: "Profile"
            p.startsWith("/search") -> "Search"
            p.startsWith("/notifications") -> "Notifications"
            p.startsWith("/message") -> "Messages"
            p.startsWith("/login") -> "Log in"
            p.trimEnd('/').isEmpty() || isFeed(url) -> "Home"
            else -> pageTitle.ifBlank { "Reddit" }
        }
    }

    fun sortLabel(url: String): String? {
        if (!isFeed(url)) return null
        val p = path(url).trimEnd('/')
        val sort = Regex("/(best|hot|new|top|rising|controversial)$").find(p)?.groupValues?.get(1)
            ?: return null
        val t = Uri.parse(url).getQueryParameter("t")
        val name = sort.replaceFirstChar { it.uppercase() }
        return if (sort == "top" && t != null) "$name · ${timeName(t)}" else name
    }

    private fun timeName(t: String) = when (t) {
        "hour" -> "Hour"; "day" -> "Today"; "week" -> "Week"; "month" -> "Month"
        "year" -> "Year"; "all" -> "All time"; else -> t
    }

    /** The same feed with another sort. [sort] is like "new" or "top?t=week". */
    fun withSort(url: String, sort: String): String {
        val sub = subredditOf(url)
        val base = if (sub != null) "https://www.reddit.com/r/$sub/" else HOME
        return base + sort.replace("?", "/?").let { if (it.contains("/?")) it else "$it/" }
    }

    fun search(query: String, inSub: String?): String {
        val q = Uri.encode(query.trim())
        return if (inSub != null) "https://www.reddit.com/r/$inSub/search/?q=$q&restrict_sr=1"
        else "https://www.reddit.com/search/?q=$q"
    }
}
