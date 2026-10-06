// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.config

import android.content.Context
import app.AppState
import app.ProxyServerState
import app.effectiveFakeDnsEnabled
import app.effectiveLocalDnsEnabled
import engine.network.toPortOrNull
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.xrayStatsApiConfig
import engine.vpn.xrayDnsHosts
import engine.xray.XrayConfigFactory
import engine.xray.XrayConfigRequest
import engine.xray.XrayCoreLogPaths
import engine.xray.XrayStatsApiConfig
import engine.xray.buildXrayOutboundPlan
import engine.xray.prepareXrayCoreLogPaths
import engine.xray.XrayRuntimePaths
import engine.xray.xrayRootRuntimePaths
import kotlinx.serialization.json.JsonObject
import java.io.File

internal class RootConfigBuildContext(
    private val androidContext: Context,
    val appState: AppState,
    private val selectedServer: ProxyServerState,
    val xrayPaths: XrayRuntimePaths,
    private val coreLogPaths: XrayCoreLogPaths,
    private val dnsHosts: List<String>,
    val statsApiConfig: XrayStatsApiConfig? = null,
) {
    fun buildRootStartConfig(
        inbounds: List<JsonObject>,
        dnsHijackInboundTags: List<String>,
        statsApiConfig: XrayStatsApiConfig? = this.statsApiConfig,
    ): RootStartConfig {
        val xrayConfigJson = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = appState,
                selectedServer = selectedServer,
                inbounds = inbounds,
                coreLogPaths = coreLogPaths,
                dnsHosts = dnsHosts,
                dnsHijackInboundTags = dnsHijackInboundTags,
                statsApiConfig = statsApiConfig,
            ),
        )
        return appState.toRootStartConfig(
            xrayConfigJson = xrayConfigJson,
            xrayPaths = xrayPaths,
        )
    }

    fun buildRootIptablesConfig(): RootIptablesConfig {
        return RootIptablesConfig().withAppSettings(
            context = androidContext,
            appState = appState,
        )
    }

}

internal fun Context.prepareRootConfigBuildContext(request: ProxyEngineStartRequest): RootConfigBuildContext {
    val appState = request.appState
    val xrayPaths = xrayRootRuntimePaths()
    val coreLogPaths = applicationContext.prepareXrayCoreLogPaths()
    val outboundPlan = appState.buildXrayOutboundPlan(request.selectedServer)
    return RootConfigBuildContext(
        androidContext = applicationContext,
        appState = appState,
        selectedServer = request.selectedServer,
        xrayPaths = xrayPaths,
        coreLogPaths = coreLogPaths,
        dnsHosts = appState.xrayDnsHosts(outboundPlan.dnsHostServers),
        statsApiConfig = request.xrayStatsApiConfig(),
    )
}

private fun AppState.toRootStartConfig(
    xrayConfigJson: String,
    xrayPaths: XrayRuntimePaths,
): RootStartConfig {
    val dataDirectory = File(xrayPaths.dataDir)
    return RootStartConfig(
        xrayConfigJson = xrayConfigJson,
        runtimePaths = RootConfigRuntimePaths(
            coreExecutablePath = xrayPaths.xrayCorePath,
            coreConfigPath = File(dataDirectory, "config.json").absolutePath,
            matcherExecutablePath = xrayPaths.bpfMatcherPath,
            bpf2SocksExecutablePath = xrayPaths.bpf2socksPath,
            hevSocks5TunnelExecutablePath = xrayPaths.hevSocks5TunnelPath,
            workingDirectory = xrayPaths.assetsDir,
            statePath = File(dataDirectory, "asteriskd.state").absolutePath,
            logPath = File(File(dataDirectory, "logs"), "asteriskd.log").absolutePath,
        ),
        enableIpv6 = enableIpv6,
        enableRootIpv6Disabler = enableRootIpv6Disabler,
        enableLocalDns = effectiveLocalDnsEnabled,
        enableFakeDns = effectiveFakeDnsEnabled,
        enableBoot = enableRootBootScript,
    )
}

internal fun AppState.tun2SocksInternalProxyPortValue(): Int {
    return socks5ProxyPort.toPortOrNull() ?: DefaultRootTun2SocksProxyPort
}

internal fun AppState.bpf2SocksBridgePortValue(): Int {
    return bpf2SocksBridgePort.toPortOrNull() ?: RootBpf2SocksDefaultBridgePort
}
