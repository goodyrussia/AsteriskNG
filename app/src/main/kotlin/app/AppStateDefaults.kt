// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package app

import features.subscription.DefaultSubscriptionGroupId
import features.subscription.DefaultSubscriptionUserAgent

val DefaultSubscriptionGroups = listOf(
    SubscriptionGroupState(
        id = DefaultSubscriptionGroupId,
        name = "默认",
        url = "",
        userAgent = DefaultSubscriptionUserAgent,
        updateInterval = "",
        enabled = true,
        builtIn = true,
    ),
)
