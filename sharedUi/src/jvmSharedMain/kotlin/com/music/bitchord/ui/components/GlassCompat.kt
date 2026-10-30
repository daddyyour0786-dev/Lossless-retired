package com.music.bitchord.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Three small pieces upstream keeps in files this repo owns, carried here so
 * the ported glass surfaces can be dropped in verbatim.
 *
 * They are not new ideas — each replaces a name this repo spells differently
 * ([SelectionSpring] rather than [GlassSpring], `HideStatusBar` rather than
 * `HideSystemBars`) — so this file is the seam rather than a second opinion.
 */

/**
 * The spring the glass selection indicator and its glyphs travel on.
 *
 * Stiffness 320 puts the whole movement at roughly a third of a second: quick
 * enough that the tap and the arrival feel like one event, damped enough to
 * take one soft pass beyond the mark and be done.
 */
val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

/**
 * Nothing that only decorates needs more than thirty frames a second.
 *
 * Every frame of anything inside a page is a frame of the whole page, and the
 * glass bars sample it live — they record it again and re-run their blur — so
 * an ornament asked for on every vsync keeps a 120Hz phone redrawing without
 * pause until it runs hot and throttles.
 */
const val MOTION_FRAME_GAP_MS = 25L

/**
 * A track's thumbnail box at [height]: square for a song, 16:9 at the same
 * height for a music video — its picture's own shape, as YouTube Music lists
 * one, rather than a still cropped into a square.
 */
fun Modifier.trackArtSize(height: Dp, isVideo: Boolean): Modifier =
    if (isVideo) size(width = height * (16f / 9f), height = height) else size(height)
