// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import engine.xray.DefaultPrimaryDnsServer
import engine.xray.DefaultSecondaryDnsServer

internal data class DnsSettingsDraft(
    val primary: String = DefaultPrimaryDnsServer,
    val secondary: String = DefaultSecondaryDnsServer,
)

internal fun AppState.toDnsSettingsDraft(): DnsSettingsDraft {
    return DnsSettingsDraft(
        primary = proxyDns.getOrNull(0) ?: DefaultPrimaryDnsServer,
        secondary = proxyDns.getOrNull(1) ?: DefaultSecondaryDnsServer,
    )
}
