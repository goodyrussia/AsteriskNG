// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveLocalDnsEnabled
import features.proxy.server.model.Custom
import features.proxy.server.model.customXrayConfigProxyServerHosts
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CustomXrayConfigRewriter {
    fun rewrite(
        request: XrayConfigRequest,
        server: Custom,
    ): JsonObject {
        server.check()
        val config = parseCustomXrayConfigJsonObject(server.configJson)
        return config.overwriteAsteriskInboundDns(request, server)
    }
}

private fun JsonObject.overwriteAsteriskInboundDns(
    request: XrayConfigRequest,
    server: Custom,
): JsonObject {
    val startupProxyServerDomains =
        customXrayConfigProxyServerHosts(server.configJson).startupProxyServerHostDnsDomains()
    val dnsPlan = request.buildXrayDnsPlan(startupProxyServerDomains)
    val outboundsRewrite = rewriteCustomDnsOutbounds(
        appState = request.appState,
        enableLocalDns = request.appState.effectiveLocalDnsEnabled,
    )
    val proxyOutboundTag = checkNotNull(outboundsRewrite.proxyOutboundTag) {
        "Custom Xray config has no proxy outbound"
    }
    val routing = rewriteCustomFixedRouting(
        dnsHijackInboundTags = request.dnsHijackInboundTags,
        proxyOutboundTag = proxyOutboundTag,
    )

    return updatedWithout(setOf("fakedns", "fakeDns")) {
        put("inbounds", request.inbounds.toJsonObjectArray())
        put("dns", buildXrayDnsConfig(dnsPlan))
        putIfNotNull("fakeDns", dnsPlan.fakeDns)
        put("outbounds", outboundsRewrite.outbounds)
        put("routing", routing)
    }
}

private data class CustomOutboundsRewrite(
    val outbounds: JsonArray,
    val proxyOutboundTag: String?,
)

private fun JsonObject.rewriteCustomDnsOutbounds(
    appState: AppState,
    enableLocalDns: Boolean,
): CustomOutboundsRewrite {
    val proxyOutbounds = (arrayValue("outbounds") ?: buildJsonArray {}).withProxyOutboundTag()
    val rewrittenOutbounds = buildJsonArray {
        proxyOutbounds.outbounds.forEach { outbound ->
            when (outbound.stringValue("tag")) {
                XrayTags.DNS_OUT -> Unit
                XrayTags.DIRECT -> Unit
                XrayTags.BLOCK -> add(outbound)
                else -> add(outbound.applyFixedProxyOutboundDomainStrategy(appState))
            }
        }
        add(
            buildFreedomOutbound(
                tag = XrayTags.DIRECT,
                domainStrategy = appState.xrayDirectOutboundDomainStrategy(),
            ),
        )
        if (enableLocalDns) {
            add(
                buildSimpleOutbound(
                    tag = XrayTags.DNS_OUT,
                    protocol = XrayProtocols.DNS,
                ),
            )
        }
    }
    return CustomOutboundsRewrite(
        outbounds = rewrittenOutbounds,
        proxyOutboundTag = proxyOutbounds.proxyOutboundTag,
    )
}

private data class CustomProxyOutbounds(
    val outbounds: List<JsonObject>,
    val proxyOutboundTag: String?,
)

private fun JsonArray.withProxyOutboundTag(): CustomProxyOutbounds {
    val outbounds = mapNotNull { element -> element as? JsonObject }.toMutableList()
    var firstProxyCandidateIndex: Int? = null
    outbounds.forEachIndexed { index, outbound ->
        val tag = outbound.stringValue("tag")
        if (tag == XrayTags.PROXY) {
            return CustomProxyOutbounds(outbounds, tag)
        }
        if (tag !in XrayTags.FIXED_OUTBOUND_TAGS && firstProxyCandidateIndex == null) {
            firstProxyCandidateIndex = index
        }
    }

    val candidateIndex = firstProxyCandidateIndex ?: return CustomProxyOutbounds(outbounds, null)
    val candidate = outbounds[candidateIndex]
    val candidateTag = candidate.stringValue("tag")
    if (!candidateTag.isNullOrBlank()) {
        return CustomProxyOutbounds(outbounds, candidateTag)
    }
    outbounds[candidateIndex] = candidate.updated {
        put("tag", XrayTags.PROXY)
    }
    return CustomProxyOutbounds(outbounds, XrayTags.PROXY)
}

private fun JsonObject.rewriteCustomFixedRouting(
    dnsHijackInboundTags: List<String>,
    proxyOutboundTag: String,
): JsonObject {
    val routing = objectValue("routing") ?: buildJsonObject {}
    return routing.updated {
        put("domainStrategy", FixedRoutingDomainStrategy)
        put(
            "rules",
            buildFixedXrayRoutingRules(
                proxyTarget = XrayRouteTarget(proxyOutboundTag, XrayRouteTargetKind.Outbound),
                dnsHijackInboundTags = dnsHijackInboundTags,
            ),
        )
    }
}
