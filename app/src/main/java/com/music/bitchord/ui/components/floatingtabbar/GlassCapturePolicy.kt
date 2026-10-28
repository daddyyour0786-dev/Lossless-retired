package com.music.bitchord.ui.components.floatingtabbar

/**
 * The selection lens is the only reader of the exported bar and tab layers.
 * At rest it is replaced by the flat indicator, so recording those layers
 * on every scroll frame would do work nobody can see.
 */
internal fun needsGlassPillBackdrop(
    glassPillEnabled: Boolean,
    hasSelection: Boolean,
    isFlat: Boolean,
): Boolean = glassPillEnabled && hasSelection && !isFlat
