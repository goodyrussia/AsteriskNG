// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.ssh

import android.content.Context
import android.os.Build
import engine.root.shellQuote
import features.logs.AndroidAppLogger
import features.logs.AndroidSshLogRepository
import features.logs.CoreLogFile
import features.logs.CoreLogFileTailer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway
import system.ShellExecOptions

/**
 * Manages the sshcore daemon lifecycle through the root shell, mirroring the
 * Xray-core runner: the daemon is tracked by a pid file and its stdout/stderr
 * are appended to a dedicated log file that is tailed into the in-app SSH log
 * screen.
 */
internal class SshCoreProcessManager(
    private val context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private var logTailer: CoreLogFileTailer? = null
    private var running = false

    /** Restores the bundled sshcore binary from native libs if needed. */
    suspend fun prepareBinary(): SshCoreRuntimeLayout {
        val layout = context.prepareSshCoreRuntimeLayout()
        withContext(Dispatchers.IO) {
            val binary = File(layout.binaryPath)
            val bundled = bundledSshCoreBinaryOrNull()
            if (bundled != null && (binary.length() <= 0 || bundled.lastModified() > binary.lastModified())) {
                File(layout.dataDir).mkdirs()
                bundled.inputStream().use { input ->
                    binary.outputStream().use { output -> input.copyTo(output) }
                }
                binary.setExecutable(true, false)
                AndroidAppLogger.info(LogTag, "restored sshcore binary to ${layout.binaryPath}")
            }
            if (!binary.canExecute()) {
                binary.setExecutable(true, false)
            }
        }
        return layout
    }

    private fun bundledSshCoreBinaryOrNull(): File? {
        if (Build.SUPPORTED_ABIS.none { it == Arm64Abi }) return null
        return File(context.applicationInfo.nativeLibraryDir, SshCoreLibraryName)
            .takeIf { it.isFile && it.length() > 0 }
    }

    suspend fun start(config: SshCoreConfig): SshCoreRuntimeLayout {
        val layout = prepareBinary()
        withContext(Dispatchers.IO) {
            context.writeSshCoreConfig(config, layout)
            File(layout.logPath).apply {
                parentFile?.mkdirs()
                if (!exists()) createNewFile()
            }
            startDaemon(layout)
            startLogTailer(layout)
            running = true
            AndroidAppLogger.info(LogTag, "sshcore started")
        }
        return layout
    }

    private suspend fun startDaemon(layout: SshCoreRuntimeLayout) {
        val command = buildString {
            appendScript("rm -f ${layout.pidPath.shellQuote()} 2>/dev/null || true")
            appendScript("chmod 755 ${layout.binaryPath.shellQuote()}")
            appendScript("chmod 666 ${layout.logPath.shellQuote()} 2>/dev/null || true")
            appendScript(
                $$"""trap '' HUP
                cd $${layout.dataDir.shellQuote()} || exit 1
                ulimit -SHn 1000000 2>/dev/null || true
                $${layout.binaryPath.shellQuote()} -config $${layout.configPath.shellQuote()} >> $${layout.logPath.shellQuote()} 2>&1 < /dev/null &
                echo $! > $${layout.pidPath.shellQuote()}
                """,
            )
        }
        val result = rootAccess.exec(command, ShellExecOptions(logFailure = false))
        if (result.errno != 0) {
            error("Failed to start sshcore daemon: ${result.stderr}")
        }
    }

    private suspend fun startLogTailer(layout: SshCoreRuntimeLayout) {
        stopLogTailer()
        val tailer = CoreLogFileTailer(
            logFiles = listOf(CoreLogFile(path = layout.logPath, defaultLevel = "info")),
            repository = AndroidSshLogRepository,
        )
        tailer.start()
        logTailer = tailer
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            stopLogTailer()
            val layout = context.prepareSshCoreRuntimeLayout()
            val command = buildString {
                appendScript(
                    $$"""pid="$(cat $${layout.pidPath.shellQuote()} 2>/dev/null || true)"
                    if [ -n "$pid" ]; then
                        kill "$pid" 2>/dev/null || true
                        sleep 0.2
                        kill -9 "$pid" 2>/dev/null || true
                    fi
                    rm -f $${layout.pidPath.shellQuote()} 2>/dev/null || true
                    """,
                )
            }
            runCatching {
                rootAccess.exec(command, ShellExecOptions(logFailure = false))
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to stop sshcore cleanly", error)
            }
            running = false
            AndroidAppLogger.info(LogTag, "sshcore stopped")
        }
    }

    private fun stopLogTailer() {
        logTailer?.stop()
        logTailer = null
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        if (!running) return@withContext false
        val layout = context.prepareSshCoreRuntimeLayout()
        val command = $$"""pid="$(cat $${layout.pidPath.shellQuote()} 2>/dev/null || true)"
            [ -n "$pid" ] || exit 1
            kill -0 "$pid" 2>/dev/null || exit 1
            """.trimIndent()
        rootAccess.exec(command, ShellExecOptions(logFailure = false)).errno == 0
    }

    private fun StringBuilder.appendScript(text: String) {
        append(text.trimIndent()).append('\n')
    }

    private companion object {
        const val LogTag = "SshCoreProcess"
        const val Arm64Abi = "arm64-v8a"
        const val SshCoreLibraryName = "libsshcore.so"
    }
}
