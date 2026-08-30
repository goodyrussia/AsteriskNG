// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import engine.network.toPortOrNull
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import features.proxy.server.model.ProxyServerConstants.PROTOCOL_SSH
import utils.decodeFlexibleBase64ToStringOrRaw
import utils.proxyUrlRemarks
import utils.userInfoOrNull
import engine.ssh.DefaultSshLocalPort

@Serializable
data class Ssh(
    var remarks: String = "",
    var server: String = "",
    var port: String = "22",
    var username: String = "",
    var password: String = "",
    var mode: String = "direct",             // direct | proxy | tls | tls_proxy
    var httpProxy: String = "",
    var httpProxyPort: String = "8080",
    var proxyUsername: String = "",
    var proxyPassword: String = "",
    var authenticateProxy: Boolean = false,
    var sni: String = "",
    var tlsVersion: String = "1.2",
    var allowInsecure: Boolean = false,
    var payloadEnabled: Boolean = false,
    var payload: String = "",
    var payloadSplitMode: String = "none",   // none | instant | delay | split | split_delay
    var payloadDelayMs: String = "300",
) : UrlProxyServer<Ssh> {

    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(
            remarks = remarks,
            address = "${server}:${port}",
            protocol = "SSH",
        )
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = ProxyServerConstants.PROTOCOL_SOCKS,
            settings = buildLegacyServerSettings(
                buildJsonObject {
                    put("address", "127.0.0.1")
                    put("port", DefaultSshLocalPort)
                },
            ),
        )
    }

    override fun parse(url: Url): Ssh {
        remarks = url.proxyUrlRemarks()
        server = url.host
        port = url.port.toString()
        url.userInfoOrNull()?.let { str ->
            val decoded = str.decodeFlexibleBase64ToStringOrRaw()
            val sep = decoded.indexOf(':')
            if (sep > -1) {
                username = decoded.substring(0, sep)
                password = decoded.substring(sep + 1)
            } else {
                username = decoded
                password = ""
            }
        }
        // Query params for mode, payload, proxy, sni, etc.
        mode = url.parameters["mode"]?.ifBlank { "direct" } ?: "direct"
        httpProxy = url.parameters["http_proxy"]?.ifBlank { "" } ?: ""
        httpProxyPort = url.parameters["http_proxy_port"]?.ifBlank { "8080" } ?: "8080"
        proxyUsername = url.parameters["proxy_user"]?.ifBlank { "" } ?: ""
        proxyPassword = url.parameters["proxy_pass"]?.ifBlank { "" } ?: ""
        authenticateProxy = url.parameters["auth_proxy"] in enabledFlags
        sni = url.parameters["sni"]?.ifBlank { "" } ?: ""
        tlsVersion = url.parameters["tls"]?.ifBlank { "1.2" } ?: "1.2"
        allowInsecure = url.parameters["allow_insecure"] in enabledFlags
        payloadEnabled = url.parameters["payload_enabled"] in enabledFlags
        payload = url.parameters["payload"]?.ifBlank { "" } ?: ""
        payloadSplitMode = url.parameters["split"]?.ifBlank { "none" } ?: "none"
        payloadDelayMs = url.parameters["delay"]?.ifBlank { "300" } ?: "300"
        return this
    }

    override fun getUrl(): String {
        val parsedPort = this@Ssh.port.toIntOrNull()
        return URLBuilder().apply {
            protocol = URLProtocol.createOrDefault(PROTOCOL_SSH)
            host = server
            parsedPort?.let { port = it }
            user = "${username}:${password}".encodeToByteArray().encodeProxyUrlBase64()
            parameters["mode"] = mode
            if (httpProxy.isNotBlank()) {
                parameters["http_proxy"] = httpProxy
                parameters["http_proxy_port"] = httpProxyPort
            }
            if (proxyUsername.isNotBlank()) {
                parameters["proxy_user"] = proxyUsername
            }
            if (proxyPassword.isNotBlank()) {
                parameters["proxy_pass"] = proxyPassword
            }
            if (authenticateProxy) {
                parameters["auth_proxy"] = "1"
            }
            if (sni.isNotBlank()) {
                parameters["sni"] = sni
            }
            if (tlsVersion != "1.2") {
                parameters["tls"] = tlsVersion
            }
            if (allowInsecure) {
                parameters["allow_insecure"] = "1"
            }
            if (payloadEnabled && payload.isNotBlank()) {
                parameters["payload_enabled"] = "1"
                parameters["payload"] = payload
            }
            if (payloadSplitMode != "none") {
                parameters["split"] = payloadSplitMode
                parameters["delay"] = payloadDelayMs
            }
            fragment = remarks
        }.buildString()
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is Ssh) proxyServerTypeMismatch()
        remarks = other.remarks
        server = other.server
        port = other.port
        username = other.username
        password = other.password
        mode = other.mode
        httpProxy = other.httpProxy
        httpProxyPort = other.httpProxyPort
        proxyUsername = other.proxyUsername
        proxyPassword = other.proxyPassword
        authenticateProxy = other.authenticateProxy
        sni = other.sni
        tlsVersion = other.tlsVersion
        allowInsecure = other.allowInsecure
        payloadEnabled = other.payloadEnabled
        payload = other.payload
        payloadSplitMode = other.payloadSplitMode
        payloadDelayMs = other.payloadDelayMs
    }

    override fun check() {
        validateCommonServerFields(remarks, server, port)
        validateRequired(username, "SSH username")
        val parsedPort = port.toPortOrNull()
        if (port.isNotBlank() && parsedPort == null) {
            proxyValidationError(ProxyServerValidationError.PortOutOfRange, 1, 65535)
        }
        val parsedProxyPort = httpProxyPort.toPortOrNull()
        if (httpProxy.isNotBlank() && parsedProxyPort == null) {
            proxyValidationError(ProxyServerValidationError.PortOutOfRange, 1, 65535)
        }
    }
}

private val SupportedSshModes = setOf("direct", "proxy", "tls", "tls_proxy")

private val enabledFlags = setOf("1", "true", "yes", "on")