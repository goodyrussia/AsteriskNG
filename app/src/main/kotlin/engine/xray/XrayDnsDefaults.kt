// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

internal const val DefaultPrimaryDnsServer = "8.8.8.8"
internal const val DefaultSecondaryDnsServer = "8.8.4.4"
internal const val DefaultFallbackDnsServer = DefaultPrimaryDnsServer
internal val DefaultProxyDnsServers = listOf(DefaultPrimaryDnsServer, DefaultSecondaryDnsServer)
internal val DefaultDirectDnsServers = listOf(
    "https+local://1.1.1.1/dns-query",
    "tcp+local://8.8.8.8",
)
