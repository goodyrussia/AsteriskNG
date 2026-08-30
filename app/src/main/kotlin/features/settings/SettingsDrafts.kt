// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import engine.xray.DefaultDirectDnsServers
import engine.xray.DefaultPrimaryDnsServer
import engine.xray.DefaultSecondaryDnsServer
import engine.xray.DnsModeFast

internal data class DnsSettingsDraft(
    val mode: Int = DnsModeFast,
    val primary: String = DefaultPrimaryDnsServer,
    val secondary: String = DefaultSecondaryDnsServer,
    val directDns: String = DefaultDirectDnsServers.joinToString(", "),
    val directDnsDomains: String = "",
)

internal fun AppState.toDnsSettingsDraft(): DnsSettingsDraft {
    return DnsSettingsDraft(
        mode = dnsMode,
        primary = proxyDns.getOrNull(0) ?: DefaultPrimaryDnsServer,
        secondary = proxyDns.getOrNull(1) ?: DefaultSecondaryDnsServer,
        directDns = directDns.joinToString(", "),
        directDnsDomains = directDnsDomains.joinToString(", "),
    )
}