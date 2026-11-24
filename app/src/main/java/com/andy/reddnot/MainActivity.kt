package com.andy.reddnot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

enum class Tab { Feed, Subs, Saved, Me }

class UiState {
    var tab by mutableStateOf(Tab.Feed)
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var browser: Browser
    private val ui = UiState()

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        store = Store(this)
        browser = Browser(this, store) { callback, params ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            try {
                filePicker.launch(params.createIntent())
                true
            } catch (_: Exception) {
                fileCallback = null
                false
            }
        }
        browser.load(linkFrom(intent) ?: HOME)

        setContent {
            AppTheme(store) {
                App(store, browser, ui, onFullscreen = ::setFullscreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let {
            browser.load(it)
            ui.tab = Tab.Feed
        }
    }

    override fun onResume() {
        super.onResume()
        browser.webView.onResume()
    }

    override fun onPause() {
        browser.webView.onPause()
        android.webkit.CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) browser.webView.destroy()
        super.onDestroy()
    }

    private fun setFullscreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun linkFrom(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.toString()
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Links.findIn(it) }
        else -> null
    }
}

val Upvote = Color(0xFFFF6A1A)
val SaveGreen = Color(0xFF34C759)
val Muted = Color(0xFF8E8E93)

// Theme colors, following the user's picks in Me.
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
val Bar: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val Card: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Bg: Color @Composable get() = MaterialTheme.colorScheme.background

@Composable
fun AppTheme(store: Store, content: @Composable () -> Unit) {
    val bg = Themes.bg(store.bgKey)
    val accent = Color(Themes.accent(store.accentKey).color)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = SaveGreen,
        background = Color(bg.bg),
        surface = Color(bg.bg),
        surfaceVariant = Color(bg.card),
        surfaceContainer = Color(bg.bar),
        surfaceContainerHigh = Color(bg.card),
        secondaryContainer = accent.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        outline = Color(0xFF3A3A40),
        outlineVariant = Color(bg.card),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
