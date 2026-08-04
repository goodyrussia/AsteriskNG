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
): XrayRoutingPlan {
    return XrayRoutingPlan(
        domainStrategy = FixedRoutingDomainStrategy,
        rules = buildFixedXrayRoutingRules(proxyTarget, dnsHijackInboundTags),
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
): JsonArray {
    return buildJsonArray {
        buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        add(
            buildJsonObject {
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
        put("inboundTag", tags.toJsonStringArray())
        put("network", "tcp,udp")
        put("port", "53")
        put("outboundTag", XrayTags.DNS_OUT)
    }
}

internal const val FixedRoutingDomainStrategy = "AsIs"
