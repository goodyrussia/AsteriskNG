// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import engine.proxy.LocalProxyRuntime
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import engine.xray.clearCoreLogs
import engine.xray.startCoreLogTailers
import features.logs.AndroidAppLogger
import features.logs.CoreLogFileTailer
import system.AndroidRootShellGateway
import java.io.File

internal class RootModeEngine<Config : RootModeStartConfig>(
    private val context: Context,
    private val rootAccess: AndroidRootShellGateway,
    private val runner: RootModeRunner<Config>,
    private val rootRequiredErrorResId: Int,
    private val startFailedErrorResId: Int,
    private val modeName: String,
    private val logTag: String,
    private val buildConfig: (RootConfigBuildContext) -> Config,
) {
    private var logFileTailers: List<CoreLogFileTailer> = emptyList()

    suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus {
        if (!rootAccess.hasRootAccess()) {
            error(context.getString(rootRequiredErrorResId))
        }
        val rootContext = context.prepareRootConfigBuildContext(request)
        val config = buildConfig(rootContext)
        if (!File(config.root.runtimeLayout.xrayCorePath).canExecute()) {
            File(config.root.runtimeLayout.xrayCorePath).setExecutable(true, false)
        }
        runner.prepareCoreLogFiles(config.root.coreLogPaths)
        config.root.coreLogPaths.clearCoreLogs(logTag)
        logFileTailers = config.root.coreLogPaths.startCoreLogTailers(config.root.enableAccessLog)
        runCatching {
            runner.start(config)
            LocalProxyRuntime.update(config.localProxyOptions)
            if (rootContext.appState.enableRootBootScript) {
                runner.installBootScript(config)
            } else {
                runner.uninstallBootScript(config.root)
            }
        }.onFailure { error ->
            runCatching { runner.stop(config.root.runtimeLayout) }
                .onFailure { stopError -> AndroidAppLogger.warn(logTag, "Failed to clean up $modeName after startup failure", stopError) }
            LocalProxyRuntime.clear()
            logFileTailers.forEach { tailer -> tailer.stop() }
            logFileTailers = emptyList()
            AndroidAppLogger.error(logTag, "Failed to start $modeName", error)
            throw IllegalStateException(
                context.getString(startFailedErrorResId, error.message.orEmpty()),
                error,
            )
        }
        return status()
    }

    suspend fun stop(): ProxyEngineStatus {
        logFileTailers.forEach { tailer -> tailer.stop() }
        logFileTailers = emptyList()
        runner.stop(context.prepareRootRuntimeLayout())
        LocalProxyRuntime.clear()
        return status()
    }

    suspend fun status(): ProxyEngineStatus {
        return ProxyEngineStatus(running = runner.isRunning(context.prepareRootRuntimeLayout()))
    }
}
