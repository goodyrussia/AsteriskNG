// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveFakeDnsEnabled
import engine.network.isIpAddress
import features.proxy.server.model.Ssh
import features.proxy.server.model.normalizedServerHost
import features.proxy.server.model.serverHost
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toCsvValues
import utils.toTrimmedNonEmptyDistinctList

internal data class XrayDnsPlan(
    val servers: JsonArray,
    val queryStrategy: String,
    val tag: String,
    val hosts: JsonObject,
    val fakeDns: JsonElement?,
)

internal fun XrayConfigRequest.buildXrayDnsPlan(
    startupProxyServerDomains: List<String> = emptyList(),
): XrayDnsPlan {
    val effectiveProxyDnsServers = if (selectedServer.server is Ssh && appState.dnsMode != DnsModeFast) {
        // The SSH tunnel exposes a TCP-only SOCKS proxy; DNS must use TCP
        // transport so queries flow through the tunnel (udp-over-socks is
        // not supported by the sshcore daemon).
        proxyDnsServers.map { server -> server.toTcpDnsServer() }
    } else {
        proxyDnsServers
    }
    return appState.buildXrayDnsPlan(
        proxyDnsServers = effectiveProxyDnsServers,
        directDnsServers = directDnsServers,
        directDnsDomains = directDnsDomains,
        dnsHosts = dnsHosts,
        startupProxyServerDomains = startupProxyServerDomains,
    )
}

private fun String.toTcpDnsServer(): String {
    val value = trim()
    if (value.startsWith("tcp+") || value.startsWith("https+") || value.startsWith("quic+") || value.startsWith("udp+")) {
        return value
    }
    return "tcp+$value"
}

private fun AppState.buildXrayDnsPlan(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>,
    dnsHosts: List<String>,
    startupProxyServerDomains: List<String>,
): XrayDnsPlan {
    val effectiveDirectDnsDomains = xrayDirectDnsDomains(directDnsDomains, startupProxyServerDomains)
    return XrayDnsPlan(
        servers = xrayDnsServers(
            proxyDnsServers = proxyDnsServers,
            directDnsServers = directDnsServers,
            effectiveDirectDnsDomains = effectiveDirectDnsDomains,
        ),
        queryStrategy = if (enableIpv6) "UseIP" else "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = dnsHosts.toDnsHostsJson(),
        fakeDns = if (effectiveFakeDnsEnabled) buildXrayFakeDnsConfig() else null,
    )
}

internal fun buildXrayDnsConfig(plan: XrayDnsPlan): JsonObject {
    return buildJsonObject {
        put("servers", plan.servers)
        put("queryStrategy", plan.queryStrategy)
        put("tag", plan.tag)
        put("disableFallbackIfMatch", true)
        putIfNotEmpty("hosts", plan.hosts)
    }
}

private fun AppState.buildXrayFakeDnsConfig(): JsonElement {
    if (!enableIpv6) {
        return buildJsonObject {
            put("ipPool", XrayFakeDnsIpv4Pool)
            put("poolSize", XrayFakeDnsIpv4OnlyPoolSize)
        }
    }
    return buildJsonArray {
        add(
            buildJsonObject {
                put("ipPool", XrayFakeDnsIpv4Pool)
                put("poolSize", XrayFakeDnsDualStackPoolSize)
            },
        )
        add(
            buildJsonObject {
                put("ipPool", XrayFakeDnsIpv6Pool)
                put("poolSize", XrayFakeDnsDualStackPoolSize)
            },
        )
    }
}

internal fun AppState.xrayProxyDnsServers(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>? = null,
): List<String> {
    val sanitizedProxyDns = proxyDnsServers.toTrimmedNonEmptyDistinctList()
    if (sanitizedProxyDns.isNotEmpty()) {
        return sanitizedProxyDns
    }
    val hasDirectDns = directDnsServers.toTrimmedNonEmptyDistinctList().isNotEmpty() &&
        (directDnsDomains == null || directDnsDomains.isNotEmpty())
    return if (!hasDirectDns) {
        listOf(DefaultFallbackDnsServer)
    } else {
        emptyList()
    }
}

internal fun AppState.xrayDirectDnsServers(directDnsServers: List<String>): List<String> {
    return directDnsServers.toTrimmedNonEmptyDistinctList()
}

internal fun AppState.xrayDirectDnsDomains(
    directDnsDomains: List<String>,
    startupProxyServerDomains: List<String> = emptyList(),
): List<String> {
    return (directDnsDomains.toTrimmedNonEmptyDistinctList() + startupProxyServerDomains).distinct()
}

internal fun Iterable<XrayProxyOutboundServer>.startupProxyServerDnsDomains(): List<String> {
    return map { outbound -> outbound.server.serverHost() }.startupProxyServerHostDnsDomains()
}

internal fun Iterable<String>.startupProxyServerHostDnsDomains(): List<String> {
    return mapNotNull { host -> host.toXrayDnsDomainRule() }.distinct()
}

private fun AppState.xrayDnsServers(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    effectiveDirectDnsDomains: List<String>,
): JsonArray {
    return buildJsonArray {
        if (effectiveFakeDnsEnabled) {
            add(JsonPrimitive("fakedns"))
        }
        when (dnsMode) {
            DnsModeFast -> {
                xrayDirectDnsServers(directDnsServers).forEach { server ->
                    add(
                        buildJsonObject {
                            put("address", server)
                            put("skipFallback", true)
                        },
                    )
                }
            }
            DnsModeTunnel -> {
                xrayProxyDnsServers(
                    proxyDnsServers = proxyDnsServers,
                    directDnsServers = emptyList(),
                    directDnsDomains = emptyList(),
                ).forEach { server -> add(JsonPrimitive(server)) }
            }
            else -> {
                val effectiveDirectDnsServers = xrayDirectDnsServers(directDnsServers)
                    .takeIf { effectiveDirectDnsDomains.isNotEmpty() }
                    .orEmpty()
                effectiveDirectDnsServers.forEach { server ->
                    add(
                        buildJsonObject {
                            put("address", server)
                            put("domains", effectiveDirectDnsDomains.toJsonStringArray())
                            put("skipFallback", true)
                            put("tag", XrayTags.DIRECT_DNS)
                        },
                    )
                }
                xrayProxyDnsServers(
                    proxyDnsServers = proxyDnsServers,
                    directDnsServers = directDnsServers,
                    directDnsDomains = effectiveDirectDnsDomains,
                ).forEach { server -> add(JsonPrimitive(server)) }
            }
        }
    }
}

private fun String.toXrayDnsDomainRule(): String? {
    val host = normalizedServerHost()
    if (host.isBlank() || host.equals("localhost", ignoreCase = true) || isIpAddress(host)) {
        return null
    }
    return "domain:$host"
}

private fun List<String>.toDnsHostsJson(): JsonObject {
    return buildJsonObject {
        forEach { entry ->
            val separatorIndex = entry.indexOf(':')
            if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) {
                return@forEach
            }
            val domain = entry.substring(0, separatorIndex).trim()
            val addresses = entry.substring(separatorIndex + 1)
                .toCsvValues()
                .mapNotNull { address -> address.trim('[', ']').takeIf(String::isNotEmpty) }
            if (domain.isNotEmpty() && addresses.isNotEmpty()) {
                put(
                    domain,
                    if (addresses.size == 1) JsonPrimitive(addresses.first()) else addresses.toJsonStringArray(),
                )
            }
        }
    }
}
