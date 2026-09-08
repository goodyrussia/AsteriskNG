// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal data class ProxyServerListItemDisplayText(
    val title: String,
    val summary: String,
    val protocol: String,
)

internal class ProxyServerListItemTextFormatter(
    private val groupNames: Map<Int, String>,
    private val unknownGroupName: String,
) {
    fun displayOf(
        serverState: ProxyServerState,
        servers: List<ProxyServerState>,
    ): ProxyServerListItemDisplayText {
        val info = serverState.server.getInfo()
        return ProxyServerListItemDisplayText(
            title = info.remarks.ifBlank { info.protocol },
            summary = summaryOf(serverState),
            protocol = info.protocol,
        )
    }

    private fun summaryOf(serverState: ProxyServerState): String {
        return serverState.server.getInfo().address
    }
}

@Composable
internal fun rememberProxyServerListItemTextFormatter(
    groupNames: Map<Int, String>,
    unknownGroupName: String,
): ProxyServerListItemTextFormatter {
    return remember(groupNames, unknownGroupName) {
        ProxyServerListItemTextFormatter(
            groupNames = groupNames,
            unknownGroupName = unknownGroupName,
        )
    }
}
