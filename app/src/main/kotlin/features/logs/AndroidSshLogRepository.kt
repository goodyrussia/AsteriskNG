// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object AndroidSshLogRepository : InMemoryCoreLogRepository() {
    fun initialize(context: Context) {
        // Repository starts empty; the tailer appends parsed lines.
    }
}