// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import android.net.ConnectivityManager
import app.AppState
import app.ProxyServerState
import engine.proxy.ProxyEngineStartRequest
import engine.xray.XrayConfigFactory
import engine.xray.XrayConfigRequest
import engine.xray.XrayCoreLogPaths
import engine.xray.XrayProtocols
import engine.xray.buildXrayOutboundPlan
import engine.xray.prepareXrayCoreLogPaths
import engine.xray.xrayDnsHosts
import features.resources.runtime.XrayResourceFilePaths
import features.resources.runtime.prepareXrayResourceFilePaths
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class RootConfigBuildContext(
    private val androidContext: Context,
    val appState: AppState,
    private val selectedServer: ProxyServerState,
    private val resourceFilePaths: XrayResourceFilePaths,
    private val coreLogPaths: XrayCoreLogPaths,
    private val dnsHosts: List<String>,
    private val deviceDnsServers: List<String>,
) {
    fun buildRootStartConfig(
        inbounds: List<JsonObject>,
        dnsHijackInboundTags: List<String>,
    ): RootStartConfig {
        val xrayConfigJson = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = appState,
                selectedServer = selectedServer,
                inbounds = inbounds,
                coreLogPaths = coreLogPaths,
                dnsHosts = dnsHosts,
                dnsHijackInboundTags = dnsHijackInboundTags,
                deviceDnsServers = deviceDnsServers,
            ),
        )
        return appState.toRootStartConfig(
            xrayConfigJson = xrayConfigJson,
            resourceFilePaths = resourceFilePaths,
            runtimeLayout = resourceFilePaths.toRootRuntimeLayout(),
            coreLogPaths = coreLogPaths,
        )
    }

    fun buildRootIptablesConfig(
        base: RootIptablesConfig,
        ignoredLocalInterfaceNames: Set<String>,
    ): RootIptablesConfig {
        return base.withAppSettings(
            context = androidContext,
            appState = appState,
            ignoredLocalInterfaceNames = ignoredLocalInterfaceNames,
        )
    }
}

internal fun Context.prepareRootConfigBuildContext(request: ProxyEngineStartRequest): RootConfigBuildContext {
    val appState = request.appState
    val resourceFilePaths = prepareXrayResourceFilePaths()
    val coreLogPaths = prepareXrayCoreLogPaths()
    val outboundPlan = appState.buildXrayOutboundPlan(request.selectedServer)
    return RootConfigBuildContext(
        androidContext = applicationContext,
        appState = appState,
        selectedServer = request.selectedServer,
        resourceFilePaths = resourceFilePaths,
        coreLogPaths = coreLogPaths,
        dnsHosts = appState.xrayDnsHosts(outboundPlan.dnsHostServers),
        deviceDnsServers = applicationContext.activeNetworkDnsServers(),
    )
}

/**
 * Reads the device's real DNS servers from the active network's LinkProperties.
 * These are the resolvers the device actually uses (carrier/router), which is the
 * "device or local" set the user prefers. Used as DNS-over-TCP tunnel targets so
 * SSH content DNS is resolved from the SSH exit's network without hardcoding any
 * public resolver.
 */
private fun Context.activeNetworkDnsServers(): List<String> {
    return runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching emptyList()
        cm.activeNetwork?.let { active ->
            cm.getLinkProperties(active)
                ?.dnsServers
                ?.mapNotNull { address -> address.hostAddress?.substringBefore('%') }
                .orEmpty()
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun AppState.toRootStartConfig(
    xrayConfigJson: String,
    resourceFilePaths: XrayResourceFilePaths,
    runtimeLayout: RootRuntimeLayout,
    coreLogPaths: XrayCoreLogPaths,
): RootStartConfig {
    return RootStartConfig(
        xrayConfigJson = xrayConfigJson,
        setuidgidPath = resourceFilePaths.setuidgidPath,
        runtimeLayout = runtimeLayout,
        enableIpv6 = enableIpv6,
        enableAccessLog = enableAccessLog,
        coreLogPaths = coreLogPaths,
    )
}

internal fun AppState.buildRootSharedProxyInbounds(
    httpInboundTag: String,
): List<JsonObject> {
    return buildList {
        httpProxyPort
            .toIntOrNull()
            ?.takeIf { enableHttpProxy && it in 1..65_535 }
            ?.let { port -> add(buildRootHttpProxyInbound(httpInboundTag, port)) }
    }
}

private fun buildRootHttpProxyInbound(
    tag: String,
    port: Int,
): JsonObject {
    return buildJsonObject {
        put("tag", tag)
        put("listen", RootSharedProxyListenAddress)
        put("port", port)
        put("protocol", XrayProtocols.HTTP)
        put(
            "settings",
            buildJsonObject {
                put("allowTransparent", false)
                put("userLevel", 0)
            },
        )
    }
}
