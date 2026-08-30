// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.R
import engine.xray.DnsModeCustom
import engine.xray.DnsModeFast
import engine.xray.DnsModeTunnel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun DnsSettingsBottomSheet(
    show: Boolean,
    mode: Int,
    primary: String,
    secondary: String,
    directDns: String,
    directDnsDomains: String,
    onModeChange: (Int) -> Unit,
    onPrimaryChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onDirectDnsChange: (String) -> Unit,
    onDirectDnsDomainsChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (Int, String, String, String, String) -> Unit,
) {
    val modeOptions = listOf(
        stringResource(R.string.settings_dns_mode_fast),
        stringResource(R.string.settings_dns_mode_tunnel),
        stringResource(R.string.settings_dns_mode_custom),
    )
    val invalidMessage = stringResource(R.string.settings_dns_address_invalid)
    val primaryError = if (mode == DnsModeFast) null else dnsAddressError(primary, invalidMessage)
    val secondaryError = if (mode == DnsModeFast) null else dnsAddressError(secondary, invalidMessage)

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
                        onSave(
                            mode,
                            primary.trim(),
                            secondary.trim(),
                            directDns.trim(),
                            directDnsDomains.trim(),
                        )
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_dns_mode),
                    items = modeOptions,
                    selectedIndex = when (mode) {
                        DnsModeFast -> 0
                        DnsModeTunnel -> 1
                        else -> 2
                    },
                    onSelectedIndexChange = { index ->
                        onModeChange(
                            when (index) {
                                0 -> DnsModeFast
                                1 -> DnsModeTunnel
                                else -> DnsModeCustom
                            }
                        )
                    },
                )

                Spacer(Modifier.height(8.dp))

                if (mode == DnsModeFast) {
                    Text(
                        text = stringResource(R.string.settings_dns_mode_fast_summary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AnimatedVisibility(
                    visible = mode != DnsModeFast,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
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

                AnimatedVisibility(
                    visible = mode == DnsModeCustom,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        SettingsTextField(
                            value = directDns,
                            onValueChange = onDirectDnsChange,
                            label = stringResource(R.string.settings_direct_dns_input),
                            errorText = null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        SettingsTextField(
                            value = directDnsDomains,
                            onValueChange = onDirectDnsDomainsChange,
                            label = stringResource(R.string.settings_direct_dns_domains),
                            errorText = null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                }
            }
        }
    }
}

private fun sanitizeDnsAddress(value: String): String {
    return value.filter { it.isDigit() || it == '.' }.take(15)
}

private fun dnsAddressError(value: String, invalidMessage: String): String? {
    return if (engine.network.isIpv4Address(value.trim())) null else invalidMessage
}