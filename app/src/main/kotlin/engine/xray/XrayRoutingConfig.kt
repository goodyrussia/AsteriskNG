// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import utils.putTrimmedIfNotBlank
import utils.toJsonStringArray
import app.AppState
import app.effectiveLocalDnsEnabled
import engine.network.isIpv4Address
import engine.network.isIpv6Address
import features.proxy.server.model.normalizedServerHost
import features.proxy.server.model.serverHost
import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toDistinctCsvValues
import utils.toTrimmedNonEmptyDistinctList
import java.net.InetAddress

internal data class XrayRoutingPlan(
    val domainStrategy: String,
    val rules: JsonArray,
    val balancers: List<JsonObject>,
    val primaryOutboundTag: String?,
)

internal fun AppState.buildXrayRoutingPlan(
    routeTargets: Map<String, XrayRouteTarget>,
    balancers: List<JsonObject>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
    proxyDirectIps: List<String> = emptyList(),
    proxyDirectDomains: List<String> = emptyList(),
): XrayRoutingPlan {
    val domainStrategy = routeDomainStrategy.toXrayRoutingDomainStrategy()
    val defaultTarget = defaultRouteTarget(routeTargets)
    return XrayRoutingPlan(
        domainStrategy = domainStrategy,
        rules = routingRules(
            routeTargets = routeTargets,
            routeProxyDns = routeProxyDns,
            routeDirectDns = routeDirectDns,
            dnsHijackInboundTags = dnsHijackInboundTags,
            defaultTarget = defaultTarget,
            proxyDirectIps = proxyDirectIps,
            proxyDirectDomains = proxyDirectDomains,
        ),
        balancers = balancers,
        primaryOutboundTag = when (defaultTarget?.kind) {
            XrayRouteTargetKind.Outbound -> defaultTarget.tag
            XrayRouteTargetKind.Balancer -> XrayTags.DEFAULT_ROUTE_LOOPBACK
            null -> null
        },
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

private fun AppState.routingRules(
    routeTargets: Map<String, XrayRouteTarget>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
    defaultTarget: XrayRouteTarget?,
    proxyDirectIps: List<String>,
    proxyDirectDomains: List<String>,
): JsonArray {
    return buildJsonArray {
        // The core's own dial to the proxy server must never re-enter the tunnel.
        // If a dial packet ever reaches the local SOCKS/TUN inbound (e.g. the
        // native gid bypass missed it), routing it back to the proxy outbound
        // would loop forever and surface as "i/o timeout". Force it direct.
        routeTargets[XrayTags.DIRECT]?.let { target ->
            buildProxyServerDirectRule(target.tag, proxyDirectIps, proxyDirectDomains)?.let(::add)
        }
        defaultTarget
            ?.takeIf { target -> target.kind == XrayRouteTargetKind.Balancer }
            ?.let { target -> add(buildDefaultBalancerRoute(target)) }
        if (effectiveLocalDnsEnabled) {
            buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        }
        if (routeDirectDns) {
            routeTargets[XrayTags.DIRECT]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.DIRECT_DNS, target)) }
        }
        if (routeProxyDns) {
            routeTargets[XrayTags.PROXY]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.PROXY_DNS, target)) }
        }
        routeRules
            .filter(RouteRule::enabled)
            .mapNotNull { rule -> rule.toXrayRule(routeTargets) }
            .forEach(::add)
    }
}

private fun buildDefaultBalancerRoute(target: XrayRouteTarget): JsonObject {
    return buildJsonObject {
        target.applyTo(this)
        put("inboundTag", listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND).toJsonStringArray())
    }
}

private fun AppState.defaultRouteTarget(routeTargets: Map<String, XrayRouteTarget>): XrayRouteTarget? {
    val defaultOutboundTag = defaultRouteOutboundTag.trim().ifBlank { XrayTags.PROXY }
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

private fun buildProxyServerDirectRule(
    directTag: String,
    ips: List<String>,
    domains: List<String>,
): JsonObject? {
    val cleanIps = ips.toTrimmedNonEmptyDistinctList()
    val cleanDomains = domains.toTrimmedNonEmptyDistinctList()
    if (cleanIps.isEmpty() && cleanDomains.isEmpty()) return null
    return buildJsonObject {
        put("type", "field")
        if (cleanIps.isNotEmpty()) put("ip", cleanIps.toJsonStringArray())
        if (cleanDomains.isNotEmpty()) {
            put("domain", cleanDomains.map { "domain:$it" }.toJsonStringArray())
        }
        put("outboundTag", directTag)
    }
}

internal fun Iterable<XrayProxyOutboundServer>.proxyServerDirectRoutes(): Pair<List<String>, List<String>> {
    val ips = mutableListOf<String>()
    val domains = mutableListOf<String>()
    for (outbound in this) {
        val host = outbound.server.serverHost().normalizedServerHost()
        if (host.isBlank() || host.equals("localhost", ignoreCase = true)) continue
        if (isIpv4Address(host) || isIpv6Address(host)) {
            ips.add(host)
            continue
        }
        domains.add(host)
        ips.addAll(host.resolveIpAddresses())
    }
    return ips.distinct() to domains.distinct()
}

private fun String.resolveIpAddresses(): List<String> {
    return runCatching {
        InetAddress.getAllByName(this)
            .mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            .filter(String::isNotBlank)
            .distinct()
    }.getOrDefault(emptyList())
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

private fun RouteRule.toXrayRule(routeTargets: Map<String, XrayRouteTarget>): JsonObject? {
    val targetOutboundTag = outboundTag.trim().ifBlank { XrayTags.PROXY }
    val target = routeTargets[targetOutboundTag] ?: return null
    val rule = buildJsonObject {
        target.applyTo(this)
        putJsonStringArrayIfNotEmpty("domain", domain.toTrimmedNonEmptyDistinctList())
        putJsonStringArrayIfNotEmpty("ip", ip.toTrimmedNonEmptyDistinctList())
        putJsonStringArrayIfNotEmpty("process", process.toTrimmedNonEmptyDistinctList())
        putTrimmedIfNotBlank("port", port)
        putTrimmedIfNotBlank("network", network)
        putJsonStringArrayIfNotEmpty("protocol", protocol.toDistinctCsvValues())
        putTrimmedIfNotBlank("ruleTag", remarks)
    }
    return if (rule.size > 1) rule else null
}

private fun Int.toXrayRoutingDomainStrategy(): String {
    return when (this) {
        0 -> "AsIs"
        2 -> "IPOnDemand"
        else -> "IPIfNonMatch"
    }
}

private val ReservedDefaultRouteOutboundTags = setOf(
    XrayTags.DNS_OUT,
    XrayTags.FRAGMENT,
    XrayTags.DEFAULT_ROUTE_LOOPBACK,
)
