// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

internal data class XrayRouteTarget(
    val tag: String,
) {
    fun applyTo(builder: JsonObjectBuilder) {
        builder.put("outboundTag", tag)
    }
}

internal data class XrayOutboundPlan(
    val proxyOutbounds: List<XrayProxyOutboundServer>,
    val observatorySelectors: List<String>,
    val burstObservatorySelectors: List<String>,
    val routeTargets: Map<String, XrayRouteTarget>,
    val dnsHostServers: List<String>,
)
