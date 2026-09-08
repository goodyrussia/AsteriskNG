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
    private val balancers = mutableListOf<XrayBalancerPlan>()
    private val observatorySelectors = mutableListOf<String>()
    private val burstObservatorySelectors = mutableListOf<String>()
    private val routeTargets = linkedMapOf<String, XrayRouteTarget>()
    private val addedOutboundTags = mutableSetOf<String>()
    private val dnsHostServers = mutableListOf<String>()

    fun build(selectedServer: ProxyServerState): XrayOutboundPlan {
        addRouteTarget(XrayTags.PROXY, selectedServer)
        addFixedRouteTargets()
        return XrayOutboundPlan(
            proxyOutbounds = proxyOutbounds,
            balancers = balancers,
            observatorySelectors = observatorySelectors.distinct(),
            burstObservatorySelectors = burstObservatorySelectors.distinct(),
            routeTargets = routeTargets,
            dnsHostServers = dnsHostServers.distinct(),
        )
    }

    private fun addFixedRouteTargets() {
        if (appState.effectiveLocalDnsEnabled) {
            routeTargets[XrayTags.DNS_OUT] = XrayRouteTarget(XrayTags.DNS_OUT, XrayRouteTargetKind.Outbound)
        }
        if (appState.enableFragment) {
            routeTargets[XrayTags.FRAGMENT] = XrayRouteTarget(XrayTags.FRAGMENT, XrayRouteTargetKind.Outbound)
        }
    }

    private fun addRouteTarget(tag: String, server: ProxyServerState) {
        addNormalOutbound(tag, server)
    }

    private fun addNormalOutbound(
        tag: String,
        server: ProxyServerState,
        dialerProxyTag: String? = null,
        allowFragment: Boolean = true,
    ) {
        if (tag in addedOutboundTags) return
        server.server.check()
        proxyOutbounds += XrayProxyOutboundServer(
            tag = tag,
            server = server.server,
            dialerProxyTag = dialerProxyTag,
            allowFragment = allowFragment,
        )
        dnsHostServers += server.server.serverHost()
        routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
        addedOutboundTags += tag
    }
}
