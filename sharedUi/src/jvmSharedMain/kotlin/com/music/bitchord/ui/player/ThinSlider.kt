package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.MixBlend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Music's scrubber: a hairline capsule with no thumb knob, which
 * thickens under your finger and settles back when you let go. Material's
 * Slider can't be shaped like this — it always draws a thumb and a tall
 * track — so this is drawn directly.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    /**
     * Sends the same travelling sheen across the bar while a version swap is crossfading.
     * Kept separate from [loading]: a mix is playback feedback, not a wait state. An Automix
     * blend draws [mixPulse] instead and suppresses this.
     */
    mixing: Boolean = false,
    /**
     * The Automix blend's beat pulse — see [rememberMixPulse]. While a blend runs the fill
     * covers the whole bar, through both the outgoing and the incoming song, and breathes with
     * the blend's beat. Shared with whatever else pulses alongside the bar, so they stay in step.
     */
    mixPulse: MixPulse? = null,
    /**
     * Sends a travelling sheen across the whole bar — played *and* unplayed,
     * the bar's full thickness — for as long as it is true.
     *
     * The wait a version switch spends fetching and measuring the other cut is
     * a wait with no measurable fraction to draw, and a stock indeterminate
     * line sat on the scrubber like a second, uglier bar beside the one the
     * listener is already watching. The sheen claims the bar itself instead:
     * no extra chrome, no slot of its own, nothing shifting under it on the
     * frame the eye lands — just motion along the bar, pointing the way the
     * music is going.
     */
    loading: Boolean = false,
    /**
     * Span of the track, as fractions of its duration, that the next Automix
     * transition is planned to occupy. Drawn as a brighter stretch of the
     * unplayed bar so the mix is visible before it arrives.
     */
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    /**
     * Draw the bar as the one LastWave draws: a hairline for the unplayed part,
     * and for the part that has played, three animated waves rolling out of
     * that line in graded tones of the accent, with a leading dot.
     *
     * Ported from LastWave rather than approximated, because the shape is the
     * point: three layers at three wavelengths and three speeds, each lifted
     * above the baseline by a sine and faded in at both ends of the played
     * stretch, so the crests read as liquid *inside* a bar of known height
     * instead of as a squiggle where a bar should be. It also behaves like one:
     * the waves only travel while the track is playing, flatten almost to the
     * line under a finger so the playhead can be read while it is dragged, and
     * stop dead when paused.
     *
     * The height is the one real difference from the capsule: the waves need
     * somewhere to go, so this draws in a taller box than the flat bar does.
     */
    wavy: Boolean = false,
    /** Whether the track is playing — the waves travel only while it is. */
    playing: Boolean = false,
    /** The hue the waves and the leading dot are drawn in. */
    accent: Color = Color.White,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
    /** Halfway between the two track colours: visible against unplayed, invisible under played. */
    markerColor: Color = Color.White.copy(alpha = 0.5f),
) {
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )
    // The sheen gets a minimum beat even when the switch resolves instantly —
    // a cached offset can land in a few hundred milliseconds — so its entry,
    // its sweep and its hand-over to the progress bar always play out as one
    // continuous morph, whenever the wait happened to start and stop. A flip
    // back to loading during the hold cancels it and the sheen simply stays,
    // so rapid toggling never blinks the bar out mid-morph.
    var shownLoading by remember { mutableStateOf(loading) }
    var sheenStart by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(loading) {
        if (loading) {
            sheenStart = System.nanoTime()
            shownLoading = true
        } else {
            val remaining = LOADING_MIN_MS - (System.nanoTime() - sheenStart) / 1_000_000L
            if (remaining > 0) delay(remaining)
            shownLoading = false
        }
    }
    // Both operations let the sheen claim the bar. A version switch is latched for a minimum
    // beat; a version swap's crossfade follows the audio engine and fades when it finishes.
    // An Automix blend has its own treatment — the beat pulse — and keeps the fill.
    val sheenVisible = shownLoading || (mixing && mixPulse?.active != true)
    val fillFactor by animateFloatAsState(
        targetValue = if (sheenVisible) 0f else 1f,
        animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
        label = "fillFactor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Generous invisible touch target — the visible bar is only ~7dp.
            // The wavy bar draws taller so its waves have room above the line,
            // so its touch target grows with it.
            .height(if (wavy) WAVY_HEIGHT else activeHeight + 22.dp)
            // One gesture loop for both taps and drags. Two separate detectors
            // — a drag one plus a tap one — meant taps never landed: the drag
            // detector took the pointer and a tap has no drag to report.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (wavy) {
            WavyTrack(
                value = value,
                // Read inside the draw, so a blend's per-frame cover and a
                // switch's morph invalidate the canvas rather than the whole
                // scrubber and its two times.
                coverOf = { if (dragging) 0f else mixPulse?.cover ?: 0f },
                fillOf = { fillFactor },
                dragging = dragging,
                playing = playing,
                accent = accent,
                inactiveColor = inactiveColor,
                markerColor = markerColor,
                transitionWindow = transitionWindow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WAVY_HEIGHT),
            )
        } else Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            // Between the two track colours, and drawn *under* the played fill:
            // once the playhead reaches the window the transition is no longer
            // upcoming, and the ordinary progress colour taking it over is what
            // says so.
            transitionWindow?.let { window ->
                val from = size.width * window.start.coerceIn(0f, 1f)
                val to = size.width * window.endInclusive.coerceIn(0f, 1f)
                if (to > from) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(from, 0f),
                        size = Size(to - from, size.height),
                        cornerRadius = radius,
                    )
                }
            }
            // While a blend runs the whole bar is the fill, outgoing song and
            // incoming alike, and it is the fill's opacity that carries the mix:
            // brightest on each beat, easing down between them over the unplayed
            // track colour. A drag in progress always shows the finger.
            val cover = if (dragging) 0f else mixPulse?.cover ?: 0f
            val base = value.coerceIn(0f, 1f)
            val fraction = base + (1f - base) * cover
            // Scaled by [fillFactor]: retracted to nothing while the sheen
            // runs (two white signals on one bar would read as progress
            // fighting the wait) and slid back in when the switch lands. The
            // capsule's minimum width rides the same factor, so the nub at
            // zero progress retires with the fill instead of sitting as a
            // dot under the sheen.
            val filled = size.width * fraction * fillFactor
            if (filled > 0f) {
                val alpha = if (dragging) activeColor.alpha else mixPulse?.alpha(activeColor.alpha) ?: activeColor.alpha
                drawRoundRect(
                    color = activeColor.copy(alpha = alpha),
                    size = Size(
                        filled.coerceAtLeast(size.height * fillFactor).coerceAtMost(size.width),
                        size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
        // Composed only while switching or mixing. The infinite animation therefore costs no
        // frames during ordinary playback, and AnimatedVisibility lets it leave gracefully.
        AnimatedVisibility(
            visible = sheenVisible,
            // Grown out of the bar's own left end — where the progress fill
            // begins — instead of slid in from a third of its own width: the
            // capsule is full-bleed, so that slide started past the screen
            // edge and flew in from outside the display. Same duration and
            // curve as the fill's retraction, so the swap reads as one morph.
            enter = fadeIn(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)),
        ) {
            MixSheen(height = if (wavy) WAVY_HEIGHT else height, color = activeColor)
        }
    }
}

private const val SHEEN_BAND_FRACTION = 0.34f

/**
 * One beat clock for everything that pulses with an Automix blend — the seek bar's fill and
 * the "Mixing" label — so the two can never drift out of step with each other.
 *
 * Every value here is snapshot state meant to be read in draw or a graphics layer, where a
 * change costs a redraw rather than a recomposition.
 */
@Stable
class MixPulse internal constructor(private val blend: () -> MixBlend?) {
    /**
     * Whether a blend is running right now. Derived, so composition that reads it recomposes
     * when a blend starts or ends — not every time the blend's beat grid is republished.
     */
    val active: Boolean by derivedStateOf { blend() != null }

    /** Whether the blend is playing; a paused one lets the pulse settle and the clock stop. */
    internal val playing: Boolean by derivedStateOf { blend()?.playing == true }

    internal val coverAnim = Animatable(0f)
    internal val depthAnim = Animatable(0f)
    internal var level by mutableFloatStateOf(0f)

    /** Kept across restarts of the clock, so a blend ending never jumps the phase. */
    internal var phase = 0.0
    internal var locked = false

    /** 0..1: how far the blend has claimed the bar and the label, eased in and out. */
    val cover: Float get() = coverAnim.value

    /**
     * [fullAlpha] as the pulse modulates it: [fullAlpha] on the beat, [PULSE_REST] of it at the
     * half-beat, and [fullAlpha] throughout whenever the pulse is faded out or switched off.
     */
    fun alpha(fullAlpha: Float, restShare: Float = PULSE_REST): Float {
        val rest = fullAlpha * restShare
        val pulsed = rest + (fullAlpha - rest) * level
        return fullAlpha + (pulsed - fullAlpha) * depthAnim.value
    }

    internal fun blendNow(): MixBlend? = blend()
}

/**
 * Runs the beat clock for [mixBlend]. [enabled] off (reduced motion) keeps the cover — the bar
 * still fills and the label still shows — but holds them steady instead of pulsing.
 */
@Composable
fun rememberMixPulse(mixBlend: () -> MixBlend?, enabled: Boolean): MixPulse {
    val current by rememberUpdatedState(mixBlend)
    val pulse = remember { MixPulse { current() } }
    val active = pulse.active
    val playing = pulse.playing
    LaunchedEffect(active, playing, enabled) {
        launch {
            pulse.coverAnim.animateTo(
                if (active) 1f else 0f,
                tween(durationMillis = COVER_MS, easing = FastOutSlowInEasing),
            )
        }
        launch {
            // Paused mid-blend, the pulse settles to a steady bar rather than beating on
            // over silence — and the frame clock below gets to stop.
            pulse.depthAnim.animateTo(
                if (active && playing && enabled) 1f else 0f,
                tween(durationMillis = PULSE_FADE_MS, easing = FastOutSlowInEasing),
            )
        }
        if (!enabled) return@LaunchedEffect
        // The pulse's own beat clock. The blend publishes a beat anchor every fade tick, and
        // each one is re-derived from player positions that wobble by a few tens of ms — drawn
        // straight, that wobble is the pulse lurching. So this runs a free phase at the blend's
        // tempo and only leans toward the published anchor a little each frame: it follows a
        // real tempo change or the grid moving to the other song within a fraction of a
        // second, and never visibly jumps. Runs until the pulse has faded out after the blend.
        var last = System.nanoTime()
        while ((active && playing) || pulse.depthAnim.value > 0f) {
            withFrameNanos {
                val now = System.nanoTime()
                val dt = (now - last).coerceIn(0L, MAX_FRAME_NANOS)
                last = now
                val blend = pulse.blendNow()
                val beatNanos = blend?.beatMs?.takeIf { it > 0f }?.let { it * 1_000_000.0 }
                var phase = pulse.phase
                if (beatNanos != null && blend != null) {
                    phase += dt / beatNanos
                    val target = Math.floorMod(now - blend.beatAnchorNanos, beatNanos.toLong()) / beatNanos
                    // Shortest way round the circle to where the beat should be. The very first
                    // lock snaps — nothing is showing yet — and every one after only leans.
                    val error = ((target - phase) % 1.0 + 1.5) % 1.0 - 0.5
                    phase += if (pulse.locked) error * PHASE_PULL else error
                    pulse.locked = true
                } else {
                    // No tempo known, or the blend just ended: a slow breath from wherever the
                    // phase already is, which the depth fade then takes away.
                    phase += dt / BREATH_NANOS
                    if (blend != null) pulse.locked = false
                }
                phase -= kotlin.math.floor(phase)
                pulse.phase = phase
                pulse.level = beatShape(phase.toFloat())
            }
        }
        pulse.locked = false
    }
    return pulse
}

/** Height of the wavy bar's drawing box — taller than the capsule so the waves have room. */
private val WAVY_HEIGHT = 40.dp

/** Where the wavy bar's flat baseline sits in that box, as a fraction of its height. */
private const val WAVE_BASELINE = 0.58f

/** Thickness of the wavy bar's baseline, and of the unplayed hairline it continues. */
private val WAVE_LINE = 3.5.dp

/** Radius of the dot that marks the playhead on the wavy bar. */
private val WAVE_THUMB = 4.dp

/** How long the fill takes to glide out to full width, and back, around a blend. */
private const val COVER_MS = 700

/** How long the beat pulse takes to swell in as a blend starts and settle as it ends. */
private const val PULSE_FADE_MS = 900

/** Opacity between beats, as a share of the fill's own: low enough to read, never gone. */
private const val PULSE_REST = 0.1f

/** Share of the remaining phase error corrected each frame; small enough to be invisible. */
private const val PHASE_PULL = 0.06

/** Breathing period when neither track's tempo is known. */
private const val BREATH_NANOS = 1_800_000_000.0

/** A dropped frame or a backgrounded app must not fling the phase forward. */
private const val MAX_FRAME_NANOS = 100_000_000L

/**
 * The pulse's brightness through one beat, 0..1: a raised cosine squared, peaking on the
 * beat and easing to nothing at the half-beat. Smooth in value and slope everywhere, so
 * there is no edge in it for the eye to catch — a breath on each beat rather than a flash.
 */
private fun beatShape(phase: Float): Float {
    val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * phase)
    return c * c
}

/** Length of one morph step — entry, fill retraction, fill return, exit — all on the same curve. */
private const val MORPH_MS = 450

/** Shortest time the sheen stays up, so even an instant switch still plays its morph. */
private const val LOADING_MIN_MS = 600L

/**
 * Spacing between the sampled points of a wave.
 *
 * A compromise, not a measurement: at 1.5 dp a 1080 px bar is a few hundred
 * points, which a phone draws in one frame without noticing, and the curve is
 * smooth enough that no crest reads as a corner.
 */
private val WAVE_STEP = 1.5.dp

/**
 * The three wave layers, back to front: wavelength, peak amplitude, and the
 * period of one full travel.
 *
 * Back to front is slowest to fastest and widest to narrowest, which is what
 * makes the stack read as depth rather than as one wave drawn three times — the
 * long slow swell carries the shape, the quick thin one at the front gives it
 * its edge.
 */
private val WAVE_LAYERS = listOf(
    WaveLayer(wavelength = 150.dp, amplitude = 11.dp, periodSeconds = 2.4f, stroke = 1.0.dp),
    WaveLayer(wavelength = 115.dp, amplitude = 8.5.dp, periodSeconds = 1.8f, stroke = 1.2.dp),
    WaveLayer(wavelength = 80.dp, amplitude = 6.5.dp, periodSeconds = 1.3f, stroke = 1.6.dp),
)

/** One of [WAVE_LAYERS]. */
private class WaveLayer(
    val wavelength: Dp,
    val amplitude: Dp,
    val periodSeconds: Float,
    val stroke: Dp,
)

/**
 * How much of a wave the finger flattens it to. Not zero: the bar has to stay a
 * wave while it is dragged, or the shape jumps at the moment of touch.
 */
private val WAVE_DRAG_AMPLITUDE = 1.2.dp

/** Length of the eased run-in at each end of the played stretch, before it is capped to fit. */
private val WAVE_ENVELOPE = 44.dp

/**
 * The wavy bar — see [ThinSlider.wavy].
 *
 * Three sine layers at three wavelengths, all drawn *above* a flat baseline and
 * clipped to the stretch that has played. Each layer's amplitude is tapered by a
 * smootherstep at both ends of that stretch, so the stack starts from the line
 * at the left edge, builds to full height in the middle of the played part, and
 * settles back onto the line exactly at the playhead: waves that grow out of the
 * bar rather than a squiggle laid over it.
 *
 * The odd-looking envelope is what makes it read as one object. Without it the
 * waves pop into existence at x = 0 and are cut off dead at the playhead, with
 * both edges visibly wrong at once — the left one as a step in the curve, the
 * right one as a wall the playhead sits against. Tapering both ends costs one
 * multiply per sample and removes both.
 *
 * Nothing here animates on its own account: the layers share one clock, that
 * clock only advances while [playing], and the per-layer amplitudes are the only
 * animated values. A paused bar therefore costs no frames at all, and the waves
 * resume from where they stopped instead of snapping back to the start of a
 * cycle.
 */
@Composable
private fun WavyTrack(
    value: Float,
    coverOf: () -> Float,
    fillOf: () -> Float,
    dragging: Boolean,
    playing: Boolean,
    accent: Color,
    inactiveColor: Color,
    markerColor: Color,
    transitionWindow: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
) {
    // Seconds since the waves last started moving. Kept across a pause, so the
    // picture on resume is the one before it.
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // Advanced by the frame's own delta rather than measured from a
                // start instant, and clamped: a backgrounded app must not come
                // back with the waves flung forward by however long it was away.
                clock += ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_WAVE_STEP_SECONDS)
                last = now
            }
        }
    }

    // A finger on the bar flattens it almost to the line — read the playhead,
    // not the wave — and it swells back when the finger lifts.
    val amplitudes = WAVE_LAYERS.map { layer ->
        animateFloatAsState(
            targetValue = if (dragging) WAVE_DRAG_AMPLITUDE.value else layer.amplitude.value,
            animationSpec = tween(durationMillis = 180),
            label = "waveAmplitude${layer.wavelength.value}",
        )
    }

    // Graded tones of one accent rather than three hues: the stack has to read as
    // light through the same liquid, and the player's accent is what the rest of
    // its chrome is drawn in.
    val lit = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val deep = remember(accent) { lerp(accent, Color.Black, 0.32f) }
    val soft = remember(accent) { lerp(accent, Color.White, 0.80f) }
    val mid = remember(accent) { lerp(accent, Color.White, 0.28f) }
    val waveColors = remember(lit, deep, soft, mid) {
        listOf(
            listOf(soft.copy(alpha = 0.40f), lerp(soft, Color.Black, 0.15f).copy(alpha = 0.30f)),
            listOf(mid.copy(alpha = 0.66f), lerp(lit, deep, 0.5f).copy(alpha = 0.55f)),
            listOf(lit.copy(alpha = 0.95f), deep.copy(alpha = 0.88f)),
        )
    }
    // Allocated once and reset per frame: three paths rebuilt sixty times a
    // second is garbage the drawing loop does not need to make.
    val contours = remember { List(WAVE_LAYERS.size) { Path() } }
    val clipping = remember { Path() }

    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val line = WAVE_LINE.toPx()
        val half = line / 2f
        val baseline = height * WAVE_BASELINE
        val crestLine = baseline - half
        val base = value.coerceIn(0f, 1f)
        val fraction = (base + (1f - base) * coverOf()).coerceIn(0f, 1f) * fillOf()
        val thumbX = (fraction * width).coerceIn(0f, width)

        // The unplayed part is a hairline, not a wave: the height is where the
        // music has got to, and a wave there would be shape for its own sake.
        drawLine(
            color = inactiveColor,
            start = Offset(0f, baseline),
            end = Offset(width, baseline),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        // Where the next Automix transition is planned, on the stretch still to
        // come — under the playhead's own fill, like the capsule's marker.
        transitionWindow?.let { window ->
            val from = width * window.start.coerceIn(0f, 1f)
            val to = width * window.endInclusive.coerceIn(0f, 1f)
            if (to > from) {
                drawLine(
                    color = markerColor,
                    start = Offset(from, baseline),
                    end = Offset(to, baseline),
                    strokeWidth = line * 1.7f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (thumbX > 0.5f) {
            // The played stretch is clipped to the baseline's own capsule, so a
            // crest at either end cannot spill past the bar's rounded ends.
            clipping.reset()
            clipping.addRoundRect(
                RoundRect(
                    rect = Rect(left = -half, top = 0f, right = thumbX + half, bottom = height),
                    topLeft = CornerRadius(half, half),
                    bottomLeft = CornerRadius(half, half),
                    topRight = CornerRadius(half, half),
                    bottomRight = CornerRadius(half, half),
                ),
            )
            val activeWidth = thumbX.coerceAtLeast(1f)
            val step = WAVE_STEP.toPx()
            // Tapered over a fixed run-in, but never over more than half the
            // played stretch — at the very start of a track there is nothing to
            // taper over, and a full-length envelope there would flatten the
            // whole thing to the line.
            val runIn = minOf(WAVE_ENVELOPE.toPx(), thumbX * 0.48f)
            val invRunIn = if (runIn > 0f) 1f / runIn else 0f

            clipPath(clipping) {
                WAVE_LAYERS.forEachIndexed { index, layer ->
                    val path = contours[index]
                    path.reset()
                    path.moveTo(0f, crestLine)
                    val amplitude = amplitudes[index].value.dp.toPx()
                    val wavelength = layer.wavelength.toPx()
                    val phase = 2f * PI.toFloat() * (clock / layer.periodSeconds)
                    val cyclesPerPx = 2f * PI.toFloat() / wavelength
                    var x = 0f
                    while (x < thumbX) {
                        val envelope = if (invRunIn > 0f) {
                            smootherstep(x * invRunIn) * smootherstep((thumbX - x) * invRunIn)
                        } else {
                            1f
                        }
                        // Above the baseline only: a wave that dips below it
                        // would trade silhouettes with the hairline it grows out
                        // of and lose the bar.
                        val lift = (0.5f + 0.5f * sin(x * cyclesPerPx - phase)) * amplitude * envelope
                        path.lineTo(x, crestLine - lift)
                        x += step
                    }
                    // Landed on the line exactly at the playhead, so the wave
                    // meets the thumb instead of stopping short of it.
                    path.lineTo(thumbX, crestLine)
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = waveColors[index],
                            startX = 0f,
                            endX = activeWidth,
                        ),
                        style = Stroke(
                            width = layer.stroke.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                // The baseline comes back over the waves' feet, so the stack is
                // seated in it rather than floating on it.
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(lit, deep),
                        startX = 0f,
                        endX = activeWidth,
                    ),
                    start = Offset(0f, baseline),
                    end = Offset(thumbX, baseline),
                    strokeWidth = line,
                    cap = StrokeCap.Round,
                )
            }

            // The playhead: a soft halo so it reads against a bright cover, the
            // dot itself in the accent, and a white ring that keeps it visible on
            // artwork the accent disappears into.
            val thumb = WAVE_THUMB.toPx()
            drawCircle(
                color = accent.copy(alpha = 0.30f),
                radius = thumb + 3.dp.toPx(),
                center = Offset(thumbX, baseline),
            )
            drawCircle(color = lit, radius = thumb, center = Offset(thumbX, baseline))
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = thumb,
                center = Offset(thumbX, baseline),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}

/**
 * Smootherstep, 0..1, flat in value *and* slope at both ends.
 *
 * A plain smoothstep leaves a visible kink where the wave hands over to its
 * taper; this one has zero third derivative at the ends as well, which is the
 * cheapest way to make the join invisible at the sizes a seek bar is drawn.
 */
private fun smootherstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * c * (c * (c * 6f - 15f) + 10f)
}

/** Largest clock advance one frame may contribute — see the wave clock in [WavyTrack]. */
private const val MAX_WAVE_STEP_SECONDS = 0.1f

/**
 * A highlight sweeping the bar's full thickness — played *and* unplayed
 * alike — about once a second, at the progress fill's own brightness.
 *
 * Loading has no measurable fraction to draw, so an indeterminate indicator
 * has to draw *something*: the usual choice is a thin line claiming a
 * sliver of the scrubber's height, which reads as a second, lesser bar
 * growing out of the first. This band instead takes the whole thickness the
 * scrubber already occupies and moves along it, so the wait looks like the
 * bar itself moving rather than an alien element parked on top — no gap, no
 * slot, no shifting of the controls below it.
 *
 * One pass a second rather than the old two: any faster and the band is a
 * strobe the eye tracks instead of a wait it can ignore. The pass reverses
 * at each edge rather than restarting from the far one.
 */
@Composable
private fun MixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = -SHEEN_BAND_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            // Reverses rather than restarting: a restart teleports the band
            // back to the far edge every second, which is a hitch the eye
            // catches each time. Ping-pong has no edge to fall off.
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * SHEEN_BAND_FRACTION
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            // The band is clipped to the same capsule the track is drawn
            // with: a plain rect bared square corners wherever the sweep
            // crossed the rounded ends.
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
