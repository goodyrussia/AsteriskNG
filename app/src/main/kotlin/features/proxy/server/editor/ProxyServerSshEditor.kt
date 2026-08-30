// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.R
import features.proxy.server.model.Ssh
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun LazyListScope.sshProxyServer(sshEdit: Ssh) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current
        val modeOptions = remember { listOf("direct", "proxy", "tls", "tls_proxy") }
        val modeLabels = listOf(
            stringResource(R.string.ssh_editor_mode_direct),
            stringResource(R.string.ssh_editor_mode_proxy),
            stringResource(R.string.ssh_editor_mode_tls),
            stringResource(R.string.ssh_editor_mode_tls_proxy),
        )
        val modeIndex = remember { mutableIntStateOf(modeOptions.indexOf(sshEdit.mode).coerceAtLeast(0)) }

        val tlsVersionOptions = remember { listOf("1.0", "1.1", "1.2", "1.3") }
        val tlsVersionIndex = remember {
            mutableIntStateOf(tlsVersionOptions.indexOf(sshEdit.tlsVersion).coerceAtLeast(2))
        }

        val splitOptions = remember { listOf("none", "instant", "delay", "split", "split_delay") }
        val splitLabels = listOf(
            stringResource(R.string.ssh_editor_payload_split_none),
            stringResource(R.string.ssh_editor_payload_split_instant),
            stringResource(R.string.ssh_editor_payload_split_delay),
            stringResource(R.string.ssh_editor_payload_split_none),
            stringResource(R.string.ssh_editor_payload_split_delay),
        )
        val splitIndex = remember {
            mutableIntStateOf(splitOptions.indexOf(sshEdit.payloadSplitMode).coerceAtLeast(0))
        }
        var payloadEnabled by remember { mutableStateOf(sshEdit.payloadEnabled) }

        SmallTitle(text = stringResource(R.string.proxy_editor_properties))
        TextField(
            label = stringResource(R.string.proxy_editor_remarks),
            state = rememberTextFieldState(initialText = sshEdit.remarks),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { sshEdit.remarks = asCharSequence().toString() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_server),
            state = rememberTextFieldState(initialText = sshEdit.server),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { sshEdit.server = asCharSequence().toString() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_port),
            state = rememberTextFieldState(initialText = sshEdit.port),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) { revertAllChanges(); return@InputTransformation }
                sshEdit.port = asCharSequence().toString()
            },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_username_optional),
            state = rememberTextFieldState(initialText = sshEdit.username),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { sshEdit.username = asCharSequence().toString() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_password),
            state = rememberTextFieldState(initialText = sshEdit.password),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { sshEdit.password = asCharSequence().toString() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        // Mode selector
        OverlayDropdownPreference(
            title = stringResource(R.string.ssh_editor_mode),
            items = modeLabels,
            selectedIndex = modeIndex.intValue,
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onSelectedIndexChange = { index ->
                modeIndex.intValue = index
                sshEdit.mode = modeOptions[index]
            },
        )

        // HTTP proxy (shown for proxy/tls_proxy)
        if (sshEdit.mode in listOf("proxy", "tls_proxy")) {
            SmallTitle(text = stringResource(R.string.ssh_editor_http_proxy))
            TextField(
                label = stringResource(R.string.proxy_editor_server),
                state = rememberTextFieldState(initialText = sshEdit.httpProxy),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation { sshEdit.httpProxy = asCharSequence().toString() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            TextField(
                label = stringResource(R.string.proxy_editor_port),
                state = rememberTextFieldState(initialText = sshEdit.httpProxyPort),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation {
                    if (!asCharSequence().isDigitsOnly()) { revertAllChanges(); return@InputTransformation }
                    sshEdit.httpProxyPort = asCharSequence().toString()
                },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            TextField(
                label = stringResource(R.string.proxy_editor_username_optional),
                state = rememberTextFieldState(initialText = sshEdit.proxyUsername),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation { sshEdit.proxyUsername = asCharSequence().toString() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            TextField(
                label = stringResource(R.string.proxy_editor_password_optional),
                state = rememberTextFieldState(initialText = sshEdit.proxyPassword),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation { sshEdit.proxyPassword = asCharSequence().toString() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            SwitchPreference(
                title = stringResource(R.string.ssh_editor_authenticate_proxy),
                checked = sshEdit.authenticateProxy,
                onCheckedChange = { checked -> sshEdit.authenticateProxy = checked },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }

        // TLS fields (shown for tls/tls_proxy)
        if (sshEdit.mode in listOf("tls", "tls_proxy")) {
            SmallTitle(text = stringResource(R.string.ssh_editor_sni))
            TextField(
                label = "SNI",
                state = rememberTextFieldState(initialText = sshEdit.sni),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation { sshEdit.sni = asCharSequence().toString() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.ssh_editor_tls_version),
                items = tlsVersionOptions,
                selectedIndex = tlsVersionIndex.intValue,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onSelectedIndexChange = { index ->
                    tlsVersionIndex.intValue = index
                    sshEdit.tlsVersion = tlsVersionOptions[index]
                },
            )
            SwitchPreference(
                title = stringResource(R.string.ssh_editor_allow_insecure),
                checked = sshEdit.allowInsecure,
                onCheckedChange = { checked -> sshEdit.allowInsecure = checked },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }

        // Payload section
        SmallTitle(text = stringResource(R.string.ssh_editor_payload))
        SwitchPreference(
            title = stringResource(R.string.ssh_editor_payload_enabled),
            checked = payloadEnabled,
            onCheckedChange = { checked ->
                payloadEnabled = checked
                sshEdit.payloadEnabled = checked
            },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
        if (payloadEnabled) {
            TextField(
                label = stringResource(R.string.ssh_editor_payload),
                state = rememberTextFieldState(initialText = sshEdit.payload),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                inputTransformation = InputTransformation { sshEdit.payload = asCharSequence().toString() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.ssh_editor_payload_split),
                items = splitLabels,
                selectedIndex = splitIndex.intValue,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onSelectedIndexChange = { index ->
                    splitIndex.intValue = index
                    sshEdit.payloadSplitMode = splitOptions[index]
                },
            )
            TextField(
                label = stringResource(R.string.ssh_editor_payload_delay_ms),
                state = rememberTextFieldState(initialText = sshEdit.payloadDelayMs),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation {
                    if (!asCharSequence().isDigitsOnly()) { revertAllChanges(); return@InputTransformation }
                    sshEdit.payloadDelayMs = asCharSequence().toString()
                },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }
}