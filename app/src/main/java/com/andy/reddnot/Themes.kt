package com.andy.reddnot

/** Background themes, accent colors and thread-color palettes the user can pick in Me. */
data class BgTheme(
    val key: String,
    val name: String,
    val bg: Long,
    val bar: Long,
    val card: Long,
    /** false = leave Reddit's own dark colors alone on the page. */
    val overridePage: Boolean = true,
)

data class Swatch(val key: String, val name: String, val color: Long)

data class Palette(val key: String, val name: String, val colors: List<Long>)

object Themes {
    val backgrounds = listOf(
        BgTheme("black", "True black", 0xFF000000, 0xFF0A0A0C, 0xFF141417),
        BgTheme("midnight", "Midnight", 0xFF0B1220, 0xFF0F1828, 0xFF172238),
        BgTheme("charcoal", "Charcoal", 0xFF18181B, 0xFF1F1F23, 0xFF27272A),
        BgTheme("grape", "Grape", 0xFF140F1F, 0xFF1B1429, 0xFF251B38),
        BgTheme("forest", "Forest", 0xFF0C1410, 0xFF111C16, 0xFF18261E),
        BgTheme("reddit", "Reddit dark", 0xFF0E1113, 0xFF131719, 0xFF1A1F22, overridePage = false),
    )

    val accents = listOf(
        Swatch("blue", "Blue", 0xFF5B8CFF),
        Swatch("orange", "Orange", 0xFFFF8A3D),
        Swatch("purple", "Purple", 0xFFB57BFF),
        Swatch("green", "Green", 0xFF34C759),
        Swatch("pink", "Pink", 0xFFFF5FA2),
        Swatch("red", "Red", 0xFFFF453A),
        Swatch("teal", "Teal", 0xFF2DD4BF),
        Swatch("yellow", "Yellow", 0xFFFFD60A),
    )

    val palettes = listOf(
        Palette(
            "classic", "Classic",
            listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF32D74B, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F),
        ),
        Palette(
            "pastel", "Pastel",
            listOf(0xFFFF8A80, 0xFFFFD180, 0xFFFFFF8D, 0xFFB9F6CA, 0xFF84FFFF, 0xFF82B1FF, 0xFFB388FF, 0xFFFF80AB),
        ),
        Palette(
            "neon", "Neon",
            listOf(0xFFFF00A8, 0xFF00F0FF, 0xFFADFF00, 0xFFFFE600, 0xFFFF5F00, 0xFF8B5CFF, 0xFF00FF85, 0xFFFF2E63),
        ),
        Palette(
            "ocean", "Ocean",
            listOf(0xFF00B4D8, 0xFF48CAE4, 0xFF90E0EF, 0xFF0096C7, 0xFF2EC4B6, 0xFF4361EE, 0xFF7209B7, 0xFF3A86FF),
        ),
    )

    fun bg(key: String) = backgrounds.firstOrNull { it.key == key } ?: backgrounds[0]
    fun accent(key: String) = accents.firstOrNull { it.key == key } ?: accents[0]
    fun palette(key: String) = palettes.firstOrNull { it.key == key } ?: palettes[0]

    fun hex(color: Long) = String.format("#%06X", color and 0xFFFFFF)
}

/** Everything a swipe can do, by id (the page script uses the same ids). */
object SwipeActions {
    val names = linkedMapOf(
        "none" to "Nothing",
        "up" to "Upvote",
        "down" to "Downvote",
        "save" to "Bookmark",
        "hide" to "Hide post",
        "collapse" to "Collapse thread",
        "reply" to "Reply",
        "share" to "Share",
    )
    val forPosts = listOf("none", "up", "down", "save", "hide", "share")
    val forComments = listOf("none", "up", "down", "save", "collapse", "reply", "share")

    /** slot key -> default action. Slot keys look like "post.right.0" (0 = short, 1 = long). */
    val defaults = linkedMapOf(
        "post.right.0" to "up",
        "post.right.1" to "down",
        "post.left.0" to "save",
        "post.left.1" to "hide",
        "comment.right.0" to "up",
        "comment.right.1" to "down",
        "comment.left.0" to "collapse",
        "comment.left.1" to "save",
    )

    fun slotName(slot: String): String {
        val parts = slot.split('.')
        val length = if (parts[2] == "0") "Short" else "Long"
        val arrow = if (parts[1] == "right") "→" else "←"
        val dir = if (parts[1] == "right") "right" else "left"
        return "$arrow  $length swipe $dir"
    }
}
