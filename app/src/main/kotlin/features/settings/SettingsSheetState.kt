// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.AppState

internal class SettingsSheetState {
    var showDnsSettings by mutableStateOf(false)
    var dnsSettingsDraft by mutableStateOf(DnsSettingsDraft())

    fun openDnsSettings(appState: AppState) {
        dnsSettingsDraft = appState.toDnsSettingsDraft()
        showDnsSettings = true
    }
}

@Composable
internal fun rememberSettingsSheetState(): SettingsSheetState {
    return remember { SettingsSheetState() }
}
