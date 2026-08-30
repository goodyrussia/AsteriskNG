// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.ssh

import app.AppState
import android.content.Context
import features.logs.AndroidAppLogger
import features.proxy.server.model.Ssh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

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
)

/** Builds the sshcore daemon config JSON from an SSH proxy server. */
internal fun Ssh.toSshCoreConfig(): SshCoreConfig {
    val jsonObject = buildJsonObject {
        put("listen", "127.0.0.1:$DefaultSshLocalPort")
        put("ssh_address", server.trim())
        put("ssh_port", port.toIntOrNull() ?: 22)
        put("ssh_username", username)
        put("ssh_password", password)
        put("tunnel_mode", mode)
        put("http_proxy", httpProxy.trim())
        put("http_proxy_port", httpProxyPort.toIntOrNull() ?: 8080)
        put("proxy_username", proxyUsername)
        put("proxy_password", proxyPassword)
        put("authenticate_proxy", authenticateProxy)
        put("sni", sni.trim())
        put("tls_version", tlsVersion)
        put("tls_allow_insecure", allowInsecure)
        put("payload_enabled", payloadEnabled)
        put("payload", payload)
        put("payload_split_mode", payloadSplitMode)
        put("payload_delay_ms", payloadDelayMs.toIntOrNull() ?: 300)
        put("log_level", "info")
    }
    return SshCoreConfig(
        json = SshCoreJson.encodeToString(JsonObject.serializer(), jsonObject),
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

private const val LogTag = "SshCoreConfig"
