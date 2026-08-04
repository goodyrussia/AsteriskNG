// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import app.AppState
import features.settings.sheets.DnsSettingsBottomSheet

@Composable
internal fun SettingsBottomSheetsHost(
    sheetState: SettingsSheetState,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    DnsSettingsBottomSheet(
        show = sheetState.showDnsSettings,
        primary = sheetState.dnsSettingsDraft.primary,
        secondary = sheetState.dnsSettingsDraft.secondary,
        onPrimaryChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(primary = it)
        },
        onSecondaryChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(secondary = it)
        },
        onDismissRequest = { sheetState.showDnsSettings = false },
        onSave = { primary, secondary ->
            updateAppState { state -> state.copy(proxyDns = listOf(primary, secondary)) }
            sheetState.showDnsSettings = false
        },
    )
}
