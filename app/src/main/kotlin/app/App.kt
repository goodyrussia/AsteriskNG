// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.effects.LauncherIconSynchronizer
import app.effects.ProxyStatusSynchronizer
import app.effects.RootBootScriptSynchronizer
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.proxy.latency.AndroidProxyLatencyTester
import features.logs.AndroidAccessLogRepository
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import features.logs.AndroidSshLogRepository
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServiceUseCase

import features.settings.locale.ProvideAppLanguage
import features.settings.locale.RecreateActivityOnAppLanguageChange
import features.settings.usecase.RootBootScriptUseCase
import features.subscription.runtime.AndroidSubscriptionFetcher
import system.AndroidNetworkInterfaceProvider
import system.AndroidPackageProvider
import system.AndroidRootShellGateway
import system.AndroidUserSpaceProvider
import ui.AppTheme
import ui.feedback.AndroidToastTipNotifier
import ui.keyColorFor

@Composable
fun App(
    padding: PaddingValues = PaddingValues(0.dp),
    qrCodeScanner: suspend () -> String?,
    resourceFilePicker: suspend () -> Uri?,
    logFileCreator: suspend (String) -> Uri?,
) {
    val appContext = LocalContext.current.applicationContext
    val appScope = (appContext as AsteriskApplication).appScope
    val rootAccess = remember { AndroidRootShellGateway() }
    val userSpaces = remember(appContext, rootAccess) {
        AndroidUserSpaceProvider(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val packageCatalog = remember(appContext, rootAccess, userSpaces) {
        AndroidPackageProvider(
            context = appContext,
            rootAccess = rootAccess,
            userSpaces = userSpaces,
        )
    }
    val networkInterfaces = remember(rootAccess) {
        AndroidNetworkInterfaceProvider(rootAccess)
    }
    val subscriptionFetcher = remember { AndroidSubscriptionFetcher() }
    val qrScanner = remember(qrCodeScanner) { qrCodeScanner }
    val proxyServerImportFileUseCase = remember(appContext, resourceFilePicker) {
        ProxyServerImportFileUseCase(
            context = appContext,
            filePicker = resourceFilePicker,
        )
    }
    val proxyLatencyTester = remember { AndroidProxyLatencyTester() }
    val proxyEngine = remember(appContext, rootAccess) {
        AndroidProxyEngine(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val rootBootScriptUseCase = remember(appContext, rootAccess) {
        RootBootScriptUseCase(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val proxyServiceUseCase = remember(proxyEngine) {
        ProxyServiceUseCase(proxyEngine)
    }
    val stateStore = remember(appContext) { AndroidAppStateStore.get(appContext) }
    val tipNotifier = remember(appContext) { AndroidToastTipNotifier(appContext) }
    val services = remember(
        appScope,
        proxyEngine,
        rootAccess,
        userSpaces,
        packageCatalog,
        networkInterfaces,
        subscriptionFetcher,
        qrScanner,
        proxyServerImportFileUseCase,
        proxyLatencyTester,
        proxyServiceUseCase,
        rootBootScriptUseCase,
        tipNotifier,
        logFileCreator,
    ) {
        AppServices(
            appScope = appScope,
            proxyEngine = proxyEngine,
            rootAccess = rootAccess,
            userSpaces = userSpaces,
            packageCatalog = packageCatalog,
            networkInterfaces = networkInterfaces,
            subscriptionFetcher = subscriptionFetcher,
            qrScanner = qrScanner,
            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
            proxyLatencyTester = proxyLatencyTester,
            proxyServiceUseCase = proxyServiceUseCase,
            rootBootScriptUseCase = rootBootScriptUseCase,
            tipNotifier = tipNotifier,
            logFileCreator = logFileCreator,
            coreLogRepository = AndroidCoreLogRepository,
            accessLogRepository = AndroidAccessLogRepository,
            logcatRepository = AndroidLogcatRepository,
            sshLogRepository = AndroidSshLogRepository,
        )
    }
    val chromeState by stateStore.collectAppChromeState()
    val updateAppState: ((AppState) -> AppState) -> Unit = remember(stateStore) {
        { transform -> stateStore.update(transform) }
    }
    val keyColor = keyColorFor(chromeState.seedIndex)
    RecreateActivityOnAppLanguageChange(languageMode = chromeState.languageMode)
    ProxyStatusSynchronizer(
        stateStore = stateStore,
        proxyEngine = proxyEngine,
        updateAppState = updateAppState,
    )

    LauncherIconSynchronizer(
        context = appContext,
        stateStore = stateStore,
    )

    RootBootScriptSynchronizer(
        stateStore = stateStore,
        rootBootScriptUseCase = rootBootScriptUseCase,
    )

    ProvideAppLanguage(languageMode = chromeState.languageMode) {
        AppTheme(
            colorMode = chromeState.colorMode,
            keyColor = keyColor,
        ) {
            CompositionLocalProvider(
                LocalAppStateStore provides stateStore,
                LocalAppChromeState provides chromeState,
                LocalUpdateAppState provides updateAppState,
                LocalAppServices provides services,
            ) {
                AppContent(padding = padding)
            }
        }
    }
}
