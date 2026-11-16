// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Stable
import features.resources.ResourceFileXrayCoreName
import features.proxy.server.model.ProxyServer

@Stable
data class SubscriptionGroupState(
    val id: Int,
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val enabled: Boolean,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
)

data class ProxyServerState(
    val id: Int,
    val server: ProxyServer<*>,
    val groupId: Int,
    val latency: String = "",
)

internal fun ProxyServerState.displayTitle(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol }
}

fun ProxyServerState.proxyServerOutboundTag(): String {
    return id.toString()
}

enum class ResourceFileKind(
    val fileName: String,
) {
    XrayCore(ResourceFileXrayCoreName),
    ;

    val displayName: String
        get() = "Xray"
}

@Stable
data class ResourceFileStatus(
    val exists: Boolean = false,
    val sizeBytes: Long = 0,
    val updatedAtMillis: Long = 0,
    val isBundledCore: Boolean = false,
)

@Stable
data class CustomResourceFileState(
    val id: Int,
    val name: String,
    val url: String,
)

@Stable
data class CustomResourceFileStatus(
    val file: CustomResourceFileState,
    val status: ResourceFileStatus = ResourceFileStatus(),
)

@Stable
data class ResourceFilesStatus(
    val xrayCore: ResourceFileStatus = ResourceFileStatus(),
    val customResourceFiles: List<CustomResourceFileStatus> = emptyList(),
)

fun ResourceFilesStatus.statusOf(kind: ResourceFileKind): ResourceFileStatus {
    return when (kind) {
        ResourceFileKind.XrayCore -> xrayCore
    }
}

fun sanitizeCustomResourceFileName(value: String, fallback: String): String {
    val candidate = value
        .trim()
        .replace('\\', '/')
        .substringAfterLast('/')
        .map { char -> if (char.isResourceFileNameChar()) char else '_' }
        .joinToString("")
        .trim()
    return candidate
        .takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: fallback
}

fun customResourceFileNameOrNull(value: String): String? {
    if (value.isBlank() || value != value.trim()) return null
    if (value.any { char -> char.isWhitespace() || char == ':' }) return null
    return sanitizeCustomResourceFileName(value, fallback = "")
        .takeIf { sanitized -> sanitized == value }
}

private fun Char.isResourceFileNameChar(): Boolean {
    return code >= 32 && this != '/' && this != '\\'
}
