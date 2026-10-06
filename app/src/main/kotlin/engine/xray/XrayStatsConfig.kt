// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class XrayStatsApiConfig(
    val listenAddress: String,
    val port: Int,
    val apiTag: String = XrayStatsApiTag,
)

internal fun JsonObjectBuilder.putXrayStatsApiConfig(config: XrayStatsApiConfig?) {
    if (config == null) return
    put("stats", buildJsonObject {})
    put(
        "policy",
        buildJsonObject {
            putStatsPolicySystem()
        },
    )
    put(
        "api",
        buildJsonObject {
            put("tag", config.apiTag)
            put("listen", "${config.listenAddress}:${config.port}")
            putStatsApiServices(listOf(XrayStatsServiceName))
        },
    )
}

private fun JsonObjectBuilder.putStatsPolicySystem() {
    put("system", buildJsonObject { putStatsPolicyFlags() })
}

private fun JsonObjectBuilder.putStatsPolicyFlags() {
    put("statsInboundUplink", true)
    put("statsInboundDownlink", true)
    put("statsOutboundUplink", true)
    put("statsOutboundDownlink", true)
}

private fun JsonObjectBuilder.putStatsApiServices(services: List<String>) {
    put(
        "services",
        buildJsonArray {
            services.forEach { service -> add(JsonPrimitive(service)) }
        },
    )
}

internal const val XrayStatsApiTag = "api"
private const val XrayStatsServiceName = "StatsService"
