// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.usecase

import android.content.Context
import app.AppState
import engine.proxy.ProxyEngineStartRequest
import engine.root.prepareRootConfigBuildContext
import engine.root.prepareRootRuntimeLayout
import engine.root.removeRootBootScript
import engine.tproxy.TproxyRootRunner
import engine.tproxy.buildTproxyStartConfig
import engine.xray.prepareXrayCoreLogPaths
import kotlinx.coroutines.CancellationException
import system.AndroidRootShellGateway

internal class RootBootScriptUseCase(
    context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private val appContext = context.applicationContext
    private val tproxyRootRunner = TproxyRootRunner(rootAccess)

    suspend fun setEnabled(
        state: AppState,
        enabled: Boolean,
    ): RootBootScriptResult {
        if (!rootAccess.hasRootAccess()) {
            return RootBootScriptResult.RootUnavailable
        }
        return if (enabled) {
            install(state)
        } else {
            uninstall(rootAccessVerified = true)
        }
    }

    suspend fun refresh(state: AppState): RootBootScriptResult {
        if (!state.enableRootBootScript) {
            return RootBootScriptResult.Success
        }
        if (!rootAccess.hasRootAccess()) {
            return RootBootScriptResult.RootUnavailable
        }
        return install(state)
    }

    suspend fun uninstall(rootAccessVerified: Boolean = false): RootBootScriptResult {
        if (!rootAccessVerified && !rootAccess.hasRootAccess()) {
            return RootBootScriptResult.RootUnavailable
        }
        return runCatching {
            rootAccess.removeRootBootScript(
                runtimeLayout = appContext.prepareRootRuntimeLayout(),
                coreLogPaths = appContext.prepareXrayCoreLogPaths(),
                failureMessage = "Failed to remove ROOT boot script",
            )
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = Throwable::toRootBootScriptResult,
        )
    }

    private suspend fun install(state: AppState): RootBootScriptResult {
        val selectedServer = state.proxyServers.firstOrNull { server -> server.id == state.selectedProxyServerId }
            ?: return RootBootScriptResult.MissingServer
        return runCatching {
            val request = ProxyEngineStartRequest(state, selectedServer)
            val rootContext = appContext.prepareRootConfigBuildContext(request)
            tproxyRootRunner.installBootScript(rootContext.buildTproxyStartConfig())
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = Throwable::toRootBootScriptResult,
        )
    }
}

private fun Throwable.toRootBootScriptResult(): RootBootScriptResult {
    if (this is CancellationException) {
        throw this
    }
    return RootBootScriptResult.Failed(this)
}

internal sealed interface RootBootScriptResult {
    data object Success : RootBootScriptResult

    data object MissingServer : RootBootScriptResult

    data object RootUnavailable : RootBootScriptResult

    data class Failed(val error: Throwable) : RootBootScriptResult
}
