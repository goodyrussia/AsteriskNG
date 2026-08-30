// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import engine.xray.XrayCoreLogPaths
import engine.xray.clearCoreLogFilesAsApp
import engine.xray.prepareXrayCoreLogPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun Context.clearCoreLogFile(logFile: XrayLogFile) {
    val logPath = applicationContext.prepareXrayCoreLogPaths().pathOf(logFile)
    if (logPath.isBlank()) {
        return
    }

    withContext(Dispatchers.IO) {
        clearCoreLogFilesAsApp(
            logPaths = listOf(logPath),
            logTag = LogTag,
        )
    }
}

private fun XrayCoreLogPaths.pathOf(logFile: XrayLogFile): String {
    return when (logFile) {
        XrayLogFile.Error -> errorLogPath
        XrayLogFile.Access -> accessLogPath
    }
}

internal enum class XrayLogFile {
    Error,
    Access,
}

internal suspend fun Context.clearSshLogFile() {
    val logFile = File(filesDir, "xray/ssh.log")
    withContext(Dispatchers.IO) {
        logFile.apply {
            parentFile?.mkdirs()
            writeText("")
        }
    }
}

private const val LogTag = "CoreLogClear"
