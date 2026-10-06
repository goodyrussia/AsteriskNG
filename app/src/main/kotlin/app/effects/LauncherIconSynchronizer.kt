// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.MainActivity
import features.logs.AndroidAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LauncherIconSynchronizer(
    context: Context,
) {
    val appContext = context.applicationContext
    LaunchedEffect(appContext) {
        runCatching {
            withContext(Dispatchers.IO) {
                appContext.useDefaultLauncherIcon()
            }
        }.onFailure { error ->
            AndroidAppLogger.warn(
                LogTag,
                "Failed to synchronize launcher icon",
                error,
            )
        }
    }
}

private fun Context.useDefaultLauncherIcon() {
    val packageManager = packageManager
    val launcherPackageName = MainActivity::class.java.name.substringBeforeLast('.')
    val monetLauncher = ComponentName(packageName, "$launcherPackageName.MonetLauncherActivity")
    val defaultLauncher = ComponentName(packageName, "$launcherPackageName.DefaultLauncherActivity")

    packageManager.setComponentEnabledSetting(
        defaultLauncher,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
    )
    packageManager.setComponentEnabledSetting(
        monetLauncher,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

private const val LogTag = "LauncherIconSync"
