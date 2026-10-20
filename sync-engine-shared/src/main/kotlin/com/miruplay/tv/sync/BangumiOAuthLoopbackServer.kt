package com.miruplay.tv.sync

import fi.iki.elonen.NanoHTTPD

/**
 * Minimal loopback HTTP server that captures the Bangumi OAuth redirect.
 *
 * The TV browser does not hand custom schemes back to the app, so the
 * registered redirect URI is plain HTTP on loopback
 * ([com.miruplay.tv.scraper.core.BangumiOAuthConstants.CALLBACK_URL]).
 * [start] blocks until the first `/callback` hit (or [stop]), then the
 * coordinator exchanges the code. Bound to 127.0.0.1 only: only processes
 * on this device can reach it.
 */
class BangumiOAuthLoopbackServer(
    private val port: Int,
    private val path: String = "/callback",
    private val onCallback: (callbackUrl: String) -> Unit,
) {
    private var server: NanoHTTPD? = null

    fun start() {
        if (server != null) return
        server = object : NanoHTTPD("127.0.0.1", port) {
            override fun serve(session: IHTTPSession): Response {
                val uri = session.uri.substringBefore('?')
                if (session.method == Method.GET && uri == path) {
                    val query = session.queryParameterString.orEmpty()
                    val callbackUrl = "http://127.0.0.1:$port$path" +
                        (if (query.isBlank()) "" else "?$query")
                    runCatching { onCallback(callbackUrl) }
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        "text/html; charset=utf-8",
                        "<html><body><h1>授权成功，请返回电视</h1></body></html>",
                    )
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        }.also { it.start(SOCKET_READ_TIMEOUT_MS, false) }
    }

    fun stop() {
        runCatching { server?.stop() }
        server = null
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT_MS = 10_000
    }
}
