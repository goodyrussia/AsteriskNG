// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SettingsThemeSection(
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    languageOptions: List<String>,
    languageMode: Int,
    isThemeColorMode: Boolean,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_theme))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_color_mode),
            items = colorModeOptions,
            selectedIndex = colorMode,
            onSelectedIndexChange = onColorModeChange,
        )
        AnimatedVisibility(
            visible = isThemeColorMode,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_theme_color),
                items = keyColorOptions,
                selectedIndex = seedIndex,
                onSelectedIndexChange = onSeedIndexChange,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
        )
    }
}

@Composable
internal fun SettingsNetworkSection(
    shareHotspot: Boolean,
    enableRootBootScript: Boolean,
    onOpenDnsSettings: () -> Unit,
    onShareHotspotChange: (Boolean) -> Unit,
    onEnableRootBootScriptChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_network))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_dns),
            summary = stringResource(R.string.settings_dns_summary_simple),
            onClick = onOpenDnsSettings,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_share_hotspot),
            summary = stringResource(R.string.settings_share_hotspot_summary),
            checked = shareHotspot,
            onCheckedChange = onShareHotspotChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_root_boot_script),
            summary = stringResource(R.string.settings_root_boot_script_summary),
            checked = enableRootBootScript,
            onCheckedChange = onEnableRootBootScriptChange,
        )
    }
}

@Composable
internal fun SettingsLogsSection(
    onOpenCoreLogs: () -> Unit,
    onOpenLogcatLogs: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_logs))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_core_logs),
            onClick = onOpenCoreLogs,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_logcat),
            onClick = onOpenLogcatLogs,
        )
    }
}

@Composable
internal fun SettingsAboutSection(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_about))
    SettingsSectionCard(bottomPadding = 0.dp) {
        ArrowPreference(
            title = stringResource(R.string.settings_about_project),
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }
}
