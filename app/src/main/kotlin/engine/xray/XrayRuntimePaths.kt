// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import android.content.Context
import java.io.File

internal data class XrayRuntimePaths(
    val dataDir: String,
    val assetsDir: String,
    val asteriskdPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
    val xrayCorePath: String,
    val hevSocks5TunnelPath: String,
)

internal fun Context.prepareXrayRuntimePaths(): XrayRuntimePaths {
    xrayRuntimeFilesDir().mkdirs()
    File(xrayRuntimeFilesDir(), XrayAssetsDirectoryName).mkdirs()
    return xrayRuntimePaths()
}

internal fun Context.xrayRuntimePaths(): XrayRuntimePaths {
    val dataDir = xrayRuntimeFilesDir()
    return XrayRuntimePaths(
        dataDir = dataDir.absolutePath,
        assetsDir = File(dataDir, XrayAssetsDirectoryName).absolutePath,
        asteriskdPath = File(applicationInfo.nativeLibraryDir, AsteriskdLibraryName).absolutePath,
        bpfMatcherPath = File(applicationInfo.nativeLibraryDir, BpfMatcherLibraryName).absolutePath,
        bpf2socksPath = File(applicationInfo.nativeLibraryDir, Bpf2SocksLibraryName).absolutePath,
        // The runtime layout keeps the data-dir core slot path; the core executable is resolved below for ROOT.
        xrayCorePath = File(dataDir, XrayCoreSlotName).absolutePath,
        hevSocks5TunnelPath = File(applicationInfo.nativeLibraryDir, HevSocks5TunnelLibraryName).absolutePath,
    )
}

internal fun Context.xrayRootRuntimePaths(): XrayRuntimePaths {
    // VPN runs the core in-process; only ROOT configuration needs the core executable path.
    return xrayRuntimePaths().copy(
        xrayCorePath = File(applicationInfo.nativeLibraryDir, XrayCoreLibraryName).absolutePath,
    )
}

private fun Context.xrayRuntimeFilesDir(): File {
    return File(filesDir, "xray")
}

private const val AsteriskdLibraryName = "libasteriskd.so"
private const val BpfMatcherLibraryName = "libbpf-matcher.so"
private const val Bpf2SocksLibraryName = "libbpf2socks.so"
private const val XrayCoreLibraryName = "libxray.so"
private const val HevSocks5TunnelLibraryName = "libhev-socks5-tunnel-cli.so"
private const val XrayAssetsDirectoryName = "assets"
private const val XrayCoreSlotName = "xray"
