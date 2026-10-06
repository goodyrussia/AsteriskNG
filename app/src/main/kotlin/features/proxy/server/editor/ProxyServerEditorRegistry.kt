// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.lazy.LazyListScope
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard

internal fun ProxyServer<*>.editableCopy(): ProxyServer<*> {
    return when (this) {
        is HTTP -> copy()
        is Socks -> copy()
        is Shadowsocks -> copy(parms = parms.copy())
        is VMess -> copy(parms = parms.copy())
        is Trojan -> copy(parms = parms.copy())
        is VLESS -> copy(parms = parms.copy())
        is Wireguard -> copy()
        is Hysteria2 -> copy()
        else -> unsupportedProxyServerEditor()
    }
}

internal fun LazyListScope.proxyServerEditorContent(
    proxyServer: ProxyServer<*>,
) {
    when (proxyServer) {
        is HTTP -> httpProxyServer(proxyServer)
        is Socks -> socksProxyServer(proxyServer)
        is Shadowsocks -> shadowsocksProxyServer(proxyServer)
        is VMess -> vmessProxyServer(proxyServer)
        is Trojan -> trojanProxyServer(proxyServer)
        is VLESS -> vlessProxyServer(proxyServer)
        is Wireguard -> wireguardProxyServer(proxyServer)
        is Hysteria2 -> hysteria2ProxyServer(proxyServer)
        else -> proxyServer.unsupportedProxyServerEditor()
    }
}

private fun ProxyServer<*>.unsupportedProxyServerEditor(): Nothing {
    error("Unsupported proxy server editor: ${this::class.simpleName}")
}
