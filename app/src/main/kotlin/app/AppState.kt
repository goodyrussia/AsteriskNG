// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import app.modes.ColorModeSystem
import app.modes.LanguageModeSystem
import app.modes.ProxyAppListModeGlobal
import engine.proxy.DefaultLocalProxyPort
import engine.root.DefaultRootHttpProxyPort
import engine.tproxy.DefaultTproxyPort
import engine.xray.DefaultDirectDnsDomains
import engine.xray.DefaultDirectDnsServers
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import engine.xray.DefaultProxyDnsServers
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileV2FlyGeoIpOnlyCnPrivateUrl

data class AppState(
    val colorMode: Int = ColorModeSystem,
    val languageMode: Int = LanguageModeSystem,
    val seedIndex: Int = 0,

    val subscriptionGroups: List<SubscriptionGroupState> = DefaultSubscriptionGroups,
    val nextSubscriptionGroupId: Int = 4,
    val enableAllProxyGroup: Boolean = false,

    val enableResolveProxyServerDomain: Boolean = true,

    val localProxyPort: String = DefaultLocalProxyPort.toString(),
    val enableDynamicLocalProxyPort: Boolean = false,
    val localProxyListenAllInterfaces: Boolean = false,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",

    val proxyServers: List<ProxyServerState> = emptyList(),
    val nextProxyServerId: Int = 10,
    val selectedProxyServerId: Int = 1,
    val proxyRunning: Boolean = false,

    val coreLogLevel: Int = 3,
    val enableAccessLog: Boolean = false,
    val resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
    val customResourceFileGeoIpUrl: String = ResourceFileLoyalsoldierGeoIpUrl,
    val customResourceFileGeoSiteUrl: String = ResourceFileLoyalsoldierGeoSiteUrl,
    val customResourceFileGeoIpOnlyCnPrivateUrl: String = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
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

    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,
    val enableFakeDns: Boolean = false,
    val proxyDns: List<String> = DefaultProxyDnsServers,
    val directDns: List<String> = DefaultDirectDnsServers,
    val directDnsDomains: List<String> = DefaultDirectDnsDomains,
    val enableDirectDnsForProxyServerDomains: Boolean = true,
    val dnsHosts: List<String> = emptyList(),

    val transparentProxyPort: String = DefaultTproxyPort.toString(),
    val enableRootBootScript: Boolean = false,
    val shareHotspot: Boolean = true,
    val enableHttpProxy: Boolean = false,
    val httpProxyPort: String = DefaultRootHttpProxyPort.toString(),

    val externalInterfaces: List<String> = emptyList(),
    val ignoredInterfaces: List<String> = emptyList(),
    val privateAddressCidrs: List<String> = emptyList(),

    val proxyAppListMode: Int = ProxyAppListModeGlobal,
    val proxyAppListSelectedApps: List<String> = emptyList(),
)

val AppState.effectiveLocalDnsEnabled: Boolean
    get() = true

val AppState.effectiveFakeDnsEnabled: Boolean
    get() = enableFakeDns
