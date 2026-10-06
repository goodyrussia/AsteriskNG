// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.effectiveLocalDnsEnabled
import features.proxy.server.model.serverHost

internal fun AppState.buildXrayOutboundPlan(selectedServer: ProxyServerState): XrayOutboundPlan {
    return XrayOutboundPlanner(this).build(selectedServer)
}

private class XrayOutboundPlanner(
    private val appState: AppState,
) {
    private val proxyOutbounds = mutableListOf<XrayProxyOutboundServer>()
    private val routeTargets = linkedMapOf<String, XrayRouteTarget>()
    private val dnsHostServers = mutableListOf<String>()

    fun build(selectedServer: ProxyServerState): XrayOutboundPlan {
        addNormalOutbound(XrayTags.PROXY, selectedServer)
        addFixedRouteTargets()
        return XrayOutboundPlan(
            proxyOutbounds = proxyOutbounds,
            observatorySelectors = emptyList(),
            burstObservatorySelectors = emptyList(),
            routeTargets = routeTargets,
            dnsHostServers = dnsHostServers.distinct(),
        )
    }

    private fun addFixedRouteTargets() {
        routeTargets[XrayTags.DIRECT] = XrayRouteTarget(XrayTags.DIRECT)
        routeTargets[XrayTags.BLOCK] = XrayRouteTarget(XrayTags.BLOCK)
        if (appState.effectiveLocalDnsEnabled) {
            routeTargets[XrayTags.DNS_OUT] = XrayRouteTarget(XrayTags.DNS_OUT)
        }
        if (appState.enableFragment) {
            routeTargets[XrayTags.FRAGMENT] = XrayRouteTarget(XrayTags.FRAGMENT)
        }
    }

    private fun addNormalOutbound(tag: String, server: ProxyServerState) {
        proxyOutbounds += XrayProxyOutboundServer(
            tag = tag,
            server = server.server,
        )
        dnsHostServers += server.server.serverHost()
        routeTargets[tag] = XrayRouteTarget(tag)
    }
}
