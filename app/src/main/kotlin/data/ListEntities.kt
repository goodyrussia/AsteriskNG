// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ProxyServerState
import features.logs.AndroidAppLogger

@Entity(tableName = "proxy_servers")
internal data class ProxyServerEntity(
    @PrimaryKey val id: Int,
    val position: Int,
    val serverJson: String,
) {
    fun toState(): ProxyServerState? {
        return runCatching {
            ProxyServerState(
                id = id,
                server = serverJson.decodePersistedProxyServer(),
            )
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to parse persisted proxy server id=$id", error)
        }.getOrNull()
    }

    companion object {
        fun from(position: Int, server: ProxyServerState): ProxyServerEntity {
            return ProxyServerEntity(
                id = server.id,
                position = position,
                serverJson = server.server.encodePersistedProxyServer(),
            )
        }
    }
}

@Entity(
    tableName = "proxy_app_list_selected_apps",
    indices = [Index("position")],
)
internal data class ProxyAppListSelectedAppEntity(
    @PrimaryKey val packageKey: String,
    val position: Int,
)

internal fun List<ProxyServerState>.hasSamePersistedContent(other: List<ProxyServerState>): Boolean {
    return size == other.size && zip(other).all { (previous, next) ->
        previous.id == next.id &&
            previous.server.encodePersistedProxyServer() == next.server.encodePersistedProxyServer()
    }
}

private const val LogTag = "ListEntities"
