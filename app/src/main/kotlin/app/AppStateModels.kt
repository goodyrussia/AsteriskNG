// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import features.proxy.server.model.ProxyServer

data class ProxyServerState(
    val id: Int,
    val server: ProxyServer<*>,
    val latency: String = "",
)

fun ProxyServerState.proxyServerOutboundTag(): String {
    return id.toString()
}
