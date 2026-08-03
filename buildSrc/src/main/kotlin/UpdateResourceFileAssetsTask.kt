// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Downloads the legacy hev-socks5-tunnel helper for the temporary TUN2SOCKS
 * runtime. Xray itself is built reproducibly from the pinned Eichgee source
 * by GitHub Actions and placed in generated/xrayCoreJniLibs before Gradle.
 */
abstract class UpdateResourceFileAssetsTask : DefaultTask() {
    @get:Input
    abstract val hevSocks5TunnelVersion: Property<String>

    @get:OutputDirectory
    abstract val hevSocks5TunnelJniLibsDir: DirectoryProperty

    init {
        group = "resources"
        description = "Download bundled hev-socks5-tunnel assets."
    }

    @TaskAction
    fun updateAssets() {
        AndroidHevSocks5TunnelAssets.forEach { asset ->
            downloadFile(
                url = hevSocks5TunnelArchiveUrl(asset.releaseName),
                target = File(hevSocks5TunnelJniLibsDir.get().asFile, "${asset.androidAbi}/libhev-socks5-tunnel.so"),
            )
        }
    }

    private fun hevSocks5TunnelArchiveUrl(releaseName: String): String {
        val version = hevSocks5TunnelVersion.get()
        return "https://github.com/heiher/hev-socks5-tunnel/releases/download/$version/$releaseName"
    }

    private fun downloadFile(url: String, target: File) {
        target.parentFile.mkdirs()
        val tempFile = target.resolveSibling("${target.name}.tmp")
        logger.lifecycle("Downloading $url")
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw GradleException("Failed to download $url: HTTP $code")
            }
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (tempFile.length() <= 0) {
            tempFile.delete()
            throw GradleException("Downloaded file is empty: $url")
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            throw GradleException("Unable to move ${tempFile.absolutePath} to ${target.absolutePath}")
        }
        logger.lifecycle("Updated ${target.absolutePath} (${target.length()} bytes)")
    }
}

private data class HevSocks5TunnelAsset(
    val androidAbi: String,
    val releaseName: String,
)

private val AndroidHevSocks5TunnelAssets = listOf(
    HevSocks5TunnelAsset("arm64-v8a", "hev-socks5-tunnel-linux-arm64"),
)
