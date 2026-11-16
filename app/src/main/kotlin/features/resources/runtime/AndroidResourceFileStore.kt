// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.runtime.writeResourceAtomically as writeAtomically

import android.content.Context
import android.net.Uri
import app.AppState
import app.CustomResourceFileState
import app.CustomResourceFileStatus
import app.ResourceFileKind
import app.ResourceFileStatus
import app.ResourceFilesStatus
import features.resources.isSupportedCustomResourceName
import features.resources.isSupportedResourceName
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipInputStream

internal class AndroidResourceFileStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    val dataDir: File = appContext.xrayResourceFilesDir()
    private val assetDirectory = ResourceAssetDirectory(dataDir)
    val assetsDir: File = assetDirectory.assetsDir

    fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return currentStatus(customResourceFiles)
    }

    fun currentStatus(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return ResourceFilesStatus(
            xrayCore = effectiveXrayCoreFile().let { target ->
                target.toStatus(ResourceFileKind.XrayCore).copy(
                    isBundledCore = target != file(ResourceFileKind.XrayCore),
                )
            },
            customResourceFiles = scanCustomResources(customResourceFiles).map { customFile ->
                CustomResourceFileStatus(
                    file = customFile,
                    status = file(customFile).toStatus(),
                )
            },
        )
    }

    fun file(kind: ResourceFileKind): File {
        return if (kind == ResourceFileKind.XrayCore) File(dataDir, kind.fileName)
        else assetDirectory.file(kind.fileName)
    }

    fun file(customFile: CustomResourceFileState): File {
        require(isSupportedCustomResourceName(customFile.name)) { "Unsupported resource: ${customFile.name}" }
        require(ResourceFileKind.entries.none { it.fileName == customFile.name }) {
            "Reserved resource name: ${customFile.name}"
        }
        return assetDirectory.file(customFile.name)
    }

    fun migrateRegistered(customResourceFiles: List<CustomResourceFileState>) {
        assetDirectory.migrateRegistered(
            ResourceFileKind.entries.filterNot { it == ResourceFileKind.XrayCore }.map { it.fileName } +
                customResourceFiles.map { it.name },
            marker = File(appContext.noBackupFilesDir, "resource-assets-migration-v1"),
        )
    }

    fun scanCustomResources(customResourceFiles: List<CustomResourceFileState>): List<CustomResourceFileState> {
        val diskFiles = assetDirectory.scan(::isSupportedResourceName)
        val builtInNames = ResourceFileKind.entries.map { it.fileName }.toSet()
        val registered = customResourceFiles.filter {
            isSupportedCustomResourceName(it.name) && it.name !in builtInNames
        }.distinctBy { it.name }
        val knownNames = registered.map { it.name }.toSet() + ResourceFileKind.entries.map { it.fileName }
        var nextId = (customResourceFiles.maxOfOrNull { it.id } ?: 0) + 1
        return registered + diskFiles.filter { it.name !in knownNames }.map {
            CustomResourceFileState(id = nextId++, name = it.name, url = "")
        }
    }

    fun hasCustomXrayCore(): Boolean {
        return file(ResourceFileKind.XrayCore).coreBinaryOwnerUidOrNull() != null
    }

    fun effectiveXrayCoreFile(): File {
        // Only a missing custom Core falls back; invalid uploads remain visible as errors.
        return if (hasCustomXrayCore()) file(ResourceFileKind.XrayCore)
        else File(appContext.applicationInfo.nativeLibraryDir, XrayCoreLibraryName)
    }

    fun installInitialXrayCoreCandidate(candidate: File): Boolean {
        return publishCoreBinaryCandidate(candidate, file(ResourceFileKind.XrayCore), replaceExisting = false)
    }

    fun replaceXrayCoreCandidate(candidate: File) {
        publishCoreBinaryCandidate(candidate, file(ResourceFileKind.XrayCore), replaceExisting = true)
    }

    fun stageXrayCoreCandidate(uri: Uri): File {
        val uploaded = appContext.contentResolver.openInputStream(uri)?.use(::writeXrayCoreCandidate)
            ?: throw FileNotFoundException(uri.toString())
        val extracted = createXrayCoreCandidateFile()
        val found = runCatching {
            ZipInputStream(uploaded.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.substringAfterLast('/') == "xray") {
                        extracted.outputStream().use { output ->
                            zip.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                        return@runCatching true
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                false
            }
        }.getOrDefault(false)
        return if (found) {
            uploaded.delete()
            extracted
        } else {
            extracted.delete()
            uploaded
        }
    }

    private fun writeXrayCoreCandidate(input: java.io.InputStream): File {
        val candidate = createXrayCoreCandidateFile()
        try {
            candidate.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
            return candidate
        } catch (error: Throwable) {
            candidate.delete()
            throw error
        }
    }

    private fun createXrayCoreCandidateFile(): File {
        require(appContext.cacheDir.exists() || appContext.cacheDir.mkdirs())
        return File.createTempFile("xray-core-", ".candidate", appContext.cacheDir)
    }

    fun replaceCustom(customFile: CustomResourceFileState, uri: Uri) {
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        dataDir.mkdirs()
        val replaceTempFile = assetDirectory.createCandidate("replace-")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        replaceFile(replaceTempFile, target)
    }

    fun applyPermissions(kind: ResourceFileKind) {
        kind.applyPermissions(file(kind))
    }

    fun deleteCustom(customFile: CustomResourceFileState) {
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        target.delete()
    }

    fun renameCustom(previousFile: CustomResourceFileState, customFile: CustomResourceFileState) {
        val source = file(previousFile)
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == source.name || kind.fileName == target.name }) return
        if (source.absolutePath == target.absolutePath) return
        if (!source.isFile) return

        dataDir.mkdirs()
        check(!target.exists()) { "Resource already exists: ${target.name}" }
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                writeAtomically(target) { output -> input.copyTo(output) }
            }
            source.delete()
        }
    }

    fun preparePaths(): XrayResourceFilePaths {
        dataDir.mkdirs()
        return currentPaths()
    }

    fun currentPaths(): XrayResourceFilePaths {
        return XrayResourceFilePaths(
            dataDir = dataDir.absolutePath,
            assetsDir = assetsDir.absolutePath,
            asteriskdPath = File(appContext.applicationInfo.nativeLibraryDir, AsteriskdLibraryName).absolutePath,
            bpfMatcherPath = File(appContext.applicationInfo.nativeLibraryDir, BpfMatcherLibraryName).absolutePath,
            bpf2socksPath = File(appContext.applicationInfo.nativeLibraryDir, Bpf2SocksLibraryName).absolutePath,
            xrayCorePath = file(ResourceFileKind.XrayCore).absolutePath,
            hevSocks5TunnelPath = File(appContext.applicationInfo.nativeLibraryDir, HevSocks5TunnelLibraryName).absolutePath,
        )
    }
}

internal data class XrayResourceFilePaths(
    val dataDir: String,
    val assetsDir: String,
    val asteriskdPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
    val xrayCorePath: String,
    val hevSocks5TunnelPath: String,
)

internal fun Context.synchronizeResourceAssets(state: AppState): AppState {
    val store = AndroidResourceFileStore(this)
    store.migrateRegistered(state.customResourceFiles)
    return state.withScannedResourceFiles(store.scanCustomResources(state.customResourceFiles))
}

internal fun AppState.withScannedResourceFiles(files: List<CustomResourceFileState>): AppState = copy(
    customResourceFiles = files,
    nextCustomResourceFileId = maxOf(nextCustomResourceFileId, (files.maxOfOrNull { it.id } ?: 0) + 1),
)

internal fun Context.xrayResourceFilesDir(): File {
    return File(filesDir, "xray")
}

internal fun Context.prepareXrayResourceFilePaths(): XrayResourceFilePaths {
    return AndroidResourceFileStore(this).preparePaths()
}

internal fun Context.xrayResourceFilePaths(): XrayResourceFilePaths {
    return AndroidResourceFileStore(this).currentPaths()
}

internal fun Context.xrayRootResourceFilePaths(): XrayResourceFilePaths {
    val store = AndroidResourceFileStore(this)
    // VPN only needs resource paths; inspecting the custom CLI belongs to ROOT configuration.
    return store.currentPaths().copy(xrayCorePath = store.effectiveXrayCoreFile().absolutePath)
}

private const val AsteriskdLibraryName = "libasteriskd.so"
private const val BpfMatcherLibraryName = "libbpf-matcher.so"
private const val Bpf2SocksLibraryName = "libbpf2socks.so"
private const val XrayCoreLibraryName = "libxray.so"
private const val HevSocks5TunnelLibraryName = "libhev-socks5-tunnel-cli.so"

internal fun resourceFileExists(
    kind: ResourceFileKind?,
    targetExists: Boolean,
    targetLength: Long,
): Boolean {
    return targetExists && (kind == ResourceFileKind.XrayCore || targetLength > 0)
}

private fun File.toStatus(kind: ResourceFileKind? = null): ResourceFileStatus {
    val targetExists = exists()
    val targetLength = takeIf { targetExists }?.length() ?: 0L
    return ResourceFileStatus(
        exists = resourceFileExists(kind, targetExists, targetLength),
        sizeBytes = targetLength,
        updatedAtMillis = takeIf { targetExists }?.lastModified() ?: 0,
    )
}

private fun replaceFile(source: File, target: File) {
    if (source.length() <= 0) {
        source.delete()
        error("${target.name} is empty")
    }
    if (target.exists()) {
        target.delete()
    }
    if (!source.renameTo(target)) {
        source.inputStream().use { input ->
            writeAtomically(target) { output -> input.copyTo(output) }
        }
        source.delete()
    }
}

private fun ResourceFileKind.applyPermissions(file: File) {
    if (this == ResourceFileKind.XrayCore) {
        file.setExecutable(true, false)
    }
}
