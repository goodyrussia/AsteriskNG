// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.proxy.server.editor

import app.LocalAppStateStore
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.collectAppState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import app.R
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.proxyServerCopyTextOrNull
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import kotlinx.coroutines.launch
import app.navigation.ProxyServerEditResult
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Ok
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageScrollModifiers
import features.proxy.server.validation.rememberProxyServerValidationMessageResolver
import ui.clipboard.setPlainText
import androidx.compose.runtime.getValue
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

@Composable
fun ProxyServerPage(
    padding: PaddingValues,
    ps: ProxyServer<*>,
    serverId: Int? = null,
    groupId: Int? = null,
    returnGroupId: Int? = null,
    resultKey: String? = null,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val validationFailedMessage = stringResource(R.string.proxy_editor_validation_failed)
    val validationMessageOf = rememberProxyServerValidationMessageResolver(validationFailedMessage)
    val copiedMessage = stringResource(R.string.common_copied)
    val unsupportedMessage = stringResource(R.string.common_unsupported)

    val psEdit = remember(ps) {
        ps.editableCopy()
    }
    val editorOptions = remember {
        ProxyServerEditorOptions(groupOptions = emptyList(), memberOptions = emptyList())
    }
    val title = psEdit.editorTitle()

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = { navigator.pop() },
                    )
                },
                actions = {
                    NavigationIcon(
                        onClick = {
                            scope.launch {
                                try {
                                    psEdit.check()
                                    val copyText = psEdit.proxyServerCopyTextOrNull(
                                        context = context,
                                        appState = appState,
                                        serverId = serverId,
                                        groupId = groupId,
                                    )
                                    if (copyText == null) {
                                        tipNotifier.show(unsupportedMessage)
                                    } else {
                                        clipboard.setPlainText(copyText)
                                        tipNotifier.show(copiedMessage)
                                    }
                                } catch (e: IllegalArgumentException) {
                                    tipNotifier.show(validationMessageOf(e))
                                }
                            }
                        },
                        imageVector = MiuixIcons.Copy,
                    )
                    NavigationIcon(
                        onClick = {
                            try {
                                psEdit.check()
                                if (resultKey != null && serverId != null) {
                                    navigator.setResult(
                                        resultKey,
                                        ProxyServerEditResult(
                                            serverId = serverId,
                                            server = psEdit,
                                            groupId = groupId,
                                            returnGroupId = returnGroupId,
                                        ),
                                    )
                                } else {
                                    ps.update(psEdit)
                                    navigator.pop()
                                }
                            } catch (e: IllegalArgumentException) {
                                scope.launch {
                                    tipNotifier.show(validationMessageOf(e))
                                }
                            }
                        },
                        imageVector = MiuixIcons.Ok,
                    )
                },
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        Box(
            modifier = Modifier.imePadding(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    topAppBarScrollBehavior,
                ),
                contentPadding = contentPadding,
            ) {
                proxyServerEditorContent(psEdit, editorOptions)
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
