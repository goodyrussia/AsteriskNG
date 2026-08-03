// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import app.R
import engine.root.RootModeEngine
import engine.tproxy.TproxyRootRunner
import engine.tproxy.buildTproxyStartConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway

class AndroidProxyEngine(
    context: Context,
    rootAccess: AndroidRootShellGateway,
) {
    private val tproxyEngine = RootModeEngine(
        context = context.applicationContext,
        rootAccess = rootAccess,
        runner = TproxyRootRunner(rootAccess),
        rootRequiredErrorResId = R.string.error_tproxy_root_required,
        startFailedErrorResId = R.string.error_tproxy_start_failed,
        modeName = "TPROXY",
        logTag = "TproxyEngine",
        buildConfig = { rootContext -> rootContext.buildTproxyStartConfig() },
    )
    private val operationMutex = Mutex()

    suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) {
            val resolvedRequest = request.copy(appState = request.appState.withResolvedDynamicLocalProxyPort())
            tproxyEngine.start(resolvedRequest).copy(appState = resolvedRequest.appState)
        }
    }

    suspend fun stop(): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) { tproxyEngine.stop() }
    }

    suspend fun restart(request: ProxyEngineStartRequest): ProxyEngineStatus = start(request)

    suspend fun status(): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) { tproxyEngine.status() }
    }
}
