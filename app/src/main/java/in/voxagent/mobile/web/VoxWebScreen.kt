package `in`.voxagent.mobile.web

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VoxWebScreen(
    route: String,
    tokenProvider: () -> String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(route) {
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
            addJavascriptInterface(VoxHostBridge(tokenProvider), "VoxHost")
            webViewClient = object : WebViewClient() {
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

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.host != ASSET_HOST
            }
            loadUrl("$ENTRY_URL#/$route")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("VoxHost")
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
