package com.ontimestack.webkiosk.app

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ontimestack.webkiosk.MainActivity

class WebViewManager(
    private val activity: MainActivity,
    private val homePageUrl: String,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onErrorChanged: (Boolean) -> Unit,
    private val onRenderProcessGone: () -> Unit
) {
    private var currentWebView: WebView? = null
    private var mainFrameFailed = false

    fun createWebView(): WebView {
        return WebView(activity).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            configureSettings()
            configureClients()
            loadUrl(homePageUrl)
            requestFocus()
            currentWebView = this
        }
    }

    fun reload() {
        currentWebView?.post { currentWebView?.reload() }
    }

    fun destroy(webView: WebView? = currentWebView) {
        if (webView == null || currentWebView !== webView) return
        currentWebView = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureSettings() {
        val isDebuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(isDebuggable)
        CookieManager.getInstance().setAcceptCookie(true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            displayZoomControls = false
            builtInZoomControls = false
            setSupportZoom(false)
            textZoom = 100
            minimumFontSize = 1
            minimumLogicalFontSize = 1
            useWideViewPort = true
        }
    }

    private fun WebView.configureClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                mainFrameFailed = false
                view.visibility = View.INVISIBLE
                onErrorChanged(false)
                onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (mainFrameFailed) return
                view.postDelayed({
                    if (!mainFrameFailed) {
                        view.visibility = View.VISIBLE
                        onErrorChanged(false)
                        onLoadingChanged(false)
                    }
                }, VIEWPORT_SETTLE_MS)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            @Deprecated("Required for Android 5.x WebView")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (failingUrl == null || failingUrl == view?.url) markMainFrameFailed()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) markMainFrameFailed()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "https"
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            @Deprecated("Required for Android 5.x WebView")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !url.toUri().scheme.equals("https", ignoreCase = true)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                destroy(view)
                onRenderProcessGone()
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (!isTrustedHomeOrigin(origin)) {
                    callback.invoke(origin, false, false)
                    return
                }

                val granted = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    callback.invoke(origin, true, false)
                } else {
                    activity.requestGeolocationPermission(origin, callback)
                }
            }
        }
    }

    private fun markMainFrameFailed() {
        mainFrameFailed = true
        onLoadingChanged(false)
        onErrorChanged(true)
    }

    private fun isTrustedHomeOrigin(origin: String): Boolean {
        val home = homePageUrl.toUri()
        val requested = origin.toUri()
        return home.scheme.equals("https", ignoreCase = true) &&
            requested.scheme.equals("https", ignoreCase = true) &&
            home.host.equals(requested.host, ignoreCase = true) &&
            effectivePort(home) == effectivePort(requested)
    }

    private fun effectivePort(uri: Uri): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    companion object {
        private const val VIEWPORT_SETTLE_MS = 250L
    }
}
