// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.unit.dp
import app.AppState
import app.ProxyServerListState
import app.ProxyServerState
import app.R
import app.modes.ProxyServerListLayoutDouble
import app.modes.ProxyServerListLayoutMultiple
import app.modes.ProxyServerListLayoutSingle
import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.navigation.Navigator
import app.navigation.Route
import data.AndroidAppStateStore
import engine.proxy.latency.ProxyServerLatencyTestMode
import features.proxy.server.model.getUrlOrNull
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.createProxyServer
import features.proxy.server.usecase.deleteDuplicateServersInGroup
import features.proxy.server.usecase.deleteInvalidServersInGroup
import features.proxy.server.usecase.importProxyServersFromText
import features.proxy.server.usecase.withDeletedProxyServers
import features.proxy.server.usecase.withImportedProxyServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import ui.components.DeleteConfirmationDialog
import ui.feedback.AndroidToastTipNotifier
import ui.layout.AdaptiveTopAppBar
import ui.text.formatTemplate

@Composable
internal fun ProxyServerListTopBar(
    isWideScreen: Boolean,
    scrollBehavior: ScrollBehavior,
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    selectedServer: ProxyServerState?,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    qrScanner: suspend () -> String?,
    proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    proxyServiceUseCase: ProxyServiceUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean) -> Unit,
) {
    var pendingDeletionAction by remember { mutableStateOf<ProxyServerListToolAction?>(null) }

    fun executeToolAction(action: ProxyServerListToolAction) {
        handleProxyServerListToolAction(
            action = action,
            searchValue = searchValue,
            selectedServer = selectedServer,
            proxyListState = proxyListState,
            stateStore = stateStore,
            updateAppState = updateAppState,
            proxyServiceUseCase = proxyServiceUseCase,
            clipboard = clipboard,
            tipNotifier = tipNotifier,
            scope = scope,
            messages = messages,
            serviceOperationInProgress = serviceOperationInProgress,
            runProxyServiceOperation = runProxyServiceOperation,
            onTestProxyServerLatency = onTestProxyServerLatency,
        )
    }

    fun requestToolAction(action: ProxyServerListToolAction) {
        if (action.isDeletion && proxyListState.enableDeletionConfirmation) {
            pendingDeletionAction = action
        } else {
            executeToolAction(action)
        }
    }

    AdaptiveTopAppBar(
        title = androidx.compose.ui.res.stringResource(R.string.proxy_server_list_title),
        isWideScreen = isWideScreen,
        scrollBehavior = scrollBehavior,
        actions = {
            ProxyServerListAddMenu { action ->
                handleProxyServerListAddAction(
                    action = action,
                    proxyListState = proxyListState,
                    updateAppState = updateAppState,
                    navigator = navigator,
                    qrScanner = qrScanner,
                    proxyServerImportFileUseCase = proxyServerImportFileUseCase,
                    clipboard = clipboard,
                    tipNotifier = tipNotifier,
                    scope = scope,
                    backgroundScope = backgroundScope,
                    messages = messages,
                    resultKey = resultKey,
                )
            }
            ProxyServerListToolsMenu(
                layout = proxyListState.proxyServerListLayout,
                sort = proxyListState.proxyServerListSort,
            ) { action -> requestToolAction(action) }
        },
        bottomContent = {
            ProxyServerListSearchBar(
                searchValue = searchValue,
                onSearchValueChange = onSearchValueChange,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            )
        },
    )

    pendingDeletionAction?.let { action ->
        DeleteConfirmationDialog(
            show = true,
            title = androidx.compose.ui.res.stringResource(action.deletionConfirmationTitleResId),
            onDismissRequest = { pendingDeletionAction = null },
            onConfirm = {
                pendingDeletionAction = null
                executeToolAction(action)
            },
        )
    }
}

private val ProxyServerListToolAction.isDeletion: Boolean
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers,
        ProxyServerListToolAction.DeleteInvalidServers,
        ProxyServerListToolAction.DeleteAllServers,
        -> true

        else -> false
    }

private val ProxyServerListToolAction.deletionConfirmationTitleResId: Int
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers -> R.string.proxy_server_list_delete_duplicates
        ProxyServerListToolAction.DeleteInvalidServers -> R.string.proxy_server_list_delete_invalid
        ProxyServerListToolAction.DeleteAllServers -> R.string.proxy_server_list_delete_all
        else -> error("Deletion confirmation is only available for deletion actions")
    }

private fun handleProxyServerListAddAction(
    action: ProxyServerListAddAction,
    proxyListState: ProxyServerListState,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    qrScanner: suspend () -> String?,
    proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
) {
    when (action) {
        ProxyServerListAddAction.ScanQrCode -> {
            scope.launch {
                runCatching { qrScanner() }
                    .onSuccess { scanText ->
                        if (scanText.isNullOrBlank()) return@onSuccess
                        importProxyServersInBackground(
                            text = scanText,
                            source = ProxyServerImportSource.QrCode,
                            updateAppState = updateAppState,
                            tipNotifier = tipNotifier,
                            backgroundScope = backgroundScope,
                            messages = messages,
                        )
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        ProxyServerListAddAction.Clipboard -> {
            scope.launch {
                val text = clipboard.getPlainText().orEmpty()
                importProxyServersInBackground(
                    text = text,
                    source = ProxyServerImportSource.Clipboard,
                    updateAppState = updateAppState,
                    tipNotifier = tipNotifier,
                    backgroundScope = backgroundScope,
                    messages = messages,
                )
            }
        }

        ProxyServerListAddAction.File -> {
            scope.launch {
                runCatching { proxyServerImportFileUseCase.readText() }
                    .onSuccess { text ->
                        text?.let {
                            importProxyServersInBackground(
                                text = it,
                                source = ProxyServerImportSource.File,
                                updateAppState = updateAppState,
                                tipNotifier = tipNotifier,
                                backgroundScope = backgroundScope,
                                messages = messages,
                            )
                        }
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        else -> {
            val serverId = proxyListState.nextProxyServerId
            navigator.navigateForResult(
                route = Route.ProxyServerEditor(
                    ps = createProxyServer(action),
                    serverId = serverId,
                    groupId = 0,
                    resultKey = resultKey,
                ),
                requestKey = resultKey,
            )
        }
    }
}

private fun importProxyServersInBackground(
    text: String,
    source: ProxyServerImportSource,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    backgroundScope.launch {
        runCatching {
            importProxyServers(
                text = text,
                source = source,
                updateAppState = updateAppState,
                tipNotifier = tipNotifier,
                messages = messages,
            )
        }.onFailure { error -> tipNotifier.showError(error) }
    }
}

private suspend fun importProxyServers(
    text: String,
    source: ProxyServerImportSource,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
) {
    val importResult = importProxyServersFromText(
        text = text,
        source = source,
    )
    if (importResult.servers.isNotEmpty()) {
        updateAppState { state ->
            state.withImportedProxyServers(importResult, 0)
        }
    }
    tipNotifier.show(
        messages.importResultTemplate.formatTemplate(
            "serverCount" to importResult.servers.size,
        ),
    )
}

private fun handleProxyServerListToolAction(
    action: ProxyServerListToolAction,
    searchValue: String,
    selectedServer: ProxyServerState?,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean) -> Unit,
) {
    val currentFilteredServers = proxyListState.proxyServers.filteredForProxyServerList(searchValue)
    when (action) {
        ProxyServerListToolAction.RestartService -> {
            restartSelectedProxyService(
                selectedServer = selectedServer,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.TestLatency -> {
            onTestProxyServerLatency(
                currentFilteredServers,
                ProxyServerLatencyTestMode.TcpConnect,
                messages.latencyDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.TestRealConnection -> {
            onTestProxyServerLatency(
                currentFilteredServers,
                ProxyServerLatencyTestMode.RealConnection,
                messages.realConnectionDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.SetLayoutSingle -> {
            updateAppState { state -> state.copy(proxyServerListLayout = ProxyServerListLayoutSingle) }
        }

        ProxyServerListToolAction.SetLayoutDouble -> {
            updateAppState { state -> state.copy(proxyServerListLayout = ProxyServerListLayoutDouble) }
        }

        ProxyServerListToolAction.SetLayoutMultiple -> {
            updateAppState { state -> state.copy(proxyServerListLayout = ProxyServerListLayoutMultiple) }
        }

        ProxyServerListToolAction.SetSortDefault -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortDefault) }
        }

        ProxyServerListToolAction.SetSortName -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortName) }
        }

        ProxyServerListToolAction.SetSortLatency -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortLatency) }
        }

        ProxyServerListToolAction.CopyAllUrls -> {
            copyAllServerUrls(
                servers = proxyListState.proxyServers,
                clipboard = clipboard,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteDuplicateServers -> {
            deleteDuplicateServers(
                servers = proxyListState.proxyServers,
                updateAppState = updateAppState,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteInvalidServers -> {
            deleteInvalidServers(
                servers = proxyListState.proxyServers,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.DeleteAllServers -> {
            deleteAllServers(
                servers = proxyListState.proxyServers,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }
    }
}

private fun restartSelectedProxyService(
    selectedServer: ProxyServerState?,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    if (serviceOperationInProgress) return
    runProxyServiceOperation {
        when (
            val result = proxyServiceUseCase.restart(
                state = stateStore.state.value,
                selectedServer = selectedServer,
            )
        ) {
            is ProxyServiceResult.Success -> {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
                tipNotifier.show(messages.serviceRestarted)
            }

            ProxyServiceResult.MissingServer -> {
                tipNotifier.show(messages.selectServerFirst)
            }

            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(result.error, messages.serviceStopped)
            }
        }
    }
}

private fun copyAllServerUrls(
    servers: List<ProxyServerState>,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    scope.launch {
        var invalidCount = 0
        val urls = servers.mapNotNull { server ->
            if (server.server.validateBasic().isNotEmpty()) {
                invalidCount++
                return@mapNotNull null
            }
            runCatching { server.server.getUrlOrNull() }.fold(
                onSuccess = { url -> url },
                onFailure = {
                    invalidCount++
                    null
                },
            )
        }
            .joinToString("\n")
        if (urls.isBlank()) {
            tipNotifier.show(if (invalidCount > 0) messages.configInvalid else messages.unsupported)
        } else {
            clipboard.setPlainText(urls)
            tipNotifier.show(messages.copied)
        }
    }
}

private fun deleteInvalidServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    val previewResult = stateStore.state.value.proxyServers.deleteInvalidServersInGroup(currentGroupServerIds)
    deleteServersByIds(
        serverIds = previewResult.removedServerIds,
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.invalidServersDeletedTemplate,
        emptyMessage = messages.noInvalidServers,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteAllServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    deleteServersByIds(
        serverIds = servers.map { server -> server.id }.toSet(),
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.allServersDeletedTemplate,
        emptyMessage = messages.noServersToDelete,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteServersByIds(
    serverIds: Set<Int>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    deletedTemplate: String,
    emptyMessage: String,
    serviceStoppedMessage: String,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val stateSnapshot = stateStore.state.value
    val existingServerIds = stateSnapshot.proxyServers
        .asSequence()
        .map { server -> server.id }
        .filter { serverId -> serverId in serverIds }
        .toSet()
    if (existingServerIds.isEmpty()) {
        scope.launch { tipNotifier.show(emptyMessage) }
        return
    }

    fun applyDeleteAndNotify() {
        var removedCount = 0
        updateAppState { state ->
            val deletedServerIds = state.proxyServers
                .asSequence()
                .map { server -> server.id }
                .filter { serverId -> serverId in serverIds }
                .toSet()
            removedCount = deletedServerIds.size
            state.withDeletedProxyServers(deletedServerIds)
        }
        scope.launch {
            tipNotifier.show(
                if (removedCount > 0) {
                    deletedTemplate.formatTemplate("count" to removedCount)
                } else {
                    emptyMessage
                },
            )
        }
    }

    val selectedServerWillBeDeleted = stateSnapshot.selectedProxyServerId in existingServerIds
    if (!stateSnapshot.proxyRunning || !selectedServerWillBeDeleted) {
        applyDeleteAndNotify()
        return
    }
    if (serviceOperationInProgress) return

    runProxyServiceOperation {
        when (val stopResult = proxyServiceUseCase.stop(stateStore.state.value.runMode)) {
            is ProxyServiceResult.Success -> applyDeleteAndNotify()
            ProxyServiceResult.MissingServer -> applyDeleteAndNotify()
            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(stopResult.error, serviceStoppedMessage)
            }
        }
    }
}

private fun deleteDuplicateServers(
    servers: List<ProxyServerState>,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    var removedCount = 0
    updateAppState { state ->
        val result = state.proxyServers.deleteDuplicateServersInGroup(
            currentGroupServerIds = currentGroupServerIds,
            selectedProxyServerId = state.selectedProxyServerId,
        )
        removedCount = result.removedCount
        if (removedCount == 0) {
            state
        } else {
            state.copy(proxyServers = result.servers)
        }
    }
    scope.launch {
        tipNotifier.show(
            if (removedCount > 0) {
                messages.duplicatesDeletedTemplate.formatTemplate("count" to removedCount)
            } else {
                messages.noDuplicates
            },
        )
    }
}
