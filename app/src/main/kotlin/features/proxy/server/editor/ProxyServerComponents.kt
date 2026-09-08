// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

internal fun CharSequence.isDigitsOnly(): Boolean {
    if (isEmpty()) return true
    return all { char -> char.isDigit() }
}

internal data class ProxyServerEditorGroupOption(
    val id: Int?,
    val label: String,
)

internal data class ProxyServerEditorMemberOption(
    val id: Int,
    val label: String,
)
