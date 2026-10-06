// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.AppState
import features.settings.sheets.sanitizeExternalInterfaces
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal class SettingsSheetState(
    private val updateAppState: ((AppState) -> AppState) -> Unit,
) {
    var showLocalProxySettings by mutableStateOf(false)
    var localProxySettingsDraft by mutableStateOf(LocalProxySettingsDraft())

    var showTunSettings by mutableStateOf(false)
    var tunSettingsDraft by mutableStateOf(TunSettingsDraft())

    var showDnsSettings by mutableStateOf(false)
    var dnsSettingsDraft by mutableStateOf(DnsSettingsDraft())

    var showMuxSettings by mutableStateOf(false)
    var muxSettingsDraft by mutableStateOf(MuxSettingsDraft())

    var showFragmentSettings by mutableStateOf(false)
    var fragmentSettingsDraft by mutableStateOf(FragmentSettingsDraft())

    var showExternalInterfaces by mutableStateOf(false)
    var externalInterfacesDraft by mutableStateOf(emptyList<String>())

    fun openLocalProxySettings(appState: AppState) {
        localProxySettingsDraft = appState.toLocalProxySettingsDraft()
        showLocalProxySettings = true
    }

    fun openTunSettings(appState: AppState) {
        tunSettingsDraft = appState.toTunSettingsDraft()
        showTunSettings = true
    }

    fun openDnsSettings(appState: AppState) {
        dnsSettingsDraft = appState.toDnsSettingsDraft()
        showDnsSettings = true
    }

    fun openMuxSettings(appState: AppState) {
        muxSettingsDraft = appState.toMuxSettingsDraft()
        showMuxSettings = true
    }

    fun openFragmentSettings(appState: AppState) {
        fragmentSettingsDraft = appState.toFragmentSettingsDraft()
        showFragmentSettings = true
    }

    fun openExternalInterfaces(appState: AppState) {
        val sanitizedInterfaces = appState.externalInterfaces.sanitizeExternalInterfaces()
        externalInterfacesDraft = sanitizedInterfaces
        if (sanitizedInterfaces != appState.externalInterfaces) {
            updateAppState { state -> state.copy(externalInterfaces = sanitizedInterfaces) }
        }
        showExternalInterfaces = true
    }
}

@Composable
internal fun rememberSettingsSheetState(
    updateAppState: ((AppState) -> AppState) -> Unit,
): SettingsSheetState {
    return remember(updateAppState) { SettingsSheetState(updateAppState) }
}
