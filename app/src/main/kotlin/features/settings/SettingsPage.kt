// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.AppState
import app.LocalAppChromeState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.ProjectInfo
import app.R
import app.collectAppState
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeSystem
import app.navigation.Route
import engine.proxy.withResolvedDynamicLocalProxyPort
import features.proxy.server.usecase.ProxyServiceResult
import features.settings.usecase.RootBootScriptResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import ui.KeyColors
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsPage(padding: PaddingValues) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    subtitle = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
                )
            }
        },
    ) { innerPadding ->
        SettingsContent(innerPadding, padding, topAppBarScrollBehavior)
    }
}

@Composable
private fun SettingsContent(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val rootBootScriptUseCase = services.rootBootScriptUseCase
    val tipNotifier = services.tipNotifier
    val lazyListState = rememberLazyListState()
    var rootBootScriptSwitchInProgress by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val contentPadding = pageContentPaddingWithCutout(innerPadding, outerPadding, isWideScreen)
    val listPadding = pageListPadding(contentPadding)

    val colorModeOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_light),
        stringResource(R.string.option_dark),
        stringResource(R.string.option_theme_system),
        stringResource(R.string.option_theme_light),
        stringResource(R.string.option_theme_dark),
    )
    val languageOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_english),
        stringResource(R.string.option_simplified_chinese),
    )
    val keyColorOptions = listOf(
        stringResource(R.string.theme_color_default),
        stringResource(R.string.theme_color_blue),
        stringResource(R.string.theme_color_green),
        stringResource(R.string.theme_color_violet),
        stringResource(R.string.theme_color_yellow),
        stringResource(R.string.theme_color_orange),
        stringResource(R.string.theme_color_rose),
        stringResource(R.string.theme_color_cyan),
    ).take(KeyColors.size + 1)
    val rootRequiredMessage = stringResource(R.string.settings_root_required)
    val rootBootScriptFailedMessage = stringResource(R.string.settings_root_boot_script_failed)
    val runtimeSettingsFailedMessage = stringResource(R.string.settings_runtime_apply_failed)
    val selectServerFirstMessage = stringResource(R.string.proxy_server_list_select_first)
    val sheetState = rememberSettingsSheetState()

    val updateRuntimeAppState: (((AppState) -> AppState) -> Unit) = remember(
        appState,
        updateAppState,
        services,
        runtimeSettingsFailedMessage,
        selectServerFirstMessage,
    ) {
        { transform ->
            val nextState = transform(appState)
            updateAppState(transform)
            if (appState.proxyRunning) {
                services.appScope.launch {
                    val selectedServer = nextState.proxyServers.firstOrNull { it.id == nextState.selectedProxyServerId }
                    when (val result = services.proxyServiceUseCase.restart(nextState, selectedServer)) {
                        is ProxyServiceResult.Success -> {
                            result.appState?.let { restartedState ->
                                updateAppState { current ->
                                    restartedState.copy(
                                        shareHotspot = current.shareHotspot,
                                        proxyDns = current.proxyDns,
                                        enableRootBootScript = current.enableRootBootScript,
                                    )
                                }
                            }
                        }
                        ProxyServiceResult.MissingServer -> tipNotifier.show(selectServerFirstMessage)
                        is ProxyServiceResult.Failed -> {
                            updateAppState { state -> state.copy(proxyRunning = false) }
                            tipNotifier.showError(result.error, runtimeSettingsFailedMessage)
                        }
                    }
                }
            }
        }
    }

    Box {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
            contentPadding = listPadding,
        ) {
            item(key = "settings_theme") {
                SettingsThemeSection(
                    colorModeOptions = colorModeOptions,
                    colorMode = appState.colorMode,
                    keyColorOptions = keyColorOptions,
                    seedIndex = appState.seedIndex,
                    languageOptions = languageOptions,
                    languageMode = appState.languageMode,
                    isThemeColorMode = appState.colorMode in ColorModeThemeSystem..ColorModeThemeDark,
                    onColorModeChange = { index -> updateAppState { state -> state.copy(colorMode = index) } },
                    onSeedIndexChange = { index -> updateAppState { state -> state.copy(seedIndex = index) } },
                    onLanguageModeChange = { index -> updateAppState { state -> state.copy(languageMode = index) } },
                )
            }
            item(key = "settings_network") {
                SettingsNetworkSection(
                    shareHotspot = appState.shareHotspot,
                    enableRootBootScript = appState.enableRootBootScript,
                    onOpenDnsSettings = { sheetState.openDnsSettings(appState) },
                    onShareHotspotChange = { enabled ->
                        updateRuntimeAppState { state -> state.copy(shareHotspot = enabled) }
                    },
                    onEnableRootBootScriptChange = { enabled ->
                        if (!rootBootScriptSwitchInProgress) {
                            val currentState = appState
                            rootBootScriptSwitchInProgress = true
                            services.appScope.launch {
                                try {
                                    val bootState = if (enabled) currentState.withResolvedDynamicLocalProxyPort() else currentState
                                    when (val result = rootBootScriptUseCase.setEnabled(bootState, enabled)) {
                                        RootBootScriptResult.Success -> updateAppState { state ->
                                            state.copy(enableRootBootScript = enabled, localProxyPort = bootState.localProxyPort)
                                        }
                                        RootBootScriptResult.MissingServer -> tipNotifier.show(selectServerFirstMessage)
                                        RootBootScriptResult.RootUnavailable -> tipNotifier.show(rootRequiredMessage)
                                        is RootBootScriptResult.Failed -> tipNotifier.showError(
                                            result.error,
                                            rootBootScriptFailedMessage,
                                        )
                                    }
                                } finally {
                                    withContext(Dispatchers.Main.immediate) {
                                        rootBootScriptSwitchInProgress = false
                                    }
                                }
                            }
                        }
                    },
                )
            }
            item(key = "settings_logs") {
                SettingsLogsSection(
                    onOpenCoreLogs = { navigator.push(Route.CoreLogs) },
                    onOpenLogcatLogs = { navigator.push(Route.LogcatLogs) },
                )
            }
            item(key = "settings_about") {
                SettingsAboutSection(
                    onOpenAbout = { navigator.push(Route.About) },
                    onOpenLicenses = { navigator.push(Route.License) },
                )
            }
        }
        VerticalScrollBar(
            adapter = top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
        SettingsBottomSheetsHost(
            sheetState = sheetState,
            updateAppState = updateRuntimeAppState,
        )
    }
}
