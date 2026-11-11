// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.runtime

import android.content.Context
import engine.root.mode.RootModeCatalog
import features.logs.androidCoreLogErrorFile
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Watches the Xray core error log for repeated dial failures against the currently selected
 * proxy server and surfaces a single "server unreachable" explanation while the proxy runs.
 *
 * Startup failures are already covered by [RootFailureWatcher]; this covers the tunnel dying
 * (or never establishing) *after* a successful start, which otherwise looks like a healthy
 * "running" proxy while every app fails to load. Published through [ProxyErrorBus], so the
 * existing [features.proxy.ProxyErrorHost] dialog renders it.
 *
 * Callers are serialized by the proxy engine's operation mutex; [start] and [stop] are safe
 * to call from any thread.
 */
internal object RootDialFailureMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicReference<Job?>(null)

    fun start(context: Context, runMode: Int, proxyHost: String) {
        stop()
        if (proxyHost.isBlank()) return
        val errorLog = context.applicationContext.androidCoreLogErrorFile()
        active.set(scope.launch { watch(errorLog, runMode, proxyHost) })
    }

    fun stop() {
        active.getAndSet(null)?.cancel()
    }

    private suspend fun CoroutineScope.watch(errorLog: File, runMode: Int, proxyHost: String) {
        val marker = "failed to dial to $proxyHost"
        val failures = ArrayDeque<Long>()
        var published = false
        var position = runCatching { errorLog.length() }.getOrDefault(0L)
        while (currentCoroutineContext().isActive) {
            try {
                if (errorLog.exists()) {
                    RandomAccessFile(errorLog, "r").use { reader ->
                        if (position > reader.length()) {
                            position = 0L
                        }
                        reader.seek(position)
                        var line = reader.readUtf8Line()
                        while (line != null) {
                            ensureActive()
                            if (!published && marker in line) {
                                val now = System.currentTimeMillis()
                                failures.addLast(now)
                                while (failures.isNotEmpty() && now - failures.first() > FailureWindowMillis) {
                                    failures.removeFirst()
                                }
                                if (failures.size >= FailureThreshold) {
                                    published = true
                                    publishUnreachable(runMode, proxyHost, line)
                                }
                            }
                            line = reader.readUtf8Line()
                        }
                        position = reader.filePointer
                    }
                }
            } catch (_: Exception) {
                // Transient IO while the core recreates the log; retry on the next poll.
            }
            delay(PollIntervalMillis.milliseconds)
        }
    }

    private fun publishUnreachable(runMode: Int, proxyHost: String, sampleLine: String) {
        val mode = RootModeCatalog.find(runMode)?.daemonMode?.wireValue ?: "unknown"
        ProxyErrorBus.publish(
            ProxyErrorExplanation(
                mode = mode,
                occurredAtEpochMillis = System.currentTimeMillis(),
                rawMessage = DiagnosticRedaction.redact(
                    "Proxy server unreachable ($proxyHost): $sampleLine".take(MaxMessageLength),
                ),
                diagnostics = emptyList(),
            ),
        )
    }

    private fun RandomAccessFile.readUtf8Line(): String? {
        return readLine()?.let { line ->
            String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }
    }

    private const val PollIntervalMillis = 5_000L
    private const val FailureWindowMillis = 120_000L
    private const val FailureThreshold = 3
    private const val MaxMessageLength = 500
}
