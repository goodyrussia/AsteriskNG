// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.AppState

internal class AppSettingsPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): AppState {
        val defaults = AppState()
        return defaults.copy(
            enableDeletionConfirmation = preferences.getBoolean(
                KeyEnableDeletionConfirmation,
                defaults.enableDeletionConfirmation,
            ),
            runMode = preferences.getInt(KeyRunMode, defaults.runMode),
            enableResolveProxyServerDomain = preferences.getBoolean(
                KeyEnableResolveProxyServerDomain,
                defaults.enableResolveProxyServerDomain,
            ),
            enableVpnLocalDns = preferences.getBoolean(KeyEnableVpnLocalDns, defaults.enableVpnLocalDns),
            localProxyPort = preferences.getString(KeyLocalProxyPort, defaults.localProxyPort) ?: defaults.localProxyPort,
            enableDynamicLocalProxyPort = preferences.getBoolean(
                KeyEnableDynamicLocalProxyPort,
                defaults.enableDynamicLocalProxyPort,
            ),
            localProxyListenAllInterfaces = preferences.getBoolean(
                KeyLocalProxyListenAllInterfaces,
                defaults.localProxyListenAllInterfaces,
            ),
            localProxyUsername = preferences.getString(
                KeyLocalProxyUsername,
                defaults.localProxyUsername,
            ) ?: defaults.localProxyUsername,
            localProxyPassword = preferences.getString(
                KeyLocalProxyPassword,
                defaults.localProxyPassword,
            ) ?: defaults.localProxyPassword,
            enableVpnAppendHttpProxy = preferences.getBoolean(
                KeyEnableVpnAppendHttpProxy,
                defaults.enableVpnAppendHttpProxy,
            ),
            enableVpnHevTun = preferences.getBoolean(
                KeyEnableVpnHevTun,
                defaults.enableVpnHevTun,
            ),
            tunMtu = preferences.getString(KeyTunMtu, defaults.tunMtu) ?: defaults.tunMtu,
            tunVpnDns = preferences.getString(KeyTunVpnDns, defaults.tunVpnDns) ?: defaults.tunVpnDns,
            tunIpv4Cidr = preferences.getString(KeyTunIpv4Cidr, defaults.tunIpv4Cidr) ?: defaults.tunIpv4Cidr,
            tunIpv6Cidr = preferences.getString(KeyTunIpv6Cidr, defaults.tunIpv6Cidr) ?: defaults.tunIpv6Cidr,
            nextProxyServerId = preferences.getInt(KeyNextProxyServerId, defaults.nextProxyServerId),
            selectedProxyServerId = preferences.getInt(KeySelectedProxyServerId, defaults.selectedProxyServerId),
            proxyServerListLayout = preferences.getInt(
                KeyProxyServerListLayout,
                defaults.proxyServerListLayout,
            ),
            proxyServerListSort = preferences.getInt(KeyProxyServerListSort, defaults.proxyServerListSort),
            coreLogLevel = preferences.getInt(KeyCoreLogLevel, defaults.coreLogLevel),
            enableAccessLog = preferences.getBoolean(KeyEnableAccessLog, defaults.enableAccessLog),
            enableSniffing = preferences.getBoolean(KeyEnableSniffing, defaults.enableSniffing),
            enableSniffingRouteOnly = preferences.getBoolean(
                KeyEnableSniffingRouteOnly,
                defaults.enableSniffingRouteOnly,
            ),
            enableMux = preferences.getBoolean(KeyEnableMux, defaults.enableMux),
            muxConcurrency = preferences.getString(KeyMuxConcurrency, defaults.muxConcurrency) ?: defaults.muxConcurrency,
            muxXudpConcurrency = preferences.getString(
                KeyMuxXudpConcurrency,
                defaults.muxXudpConcurrency,
            ) ?: defaults.muxXudpConcurrency,
            muxXudpProxyUdp443 = preferences.getInt(KeyMuxXudpProxyUdp443, defaults.muxXudpProxyUdp443),
            enableFragment = preferences.getBoolean(KeyEnableFragment, defaults.enableFragment),
            fragmentPackets = preferences.getString(
                KeyFragmentPackets,
                defaults.fragmentPackets,
            ) ?: defaults.fragmentPackets,
            fragmentLength = preferences.getString(
                KeyFragmentLength,
                defaults.fragmentLength,
            ) ?: defaults.fragmentLength,
            fragmentInterval = preferences.getString(
                KeyFragmentInterval,
                defaults.fragmentInterval,
            ) ?: defaults.fragmentInterval,
            enableTrafficStatsNotification = preferences.getBoolean(
                KeyEnableTrafficStatsNotification,
                defaults.enableTrafficStatsNotification,
            ),
            enableIpv6 = preferences.getBoolean(KeyEnableIpv6, defaults.enableIpv6),
            enableIpv6Prefer = preferences.getBoolean(KeyEnableIpv6Prefer, defaults.enableIpv6Prefer),
            enableFakeDns = preferences.getBoolean(KeyEnableFakeDns, defaults.enableFakeDns),
            proxyDns = preferences.getStringList(KeyProxyDns, defaults.proxyDns),
            directDns = preferences.getStringList(KeyDirectDns, defaults.directDns),
            directDnsDomains = preferences.getStringList(KeyDirectDnsDomains, defaults.directDnsDomains),
            enableDirectDnsForProxyServerDomains = preferences.getBoolean(
                KeyEnableDirectDnsForProxyServerDomains,
                defaults.enableDirectDnsForProxyServerDomains,
            ),
            dnsHosts = preferences.getStringList(KeyDnsHosts, defaults.dnsHosts),
            transparentProxyPort = preferences.getString(
                KeyTransparentProxyPort,
                defaults.transparentProxyPort,
            ) ?: defaults.transparentProxyPort,
            enableRootBootScript = preferences.getBoolean(
                KeyEnableRootBootScript,
                defaults.enableRootBootScript,
            ),
            enableRootEbpfRules = preferences.getBoolean(
                KeyEnableRootEbpfRules,
                defaults.enableRootEbpfRules,
            ),
            enableRootEbpfDirectCidrBypass = preferences.getBoolean(
                KeyEnableRootEbpfDirectCidrBypass,
                defaults.enableRootEbpfDirectCidrBypass,
            ),
            enableRootIpv6Disabler = preferences.getBoolean(
                KeyEnableRootIpv6Disabler,
                defaults.enableRootIpv6Disabler,
            ),
            bpf2SocksBridgePort = preferences.getString(
                KeyBpf2SocksBridgePort,
                defaults.bpf2SocksBridgePort,
            ) ?: defaults.bpf2SocksBridgePort,
            socks5ProxyPort = preferences.getString(
                KeySocks5ProxyPort,
                defaults.socks5ProxyPort,
            ) ?: defaults.socks5ProxyPort,
            externalInterfaces = preferences.getStringList(KeyExternalInterfaces, defaults.externalInterfaces),
            proxyAppListMode = preferences.getInt(KeyProxyAppListMode, defaults.proxyAppListMode),
        )
    }

    fun save(state: AppState) {
        preferences.edit { putAppState(state) }
    }

    private fun SharedPreferences.Editor.putAppState(state: AppState): SharedPreferences.Editor {
        return putBoolean(KeyEnableDeletionConfirmation, state.enableDeletionConfirmation)
            .putInt(KeyRunMode, state.runMode)
            .putBoolean(KeyEnableResolveProxyServerDomain, state.enableResolveProxyServerDomain)
            .putBoolean(KeyEnableVpnLocalDns, state.enableVpnLocalDns)
            .putString(KeyLocalProxyPort, state.localProxyPort)
            .putBoolean(KeyEnableDynamicLocalProxyPort, state.enableDynamicLocalProxyPort)
            .putBoolean(KeyLocalProxyListenAllInterfaces, state.localProxyListenAllInterfaces)
            .putString(KeyLocalProxyUsername, state.localProxyUsername)
            .putString(KeyLocalProxyPassword, state.localProxyPassword)
            .putBoolean(KeyEnableVpnAppendHttpProxy, state.enableVpnAppendHttpProxy)
            .putBoolean(KeyEnableVpnHevTun, state.enableVpnHevTun)
            .putString(KeyTunMtu, state.tunMtu)
            .putString(KeyTunVpnDns, state.tunVpnDns)
            .putString(KeyTunIpv4Cidr, state.tunIpv4Cidr)
            .putString(KeyTunIpv6Cidr, state.tunIpv6Cidr)
            .putInt(KeyNextProxyServerId, state.nextProxyServerId)
            .putInt(KeySelectedProxyServerId, state.selectedProxyServerId)
            .putInt(KeyProxyServerListLayout, state.proxyServerListLayout)
            .putInt(KeyProxyServerListSort, state.proxyServerListSort)
            .putInt(KeyCoreLogLevel, state.coreLogLevel)
            .putBoolean(KeyEnableAccessLog, state.enableAccessLog)
            .putBoolean(KeyEnableSniffing, state.enableSniffing)
            .putBoolean(KeyEnableSniffingRouteOnly, state.enableSniffingRouteOnly)
            .putBoolean(KeyEnableMux, state.enableMux)
            .putString(KeyMuxConcurrency, state.muxConcurrency)
            .putString(KeyMuxXudpConcurrency, state.muxXudpConcurrency)
            .putInt(KeyMuxXudpProxyUdp443, state.muxXudpProxyUdp443)
            .putBoolean(KeyEnableFragment, state.enableFragment)
            .putString(KeyFragmentPackets, state.fragmentPackets)
            .putString(KeyFragmentLength, state.fragmentLength)
            .putString(KeyFragmentInterval, state.fragmentInterval)
            .putBoolean(KeyEnableTrafficStatsNotification, state.enableTrafficStatsNotification)
            .putBoolean(KeyEnableIpv6, state.enableIpv6)
            .putBoolean(KeyEnableIpv6Prefer, state.enableIpv6Prefer)
            .putBoolean(KeyEnableFakeDns, state.enableFakeDns)
            .putStringList(KeyProxyDns, state.proxyDns)
            .putStringList(KeyDirectDns, state.directDns)
            .putStringList(KeyDirectDnsDomains, state.directDnsDomains)
            .putBoolean(KeyEnableDirectDnsForProxyServerDomains, state.enableDirectDnsForProxyServerDomains)
            .putStringList(KeyDnsHosts, state.dnsHosts)
            .putString(KeyTransparentProxyPort, state.transparentProxyPort)
            .putBoolean(KeyEnableRootBootScript, state.enableRootBootScript)
            .putBoolean(KeyEnableRootEbpfRules, state.enableRootEbpfRules)
            .putBoolean(KeyEnableRootEbpfDirectCidrBypass, state.enableRootEbpfDirectCidrBypass)
            .putBoolean(KeyEnableRootIpv6Disabler, state.enableRootIpv6Disabler)
            .putString(KeyBpf2SocksBridgePort, state.bpf2SocksBridgePort)
            .putString(KeySocks5ProxyPort, state.socks5ProxyPort)
            .putStringList(KeyExternalInterfaces, state.externalInterfaces)
            .putInt(KeyProxyAppListMode, state.proxyAppListMode)
    }

    private fun SharedPreferences.getStringList(key: String, defaultValue: List<String>): List<String> {
        return getString(key, null)?.let(StringListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putStringList(
        key: String,
        values: List<String>,
    ): SharedPreferences.Editor {
        return putString(key, StringListJson.encode(values))
    }
}

private const val PreferencesName = "asteriskng_settings"
private const val KeyEnableDeletionConfirmation = "enable_deletion_confirmation"
private const val KeyRunMode = "run_mode"
private const val KeyEnableResolveProxyServerDomain = "enable_resolve_proxy_server_domain"
private const val KeyEnableVpnLocalDns = "enable_vpn_local_dns"
private const val KeyLocalProxyPort = "local_proxy_port"
private const val KeyEnableDynamicLocalProxyPort = "enable_dynamic_local_proxy_port"
private const val KeyLocalProxyListenAllInterfaces = "local_proxy_listen_all_interfaces"
private const val KeyLocalProxyUsername = "local_proxy_username"
private const val KeyLocalProxyPassword = "local_proxy_password"
private const val KeyEnableVpnAppendHttpProxy = "enable_vpn_append_http_proxy"
private const val KeyEnableVpnHevTun = "enable_vpn_hev_tun"
private const val KeyTunMtu = "tun_mtu"
private const val KeyTunVpnDns = "tun_vpn_dns"
private const val KeyTunIpv4Cidr = "tun_ipv4_cidr"
private const val KeyTunIpv6Cidr = "tun_ipv6_cidr"
private const val KeyNextProxyServerId = "next_proxy_server_id"
private const val KeySelectedProxyServerId = "selected_proxy_server_id"
private const val KeyProxyServerListLayout = "proxy_server_list_layout"
private const val KeyProxyServerListSort = "proxy_server_list_sort"
private const val KeyCoreLogLevel = "core_log_level"
private const val KeyEnableAccessLog = "enable_access_log"
private const val KeyEnableSniffing = "enable_sniffing"
private const val KeyEnableSniffingRouteOnly = "enable_sniffing_route_only"
private const val KeyEnableMux = "enable_mux"
private const val KeyMuxConcurrency = "mux_concurrency"
private const val KeyMuxXudpConcurrency = "mux_xudp_concurrency"
private const val KeyMuxXudpProxyUdp443 = "mux_xudp_proxy_udp_443"
private const val KeyEnableFragment = "enable_fragment"
private const val KeyFragmentPackets = "fragment_packets"
private const val KeyFragmentLength = "fragment_length"
private const val KeyFragmentInterval = "fragment_interval"
private const val KeyEnableTrafficStatsNotification = "enable_traffic_stats_notification"
private const val KeyEnableIpv6 = "enable_ipv6"
private const val KeyEnableIpv6Prefer = "enable_ipv6_prefer"
private const val KeyEnableFakeDns = "enable_fake_dns"
private const val KeyProxyDns = "proxy_dns"
private const val KeyDirectDns = "direct_dns"
private const val KeyDirectDnsDomains = "direct_dns_domains"
private const val KeyEnableDirectDnsForProxyServerDomains = "enable_direct_dns_for_proxy_server_domains"
private const val KeyDnsHosts = "dns_hosts"
private const val KeyTransparentProxyPort = "transparent_proxy_port"
private const val KeyEnableRootBootScript = "enable_root_boot_script"
private const val KeyEnableRootEbpfRules = "enable_root_ebpf_rules"
private const val KeyEnableRootEbpfDirectCidrBypass = "enable_root_ebpf_direct_cidr_bypass"
private const val KeyEnableRootIpv6Disabler = "enable_root_ipv6_disabler"
private const val KeyBpf2SocksBridgePort = "bpf2socks_bridge_port"
private const val KeySocks5ProxyPort = "socks5_proxy_port"
private const val KeyExternalInterfaces = "external_interfaces"
private const val KeyProxyAppListMode = "proxy_app_list_mode"
