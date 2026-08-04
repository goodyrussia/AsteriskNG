// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import android.os.SystemClock
import app.ProxyServerState
import engine.network.toPortOrNull
import features.logs.AndroidAppLogger
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.customXrayConfigProxyOutboundEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

internal class AndroidProxyLatencyTester {
    suspend fun test(server: ProxyServerState): ProxyServerLatencyTestResult = withContext(Dispatchers.IO) {
        ProxyServerLatencyTestResult(tcpConnectLatency(server))
    }

    private suspend fun tcpConnectLatency(server: ProxyServerState): Long {
        val endpoint = server.server.endpoint() ?: return FailedDelayMillis
        var bestMillis = FailedDelayMillis
        repeat(TcpConnectAttempts) {
            currentCoroutineContext().ensureActive()
            val millis = socketConnectTime(endpoint.host, endpoint.port)
            if (millis >= 0 && (bestMillis !in 0..millis)) {
                bestMillis = millis
            }
        }
        AndroidAppLogger.debug(LogTag, "TCP latency test serverId=${server.id} result=${bestMillis}ms")
        return bestMillis
    }

    private fun socketConnectTime(host: String, port: Int): Long {
        return runCatching {
            Socket().use { socket ->
                val startedAt = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(host, port), TcpConnectTimeoutMillis)
                SystemClock.elapsedRealtime() - startedAt
            }
        }.onFailure { error ->
            when (error) {
                is UnknownHostException -> AndroidAppLogger.debug(LogTag, "Unknown host for TCP latency test: $host")
                is IOException -> AndroidAppLogger.debug(LogTag, "TCP latency test IO failure: $host:$port ${error.message}")
                else -> AndroidAppLogger.warn(LogTag, "TCP latency test failed: $host:$port", error)
            }
        }.getOrDefault(FailedDelayMillis)
    }
}

enum class ProxyServerLatencyTestMode {
    TcpConnect,
}

data class ProxyServerLatencyTestResult(
    val elapsedMillis: Long,
) {
    companion object {
        val Failed = ProxyServerLatencyTestResult(elapsedMillis = -1L)
    }
}

private data class ProxyServerEndpoint(
    val host: String,
    val port: Int,
)

private fun ProxyServer<*>.endpoint(): ProxyServerEndpoint? {
    return when (this) {
        is HTTP -> endpoint(server, port)
        is Shadowsocks -> endpoint(server, port)
        is Socks -> endpoint(server, port)
        is Trojan -> endpoint(server, port)
        is VLESS -> endpoint(server, port)
        is VMess -> endpoint(server, port)
        is Wireguard -> endpoint(server, port)
        is Custom -> customXrayConfigProxyOutboundEndpoint(configJson)
            ?.let { endpoint -> ProxyServerEndpoint(endpoint.host, endpoint.port) }
        else -> null
    }
}

private fun endpoint(host: String, port: String): ProxyServerEndpoint? {
    val parsedPort = port.toPortOrNull() ?: return null
    return host.trim()
        .takeIf(String::isNotEmpty)
        ?.let { ProxyServerEndpoint(it, parsedPort) }
}

private const val LogTag = "ProxyLatencyTest"
private const val FailedDelayMillis = -1L
private const val TcpConnectAttempts = 2
private const val TcpConnectTimeoutMillis = 3_000
