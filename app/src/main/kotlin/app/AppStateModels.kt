// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Stable
import features.proxy.server.model.ProxyServer

@Stable
data class SubscriptionGroupState(
    val id: Int,
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val enabled: Boolean,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
)

data class ProxyServerState(
    val id: Int,
    val server: ProxyServer<*>,
    val groupId: Int,
    val latency: String = "",
)

fun ProxyServerState.proxyServerOutboundTag(): String {
    return id.toString()
}
