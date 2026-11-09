package com.mio.os.ui.apps

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SheetHandle
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

/**
 * SEGAWA Browser: a real single-tab WebView browser with a smart address
 * bar (URLs load directly, anything else uses the selected search engine), bookmarks
 * and history stored on-device in [com.mio.os.core.MioSettings].
 */
@Composable
fun BrowserScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var urlText by remember { mutableStateOf(settings.browserHome) }
    var pageTitle by remember { mutableStateOf("Browser") }
    var pageUrl by remember { mutableStateOf(settings.browserHome) }
    var progress by remember { mutableIntStateOf(100) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun normalized(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return settings.browserHome
        if (t.contains(' ') || !t.contains('.')) {
            val base = SearchEngines[settings.browserEngine] ?: "https://duckduckgo.com/?q="
            return base + Uri.encode(t)
        }
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    fun pushHistory(title: String, url: String) {
        if (!url.startsWith("http")) return
        val clean = title.ifBlank { url }.take(80)
        val next = (listOf(clean to url) + parsePairs(settings.browserHistory)
            .filterNot { it.second == url }).take(50)
        Mio.prefs.edit { it.copy(browserHistory = renderPairs(next)) }
    }

    val bookmarks = remember(settings.bookmarks) { parsePairs(settings.bookmarks) }
    val bookmarked = remember(bookmarks, pageUrl) { bookmarks.any { it.second == pageUrl } }

    fun toggleBookmark() {
        val next = if (bookmarked) {
            bookmarks.filterNot { it.second == pageUrl }
        } else {
            bookmarks + (pageTitle.ifBlank { pageUrl }.take(80) to pageUrl)
        }
        Mio.prefs.edit { it.copy(bookmarks = renderPairs(next)) }
        LauncherHelper.toast(context, if (bookmarked) "Bookmark removed" else "Bookmarked")
    }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else MioNav.pop()
    }

    Box(Modifier.fillMaxSize().background(MioColors.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pageTitle.ifBlank { "Browser" },
                        style = MioType.title.copy(fontSize = 13.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pageUrl,
                        style = MioType.micro.copy(fontSize = 9.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { toggleBookmark() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Star, "Bookmark",
                        tint = if (bookmarked) LocalAccent.current else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (urlText.isEmpty()) {
                        Text(
                            "Search or enter address",
                            style = MioType.body.copy(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.45f)),
                        )
                    }
                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        textStyle = MioType.title.copy(fontSize = 12.5.sp, color = Color.White),
                        cursorBrush = SolidColor(LocalAccent.current),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val target = normalized(urlText)
                            urlText = target
                            webView?.loadUrl(target)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Go",
                    style = MioType.micro.copy(fontSize = 11.sp, color = LocalAccent.current),
                    modifier = Modifier.bounceClick {
                        val target = normalized(urlText)
                        urlText = target
                        webView?.loadUrl(target)
                    }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                )
            }

            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
                    Box(
                        Modifier.fillMaxWidth(progress / 100f).height(2.dp)
                            .background(LocalAccent.current),
                    )
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                pageTitle = view.title ?: url
                                pageUrl = url
                                canBack = view.canGoBack()
                                canFwd = view.canGoForward()
                                pushHistory(view.title ?: "", url)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(settings.browserHome.ifBlank { "https://duckduckgo.com" })
                        webView = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserTool(Icons.Rounded.ArrowBackIosNew, "Back", canBack) { webView?.goBack() }
                BrowserTool(Icons.Rounded.ArrowForwardIos, "Forward", canFwd) { webView?.goForward() }
                BrowserTool(Icons.Rounded.Refresh, "Reload", true) { webView?.reload() }
                BrowserTool(Icons.Rounded.Home, "Home", true) {
                    val home = settings.browserHome.ifBlank { "https://duckduckgo.com" }
                    urlText = home
                    webView?.loadUrl(home)
                }
                BrowserTool(Icons.Rounded.History, "Library", true) { showLibrary = true }
            }
        }
    }

    if (showLibrary) {
        ModalBottomSheet(
            onDismissRequest = { showLibrary = false },
            containerColor = Color(0xFF0D0918),
            dragHandle = { SheetHandle() },
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Bookmarks & history", style = MioType.h2)
                Spacer(Modifier.height(6.dp))
                Text("Stored only on this device.", style = MioType.micro.copy(fontSize = 10.sp))
                Spacer(Modifier.height(10.dp))
                val history = remember(settings.browserHistory) { parsePairs(settings.browserHistory) }
                LazyColumn(Modifier.height(320.dp)) {
                    item {
                        Text("SEARCH ENGINE", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                        Spacer(Modifier.height(6.dp))
                    }
                    items(SearchEngines.keys.toList()) { name ->
                        Row(
                            Modifier.fillMaxWidth()
                                .bounceClick(scaleDown = 0.98f) {
                                    Mio.prefs.edit { it.copy(browserEngine = name) }
                                }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name,
                                style = MioType.title.copy(
                                    fontSize = 12.5.sp,
                                    color = if (name == settings.browserEngine) LocalAccent.current else Color.White,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (name == settings.browserEngine) {
                                Text(
                                    "✓",
                                    style = MioType.title.copy(fontSize = 14.sp, color = LocalAccent.current),
                                )
                            }
                        }
                    }
                    if (bookmarks.isNotEmpty()) {
                        item {
                            Text("★ BOOKMARKS", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                            Spacer(Modifier.height(6.dp))
                        }
                        items(bookmarks, key = { "b" + it.second }) { (t, u) ->
                            LibraryRow(t, u) {
                                urlText = u; webView?.loadUrl(u); showLibrary = false
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(10.dp))
                            Text("HISTORY", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                            Spacer(Modifier.height(6.dp))
                        }
                        items(history, key = { "h" + it.second }) { (t, u) ->
                            LibraryRow(t, u) {
                                urlText = u; webView?.loadUrl(u); showLibrary = false
                            }
                        }
                        item {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text(
                                    "Clear history",
                                    style = MioType.micro.copy(fontSize = 10.5.sp, color = MioColors.Pink),
                                    modifier = Modifier.bounceClick {
                                        Mio.prefs.edit { it.copy(browserHistory = "") }
                                    }.padding(8.dp),
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    if (bookmarks.isEmpty() && history.isEmpty()) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), corner = 16.dp, alpha = 0.06f) {
                                Text(
                                    "Nothing saved yet. Pages you visit appear here automatically.",
                                    style = MioType.body.copy(fontSize = 11.5.sp),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Search engines offered in the library sheet (name -> query URL prefix). */
private val SearchEngines = mapOf(
    "DuckDuckGo" to "https://duckduckgo.com/?q=",
    "Brave" to "https://search.brave.com/search?q=",
    "Google" to "https://www.google.com/search?q=",
    "Bing" to "https://www.bing.com/search?q=",
)

private fun parsePairs(raw: String): List<Pair<String, String>> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        val i = line.indexOf('|')
        if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
    }.toList()
}

private fun renderPairs(pairs: List<Pair<String, String>>): String =
    pairs.joinToString("\n") { (t, u) -> "${t.replace('|', ' ').replace('\n', ' ')}|$u" }

@Composable
private fun BrowserTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.09f else 0.04f))
            .bounceClick { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun LibraryRow(title: String, url: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MioType.title.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(url, style = MioType.micro.copy(fontSize = 9.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
