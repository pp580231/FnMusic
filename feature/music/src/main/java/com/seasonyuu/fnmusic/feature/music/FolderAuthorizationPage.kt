package com.seasonyuu.fnmusic.feature.music

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.seasonyuu.fnmusic.core.designsystem.FnSurface
import com.seasonyuu.fnmusic.core.designsystem.LocalLiquidGlassEnabled
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun FolderAuthorizationPage(request: FolderAuthorizationRequest,
    onResult: (FolderAuthorizationResult) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var cookieReady by remember(request) { mutableStateOf(!request.relayMode) }
    val resultHandler by rememberUpdatedState(onResult)
    val backHandler by rememberUpdatedState(onBack)
    val origin = request.origin.toHttpUrlOrNull()
    val surfaceColor = FnSurface

    LaunchedEffect(request) {
        if (request.relayMode) {
            val url = request.origin.toHttpUrlOrNull()
            if (url == null || !url.isHttps) error = "中继地址无效，无法打开授权页面"
            else {
                // The HTTP client's music-token is intentionally never copied to WebView.
                CookieManager.getInstance().setCookie(request.origin, relayRoutingCookie(request.relayMode)) {
                    cookieReady = true
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = surfaceColor) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides false, LocalAppBarBackdrop provides null) {
                    MusicAppBar("授权文件夹", onBack = backHandler, fullAppBarBlur = false,
                        drawBackgroundBlur = false, modifier = Modifier.padding(horizontal = 20.dp), actions = {
                            Icon(painterResource(R.drawable.ic_fnos_mark), contentDescription = "飞牛 fnOS",
                                modifier = Modifier.size(width = 28.dp, height = 22.dp), tint = Color.Unspecified)
                        })
                }
                if (loading && error == null) LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(2.dp).align(androidx.compose.ui.Alignment.BottomCenter),
                    trackColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            }
            error?.let { message ->
                Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { error = null; loading = true }) { Text("重试") }
            }
            if (cookieReady && error == null) AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                WebView(context).apply {
                    setBackgroundColor(surfaceColor.toArgb())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, navigation: WebResourceRequest): Boolean {
                            if (!navigation.isForMainFrame) return false
                            val target = navigation.url.toString().toHttpUrlOrNull() ?: return true
                            if (origin == null || target.scheme != origin.scheme || target.host != origin.host || target.port != origin.port) return true
                            if (target.encodedPath == request.callbackPath) {
                                resultHandler(request.callback(target.toString()))
                                return true
                            }
                            return false
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            val page = url.toHttpUrlOrNull()
                            if (page != null && origin != null && page.scheme == origin.scheme &&
                                page.host == origin.host && page.port == origin.port && page.encodedPath == "/login") {
                                // fnOS's login form can collapse to zero height in an embedded WebView:
                                // its viewport-height CSS is capped by a zero-height root container.
                                view.evaluateJavascript(FNOS_LOGIN_LAYOUT_FIX, null)
                            }
                        }
                        override fun onReceivedError(view: WebView, resource: WebResourceRequest, failure: WebResourceError) {
                            if (resource.isForMainFrame) { loading = false; error = "无法加载 fnOS 授权页面，请检查连接后重试" }
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                            handler.cancel()
                            loading = false
                            error = "fnOS 证书验证失败，无法继续授权"
                        }
                    }
                    loadUrl(request.url)
                }
            }, update = { it.setBackgroundColor(surfaceColor.toArgb()) }, onRelease = {
                it.stopLoading()
                it.webViewClient = WebViewClient()
                it.destroy()
            })
        }
    }
}

private const val FNOS_LOGIN_LAYOUT_FIX = """(function() {
    if (window.__fnMusicLoginLayoutFix) return;
    window.__fnMusicLoginLayoutFix = true;
    function fix() {
        var root = document.getElementById('root');
        var form = document.querySelector('.login-form');
        var height = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);
        if (height < 200) return;
        if (root) root.style.setProperty('height', height + 'px', 'important');
        if (form) {
            form.style.setProperty('height', height + 'px', 'important');
            form.style.setProperty('max-height', 'none', 'important');
        }
    }
    fix();
    new MutationObserver(fix).observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('resize', fix);
})()"""
