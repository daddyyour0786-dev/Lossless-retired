package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The hairline a floating bar wears along its edge.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. A half-pixel of
 * white is what draws the line: enough to read as the edge of something solid,
 * not enough to read as a border.
 *
 * Shared by [FloatingBottomBar], [FrostedTopBar]'s floating controls and the
 * detail page's translucent buttons, so every floating surface in the app has
 * the same edge.
 */
internal val CHROME_EDGE_WIDTH = 0.5.dp

internal val CHROME_EDGE_COLOR = Color.White.copy(alpha = 0.10f)
