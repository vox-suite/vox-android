package `in`.voxagent.mobile.web

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import `in`.voxagent.mobile.BuildConfig

private const val ASSET_HOST = "appassets.androidplatform.net"
private const val ENTRY_PATH = "/assets/web/index.html"
private const val ENTRY_URL = "https://$ASSET_HOST$ENTRY_PATH"
private const val BACKGROUND = 0xFF040506.toInt()

private fun contentSecurityPolicy(): String {
    val api = BuildConfig.VOX_API_BASE_URL.trimEnd('/')
    val socket = api.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    return listOf(
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob: https:",
        "font-src 'self' data:",
        "connect-src 'self' $api $socket",
        "object-src 'none'",
        "base-uri 'none'",
        "frame-ancestors 'none'",
    ).joinToString("; ")
}

@SuppressLint("MissingOnRenderProcessGone")
private class VoxWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val policy: String,
    private val onRenderProcessCrashed: () -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val response = assetLoader.shouldInterceptRequest(request.url) ?: return null
        if (request.url.path == ENTRY_PATH) {
            response.responseHeaders =
                (response.responseHeaders ?: emptyMap()) +
                ("Content-Security-Policy" to policy)
        }
        return response
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        onRenderProcessCrashed()
        return true
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = request.url.host != ASSET_HOST
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VoxWebScreen(
    route: String,
    tokenProvider: () -> String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    val webView = remember(route, generation) {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        val policy = contentSecurityPolicy()
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = WebView.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(false)
                builtInZoomControls = false
            }
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            addJavascriptInterface(VoxHostBridge(context, tokenProvider), "VoxHost")
            webViewClient = VoxWebViewClient(assetLoader, policy) { generation += 1 }
            loadUrl("$ENTRY_URL#/$route")
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.evaluateJavascript("window.dispatchEvent(new Event('focus'))", null)
                }
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.removeJavascriptInterface("VoxHost")
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
