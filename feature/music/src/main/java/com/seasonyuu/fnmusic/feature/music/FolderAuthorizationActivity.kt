package com.seasonyuu.fnmusic.feature.music

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Owns a native View hierarchy; the Compose host communicates only through Activity Result. */
class FolderAuthorizationActivity : ComponentActivity() {
    private lateinit var launch: FolderAuthorizationLaunch
    private lateinit var origin: HttpUrl
    private lateinit var container: FrameLayout
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorPanel: View
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val input = FolderAuthorizationContract.readInput(intent)
        if (input != null && ColorUtils.calculateLuminance(input.backgroundColor) <= .5) {
            setTheme(R.style.Theme_FnMusic_FolderAuthorization_Dark)
        }
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val source = input?.request?.origin?.toHttpUrlOrNull()
        val url = input?.request?.url?.toHttpUrlOrNull()
        if (input == null || source == null || url == null || !sameOrigin(source, url) ||
            input.request.state.isBlank() || !input.request.callbackPath.startsWith("/") ||
            (input.request.relayMode && !source.isHttps)) {
            complete(FolderAuthorizationResult.Invalid)
            return
        }
        launch = input
        origin = source
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(input.backgroundColor))
        setContentView(R.layout.activity_folder_authorization)
        val root = findViewById<View>(R.id.authorization_root)
        root.setBackgroundColor(input.backgroundColor)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, root).apply {
            val light = ColorUtils.calculateLuminance(input.backgroundColor) > .5
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        container = findViewById(R.id.authorization_web_container)
        loadingIndicator = findViewById<ProgressBar>(R.id.authorization_progress).apply {
            indeterminateTintList = ColorStateList.valueOf(input.accentColor)
        }
        errorPanel = findViewById<View>(R.id.authorization_error).apply { setBackgroundColor(input.backgroundColor) }
        findViewById<TextView>(R.id.authorization_title).setTextColor(input.foregroundColor)
        findViewById<TextView>(R.id.authorization_error_message).setTextColor(input.foregroundColor)
        findViewById<ImageButton>(R.id.authorization_back).apply {
            imageTintList = ColorStateList.valueOf(input.foregroundColor)
            setOnClickListener { finish() }
        }
        findViewById<Button>(R.id.authorization_retry).apply {
            setTextColor(input.accentColor)
            setOnClickListener { loadPage() }
        }
        loadPage(savedInstanceState?.getBundle(WEB_STATE))
    }

    private fun loadPage(savedWebState: Bundle? = null) {
        errorPanel.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE
        val view = webView ?: createWebView().also {
            webView = it
            container.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val load = {
            if (active(view) && (savedWebState == null || view.restoreState(savedWebState) == null)) {
                view.loadUrl(launch.request.url)
            }
        }
        if (launch.request.relayMode) {
            // Only the routing cookie crosses this boundary; music credentials stay in OkHttp.
            CookieManager.getInstance().setCookie(launch.request.origin, relayRoutingCookie(true)) { accepted ->
                if (active(view)) {
                    if (accepted) load() else showError(R.string.folder_authorization_relay_error)
                }
            }
        } else load()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(this).apply {
        id = R.id.authorization_web_view
        setBackgroundColor(launch.backgroundColor)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, navigation: WebResourceRequest): Boolean {
                if (!active(view)) return true
                if (!navigation.isForMainFrame) return false
                val target = navigation.url.toString().toHttpUrlOrNull() ?: return true
                if (!sameOrigin(origin, target)) return true
                if (target.encodedPath == launch.request.callbackPath) {
                    complete(launch.request.callback(target.toString()))
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (active(view)) loadingIndicator.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!active(view)) return
                loadingIndicator.visibility = View.INVISIBLE
                val page = url.toHttpUrlOrNull()
                if (page != null && sameOrigin(origin, page) && page.encodedPath == "/login") {
                    view.evaluateJavascript(FNOS_LOGIN_LAYOUT_FIX, null)
                }
            }

            override fun onReceivedError(view: WebView, resource: WebResourceRequest, failure: WebResourceError) {
                if (active(view) && resource.isForMainFrame) showError(R.string.folder_authorization_network_error)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                handler.cancel()
                if (active(view)) showError(R.string.folder_authorization_ssl_error)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (webView === view) {
                    releaseWebView(stopLoading = false)
                    showError(R.string.folder_authorization_renderer_error)
                }
                return true
            }
        }
    }

    private fun active(view: WebView) = webView === view && !isFinishing && !isDestroyed

    private fun showError(message: Int) {
        if (isFinishing || isDestroyed) return
        loadingIndicator.visibility = View.INVISIBLE
        findViewById<TextView>(R.id.authorization_error_message).setText(message)
        errorPanel.visibility = View.VISIBLE
    }

    private fun complete(result: FolderAuthorizationResult) {
        if (isFinishing || isDestroyed) return
        setResult(RESULT_OK, FolderAuthorizationContract.resultIntent(result))
        // Destruction belongs to onDestroy, after this WebView callback has returned.
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.takeIf { errorPanel.visibility != View.VISIBLE }?.let { view ->
            val webState = Bundle()
            if (view.saveState(webState) != null) outState.putBundle(WEB_STATE, webState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        releaseWebView()
        super.onDestroy()
    }

    private fun releaseWebView(stopLoading: Boolean = true) {
        val view = webView ?: return
        webView = null
        view.webViewClient = WebViewClient()
        if (stopLoading) view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private companion object {
        const val WEB_STATE = "authorization.webState"
        fun sameOrigin(left: HttpUrl, right: HttpUrl) =
            left.scheme == right.scheme && left.host == right.host && left.port == right.port
    }
}

// fnOS's login form can collapse under its zero-height root in an embedded WebView.
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
