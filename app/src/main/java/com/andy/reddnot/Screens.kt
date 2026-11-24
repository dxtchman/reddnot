@file:OptIn(ExperimentalMaterial3Api::class)

package com.andy.reddnot

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ===================================================================== app shell

@Composable
fun App(store: Store, browser: Browser, ui: UiState, onFullscreen: (Boolean) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        browser.onSnack = { text, undo ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = text,
                    actionLabel = if (undo != null) "Undo" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) undo?.invoke()
            }
        }
    }

    val openInFeed: (String) -> Unit = { url ->
        browser.load(url)
        ui.tab = Tab.Feed
    }

    BackHandler(enabled = ui.tab != Tab.Feed || browser.canGoBack) {
        if (ui.tab != Tab.Feed) ui.tab = Tab.Feed else browser.goBack()
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            containerColor = Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomBar(ui, onReselectFeed = browser::scrollToTop) },
        ) { pad: PaddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding()
            ) {
                FeedScreen(store, browser, visible = ui.tab == Tab.Feed)
                when (ui.tab) {
                    Tab.Feed -> Unit
                    Tab.Subs -> Page { SubsScreen(store, browser, openInFeed) }
                    Tab.Saved -> Page { SavedScreen(store, browser, openInFeed) }
                    Tab.Me -> Page { MeScreen(store, browser, openInFeed) }
                }
            }
        }

        // Fullscreen video from the page
        val full = browser.fullscreenView
        LaunchedEffect(full != null) { onFullscreen(full != null) }
        if (full != null) {
            key(full) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        (full.parent as? ViewGroup)?.removeView(full)
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            addView(full, FrameLayout.LayoutParams(-1, -1))
                        }
                    },
                )
            }
            BackHandler { browser.exitFullscreen() }
        }
    }
}

/** An opaque page drawn over the feed (the WebView stays alive underneath). */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Bg) { content() }
}

@Composable
private fun BottomBar(ui: UiState, onReselectFeed: () -> Unit) {
    NavigationBar(containerColor = Bar, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Tab.Feed, "Feed", Icons.Filled.Home),
            Triple(Tab.Subs, "Subreddits", Icons.AutoMirrored.Filled.List),
            Triple(Tab.Saved, "Bookmarks", RnIcons.Bookmark),
            Triple(Tab.Me, "Me", Icons.Filled.Person),
        )
        items.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = ui.tab == tab,
                onClick = {
                    if (tab == Tab.Feed && ui.tab == Tab.Feed) onReselectFeed()
                    ui.tab = tab
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                ),
            )
        }
    }
}

private val barColors
    @Composable get() = TopAppBarDefaults.topAppBarColors(
        containerColor = Bar,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
    )

// ===================================================================== feed (the WebView)

@Composable
private fun FeedScreen(store: Store, browser: Browser, visible: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }

    val url = browser.url
    val sub = Links.subredditOf(url)
    val postId = Links.postIdOf(url)
    val sort = Links.sortLabel(url)
    val onNews = store.isNews(url)
    val onHome = Links.isHomeFeed(url)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = barColors,
            navigationIcon = {
                if (browser.canGoBack) {
                    IconButton(onClick = browser::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            title = {
                Column(Modifier.clickable(onClick = browser::scrollToTop)) {
                    Text(
                        if (onNews) "News" else Links.title(url, browser.pageTitle),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sort != null) Text(sort, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            },
            actions = {
                if (postId != null) {
                    val saved = store.bookmarks.any { it.id == postId }
                    IconButton(onClick = browser::toggleCurrentPostBookmark) {
                        Icon(
                            if (saved) RnIcons.Bookmark else RnIcons.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (saved) SaveGreen else Color.White,
                        )
                    }
                }
                if (Links.isFeed(url)) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(RnIcons.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                "Best" to "best", "Hot" to "hot", "New" to "new", "Rising" to "rising",
                                "Top · Today" to "top?t=day", "Top · Week" to "top?t=week",
                                "Top · Month" to "top?t=month", "Top · Year" to "top?t=year",
                                "Top · All time" to "top?t=all",
                            ).filter { sub == null || it.second != "best" }.forEach { (label, value) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    sortMenu = false
                                    browser.load(Links.withSort(url, value))
                                })
                            }
                        }
                    }
                }
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            onClick = { menu = false; browser.reload() },
                        )
                        if (sub != null && !sub.contains('+') && !store.hasSub(sub)) {
                            DropdownMenuItem(
                                text = { Text("Add r/$sub to Subreddits") },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = {
                                    menu = false
                                    store.addSub(sub)
                                    browser.onSnack("Added r/$sub", null)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share link") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menu = false; share(context, url) },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            onClick = {
                                menu = false
                                clipboard.setText(AnnotatedString(url))
                                browser.onSnack("Link copied", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(RnIcons.OpenInBrowser, null) },
                            onClick = { menu = false; browser.openExternal(android.net.Uri.parse(url)) },
                        )
                    }
                }
            },
        )

        if (onHome || onNews) {
            FeedTabs(selected = if (onNews) "news" else browser.homeTab ?: "foryou") { tab ->
                if (tab == "news") browser.load(store.newsUrl()) else browser.selectHomeTab(tab)
            }
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    browser.view.also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
            )
            if (browser.loading) {
                LinearProgressIndicator(
                    progress = { browser.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent,
                    trackColor = Color.Transparent,
                )
            }
        }
    }

    if (searching) {
        SearchDialog(sub, onDismiss = { searching = false }) { query, onlySub ->
            searching = false
            browser.load(Links.search(query, if (onlySub) sub else null))
        }
    }
}

/** For You · Following · News, under the top bar on the home feed. */
@Composable
private fun FeedTabs(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Bar).padding(horizontal = 8.dp)) {
        listOf("foryou" to "For You", "following" to "Following", "news" to "News").forEach { (key, label) ->
            val on = key == selected
            Column(
                Modifier.weight(1f).clickable { onSelect(key) }.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else Muted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (on) Accent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SearchDialog(sub: String?, onDismiss: () -> Unit, onSearch: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var onlySub by remember { mutableStateOf(sub != null) }
    val go: () -> Unit = { if (text.isNotBlank()) onSearch(text, onlySub) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Search") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Search Reddit") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sub != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = onlySub, onCheckedChange = { onlySub = it })
                        Text("Only in r/$sub")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = go) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ===================================================================== subreddits

@Composable
private fun SubsScreen(store: Store, browser: Browser, open: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val add: () -> Unit = {
        if (store.addSub(text)) {
            text = ""
        } else {
            browser.onSnack("That doesn't look like a subreddit name", null)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(colors = barColors, title = { Text("Subreddits", fontWeight = FontWeight.SemiBold) })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Add a subreddit, like pcmasterrace") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = add) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item { SubRow("Home", "Your front page") { open(HOME) } }
            item { SubRow("Popular", "r/popular") { open("https://www.reddit.com/r/popular/") } }
            item { SubRow("All", "r/all") { open("https://www.reddit.com/r/all/") } }
            item { SectionHeader(if (store.subs.isEmpty()) "No shortcuts yet" else "Your shortcuts · swipe to remove") }
            items(store.subs.toList(), key = { it }) { name ->
                SwipeRow(
                    onDelete = {
                        store.removeSub(name)
                        browser.onSnack("Removed r/$name") { store.addSub(name) }
                    },
                ) {
                    SubRow("r/$name", null) { open("https://www.reddit.com/r/$name/") }
                }
            }
        }
    }
}

@Composable
private fun SubRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Bg),
        leadingContent = {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title.removePrefix("r/").take(1).uppercase(),
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = if (subtitle != null) { { Text(subtitle, color = Muted) } } else null,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Muted,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

// ===================================================================== bookmarks

@Composable
private fun SavedScreen(store: Store, browser: Browser, open: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    val shown = store.bookmarks.filter { b ->
        (filter == "all" || b.kind == filter) &&
            (query.isBlank() || listOf(b.title, b.subreddit, b.author, b.snippet).any { it.contains(query, true) })
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = barColors,
            title = { Text("Bookmarks", fontWeight = FontWeight.SemiBold) },
            actions = {
                Text("${store.bookmarks.size}", color = Muted, modifier = Modifier.padding(end = 16.dp))
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search bookmarks") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "post" to "Posts", "comment" to "Comments").forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }

        if (store.bookmarks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(RnIcons.BookmarkBorder, null, tint = Muted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No bookmarks yet", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Swipe a post left a little to bookmark it, or tap the bookmark in the top bar on a post.",
                    color = Muted,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { b ->
                    SwipeRow(
                        onDelete = {
                            store.removeBookmark(b.id)
                            browser.pushState()
                            browser.onSnack("Bookmark removed") { store.addBookmark(b); browser.pushState() }
                        },
                        onShare = { share(context, b.url) },
                    ) {
                        BookmarkRow(b) { open(b.url) }
                    }
                    HorizontalDivider(color = Card)
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(b: Bookmark, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Bg).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                b.title.ifBlank { "Untitled" },
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (b.kind == "comment" && b.snippet.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row {
                    Box(Modifier.width(3.dp).height(36.dp).background(Color(0xFF5E5CE6)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (b.author.isNotBlank()) "u/${b.author}: " else "") + b.snippet,
                        color = Color(0xFFD0D0D6),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                b.subreddit.ifBlank { null },
                if (b.kind == "post" && b.author.isNotBlank()) "u/${b.author}" else null,
                "saved " + DateUtils.getRelativeTimeSpanString(b.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            ).joinToString("  ·  ")
            Text(meta, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.thumb.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = b.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Card),
            )
        }
    }
}

/** Swipe left to delete; swipe right to share when [onShare] is given. */
@Composable
private fun SwipeRow(onDelete: () -> Unit, onShare: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onShare?.invoke(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = onShare != null,
        backgroundContent = {
            val toLeft = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = if (toLeft) Color(0xFFFF453A) else Accent
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    if (toLeft) Icons.Filled.Delete else Icons.Filled.Share,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        },
    ) { content() }
}

// ===================================================================== me / settings

@Composable
private fun MeScreen(store: Store, browser: Browser, open: (String) -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(colors = barColors, title = { Text("Me", fontWeight = FontWeight.SemiBold) })
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionHeader("Account") }
            item { LinkRow("Profile") { open("https://www.reddit.com/user/me/") } }
            item { LinkRow("Notifications") { open("https://www.reddit.com/notifications") } }
            item { LinkRow("Messages") { open("https://www.reddit.com/message/inbox/") } }
            item { LinkRow("Saved on Reddit") { open("https://www.reddit.com/user/me/saved/") } }
            item { LinkRow("Log in") { open("https://www.reddit.com/login/") } }
            item { LinkRow("Log out") { confirmLogout = true } }

            item { SectionHeader("Theme") }
            item {
                SettingLabel("Background")
                SwatchRow(
                    items = Themes.backgrounds.map { Triple(it.key, it.name, Color(it.bg)) },
                    selected = store.bgKey,
                    ring = true,
                ) { store.changeBg(it); browser.pushState() }
            }
            item {
                SettingLabel("Accent color")
                SwatchRow(
                    items = Themes.accents.map { Triple(it.key, it.name, Color(it.color)) },
                    selected = store.accentKey,
                ) { store.changeAccent(it); browser.pushState() }
            }
            item {
                SettingLabel("Comment thread lines")
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rainbow" to "Rainbow", "accent" to "Accent", "off" to "Reddit's").forEach { (mode, label) ->
                        FilterChip(
                            selected = store.threadMode == mode,
                            onClick = { store.changeThreadMode(mode); browser.pushState() },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (store.threadMode == "rainbow") {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Themes.palettes.forEach { palette ->
                            PaletteRow(palette, selected = store.paletteKey == palette.key) {
                                store.changePalette(palette.key); browser.pushState()
                            }
                        }
                    }
                }
            }
            item {
                var zoom by remember(store.textZoom) { mutableStateOf(store.textZoom.toFloat()) }
                SettingLabel("Text size  ·  ${zoom.toInt()}%")
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    onValueChangeFinished = { store.changeTextZoom(zoom.toInt()); browser.pushState() },
                    valueRange = 80f..150f,
                    steps = 6,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                ToggleRow("Hide Reddit's header and tabs", "Use the app's top bar and tab row instead", store.hideHeader) {
                    store.changeHideHeader(it); browser.pushState()
                }
            }
            item {
                ToggleRow("Hide promoted posts", null, store.hideAds) {
                    store.changeHideAds(it); browser.pushState()
                }
            }

            item { SectionHeader("Swipe actions · posts") }
            SwipeActions.defaults.keys.filter { it.startsWith("post.") }.forEach { slot ->
                item(key = slot) {
                    SwipeSlotRow(slot, store.swipes[slot] ?: "none", SwipeActions.forPosts) {
                        store.changeSwipe(slot, it); browser.pushState()
                    }
                }
            }
            item { SectionHeader("Swipe actions · comments") }
            SwipeActions.defaults.keys.filter { it.startsWith("comment.") }.forEach { slot ->
                item(key = slot) {
                    SwipeSlotRow(slot, store.swipes[slot] ?: "none", SwipeActions.forComments) {
                        store.changeSwipe(slot, it); browser.pushState()
                    }
                }
            }
            item {
                Text(
                    "You'll feel a tick when a swipe crosses into each action.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item { SectionHeader("News tab") }
            item {
                var news by remember(store.newsSubs) { mutableStateOf(store.newsSubs.replace("+", " ")) }
                val save: () -> Unit = {
                    if (store.changeNewsSubs(news)) browser.onSnack("News tab updated", null)
                    else browser.onSnack("Add at least one subreddit name", null)
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = news,
                        onValueChange = { news = it },
                        label = { Text("Subreddits, separated by spaces") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = save) { Text("Save") }
                }
            }

            item { SectionHeader("Hidden posts") }
            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Bg),
                    headlineContent = { Text("${store.hiddenCount} hidden") },
                    supportingContent = { Text("Posts you swiped away", color = Muted) },
                    trailingContent = {
                        TextButton(
                            enabled = store.hiddenCount > 0,
                            onClick = { store.clearHidden(); browser.pushState() },
                        ) { Text("Unhide all") }
                    },
                )
            }
            item {
                Text(
                    "Reddnot 0.2 · a custom shell for reddit.com",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Card,
            title = { Text("Log out?") },
            text = { Text("This clears Reddit's cookies in the app. Your bookmarks stay.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; browser.logOut(); open(HOME) }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Bg),
        headlineContent = { Text(title) },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Bg),
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle, color = Muted) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** A scrolling row of color circles with names. */
@Composable
private fun SwatchRow(
    items: List<Triple<String, String, Color>>,
    selected: String,
    ring: Boolean = false,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (key, name, color) ->
            val on = key == selected
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(key) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp)
                        .border(if (on) 3.dp else if (ring) 1.dp else 0.dp, if (on) Accent else Color(0xFF3A3A40), CircleShape)
                        .padding(if (on) 5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) Color.White else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PaletteRow(palette: Palette, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Card else Color.Transparent)
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(palette.name, modifier = Modifier.width(80.dp), color = if (selected) Color.White else Muted)
        palette.colors.forEach { c ->
            Box(Modifier.padding(end = 6.dp).width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(Color(c)))
        }
    }
}

/** One swipe slot with a menu of actions. */
@Composable
private fun SwipeSlotRow(slot: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.clickable { open = true },
            colors = ListItemDefaults.colors(containerColor = Bg),
            headlineContent = { Text(SwipeActions.slotName(slot)) },
            trailingContent = {
                Text(SwipeActions.names[current] ?: current, color = if (current == "none") Muted else Accent)
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { id ->
                DropdownMenuItem(
                    text = { Text(SwipeActions.names[id] ?: id) },
                    onClick = { open = false; onPick(id) },
                )
            }
        }
    }
}

// ===================================================================== helpers

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}
