// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import android.content.SharedPreferences
import app.AppState
import app.CustomResourceFileState
import androidx.core.content.edit
import engine.network.isIpv4Address

internal class AppSettingsPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): AppState {
        val defaults = AppState()
        val customResourceFiles = preferences.getCustomResourceFileList(
            KeyCustomResourceFiles,
            defaults.customResourceFiles,
        )
        val nextCustomResourceFileId = maxOf(
            preferences.getInt(KeyNextCustomResourceFileId, defaults.nextCustomResourceFileId),
            (customResourceFiles.maxOfOrNull { file -> file.id } ?: 0) + 1,
        )
        return defaults.copy(
            colorMode = preferences.getInt(KeyColorMode, defaults.colorMode),
            languageMode = preferences.getInt(KeyLanguageMode, defaults.languageMode),
            seedIndex = preferences.getInt(KeySeedIndex, defaults.seedIndex),
            nextSubscriptionGroupId = preferences.getInt(
                KeyNextSubscriptionGroupId,
                defaults.nextSubscriptionGroupId,
            ),
            enableAllProxyGroup = preferences.getBoolean(KeyEnableAllProxyGroup, defaults.enableAllProxyGroup),
            enableResolveProxyServerDomain = true,
            localProxyPort = defaults.localProxyPort,
            enableDynamicLocalProxyPort = false,
            localProxyListenAllInterfaces = false,
            localProxyUsername = "",
            localProxyPassword = "",
            nextProxyServerId = preferences.getInt(KeyNextProxyServerId, defaults.nextProxyServerId),
            selectedProxyServerId = preferences.getInt(KeySelectedProxyServerId, defaults.selectedProxyServerId),
            coreLogLevel = 3,
            enableAccessLog = false,
            resourceFileSource = preferences.getInt(KeyResourceFileSource, defaults.resourceFileSource),
            customResourceFileGeoIpUrl = preferences.getString(
                KeyCustomResourceFileGeoIpUrl,
                defaults.customResourceFileGeoIpUrl,
            ) ?: defaults.customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl = preferences.getString(
                KeyCustomResourceFileGeoSiteUrl,
                defaults.customResourceFileGeoSiteUrl,
            ) ?: defaults.customResourceFileGeoSiteUrl,
            customResourceFileGeoIpOnlyCnPrivateUrl = preferences.getString(
                KeyCustomResourceFileGeoIpOnlyCnPrivateUrl,
                defaults.customResourceFileGeoIpOnlyCnPrivateUrl,
            ) ?: defaults.customResourceFileGeoIpOnlyCnPrivateUrl,
            customResourceFiles = customResourceFiles,
            nextCustomResourceFileId = nextCustomResourceFileId,
            enableSniffing = false,
            enableSniffingRouteOnly = true,
            enableMux = false,
            muxConcurrency = defaults.muxConcurrency,
            muxXudpConcurrency = defaults.muxXudpConcurrency,
            muxXudpProxyUdp443 = defaults.muxXudpProxyUdp443,
            enableFragment = false,
            fragmentPackets = defaults.fragmentPackets,
            fragmentLength = defaults.fragmentLength,
            fragmentInterval = defaults.fragmentInterval,
            enableIpv6 = false,
            enableIpv6Prefer = false,
            enableFakeDns = false,
            dnsMode = preferences.getInt(KeyDnsMode, defaults.dnsMode),
            proxyDns = preferences.getSimplifiedDnsServers(defaults.proxyDns),
            directDns = defaults.directDns,
            directDnsDomains = emptyList(),
            enableDirectDnsForProxyServerDomains = true,
            dnsHosts = emptyList(),
            transparentProxyPort = defaults.transparentProxyPort,
            enableRootBootScript = preferences.getBoolean(
                KeyEnableRootBootScript,
                defaults.enableRootBootScript,
            ),
            shareHotspot = preferences.getBoolean(KeyShareHotspot, defaults.shareHotspot),
            enableHttpProxy = false,
            httpProxyPort = defaults.httpProxyPort,
            externalInterfaces = emptyList(),
            ignoredInterfaces = emptyList(),
            privateAddressCidrs = emptyList(),
            proxyAppListMode = preferences.getInt(KeyProxyAppListMode, defaults.proxyAppListMode),
        )
    }

    fun save(state: AppState) {
        preferences.edit { putAppState(state) }
    }

    private fun SharedPreferences.Editor.putAppState(state: AppState): SharedPreferences.Editor {
        return remove("run_mode")
            .remove("enable_vpn_local_dns")
            .remove("enable_vpn_append_http_proxy")
            .remove("tun_mtu")
            .remove("tun_vpn_dns")
            .remove("tun_ipv4_cidr")
            .remove("tun_ipv6_cidr")
            .remove("socks5_proxy_port")
            .remove("route_domain_strategy")
            .remove("default_route_outbound_tag")
            .remove("next_route_rule_id")
            .remove(KeyEnableResolveProxyServerDomain)
            .remove(KeyLocalProxyPort)
            .remove(KeyEnableDynamicLocalProxyPort)
            .remove(KeyLocalProxyListenAllInterfaces)
            .remove(KeyLocalProxyUsername)
            .remove(KeyLocalProxyPassword)
            .remove(KeyEnableSniffing)
            .remove(KeyEnableSniffingRouteOnly)
            .remove(KeyEnableMux)
            .remove(KeyMuxConcurrency)
            .remove(KeyMuxXudpConcurrency)
            .remove(KeyMuxXudpProxyUdp443)
            .remove(KeyEnableFragment)
            .remove(KeyFragmentPackets)
            .remove(KeyFragmentLength)
            .remove(KeyFragmentInterval)
            .remove(KeyEnableIpv6)
            .remove(KeyEnableIpv6Prefer)
            .remove(KeyEnableFakeDns)
            .remove(KeyDirectDns)
            .remove(KeyDirectDnsDomains)
            .remove(KeyEnableDirectDnsForProxyServerDomains)
            .remove(KeyDnsHosts)
            .remove(KeyTransparentProxyPort)
            .remove(KeyEnableHttpProxy)
            .remove(KeyHttpProxyPort)
            .remove(KeyExternalInterfaces)
            .remove(KeyIgnoredInterfaces)
            .remove(KeyPrivateAddressCidrs)
            .remove(KeyCoreLogLevel)
            .remove(KeyEnableAccessLog)
            .putInt(KeyColorMode, state.colorMode)
            .putInt(KeyLanguageMode, state.languageMode)
            .putInt(KeySeedIndex, state.seedIndex)
            .putInt(KeyNextSubscriptionGroupId, state.nextSubscriptionGroupId)
            .putBoolean(KeyEnableAllProxyGroup, state.enableAllProxyGroup)

            .putInt(KeyNextProxyServerId, state.nextProxyServerId)
            .putInt(KeySelectedProxyServerId, state.selectedProxyServerId)

            .putInt(KeyResourceFileSource, state.resourceFileSource)
            .putString(KeyCustomResourceFileGeoIpUrl, state.customResourceFileGeoIpUrl)
            .putString(KeyCustomResourceFileGeoSiteUrl, state.customResourceFileGeoSiteUrl)
            .putString(KeyCustomResourceFileGeoIpOnlyCnPrivateUrl, state.customResourceFileGeoIpOnlyCnPrivateUrl)
            .putCustomResourceFileList(KeyCustomResourceFiles, state.customResourceFiles)
            .putInt(KeyNextCustomResourceFileId, state.nextCustomResourceFileId)
            .putStringList(KeyProxyDns, state.proxyDns)
            .putInt(KeyDnsMode, state.dnsMode)
            .putBoolean(KeyEnableRootBootScript, state.enableRootBootScript)
            .putBoolean(KeyShareHotspot, state.shareHotspot)
            .putInt(KeyProxyAppListMode, state.proxyAppListMode)
    }

    private fun SharedPreferences.getStringList(key: String, defaultValue: List<String>): List<String> {
        return getString(key, null)?.let(StringListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.getSimplifiedDnsServers(defaultValue: List<String>): List<String> {
        val saved = getStringList(KeyProxyDns, defaultValue)
        return defaultValue.indices.map { index ->
            saved.getOrNull(index)?.trim()?.takeIf(::isIpv4Address) ?: defaultValue[index]
        }
    }

    private fun SharedPreferences.Editor.putStringList(
        key: String,
        values: List<String>,
    ): SharedPreferences.Editor {
        return putString(key, StringListJson.encode(values))
    }

    private fun SharedPreferences.getCustomResourceFileList(
        key: String,
        defaultValue: List<CustomResourceFileState>,
    ): List<CustomResourceFileState> {
        return getString(key, null)?.let(CustomResourceFileListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putCustomResourceFileList(
        key: String,
        values: List<CustomResourceFileState>,
    ): SharedPreferences.Editor {
        return putString(key, CustomResourceFileListJson.encode(values))
    }
}

private const val PreferencesName = "asteriskng_settings"
private const val KeyColorMode = "color_mode"
private const val KeyLanguageMode = "language_mode"
private const val KeySeedIndex = "seed_index"
private const val KeyNextSubscriptionGroupId = "next_subscription_group_id"
private const val KeyEnableAllProxyGroup = "enable_all_proxy_group"
private const val KeyEnableResolveProxyServerDomain = "enable_resolve_proxy_server_domain"
private const val KeyLocalProxyPort = "local_proxy_port"
private const val KeyEnableDynamicLocalProxyPort = "enable_dynamic_local_proxy_port"
private const val KeyLocalProxyListenAllInterfaces = "local_proxy_listen_all_interfaces"
private const val KeyLocalProxyUsername = "local_proxy_username"
private const val KeyLocalProxyPassword = "local_proxy_password"
private const val KeyNextProxyServerId = "next_proxy_server_id"
private const val KeySelectedProxyServerId = "selected_proxy_server_id"
private const val KeyCoreLogLevel = "core_log_level"
private const val KeyEnableAccessLog = "enable_access_log"
private const val KeyResourceFileSource = "resource_file_source"
private const val KeyCustomResourceFileGeoIpUrl = "custom_resource_file_geoip_url"
private const val KeyCustomResourceFileGeoSiteUrl = "custom_resource_file_geosite_url"
private const val KeyCustomResourceFileGeoIpOnlyCnPrivateUrl = "custom_resource_file_geoip_only_cn_private_url"
private const val KeyCustomResourceFiles = "custom_resource_files"
private const val KeyNextCustomResourceFileId = "next_custom_resource_file_id"
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
private const val KeyEnableIpv6 = "enable_ipv6"
private const val KeyEnableIpv6Prefer = "enable_ipv6_prefer"
private const val KeyEnableFakeDns = "enable_fake_dns"
private const val KeyDnsMode = "dns_mode"
private const val KeyProxyDns = "proxy_dns"
private const val KeyDirectDns = "direct_dns"
private const val KeyDirectDnsDomains = "direct_dns_domains"
private const val KeyEnableDirectDnsForProxyServerDomains = "enable_direct_dns_for_proxy_server_domains"
private const val KeyDnsHosts = "dns_hosts"
private const val KeyTransparentProxyPort = "transparent_proxy_port"
private const val KeyEnableRootBootScript = "enable_root_boot_script"
private const val KeyShareHotspot = "share_hotspot"
private const val KeyEnableHttpProxy = "enable_http_proxy"
private const val KeyHttpProxyPort = "http_proxy_port"
private const val KeyExternalInterfaces = "external_interfaces"
private const val KeyIgnoredInterfaces = "ignored_interfaces"
private const val KeyPrivateAddressCidrs = "private_address_cidrs"
private const val KeyProxyAppListMode = "proxy_app_list_mode"
