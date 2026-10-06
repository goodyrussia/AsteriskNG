// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun AppTheme(
    keyColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val controller = remember(keyColor) {
        ThemeController(
            ColorSchemeMode.MonetDark,
            keyColor = keyColor,
            colorSpec = AndroidDynamicColorSpec,
            paletteStyle = AndroidDynamicPaletteStyle,
        )
    }
    MiuixTheme(controller) {
        SystemBarAppearance()
        content()
    }
}

@Composable
fun isInDarkTheme(): Boolean = true

@Composable
private fun SystemBarAppearance() {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

private val AndroidDynamicColorSpec = ThemeColorSpec.Spec2025
private val AndroidDynamicPaletteStyle = ThemePaletteStyle.TonalSpot
