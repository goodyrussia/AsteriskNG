// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

internal const val DefaultPrimaryDnsServer = "localhost"
internal const val DefaultSecondaryDnsServer = "localhost"
internal const val DefaultFallbackDnsServer = DefaultPrimaryDnsServer
internal val DefaultProxyDnsServers = listOf(DefaultPrimaryDnsServer)
internal val DefaultDirectDnsServers = listOf("localhost")
