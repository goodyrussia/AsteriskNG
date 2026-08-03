// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import data.AndroidAppStateStore
import engine.root.LegacyRootRuntimeCleaner
import features.logs.AndroidAppLogger
import features.settings.usecase.RootBootScriptResult
import features.settings.usecase.RootBootScriptUseCase
import kotlinx.coroutines.launch
import system.AndroidRootShellGateway

class LegacyRootRuntimeMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val application = appContext as AsteriskApplication
        application.appScope.launch {
            try {
                val rootAccess = AndroidRootShellGateway()
                LegacyRootRuntimeCleaner(appContext, rootAccess).clean()
                val state = AndroidAppStateStore.get(appContext).state.value
                if (state.enableRootBootScript) {
                    when (val result = RootBootScriptUseCase(appContext, rootAccess).refresh(state)) {
                        RootBootScriptResult.Success -> Unit
                        RootBootScriptResult.MissingServer -> AndroidAppLogger.warn(
                            LogTag,
                            "Skipped ROOT boot-script migration because no proxy server is selected",
                        )
                        RootBootScriptResult.RootUnavailable -> AndroidAppLogger.warn(
                            LogTag,
                            "Skipped ROOT boot-script migration because root access is unavailable",
                        )
                        is RootBootScriptResult.Failed -> AndroidAppLogger.warn(
                            LogTag,
                            "Failed to migrate ROOT boot script",
                            result.error,
                        )
                    }
                }
            } catch (error: Throwable) {
                AndroidAppLogger.warn(LogTag, "Failed to migrate retired rooted runtime", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        private const val LogTag = "LegacyRootRuntimeMigration"
    }
}
