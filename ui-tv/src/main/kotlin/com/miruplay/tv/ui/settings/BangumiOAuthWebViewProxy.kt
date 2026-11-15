package com.miruplay.tv.ui.settings

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import java.util.concurrent.Executor

/**
 * Proxy rule that can be applied to the Bangumi OAuth login WebView.
 *
 * [proxyRule] is the value accepted by [ProxyConfig.Builder.addProxyRule], while [displayAddress] is
 * the human readable `host:port` form used in status text and logs.
 */
internal data class BangumiOAuthWebViewProxyRule(
    val proxyRule: String,
    val bypassRules: List<String>,
    val displayAddress: String,
)

/**
 * Maps the app wide outbound proxy configuration onto a WebView proxy rule.
 *
 * Returns null when the proxy is disabled, the host is blank, or the port is out of range: the OAuth
 * login WebView then keeps using the direct connection.
 */
internal fun BangumiHttpProxyConfig.toBangumiOAuthWebViewProxyRule(): BangumiOAuthWebViewProxyRule? {
    if (!enabled) return null
    val normalizedHost = host.trim()
    if (normalizedHost.isEmpty()) return null
    if (port !in 1..65_535) return null
    return BangumiOAuthWebViewProxyRule(
        proxyRule = "http://$normalizedHost:$port",
        bypassRules = WEBVIEW_PROXY_BYPASS_RULES,
        displayAddress = "$normalizedHost:$port",
    )
}

// Keep the OAuth loopback callback on the direct connection so the redirect is never proxied.
private val WEBVIEW_PROXY_BYPASS_RULES: List<String> = listOf("localhost", "127.0.0.1")

/**
 * Thin wrapper around the androidx.webkit proxy override APIs used by the Bangumi OAuth WebView.
 *
 * Every platform call is defensive: WebView provider behavior varies across Android TV devices and
 * the OAuth dialog must still load (over the direct connection) when the proxy cannot be applied.
 */
internal object BangumiOAuthWebViewProxy {
    fun isSupported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }
            .getOrDefault(false)

    /**
     * Applies [rule] to the WebViews of this process.
     *
     * [onApplied] is invoked exactly once: asynchronously by the WebView when the override took
     * effect, or synchronously from here when the platform rejected it. Callers use it to continue
     * loading the authorization URL. Returns true when the override request was accepted.
     */
    fun apply(rule: BangumiOAuthWebViewProxyRule, executor: Executor, onApplied: () -> Unit): Boolean {
        return try {
            val builder = ProxyConfig.Builder().addProxyRule(rule.proxyRule)
            rule.bypassRules.forEach { bypassRule -> builder.addBypassRule(bypassRule) }
            ProxyController.getInstance().setProxyOverride(
                builder.build(),
                executor,
                Runnable { onApplied() },
            )
            true
        } catch (exception: Exception) {
            // Unsupported WebView providers throw UnsupportedOperationException / IllegalStateException.
            onApplied()
            false
        }
    }

    /** Clears any proxy override installed by [apply] and restores the direct connection. */
    fun clear(executor: Executor) {
        runCatching {
            ProxyController.getInstance().clearProxyOverride(executor, Runnable {})
        }
    }
}
