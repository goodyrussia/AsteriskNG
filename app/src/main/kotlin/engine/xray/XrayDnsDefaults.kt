// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

internal const val DefaultPrimaryDnsServer = "localhost"
internal const val DefaultSecondaryDnsServer = "localhost"
internal const val DefaultFallbackDnsServer = DefaultPrimaryDnsServer
internal val DefaultProxyDnsServers = listOf(DefaultPrimaryDnsServer)
internal val DefaultDirectDnsServers = listOf("localhost")

/**
 * Content DNS resolvers reached THROUGH the tunnel and resolved on the exit
 * node itself: the SSH/VLESS server dials its own loopback stub resolvers
 * (systemd-resolved on 127.0.0.53, dnsmasq/bind on 127.0.0.1) over the
 * direct-tcpip channel. Always reachable no matter the server's egress
 * policy, and CDN-consistent by definition (server-side upstream).
 */
internal val ServerLoopbackDnsServers = listOf("tcp://127.0.0.53", "tcp://127.0.0.1")
