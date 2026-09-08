// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

internal suspend fun parseProxyServersFromJsonConfig(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    return EmptyProxyServerImportResult
}
