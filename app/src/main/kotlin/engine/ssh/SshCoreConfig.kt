// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.ssh

import app.AppState
import android.content.Context
import android.net.ConnectivityManager
import engine.network.isIpv4Address
import features.logs.AndroidAppLogger
import features.proxy.server.model.Ssh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetAddress

/**
 * Local port the sshcore SOCKS5 daemon listens on. Xray's generated SOCKS
 * outbound for SSH servers points at 127.0.0.1:DefaultSshLocalPort.
 */
const val DefaultSshLocalPort = 10_809

const val SshCoreBinaryName = "sshcore"
const val SshCoreConfigFileName = "sshcore.json"
const val SshCorePidFileName = "sshcore.pid"
const val SshCoreLogFileName = "sshcore.log"

internal val SshCoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Serialized sshcore daemon configuration. */
internal data class SshCoreConfig(
    val json: String,
    /** Human-readable resolution outcome, written into the visible daemon log. */
    val resolveLog: String,
)

/** Builds the sshcore daemon config JSON from an SSH proxy server. */
internal fun Context.toSshCoreConfig(ssh: Ssh): SshCoreConfig {
    val host = ssh.server.trim()
    // Resolve the SSH hostname through the Android (Bionic) resolver in the
    // app layer — same pattern as the Xray bootstrap. Go's pure resolver is
    // blocked on Android (raw UDP to [::1]:53 -> EPERM), so sshcore must
    // receive a literal IP and dial it directly.
    val resolvedIp = resolveSshHostIpWithRetry(host, this)
    if (resolvedIp.isNotEmpty()) {
        AndroidAppLogger.info(LogTag, "resolved SSH host $host -> $resolvedIp")
    } else {
        AndroidAppLogger.warn(LogTag, "SSH host resolution failed: $host")
    }
    val resolveLog = if (resolvedIp.isNotEmpty()) {
        "[app] SSH host resolved: $host -> $resolvedIp"
    } else {
        "[app] SSH host resolution FAILED: $host (sshcore will use Android-safe resolver)"
    }
    val jsonObject = buildJsonObject {
        put("listen", "127.0.0.1:$DefaultSshLocalPort")
        put("ssh_address", host)
        put("ssh_resolved_ip", resolvedIp)
        put("ssh_port", ssh.port.toIntOrNull() ?: 22)
        put("ssh_username", ssh.username)
        put("ssh_password", ssh.password)
        put("tunnel_mode", ssh.mode)
        put("http_proxy", ssh.httpProxy.trim())
        put("http_proxy_port", ssh.httpProxyPort.toIntOrNull() ?: 8080)
        put("proxy_username", ssh.proxyUsername)
        put("proxy_password", ssh.proxyPassword)
        put("authenticate_proxy", ssh.authenticateProxy)
        put("sni", ssh.sni.trim())
        put("tls_version", ssh.tlsVersion)
        put("tls_allow_insecure", ssh.allowInsecure)
        put("payload_enabled", ssh.payloadEnabled)
        put("payload", ssh.payload)
        put("payload_split_mode", ssh.payloadSplitMode)
        put("payload_delay_ms", ssh.payloadDelayMs.toIntOrNull() ?: 300)
        put("log_level", "info")
    }
    return SshCoreConfig(
        json = SshCoreJson.encodeToString(JsonObject.serializer(), jsonObject),
        resolveLog = resolveLog,
    )
}

/** Runtime file layout for the sshcore daemon (inside the app files dir). */
internal data class SshCoreRuntimeLayout(
    val dataDir: String,
    val binaryPath: String,
    val configPath: String,
    val pidPath: String,
    val logPath: String,
)

internal fun Context.prepareSshCoreRuntimeLayout(): SshCoreRuntimeLayout {
    val dir = File(filesDir, "xray")
    return SshCoreRuntimeLayout(
        dataDir = dir.absolutePath,
        binaryPath = File(dir, SshCoreBinaryName).absolutePath,
        configPath = File(dir, SshCoreConfigFileName).absolutePath,
        pidPath = File(dir, SshCorePidFileName).absolutePath,
        logPath = File(dir, SshCoreLogFileName).absolutePath,
    )
}

internal fun Context.writeSshCoreConfig(config: SshCoreConfig, layout: SshCoreRuntimeLayout) {
    File(layout.configPath).apply {
        parentFile?.mkdirs()
        writeText(config.json)
    }
    AndroidAppLogger.info(LogTag, "sshcore config written: ${layout.configPath}")
}

/**
 * Resolves a hostname to its first IPv4 address using the Android (Bionic)
 * resolver, which works on device (unlike Go's pure resolver). Returns an
 * empty string when the host is already an IP, is unresolvable, or is blank.
 * Retries a few times because the first attempt may race with network state.
 */
internal fun resolveSshHostIpWithRetry(host: String, context: Context): String {
    val trimmed = host.trim()
    if (trimmed.isBlank() || isIpv4Address(trimmed)) return ""
    repeat(SshHostResolveAttempts) {
        val resolved = resolveSshHostIpOnce(trimmed, context)
        if (resolved.isNotEmpty()) return resolved
        Thread.sleep(SshHostResolveRetryDelayMs)
    }
    return ""
}

private fun resolveSshHostIpOnce(host: String, context: Context): String {
    return runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val addresses = connectivity?.activeNetwork?.getAllByName(host)?.toList().orEmpty()
            .ifEmpty { InetAddress.getAllByName(host).toList() }
        val all = addresses
            .mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            .filter(String::isNotBlank)
            .distinct()
        // IPv4 first (matches the tunnel policy), then any address as fallback.
        all.firstOrNull(::isIpv4Address) ?: all.firstOrNull().orEmpty()
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to resolve SSH host: $host", error)
    }.getOrDefault("")
}

internal const val SshHostResolveAttempts = 3
internal const val SshHostResolveRetryDelayMs = 500L

private const val LogTag = "SshCoreConfig"
