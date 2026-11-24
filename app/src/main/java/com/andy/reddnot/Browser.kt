package com.andy.reddnot

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.abs

/**
 * The reddit.com WebView plus everything around it: the injected Reddnot script,
 * link handling, and messages coming back from the page (swipes, bookmarks, hides).
 */
@SuppressLint("SetJavaScriptEnabled")
class Browser(
    private val context: Context,
    private val store: Store,
    private val showFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
) {
    var url by mutableStateOf(HOME)
        private set
    var pageTitle by mutableStateOf("")
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableIntStateOf(0)
        private set

    /** Reddit's home feed tab, as the page reports it: "foryou", "following" or null. */
    var homeTab by mutableStateOf<String?>(null)
        private set
    private var pendingTab: String? = null

    /** Fullscreen video view from the page, shown over the whole app. */
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** Set by the UI: shows a snackbar, with an Undo button when [undo] isn't null. */
    var onSnack: (text: String, undo: (() -> Unit)?) -> Unit = { text, _ ->
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private val pageScript: String by lazy {
        val css = context.assets.open("reddnot.css").bufferedReader().use { it.readText() }
        val js = context.assets.open("reddnot.js").bufferedReader().use { it.readText() }
        "window.__RN_CSS = ${JSONObject.quote(css)};\n$js"
    }
    private var documentStartScript = false

    val webView: WebView = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            // Look like regular Chrome so Reddit serves its normal mobile site.
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace(Regex("""Version/\S+\s"""), "")
        }
    }

    /** Pull-to-refresh around the WebView. Ignores sideways drags so post swipes don't refresh. */
    val view: SwipeRefreshLayout = object : SwipeRefreshLayout(context) {
        private var downX = 0f
        private var downY = 0f
        private var sideways = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; sideways = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (!sideways && dx > slop && dx > dy) sideways = true
                    if (sideways) return false
                }
            }
            return super.onInterceptTouchEvent(ev)
        }
    }.apply {
        addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setOnRefreshListener { webView.reload() }
    }

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val origins = setOf("https://reddit.com", "https://*.reddit.com")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, pageScript, origins)
            documentStartScript = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "reddnotBridge", origins) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let { handleMessage(it) }
            }
        }

        applyNativeLook()

        webView.webViewClient = object : WebViewClient() {
            // Google and Apple sign-in can't work inside an app, so their scripts never load.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host ?: return null
                val path = request.url.path.orEmpty()
                val blocked = (host == "accounts.google.com" && path.startsWith("/gsi")) ||
                    host == "appleid.cdn-apple.com" || host == "appleid.apple.com"
                return if (blocked) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                return when (uri.scheme) {
                    "http", "https" -> {
                        if (Links.isReddit(uri)) {
                            val fixed = Links.normalize(uri.toString())
                            if (fixed != uri.toString()) { view.loadUrl(fixed); true } else false
                        } else {
                            openExternal(uri); true
                        }
                    }
                    "intent" -> {
                        // Reddit's "open in app" links. Follow the web fallback if it has one.
                        val fallback = runCatching {
                            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                .getStringExtra("browser_fallback_url")
                        }.getOrNull()
                        if (fallback != null && Links.isReddit(Uri.parse(fallback))) view.loadUrl(Links.normalize(fallback))
                        true
                    }
                    else -> { openExternal(uri); true }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loading = true
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (!documentStartScript) view.evaluateJavascript(pageScript, null)
                pushState()
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                this@Browser.view.isRefreshing = false
                if (!documentStartScript) view.evaluateJavascript(pageScript, null)
                pushState()
                CookieManager.getInstance().flush()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                this@Browser.url = url
                canGoBack = view.canGoBack()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                pageTitle = title.orEmpty()
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideCustomView() {
                fullscreenView = null
                fullscreenCallback = null
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = showFileChooser(filePathCallback, fileChooserParams)
        }

        webView.setDownloadListener { downloadUrl, _, _, _, _ -> openExternal(Uri.parse(downloadUrl)) }
    }

    // ---------- navigation ----------
    fun load(target: String) {
        val fixed = Links.normalize(target)
        url = fixed
        webView.loadUrl(fixed)
    }

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun reload() {
        webView.reload()
    }

    fun scrollToTop() {
        webView.evaluateJavascript("window.scrollTo({top: 0, behavior: 'smooth'})", null)
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }

    fun openExternal(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            onSnack("No app can open that link", null)
        }
    }

    fun logOut() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            webView.clearCache(false)
            load(HOME)
        }
    }

    /** Switches Reddit's home feed to "foryou" or "following". */
    fun selectHomeTab(tab: String) {
        homeTab = tab
        if (Links.isHomeFeed(url)) {
            runFeedTab(tab)
        } else {
            pendingTab = tab
            load(HOME)
        }
    }

    private fun runFeedTab(tab: String) {
        webView.evaluateJavascript("window.reddnot && window.reddnot.feedTab('$tab')", null)
    }

    /** Theme colors and text size for the native parts around the page. */
    private fun applyNativeLook() {
        val bg = Themes.bg(store.bgKey)
        webView.setBackgroundColor(bg.bg.toInt())
        view.setColorSchemeColors(Themes.accent(store.accentKey).color.toInt())
        view.setProgressBackgroundColorSchemeColor(bg.card.toInt())
        if (webView.settings.textZoom != store.textZoom) webView.settings.textZoom = store.textZoom
    }

    // ---------- talking to the page ----------
    /** Sends bookmarks, hidden posts and settings to the page script. */
    fun pushState() {
        applyNativeLook()
        val state = JSONObject()
            .put("saved", store.bookmarkIds())
            .put("hidden", store.hiddenIds())
            .put("settings", store.settingsJson())
        webView.evaluateJavascript("window.reddnot && window.reddnot.setState($state)", null)
    }

    /** Bookmarks or un-bookmarks the post on the current comments page (top bar button). */
    fun toggleCurrentPostBookmark() {
        val id = Links.postIdOf(url)
        if (id != null && store.isBookmarked(id)) {
            store.removeBookmark(id)
            pushState()
            onSnack("Bookmark removed", null)
            return
        }
        webView.evaluateJavascript("window.reddnot ? window.reddnot.currentPost() : null") { result ->
            val data = runCatching { JSONObject(result) }.getOrNull()
            val bookmark = if (data != null) {
                bookmarkFrom(data)
            } else {
                Bookmark(
                    id = id ?: url, kind = "post", title = pageTitle.ifBlank { "Post" },
                    subreddit = Links.subredditOf(url)?.let { "r/$it" }.orEmpty(), author = "",
                    url = url, thumb = "", snippet = "", savedAt = System.currentTimeMillis(),
                )
            }
            store.addBookmark(bookmark)
            pushState()
            onSnack("Bookmarked", null)
        }
    }

    private fun bookmarkFrom(o: JSONObject) = Bookmark(
        id = o.optString("id"),
        kind = o.optString("kind", "post"),
        title = o.optString("title"),
        subreddit = o.optString("subreddit"),
        author = o.optString("author"),
        url = o.optString("url"),
        thumb = o.optString("thumb"),
        snippet = o.optString("snippet"),
        savedAt = System.currentTimeMillis(),
    )

    private fun handleMessage(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (msg.optString("type")) {
            "ready" -> {
                pushState()
                pendingTab?.let { if (Links.isHomeFeed(url)) { runFeedTab(it); pendingTab = null } }
            }

            "feedTab" -> homeTab = msg.optString("tab").ifBlank { null }

            "share" -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, msg.optString("url"))
                    .putExtra(Intent.EXTRA_SUBJECT, msg.optString("title"))
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            "haptic" -> webView.performHapticFeedback(
                if (msg.optBoolean("strong")) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
            )

            "bookmark" -> {
                val data = msg.optJSONObject("data") ?: return
                val b = bookmarkFrom(data)
                if (msg.optBoolean("saved")) {
                    store.addBookmark(b)
                    onSnack(if (b.kind == "comment") "Comment bookmarked" else "Bookmarked") {
                        store.removeBookmark(b.id); pushState()
                    }
                } else {
                    store.removeBookmark(b.id)
                    onSnack("Bookmark removed") { store.addBookmark(b); pushState() }
                }
            }

            "hide" -> {
                val id = msg.optString("id")
                store.hide(id)
                onSnack("Post hidden") { store.unhide(id); pushState() }
            }

            "toast" -> onSnack(msg.optString("text"), null)
        }
    }
}
