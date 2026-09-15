package com.ontimestack.webkiosk.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.ontimestack.webkiosk.MainActivity
import com.ontimestack.webkiosk.app.WebViewManager

@Composable
fun WebViewComponent(
    url: String,
    activity: MainActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var renderGeneration by remember { mutableIntStateOf(0) }

    val webViewManager = remember(url) {
        WebViewManager(
            activity = activity,
            homePageUrl = url,
            onLoadingChanged = { loading ->
                isLoading = loading
                if (!loading && !hasError) retryAttempt = 0
            },
            onErrorChanged = { hasError = it },
            onRenderProcessGone = {
                renderGeneration++
                retryAttempt = 0
                hasError = false
                isLoading = true
            }
        )
    }

    LaunchedEffect(hasError) {
        if (!hasError) return@LaunchedEffect
        retryAttempt = (retryAttempt + 1).coerceAtMost(MAX_RETRY_ATTEMPT)
        delay(retryDelayMillis(retryAttempt))
        if (hasError) webViewManager.reload()
    }

    DisposableEffect(context, webViewManager) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity.runOnUiThread {
                    if (hasError) webViewManager.reload()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }

        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            webViewManager.destroy()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        key(renderGeneration) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webViewManager.createWebView() },
                onRelease = { webViewManager.destroy(it) }
            )
        }

        if (hasError || isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (hasError) "Connection error — retrying…" else "Loading…",
                    color = Color.White
                )
            }
        }
    }
}

private fun retryDelayMillis(attempt: Int): Long {
    val exponent = (attempt - 1).coerceIn(0, 5)
    return (1_000L shl exponent).coerceAtMost(30_000L)
}

private const val MAX_RETRY_ATTEMPT = 6
