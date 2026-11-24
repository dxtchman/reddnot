package com.andy.reddnot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One live page per feed tab (For You, Following, News), so switching tabs keeps
 * each tab where you left it. Pages are made the first time their tab is opened.
 */
class Panes(private val make: (key: String) -> Browser) {
    val keys = listOf("foryou", "following", "news")

    private val map = LinkedHashMap<String, Browser>()

    /** Pages made so far, for drawing. */
    val created = mutableStateListOf<Browser>()

    var activeKey by mutableStateOf("foryou")
        private set

    /** Set by the UI: shows a snackbar. */
    var snack: (String, (() -> Unit)?) -> Unit = { _, _ -> }

    val active: Browser get() = get(activeKey)

    fun get(key: String): Browser = map.getOrPut(key) {
        make(key).also { b ->
            b.onSnack = { text, undo -> snack(text, undo) }
            b.broadcast = { pushAll() }
            created.add(b)
        }
    }

    fun select(key: String) {
        get(key)
        activeKey = key
    }

    fun all(): Collection<Browser> = map.values

    fun pushAll() = map.values.forEach { it.pushStateLocal() }
}
