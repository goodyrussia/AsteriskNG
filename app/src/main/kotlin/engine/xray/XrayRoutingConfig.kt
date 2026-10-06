// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.DefaultRouteOutboundTag
import app.effectiveLocalDnsEnabled
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toTrimmedNonEmptyDistinctList

internal data class XrayRoutingPlan(
    val domainStrategy: String,
    val rules: JsonArray,
    val primaryOutboundTag: String?,
)

internal fun AppState.buildXrayRoutingPlan(
    routeTargets: Map<String, XrayRouteTarget>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
): XrayRoutingPlan {
    val defaultTarget = defaultRouteTarget(routeTargets)
    return XrayRoutingPlan(
        domainStrategy = DefaultRoutingDomainStrategy,
        rules = routingRules(
            routeTargets = routeTargets,
            routeProxyDns = routeProxyDns,
            routeDirectDns = routeDirectDns,
            dnsHijackInboundTags = dnsHijackInboundTags,
        ),
        primaryOutboundTag = defaultTarget?.tag,
    )
}

internal fun buildXrayRouting(plan: XrayRoutingPlan): JsonObject {
    return buildJsonObject {
        put("domainStrategy", plan.domainStrategy)
        put("rules", plan.rules)
    }
}

private fun AppState.routingRules(
    routeTargets: Map<String, XrayRouteTarget>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
): JsonArray {
    return buildJsonArray {
        if (effectiveLocalDnsEnabled) {
            buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        }
        if (routeDirectDns) {
            routeTargets[XrayTags.DIRECT]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.DIRECT_DNS, target)) }
        }
        if (routeProxyDns) {
            routeTargets[XrayTags.PROXY]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.PROXY_DNS, target)) }
        }
    }
}

private fun defaultRouteTarget(routeTargets: Map<String, XrayRouteTarget>): XrayRouteTarget? {
    val defaultOutboundTag = DefaultRouteOutboundTag.trim().ifBlank { XrayTags.PROXY }
    val defaultTarget = routeTargets[defaultOutboundTag]?.takeIf {
        defaultOutboundTag !in ReservedDefaultRouteOutboundTags
    }
    return defaultTarget ?: routeTargets[XrayTags.PROXY]
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

private fun buildDnsUpstreamRoute(
    inboundTag: String,
    target: XrayRouteTarget,
): JsonObject {
    return buildJsonObject {
        target.applyTo(this)
        put("inboundTag", listOf(inboundTag).toJsonStringArray())
    }
}

private val ReservedDefaultRouteOutboundTags = setOf(
    XrayTags.DNS_OUT,
    XrayTags.FRAGMENT,
    XrayTags.DEFAULT_ROUTE_LOOPBACK,
)

// Routing is fixed internally; this is the former domain-strategy default (0 = "AsIs").
private const val DefaultRoutingDomainStrategy = "AsIs"
