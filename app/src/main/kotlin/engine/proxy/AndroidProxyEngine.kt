// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import android.util.Log
import app.R
import engine.root.RootModeEngine
import engine.ssh.SshCoreProcessManager
import engine.ssh.toSshCoreConfig
import engine.tproxy.TproxyRootRunner
import engine.tproxy.buildTproxyStartConfig
import features.proxy.server.model.Ssh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway

class AndroidProxyEngine(
    context: Context,
    rootAccess: AndroidRootShellGateway,
) {
    private val appContext = context.applicationContext
    private val tproxyEngine = RootModeEngine(
        context = appContext,
        rootAccess = rootAccess,
        runner = TproxyRootRunner(rootAccess),
        rootRequiredErrorResId = R.string.error_tproxy_root_required,
        startFailedErrorResId = R.string.error_tproxy_start_failed,
        modeName = "TPROXY",
        logTag = "TproxyEngine",
        buildConfig = { rootContext -> rootContext.buildTproxyStartConfig() },
    )
    private val sshCoreProcess = SshCoreProcessManager(appContext, rootAccess)
    private val operationMutex = Mutex()
    private var sshCoreRequested = false

    suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) {
            val resolvedRequest = request.copy(appState = request.appState.withResolvedDynamicLocalProxyPort())
            val ssh = request.selectedServer.server as? Ssh
            sshCoreRequested = ssh != null
            var sshCoreStarted = false
            if (ssh != null) {
                sshCoreStarted = runCatching {
                    sshCoreProcess.start(ssh.toSshCoreConfig())
                    true
                }.getOrElse { error ->
                    Log.e(LogTag, "Failed to start sshcore", error)
                    false
                }
            }
            try {
                tproxyEngine.start(resolvedRequest).copy(appState = resolvedRequest.appState)
            } catch (error: Throwable) {
                if (sshCoreStarted) {
                    sshCoreProcess.stop()
                }
                sshCoreRequested = false
                throw error
            }
        }
    }

    suspend fun stop(): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) {
            val status = tproxyEngine.stop()
            sshCoreProcess.stop()
            sshCoreRequested = false
            status
        }
    }

    suspend fun restart(request: ProxyEngineStartRequest): ProxyEngineStatus {
        stop()
        return start(request)
    }

    suspend fun status(): ProxyEngineStatus = operationMutex.withLock {
        withContext(Dispatchers.Default) {
            val tproxyRunning = tproxyEngine.status().running
            val sshRunning = !sshCoreRequested || sshCoreProcess.isRunning()
            ProxyEngineStatus(running = tproxyRunning && sshRunning)
        }
    }

    private companion object {
        const val LogTag = "ProxyEngine"
    }
}
