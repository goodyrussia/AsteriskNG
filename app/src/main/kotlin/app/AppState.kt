// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import engine.network.DefaultFakeIpIpv4Pool
import engine.network.withoutIpv6FakeIpPool
import app.modes.ColorModeDark
import app.modes.LanguageModeSystem
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyServerListLayoutSingle
import app.modes.ProxyServerListSortDefault
import app.modes.RunModeTun2Socks
import app.modes.supportsAppListEnhancement
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
import features.settings.servicecontrol.ServiceControlSettings

data class AppState(
    val colorMode: Int = ColorModeDark,
    val languageMode: Int = LanguageModeSystem,
    val seedIndex: Int = 0,
    val hideRecentTasks: Boolean = false,
    val allowScreenshots: Boolean = true,
    val hideLauncherIcon: Boolean = false,

    val subscriptionGroups: List<SubscriptionGroupState> = DefaultSubscriptionGroups,
    val nextSubscriptionGroupId: Int = 4,
    val enableAllProxyGroup: Boolean = false,
    val enableDeletionConfirmation: Boolean = true,

    val runMode: Int = RunModeTun2Socks,
    val enableResolveProxyServerDomain: Boolean = false,

    val enableVpnLocalDns: Boolean = true,
    val localProxyPort: String = VpnDefaults.LOCAL_PROXY_PORT.toString(),
    val enableDynamicLocalProxyPort: Boolean = false,
    val localProxyListenAllInterfaces: Boolean = false,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",
    val enableVpnAppendHttpProxy: Boolean = false,
    val enableVpnHevTun: Boolean = false,

    /**
     * Starts the VPN Service by itself after boot, and restores it after an app update. VPN
     * Service mode only: ROOT modes bring their own boot entry ([enableRootBootScript]).
     */
    val enableAutoConnect: Boolean = false,
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
    val customResourceFiles: List<CustomResourceFileState> = emptyList(),
    val nextCustomResourceFileId: Int = 1,
    val enableSniffing: Boolean = false,
    val enableSniffingRouteOnly: Boolean = true,

    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,

    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,

    val enableTrafficStatsNotification: Boolean = false,
    val enableBroadcastControl: Boolean = false,
    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,
    val enableFakeDns: Boolean = false,
    val fakeDnsIpv4Pool: String = DefaultFakeIpIpv4Pool,
    val proxyDns: List<String> = VpnDefaults.PROXY_DNS_SERVERS,
    val directDns: List<String> = VpnDefaults.DIRECT_DNS_SERVERS,
    val directDnsDomains: List<String> = DefaultDirectDnsDomains,
    val enableDirectDnsForProxyServerDomains: Boolean = true,
    val dnsHosts: List<String> = emptyList(),

    val transparentProxyPort: String = RootModeEngine.DefaultTproxyPort.toString(),
    val enableRootBootScript: Boolean = false,
    val enableAppListEnhancement: Boolean = false,
    val enableRootEbpfRules: Boolean = false,
    val enableRootEbpfDirectCidrBypass: Boolean = false,
    val enableRootIpv6Disabler: Boolean = false,
    val bpf2SocksBridgePort: String = RootModeEngine.DefaultBpf2SocksBridgePort.toString(),
    val socks5ProxyPort: String = RootModeEngine.DefaultTun2SocksProxyPort.toString(),

    val serviceControl: ServiceControlSettings = ServiceControlSettings(),

    val externalInterfaces: List<String> = listOf("wlan+", "ap+", "softap+"),
    val ignoredInterfaces: List<String> = emptyList(),
    val privateAddressCidrs: List<String> = emptyList(),

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
    get() = runMode.isRootRunMode() && effectiveFakeDnsEnabled &&
        !(enableAppListEnhancement && runMode.supportsAppListEnhancement())

internal fun AppState.withCompatibleProxyAppListMode(): AppState {
    val pool = fakeDnsIpv4Pool.withoutIpv6FakeIpPool()
    val mode = if (requiresGlobalProxyAppMode) ProxyAppListModeGlobal else proxyAppListMode
    return if (pool != fakeDnsIpv4Pool || mode != proxyAppListMode) {
        copy(fakeDnsIpv4Pool = pool, proxyAppListMode = mode)
    } else {
        this
    }
}
