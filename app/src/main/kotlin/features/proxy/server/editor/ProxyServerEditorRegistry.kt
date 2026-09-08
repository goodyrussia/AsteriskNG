// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.R
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Ssh
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import androidx.compose.ui.res.stringResource

internal data class ProxyServerEditorOptions(
    val groupOptions: List<ProxyServerEditorGroupOption>,
    val memberOptions: List<ProxyServerEditorMemberOption>,
)

internal fun ProxyServer<*>.editableCopy(): ProxyServer<*> {
    return when (this) {
        is Socks -> copy()
        is Shadowsocks -> copy(parms = parms.copy())
        is VMess -> copy(parms = parms.copy())
        is Trojan -> copy(parms = parms.copy())
        is VLESS -> copy(parms = parms.copy())
        is Ssh -> copy()
        else -> unsupportedProxyServerEditor()
    }
}

@Composable
internal fun ProxyServer<*>.editorTitle(): String {
    return getInfo().protocol
}

internal fun LazyListScope.proxyServerEditorContent(
    proxyServer: ProxyServer<*>,
    options: ProxyServerEditorOptions,
) {
    when (proxyServer) {
        is Socks -> socksProxyServer(proxyServer)
        is Shadowsocks -> shadowsocksProxyServer(proxyServer)
        is VMess -> vmessProxyServer(proxyServer)
        is Trojan -> trojanProxyServer(proxyServer)
        is VLESS -> vlessProxyServer(proxyServer)
        is Ssh -> sshProxyServer(proxyServer)
        else -> proxyServer.unsupportedProxyServerEditor()
    }
}

private fun ProxyServer<*>.unsupportedProxyServerEditor(): Nothing {
    error("Unsupported proxy server editor: ${this::class.simpleName}")
}
