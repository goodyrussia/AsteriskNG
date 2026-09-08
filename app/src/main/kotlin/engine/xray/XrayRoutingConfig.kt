// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toTrimmedNonEmptyDistinctList

internal data class XrayRoutingPlan(
    val domainStrategy: String,
    val rules: JsonArray,
    val balancers: List<JsonObject>,
)

internal fun buildXrayRoutingPlan(
    proxyTarget: XrayRouteTarget,
    balancers: List<JsonObject>,
    dnsHijackInboundTags: List<String>,
    directDnsRouting: Boolean = false,
): XrayRoutingPlan {
    return XrayRoutingPlan(
        domainStrategy = FixedRoutingDomainStrategy,
        rules = buildFixedXrayRoutingRules(proxyTarget, dnsHijackInboundTags, directDnsRouting),
        balancers = balancers,
    )
}

internal fun buildXrayRouting(plan: XrayRoutingPlan): JsonObject {
    return buildJsonObject {
        put("domainStrategy", plan.domainStrategy)
        put("rules", plan.rules)
        if (plan.balancers.isNotEmpty()) {
            put("balancers", plan.balancers.toJsonObjectArray())
        }
    }
}

internal fun buildFixedXrayRoutingRules(
    proxyTarget: XrayRouteTarget,
    dnsHijackInboundTags: List<String>,
    directDnsRouting: Boolean = false,
): JsonArray {
    return buildJsonArray {
        buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        // Direct-DNS fallback rule: device DNS servers tagged dns-direct are
        // routed to the direct (freedom) outbound, so they always resolve
        // even when the tunnel or server egress is unreachable.
        if (directDnsRouting) {
            add(
                buildJsonObject {
                    put("type", "field")
                    put("inboundTag", listOf(XrayTags.DIRECT_DNS).toJsonStringArray())
                    put("network", "tcp,udp")
                    put("outboundTag", XrayTags.DIRECT)
                },
            )
        }
        add(
            buildJsonObject {
                put("type", "field")
                proxyTarget.applyTo(this)
                put("network", "tcp,udp")
            },
        )
    }
}

internal fun buildXrayDnsHijackRule(inboundTags: List<String>): JsonObject? {
    val tags = inboundTags.toTrimmedNonEmptyDistinctList()
    if (tags.isEmpty()) return null
    return buildJsonObject {
        put("type", "field")
        put("inboundTag", tags.toJsonStringArray())
        put("network", "tcp,udp")
        put("port", "53")
        put("outboundTag", XrayTags.DNS_OUT)
    }
}

internal const val FixedRoutingDomainStrategy = "AsIs"
