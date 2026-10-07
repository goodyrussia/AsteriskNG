// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.publication

import android.content.Context
import engine.xray.XrayRuntimePaths
import engine.xray.xrayRuntimePaths
import java.io.File

internal data class RootRuntimeLayout(
    val configPath: String,
    val xrayCorePath: String,
    val asteriskdPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
    val hevSocks5TunnelPath: String,
    val dataDir: String,
) {
    val startupScriptPath: String
        get() = File(dataDir, "startup.sh").absolutePath

    val asteriskdConfigPath: String
        get() = File(dataDir, "asteriskd.json").absolutePath

    val asteriskdStatePath: String
        get() = File(dataDir, "asteriskd.state").absolutePath

    val logDirectoryPath: String
        get() = File(dataDir, "logs").absolutePath

    val asteriskdLogPath: String
        get() = File(logDirectoryPath, "asteriskd.log").absolutePath
}

internal fun Context.rootRuntimeLayout(): RootRuntimeLayout = xrayRuntimePaths().toRootRuntimeLayout()

internal fun Context.prepareRootPublicationDirectories(): RootRuntimeLayout {
    val layout = rootRuntimeLayout()
    val dataDirectory = File(layout.dataDir)
    require(dataDirectory.exists() || dataDirectory.mkdirs()) { "Failed to create ${dataDirectory.absolutePath}" }
    require(
        dataDirectory.isDirectory && isSafeRootPublicationDirectoryIdentity(
            directoryAbsolutePath = dataDirectory.absolutePath,
            directoryCanonicalPath = dataDirectory.canonicalPath,
            filesAbsolutePath = filesDir.absolutePath,
            filesCanonicalPath = filesDir.canonicalPath,
        ),
    ) {
        "Unsafe ROOT publication directory: ${dataDirectory.absolutePath}"
    }
    val logDirectory = File(dataDirectory, "logs")
    require(logDirectory.exists() || logDirectory.mkdirs()) { "Failed to create ${logDirectory.absolutePath}" }
    require(
        logDirectory.isDirectory &&
            logDirectory.absoluteFile.parentFile == dataDirectory.absoluteFile &&
            logDirectory.canonicalFile.parentFile == dataDirectory.canonicalFile &&
            logDirectory.canonicalFile.name == "logs",
    ) {
        "Unsafe ROOT log directory: ${logDirectory.absolutePath}"
    }
    val assetsDirectory = File(dataDirectory, "assets")
    require(assetsDirectory.exists() || assetsDirectory.mkdirs()) { "Failed to create ${assetsDirectory.absolutePath}" }
    require(
        assetsDirectory.isDirectory &&
            assetsDirectory.absoluteFile.parentFile == dataDirectory.absoluteFile &&
            assetsDirectory.canonicalFile.parentFile == dataDirectory.canonicalFile &&
            assetsDirectory.canonicalFile.name == "assets",
    ) {
        "Unsafe ROOT assets directory: ${assetsDirectory.absolutePath}"
    }
    return layout
}

internal fun isSafeRootPublicationDirectoryIdentity(
    directoryAbsolutePath: String,
    directoryCanonicalPath: String,
    filesAbsolutePath: String,
    filesCanonicalPath: String,
): Boolean {
    val absoluteDirectory = File(directoryAbsolutePath)
    val canonicalDirectory = File(directoryCanonicalPath)
    return absoluteDirectory.parentFile == File(filesAbsolutePath) &&
        canonicalDirectory.parentFile == File(filesCanonicalPath) &&
        absoluteDirectory.name == canonicalDirectory.name
}

internal fun XrayRuntimePaths.toRootRuntimeLayout(): RootRuntimeLayout {
    val dir = File(dataDir)
    return RootRuntimeLayout(
        configPath = File(dir, "config.json").absolutePath,
        xrayCorePath = xrayCorePath,
        asteriskdPath = asteriskdPath,
        bpfMatcherPath = bpfMatcherPath,
        bpf2socksPath = bpf2socksPath,
        hevSocks5TunnelPath = hevSocks5TunnelPath,
        dataDir = dataDir,
    )
}
