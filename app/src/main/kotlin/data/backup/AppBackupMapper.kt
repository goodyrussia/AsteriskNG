// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.CustomResourceFileState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.modes.RunModeVpnService
import data.PersistedProxyServer
import data.decodeProxyServer
import data.toPersistedProxyServer
import features.logs.AndroidAppLogger
import features.subscription.DefaultSubscriptionGroupId

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
            subscriptionGroups = subscriptionGroups.map(SubscriptionGroupState::toBackup),
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
        enableAllProxyGroup = enableAllProxyGroup,
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
        enableResourceAutoUpdate = enableResourceAutoUpdate,
        resourceAutoUpdateInterval = resourceAutoUpdateInterval,
        resourceFileSource = resourceFileSource,
        customResourceFileGeoIpUrl = customResourceFileGeoIpUrl,
        customResourceFileGeoSiteUrl = customResourceFileGeoSiteUrl,
        customResourceFileGeoIpOnlyCnPrivateUrl = customResourceFileGeoIpOnlyCnPrivateUrl,
        customResourceFileDirectCidrIpv4Url = customResourceFileDirectCidrIpv4Url,
        customResourceFileDirectCidrIpv6Url = customResourceFileDirectCidrIpv6Url,
        customResourceFiles = customResourceFiles.map(CustomResourceFileState::toBackup),
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

private fun SubscriptionGroupState.toBackup(): AppBackupSubscriptionGroup {
    return AppBackupSubscriptionGroup(
        id = id,
        name = name,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )
}

private fun ProxyServerState.toBackup(): AppBackupProxyServer {
    val persistedServer = server.toPersistedProxyServer()
    return AppBackupProxyServer(
        id = id,
        groupId = groupId,
        protocol = persistedServer.protocol,
        payload = persistedServer.payload,
    )
}

private fun CustomResourceFileState.toBackup(): AppBackupCustomResourceFile {
    return AppBackupCustomResourceFile(
        id = id,
        name = name,
        url = url,
    )
}

private fun AppBackupData.toAppState(): AppState {
    val defaults = AppState()
    val restoredSubscriptionGroups = subscriptionGroups
        .map(AppBackupSubscriptionGroup::toState)
        .ifEmpty { defaults.subscriptionGroups }
    val groupIds = restoredSubscriptionGroups.mapTo(mutableSetOf()) { group -> group.id }
    val fallbackGroupId = restoredSubscriptionGroups.firstOrNull { group -> group.builtIn }?.id
        ?: restoredSubscriptionGroups.firstOrNull()?.id
        ?: DefaultSubscriptionGroupId
    val restoredProxyServers = proxyServers.mapNotNull { server ->
        server.toState(
            validGroupIds = groupIds,
            fallbackGroupId = fallbackGroupId,
        )
    }
    val restoredSelectedProxyServerId = settings.selectedProxyServerId
        .takeIf { serverId -> restoredProxyServers.any { server -> server.id == serverId } }
        ?: restoredProxyServers.firstOrNull()?.id
        ?: defaults.selectedProxyServerId
    val restoredCustomResourceFiles = settings.customResourceFiles.map(AppBackupCustomResourceFile::toState)

    return defaults.copy(
        subscriptionGroups = restoredSubscriptionGroups,
        nextSubscriptionGroupId = nextId(
            defaultValue = defaults.nextSubscriptionGroupId,
            ids = restoredSubscriptionGroups.map { group -> group.id },
        ),
        enableAllProxyGroup = settings.enableAllProxyGroup,
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
        enableResourceAutoUpdate = settings.enableResourceAutoUpdate,
        resourceAutoUpdateInterval = settings.resourceAutoUpdateInterval,
        resourceFileSource = settings.resourceFileSource,
        customResourceFileGeoIpUrl = settings.customResourceFileGeoIpUrl,
        customResourceFileGeoSiteUrl = settings.customResourceFileGeoSiteUrl,
        customResourceFileGeoIpOnlyCnPrivateUrl = settings.customResourceFileGeoIpOnlyCnPrivateUrl,
        customResourceFileDirectCidrIpv4Url = settings.customResourceFileDirectCidrIpv4Url,
        customResourceFileDirectCidrIpv6Url = settings.customResourceFileDirectCidrIpv6Url,
        customResourceFiles = restoredCustomResourceFiles,
        nextCustomResourceFileId = nextId(
            defaultValue = defaults.nextCustomResourceFileId,
            ids = restoredCustomResourceFiles.map { file -> file.id },
        ),
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

private fun AppBackupSubscriptionGroup.toState(): SubscriptionGroupState {
    return SubscriptionGroupState(
        id = id,
        name = name,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )
}

private fun AppBackupProxyServer.toState(
    validGroupIds: Set<Int>,
    fallbackGroupId: Int,
): ProxyServerState? {
    return runCatching {
        ProxyServerState(
            id = id,
            server = PersistedProxyServer(
                protocol = protocol,
                payload = payload,
            ).decodeProxyServer(),
            groupId = groupId.takeIf { it in validGroupIds } ?: fallbackGroupId,
        )
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to parse backup proxy server id=$id", error)
    }.getOrNull()
}

private fun AppBackupCustomResourceFile.toState(): CustomResourceFileState {
    return CustomResourceFileState(
        id = id,
        name = name,
        url = url,
    )
}

private fun nextId(defaultValue: Int, ids: List<Int>): Int {
    return maxOf(defaultValue, (ids.maxOrNull() ?: 0) + 1)
}

private const val LogTag = "AppBackupMapper"
