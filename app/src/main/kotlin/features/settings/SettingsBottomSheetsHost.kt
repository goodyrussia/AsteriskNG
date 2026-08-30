// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import app.AppState
import engine.xray.DnsModeFast
import features.settings.sheets.DnsSettingsBottomSheet
import utils.toCsvValues

@Composable
internal fun SettingsBottomSheetsHost(
    sheetState: SettingsSheetState,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val draft = sheetState.dnsSettingsDraft
    DnsSettingsBottomSheet(
        show = sheetState.showDnsSettings,
        mode = draft.mode,
        primary = draft.primary,
        secondary = draft.secondary,
        directDns = draft.directDns,
        directDnsDomains = draft.directDnsDomains,
        onModeChange = { sheetState.dnsSettingsDraft = draft.copy(mode = it) },
        onPrimaryChange = { sheetState.dnsSettingsDraft = draft.copy(primary = it) },
        onSecondaryChange = { sheetState.dnsSettingsDraft = draft.copy(secondary = it) },
        onDirectDnsChange = { sheetState.dnsSettingsDraft = draft.copy(directDns = it) },
        onDirectDnsDomainsChange = { sheetState.dnsSettingsDraft = draft.copy(directDnsDomains = it) },
        onDismissRequest = { sheetState.showDnsSettings = false },
        onSave = { mode, primary, secondary, directDns, directDnsDomains ->
            updateAppState { state ->
                state.copy(
                    dnsMode = mode,
                    proxyDns = listOf(primary, secondary),
                    directDns = if (mode == DnsModeFast) {
                        state.directDns
                    } else {
                        directDns.toCsvValues()
                    },
                    directDnsDomains = if (mode == DnsModeFast) {
                        state.directDnsDomains
                    } else {
                        directDnsDomains.toCsvValues()
                    },
                )
            }
            sheetState.showDnsSettings = false
        },
    )
}