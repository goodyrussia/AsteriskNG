// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyServerListLayoutSingle
import app.modes.ProxyServerListSortDefault
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import engine.root.RootModeEngine
import engine.vpn.VpnDefaults
import engine.xray.DefaultDirectDnsDomains
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency

data class AppState(
    val subscriptionGroups: List<SubscriptionGroupState> = DefaultSubscriptionGroups,
    val nextSubscriptionGroupId: Int = 4,
    val enableAllProxyGroup: Boolean = false,
    val enableDeletionConfirmation: Boolean = true,

    val runMode: Int = RunModeVpnService,
    val enableResolveProxyServerDomain: Boolean = true,

    val enableVpnLocalDns: Boolean = true,
    val localProxyPort: String = VpnDefaults.LOCAL_PROXY_PORT.toString(),
    val enableDynamicLocalProxyPort: Boolean = false,
    val localProxyListenAllInterfaces: Boolean = false,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",
    val enableVpnAppendHttpProxy: Boolean = false,
    val enableVpnHevTun: Boolean = false,
    val tunMtu: String = VpnDefaults.MTU.toString(),
    val tunVpnDns: String = VpnDefaults.IPV4_DNS,
    val tunIpv4Cidr: String = VpnDefaults.IPV4_CIDR,
    val tunIpv6Cidr: String = VpnDefaults.IPV6_CIDR,

    val proxyServers: List<ProxyServerState> = emptyList(),
    val nextProxyServerId: Int = 10,
    val selectedProxyServerId: Int = 1,
    val proxyServerListLayout: Int = ProxyServerListLayoutSingle,
    val proxyServerListSort: Int = ProxyServerListSortDefault,
    val proxyRunning: Boolean = false,

    val coreLogLevel: Int = 3,
    val enableAccessLog: Boolean = false,
    val enableSniffing: Boolean = true,
    val enableSniffingRouteOnly: Boolean = false,

    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,

    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,

    val enableTrafficStatsNotification: Boolean = false,
    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,
    val enableFakeDns: Boolean = false,
    val proxyDns: List<String> = VpnDefaults.PROXY_DNS_SERVERS,
    val directDns: List<String> = VpnDefaults.DIRECT_DNS_SERVERS,
    val directDnsDomains: List<String> = DefaultDirectDnsDomains,
    val enableDirectDnsForProxyServerDomains: Boolean = false,
    val dnsHosts: List<String> = emptyList(),

    val transparentProxyPort: String = RootModeEngine.DefaultTproxyPort.toString(),
    val enableRootBootScript: Boolean = false,
    val enableRootEbpfRules: Boolean = false,
    val enableRootEbpfDirectCidrBypass: Boolean = false,
    val enableRootIpv6Disabler: Boolean = false,
    val bpf2SocksBridgePort: String = RootModeEngine.DefaultBpf2SocksBridgePort.toString(),
    val socks5ProxyPort: String = RootModeEngine.DefaultTun2SocksProxyPort.toString(),

    val externalInterfaces: List<String> = emptyList(),

    val proxyAppListMode: Int = ProxyAppListModeGlobal,
    val proxyAppListSelectedApps: List<String> = emptyList(),
)

val AppState.effectiveLocalDnsEnabled: Boolean
    get() = enableVpnLocalDns

val AppState.rootIpv6DataPathEnabled: Boolean
    get() = enableIpv6 || (effectiveLocalDnsEnabled && !enableRootIpv6Disabler)

val AppState.effectiveFakeDnsEnabled: Boolean
    get() = effectiveLocalDnsEnabled && enableFakeDns

val AppState.requiresGlobalProxyAppMode: Boolean
    get() = runMode.isRootRunMode() && effectiveFakeDnsEnabled

internal fun AppState.withCompatibleProxyAppListMode(): AppState =
    if (requiresGlobalProxyAppMode && proxyAppListMode != ProxyAppListModeGlobal) {
        copy(proxyAppListMode = ProxyAppListModeGlobal)
    } else {
        this
    }
