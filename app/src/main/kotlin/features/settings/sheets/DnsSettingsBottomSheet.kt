// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import app.R
import engine.network.isIpv4Address
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun DnsSettingsBottomSheet(
    show: Boolean,
    primary: String,
    secondary: String,
    onPrimaryChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val invalidMessage = stringResource(R.string.settings_dns_address_invalid)
    val primaryError = dnsAddressError(primary, invalidMessage)
    val secondaryError = dnsAddressError(secondary, invalidMessage)

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.settings_dns),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (primaryError == null && secondaryError == null) {
                        onSave(primary.trim(), secondary.trim())
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SettingsTextField(
                    value = primary,
                    onValueChange = onPrimaryChange,
                    label = stringResource(R.string.settings_dns_primary),
                    errorText = primaryError,
                    sanitizeInput = ::sanitizeDnsAddress,
                )
                SettingsTextField(
                    value = secondary,
                    onValueChange = onSecondaryChange,
                    label = stringResource(R.string.settings_dns_secondary),
                    errorText = secondaryError,
                    sanitizeInput = ::sanitizeDnsAddress,
                )
            }
        }
    }
}

private fun sanitizeDnsAddress(value: String): String {
    return value.filter { it.isDigit() || it == '.' }.take(15)
}

private fun dnsAddressError(value: String, invalidMessage: String): String? {
    return if (isIpv4Address(value.trim())) null else invalidMessage
}
