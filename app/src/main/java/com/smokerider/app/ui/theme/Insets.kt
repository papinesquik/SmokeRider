package com.smokerider.app.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Evita la sovrapposizione con status bar (top) e navigation/gesture bar (bottom).
 * Usa API stabili (statusBars + navigationBars) e consente extraTop/extraBottom.
 */
@Composable
fun Modifier.screenInsets(
    includeTop: Boolean = true,
    includeBottom: Boolean = true,
    extraTop: Dp = 24.dp,
    extraBottom: Dp = 0.dp
): Modifier {
    var m = this
    if (includeTop) {
        m = m.windowInsetsPadding(WindowInsets.statusBars)
    }
    if (includeBottom) {
        m = m.windowInsetsPadding(WindowInsets.navigationBars)
    }
    return m.padding(top = extraTop, bottom = extraBottom)
}
