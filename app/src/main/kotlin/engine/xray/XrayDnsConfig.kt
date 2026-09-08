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
    val effectiveProxyDnsServers = if (selectedServer.server is Ssh) {
        sshTunneledDnsServers(proxyDnsServers, deviceDnsServers)
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

/**
 * In SSH mode the tunnel exposes a TCP-only SOCKS proxy: DNS queries cannot ride
 * as UDP (the sshcore daemon's SOCKS5 UDP path is a no-op), and the Xray
 * `localhost` resolver resolves via Go's `net.LookupIP`, which Android blocks
 * (`[::1]:53` -> SELinux EPERM). So content DNS must be tunneled as DNS-over-TCP
 * (`tcp+...`) through the SSH SOCKS outbound, resolved at the SSH exit's network.
 *
 * Priority:
 *  1. The SSH profile's own configured proxy DNS servers (tcp+).
 *  2. The device's real DNS servers (tcp+), read from ConnectivityManager —
 *     only public ones are usable, since the SSH server must be able to reach them.
 *  3. A well-known public resolver, only as a last resort.
 */
private fun sshTunneledDnsServers(
    proxyDnsServers: List<String>,
    deviceDnsServers: List<String>,
): List<String> {
    val configured = proxyDnsServers.toTrimmedNonEmptyDistinctList()
        .filter { it.isUsableTunnelDns() }
        .map { server -> server.toTcpDnsServer() }
    if (configured.isNotEmpty()) return configured

    val device = deviceDnsServers.toTrimmedNonEmptyDistinctList()
        .filter { it.isUsableTunnelDns() }
        .map { server -> server.toTcpDnsServer() }
    if (device.isNotEmpty()) return device

    return listOf("tcp://1.1.1.1", "tcp://8.8.8.8")
}

/** A tunnel-target DNS address must be a public IP the SSH server can reach. */
private fun String.isUsableTunnelDns(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed == "localhost" || trimmed == "127.0.0.1" || trimmed == "::1") {
        return false
    }
    if (!isIpAddress(trimmed)) return false
    return isPublicIpAddress(trimmed)
}

/** True only for globally routable addresses; rejects loopback/private/link-local/reserved. */
private fun isPublicIpAddress(ip: String): Boolean {
    val addr = java.net.InetAddress.getByName(ip.substringBefore('%')) ?: return false
    return !(addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress)
}

private fun String.toTcpDnsServer(): String {
    val value = trim()
    if (value.startsWith("tcp://") || value.startsWith("https://") || value.startsWith("quic://") || value.startsWith("udp://")) {
        // Already in core-accepted URL scheme form.
        return value
    }
    // Normalize any bare host / "tcp+"/"https+"/"udp+" prefix to a scheme URL
    // that the Xray v4-lineage core DNS parser recognizes. The exclave-core
    // fork's NewServer switches on url.Scheme, so a bare "tcp+1.1.1.1" fails
    // scheme detection and silently collapses to UDP classic (the DNS-through-
    // tunnel bug). "tcp://1.1.1.1" is parsed as a DNS-over-TCP remote nameserver.
    val bare = value
        .substringAfter("://")
        .removePrefix("tcp+").removePrefix("https+").removePrefix("quic+").removePrefix("udp+")
    return "tcp://$bare"
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
            startupProxyServerDomains = startupProxyServerDomains,
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
    startupProxyServerDomains: List<String>,
): JsonArray {
    return buildJsonArray {
        // Proxy hostnames must never depend on the tunnel that they start.
        // Resolve them with the device resolver, then use the tunneled/local
        // mode for ordinary application DNS according to dnsMode.
        if (startupProxyServerDomains.isNotEmpty()) {
            add(
                buildJsonObject {
                    put("address", "localhost")
                    put("domains", startupProxyServerDomains.toJsonStringArray())
                    put("skipFallback", true)
                },
            )
        }
        if (effectiveFakeDnsEnabled) {
            add(JsonPrimitive("fakedns"))
        }
        when (dnsMode) {
            DnsModeFast -> {
                // DNS through the tunnel for CDN consistency.
                // Proxy server hostnames are resolved via device DNS before
                // the tunnel starts (see startupProxyServerDomains above).
                xrayProxyDnsServers(
                    proxyDnsServers = proxyDnsServers,
                    directDnsServers = emptyList(),
                    directDnsDomains = emptyList(),
                ).forEach { server -> add(JsonPrimitive(server)) }
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
