// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import app.AppState
import app.ProxyServerState
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.getCopyTextOrNull
import features.subscription.DefaultSubscriptionGroupId

internal suspend fun ProxyServerState.proxyServerCopyTextOrNull(
    context: Context,
    appState: AppState,
): String? {
    return runCatching { server.getCopyTextOrNull() }.getOrNull()
}

internal suspend fun ProxyServer<*>.proxyServerCopyTextOrNull(
    context: Context,
    appState: AppState,
    serverId: Int?,
    groupId: Int?,
): String? {
    val copyServer = ProxyServerState(
        id = serverId ?: TemporaryCopyServerId,
        server = this,
        groupId = groupId ?: DefaultSubscriptionGroupId,
    )
    return copyServer.proxyServerCopyTextOrNull(context, appState)
}

private const val TemporaryCopyServerId = -1
