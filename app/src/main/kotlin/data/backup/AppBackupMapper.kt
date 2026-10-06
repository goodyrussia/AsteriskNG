// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.ProxyServerState
import app.modes.RunModeVpnService
import data.PersistedProxyServer
import data.decodeProxyServer
import data.toPersistedProxyServer
import features.logs.AndroidAppLogger

internal fun AppState.toAppBackupFile(
    createdAtMillis: Long,
    appVersionName: String,
    appVersionCode: Int,
): AppBackupFile {
    return AppBackupFile(
        format = AppBackupFormat,
        version = CurrentAppBackupVersion,
        createdAtMillis = createdAtMillis,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        data = AppBackupData(
            settings = toBackupSettings(),
            proxyServers = proxyServers.map(ProxyServerState::toBackup),
            proxyAppListSelectedApps = proxyAppListSelectedApps,
        ),
    )
}

internal fun AppBackupFile.toRestorePreview(): AppBackupRestorePreview {
    val migrated = migrateAppBackup()
    val state = migrated.data.toAppState()
    return AppBackupRestorePreview(
        backup = migrated,
        restoredState = state,
    )
}

private fun AppState.toBackupSettings(): AppBackupSettings {
    return AppBackupSettings(
        enableDeletionConfirmation = enableDeletionConfirmation,
        enableResolveProxyServerDomain = enableResolveProxyServerDomain,
        enableVpnLocalDns = enableVpnLocalDns,
        localProxyPort = localProxyPort,
        enableDynamicLocalProxyPort = enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = localProxyListenAllInterfaces,
        localProxyUsername = localProxyUsername,
        localProxyPassword = localProxyPassword,
        enableVpnAppendHttpProxy = enableVpnAppendHttpProxy,
        enableVpnHevTun = enableVpnHevTun,
        tunMtu = tunMtu,
        tunVpnDns = tunVpnDns,
        tunIpv4Cidr = tunIpv4Cidr,
        tunIpv6Cidr = tunIpv6Cidr,
        selectedProxyServerId = selectedProxyServerId,
        proxyServerListLayout = proxyServerListLayout,
        proxyServerListSort = proxyServerListSort,
        coreLogLevel = coreLogLevel,
        enableAccessLog = enableAccessLog,
        enableSniffing = enableSniffing,
        enableSniffingRouteOnly = enableSniffingRouteOnly,
        enableMux = enableMux,
        muxConcurrency = muxConcurrency,
        muxXudpConcurrency = muxXudpConcurrency,
        muxXudpProxyUdp443 = muxXudpProxyUdp443,
        enableFragment = enableFragment,
        fragmentPackets = fragmentPackets,
        fragmentLength = fragmentLength,
        fragmentInterval = fragmentInterval,
        enableTrafficStatsNotification = enableTrafficStatsNotification,
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
        enableFakeDns = enableFakeDns,
        proxyDns = proxyDns,
        directDns = directDns,
        directDnsDomains = directDnsDomains,
        enableDirectDnsForProxyServerDomains = enableDirectDnsForProxyServerDomains,
        dnsHosts = dnsHosts,
        transparentProxyPort = transparentProxyPort,
        enableRootEbpfRules = enableRootEbpfRules,
        enableRootEbpfDirectCidrBypass = enableRootEbpfDirectCidrBypass,
        enableRootIpv6Disabler = enableRootIpv6Disabler,
        bpf2SocksBridgePort = bpf2SocksBridgePort,
        socks5ProxyPort = socks5ProxyPort,
        externalInterfaces = externalInterfaces,
        proxyAppListMode = proxyAppListMode,
    )
}

private fun ProxyServerState.toBackup(): AppBackupProxyServer {
    val persistedServer = server.toPersistedProxyServer()
    return AppBackupProxyServer(
        id = id,
        protocol = persistedServer.protocol,
        payload = persistedServer.payload,
    )
}

private fun AppBackupData.toAppState(): AppState {
    val defaults = AppState()
    val restoredProxyServers = proxyServers.mapNotNull { server -> server.toState() }
    val restoredSelectedProxyServerId = settings.selectedProxyServerId
        .takeIf { serverId -> restoredProxyServers.any { server -> server.id == serverId } }
        ?: restoredProxyServers.firstOrNull()?.id
        ?: defaults.selectedProxyServerId

    return defaults.copy(
        enableDeletionConfirmation = settings.enableDeletionConfirmation,
        runMode = RunModeVpnService,
        enableResolveProxyServerDomain = settings.enableResolveProxyServerDomain,
        enableVpnLocalDns = settings.enableVpnLocalDns,
        localProxyPort = settings.localProxyPort,
        enableDynamicLocalProxyPort = settings.enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = settings.localProxyListenAllInterfaces,
        localProxyUsername = settings.localProxyUsername,
        localProxyPassword = settings.localProxyPassword,
        enableVpnAppendHttpProxy = settings.enableVpnAppendHttpProxy,
        enableVpnHevTun = settings.enableVpnHevTun,
        tunMtu = settings.tunMtu,
        tunVpnDns = settings.tunVpnDns,
        tunIpv4Cidr = settings.tunIpv4Cidr,
        tunIpv6Cidr = settings.tunIpv6Cidr,
        proxyServers = restoredProxyServers,
        nextProxyServerId = nextId(
            defaultValue = defaults.nextProxyServerId,
            ids = restoredProxyServers.map { server -> server.id },
        ),
        selectedProxyServerId = restoredSelectedProxyServerId,
        proxyServerListLayout = settings.proxyServerListLayout,
        proxyServerListSort = settings.proxyServerListSort,
        proxyRunning = false,
        coreLogLevel = settings.coreLogLevel,
        enableAccessLog = settings.enableAccessLog,
        enableSniffing = settings.enableSniffing,
        enableSniffingRouteOnly = settings.enableSniffingRouteOnly,
        enableMux = settings.enableMux,
        muxConcurrency = settings.muxConcurrency,
        muxXudpConcurrency = settings.muxXudpConcurrency,
        muxXudpProxyUdp443 = settings.muxXudpProxyUdp443,
        enableFragment = settings.enableFragment,
        fragmentPackets = settings.fragmentPackets,
        fragmentLength = settings.fragmentLength,
        fragmentInterval = settings.fragmentInterval,
        enableTrafficStatsNotification = settings.enableTrafficStatsNotification,
        enableIpv6 = settings.enableIpv6,
        enableIpv6Prefer = settings.enableIpv6Prefer,
        enableFakeDns = settings.enableFakeDns,
        proxyDns = settings.proxyDns,
        directDns = settings.directDns,
        directDnsDomains = settings.directDnsDomains,
        enableDirectDnsForProxyServerDomains = settings.enableDirectDnsForProxyServerDomains,
        dnsHosts = settings.dnsHosts,
        transparentProxyPort = settings.transparentProxyPort,
        enableRootBootScript = false,
        enableRootEbpfRules = false,
        enableRootEbpfDirectCidrBypass = settings.enableRootEbpfDirectCidrBypass,
        enableRootIpv6Disabler = settings.enableRootIpv6Disabler,
        bpf2SocksBridgePort = settings.bpf2SocksBridgePort,
        socks5ProxyPort = settings.socks5ProxyPort,
        externalInterfaces = settings.externalInterfaces,
        proxyAppListMode = settings.proxyAppListMode,
        proxyAppListSelectedApps = proxyAppListSelectedApps,
    )
}

private fun AppBackupProxyServer.toState(): ProxyServerState? {
    return runCatching {
        ProxyServerState(
            id = id,
            server = PersistedProxyServer(
                protocol = protocol,
                payload = payload,
            ).decodeProxyServer(),
        )
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to parse backup proxy server id=$id", error)
    }.getOrNull()
}

private fun nextId(defaultValue: Int, ids: List<Int>): Int {
    return maxOf(defaultValue, (ids.maxOrNull() ?: 0) + 1)
}

private const val LogTag = "AppBackupMapper"
