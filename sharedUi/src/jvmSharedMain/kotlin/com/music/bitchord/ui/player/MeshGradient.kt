package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.paletteSwatches
import com.music.bitchord.ui.graphics.toImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.sin

private val FallbackColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

/** The four mesh colours, wrapped so the backdrop can skip recomposition. */
@Immutable
data class MeshPalette(val colors: List<Color>)

/**
 * The Apple Music "Now Playing" backdrop: four luminous colour blobs sampled
 * from the album art, drawn as soft radial gradients and softened into a mesh.
 * Colour changes on track skip crossfade over ~1.4s instead of snapping.
 *
 * The blobs drift when there is a reason to — the player opening, or
 * [trackKey] changing — and then come to rest. They used to orbit forever,
 * which meant redrawing a full-screen layer at display refresh rate for as
 * long as the player was up: the most expensive thing in the app, for motion
 * that reads as ambient at best and is invisible while the phone is in a
 * pocket. The settled frame looks the same; only the battery drain is gone.
 *
 * The drawing itself is cheap for the same reason: the blobs are rasterised
 * into a buffer a fraction of the surface's size and interpolated back up, so
 * the per-frame cost is the same whether the surface is a card or a whole
 * phone. See the note above the Canvas below.
 */
@Composable
fun MeshGradientBackground(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    driftMillis: Int = 8_000,
    /**
     * Keep the blobs orbiting instead of letting them settle.
     *
     * Off everywhere the mesh fills a screen, for the reason in the class note:
     * full-screen motion redrawn at refresh rate is the wrong thing to spend a
     * phone's battery on, and nobody is looking at it. On the Replay's cards it is
     * the opposite trade — the surface is a few hundred dp of a card the user
     * has deliberately opened and is looking straight at, the motion is what
     * makes the card feel like an object rather than a picture of one, and
     * "reduce animation" still stops it dead.
     */
    continuous: Boolean = false,
    /**
     * How far the blobs are smeared. The default is sized for a full screen;
     * a small surface needs proportionally less, or the four colours blend into
     * one wash before they reach its edges.
     *
     * It is the softness, not a RenderEffect radius: the mesh is rasterised at
     * a fraction of the surface — this far, roughly — and interpolated back up.
     * See the note above the Canvas below.
     */
    blurRadius: Dp = 64.dp,
    /**
     * Off for a surface that should read as a still image: colours snap
     * straight to target instead of crossfading, and the blobs never drift
     * on a [trackKey] change, only settling once on first composition. The
     * Replay page and its cards use this — a grid of these redrawing on every
     * card that scrolls past is cost multiplied by however many cards are on
     * screen, for colours that were meant to sit still anyway.
     */
    animated: Boolean = true,
    /**
     * How long a palette change takes to crossfade. A track skip wants it
     * brisk; a surface that changes colour on a timer wants it long enough
     * that one fade runs into the next and the colour never sits still.
     */
    colorFadeMillis: Int = 1400,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()

    val tuned = (palette.colors.ifEmpty { FallbackColors } + FallbackColors)
        .take(4)
        .map { it.tuned() }

    // Each colour slot crossfades independently when the track (palette) changes,
    // unless "reduce animation" is on, in which case colours snap straight to target.
    //
    // The animation states are created here but *read* in the draw lambda below.
    // Reading them here instead would recompose this whole composable on every
    // frame of every crossfade — the four slots and the base, five animations at
    // once — where reading them in the draw phase invalidates only the drawing.
    val colorSpec: AnimationSpec<Color> = if (reduceAnimation || !animated) snap() else tween(colorFadeMillis)
    val colorStates = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index")
    }
    val baseState = animateColorAsState(tuned.first().dimmed(), colorSpec, label = "meshBase")

    // Read in the draw lambda, not here: an Animatable read during draw
    // invalidates only the drawing, leaving composition out of the loop.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, reduceAnimation, continuous, animated) {
        when {
            !animated || reduceAnimation -> phase.snapTo(0f)
            // A full turn at a time, restarted rather than looped with an
            // infinite spec: the blobs' speeds are irrational multiples of each
            // other, so the pattern never repeats, and a linear phase keeps the
            // orbit even instead of easing to a halt each lap.
            continuous -> while (isActive) {
                phase.animateTo(
                    targetValue = phase.value + (2 * PI).toFloat(),
                    animationSpec = tween(driftMillis * 4, easing = LinearEasing),
                )
            }
            else -> phase.animateTo(
                targetValue = phase.value + DRIFT_RADIANS,
                animationSpec = tween(driftMillis, easing = FastOutSlowInEasing),
            )
        }
    }

    // The mesh is drawn into a small offscreen buffer and blown back up to the
    // surface with bilinear filtering, rather than drawn full-size and blurred.
    //
    // That is not a shortcut around the look — it is the same look for a
    // fraction of the price. A blur is a low-pass filter; bilinearly
    // interpolating a render made at one-tenth scale is the same low-pass
    // filter, minus the full-screen pass. `Modifier.blur` is a RenderEffect on
    // the layer, so while the blobs drift it had to be re-applied to a
    // full-screen layer on every frame — the single most expensive thing the
    // player did, and the reason this backdrop visibly stuttered. Here the
    // per-frame work is a ~100×200 rasterisation plus one tiny texture, and at
    // rest the whole lambda stops running because nothing it reads changes.
    //
    // [blurRadius] survives as the caller's word for "how soft": a wider blur
    // is a smaller buffer, so a screen-sized backdrop still gets the softest
    // one while a card gets a tighter mesh.
    //
    // The scale-up, its clip and the buffer's overhang are unchanged from the
    // blurred version: the layer is enlarged 1.3× so the interpolated buffer's
    // edges never reach the visible area, and clipped from outside that scale
    // so the enlargement can't paint over whatever this is standing in.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val meshScope = remember { CanvasDrawScope() }
    val buffer = remember { MeshBuffer() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                scaleX = 1.3f
                scaleY = 1.3f
            },
    ) {
        val baseColor = baseState.value
        drawRect(baseColor)

        val drift = phase.value
        val scale = (blurRadius.value / 6f).coerceIn(2f, BACKDROP_MAX_DOWNSCALE)
        val bufferWidth = (size.width / scale).roundToInt().coerceIn(8, 512)
        val bufferHeight = (size.height / scale).roundToInt().coerceIn(8, 512)
        val (mesh, meshCanvas) = buffer.obtain(bufferWidth, bufferHeight)

        // Rendered in buffer coordinates, so the anchors, the travel and the
        // blob radius are all fractions of it and come back the same size once
        // the buffer is stretched over the surface.
        meshScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = meshCanvas,
            size = Size(bufferWidth.toFloat(), bufferHeight.toFloat()),
        ) {
            drawRect(baseColor)
            colorStates.forEachIndexed { index, state ->
                val color = state.value
                val anchor = MESH_ANCHORS[index]
                val center = Offset(
                    x = (anchor.x + 0.16f * cos(drift * MESH_SPEEDS[index] + index * 1.7f)) * size.width,
                    y = (anchor.y + 0.16f * sin(drift * MESH_SPEEDS[index] * 0.9f + index * 2.3f)) * size.height,
                )
                val radius = size.maxDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }

        drawImage(
            image = mesh,
            srcSize = IntSize(bufferWidth, bufferHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )

        // Gentle scrim so white text stays legible over bright art. Full
        // resolution on purpose: stretching a banded gradient that far is how
        // you get visible steps across the middle of the screen.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.38f),
                ),
            ),
        )
    }
}

/**
 * The buffer [MeshGradientBackground] renders its blobs into, kept between
 * frames so a drifting backdrop allocates nothing at all.
 */
private class MeshBuffer {
    private var bitmap: ImageBitmap? = null
    private var canvas: GraphicsCanvas? = null
    private var width = 0
    private var height = 0

    /** The buffer and the canvas that draws into it, sized to the pair given. */
    fun obtain(width: Int, height: Int): Pair<ImageBitmap, GraphicsCanvas> {
        val current = bitmap
        val currentCanvas = canvas
        if (current != null && currentCanvas != null && this.width == width && this.height == height) {
            return current to currentCanvas
        }
        return ImageBitmap(width, height).let { fresh ->
            val freshCanvas = GraphicsCanvas(fresh)
            bitmap = fresh
            canvas = freshCanvas
            this.width = width
            this.height = height
            fresh to freshCanvas
        }
    }
}

private val MESH_ANCHORS = listOf(
    Offset(0.20f, 0.25f),
    Offset(0.80f, 0.20f),
    Offset(0.75f, 0.80f),
    Offset(0.25f, 0.75f),
)

/** Irrational-ish multiples, so the four blobs never fall back into step. */
private val MESH_SPEEDS = listOf(1f, -0.7f, 0.85f, -1.15f)

/** The smallest a buffer is allowed to get: past this the blobs go soupy. */
private const val BACKDROP_MAX_DOWNSCALE = 10f

/**
 * How long each palette in a [cyclingMeshPalette] is held, which is also how
 * long the fade into it takes — pass it as `colorFadeMillis` so one crossfade
 * runs straight into the next and the colour is never at rest.
 */
const val MESH_CYCLE_MILLIS = 3_600

/**
 * BitChord's own colours, for the surfaces that have no artwork to take them
 * from: the welcome pages and the sign-in card on Home. Starts on Apple
 * Music's red and wanders off from there.
 */
val WelcomeMeshPalettes: List<MeshPalette> = listOf(
    // Red, violet, ember.
    MeshPalette(listOf(Color(0xFFFA2D48), Color(0xFF7B2FF7), Color(0xFFFF7A45), Color(0xFF2E1A8A))),
    // Sunset.
    MeshPalette(listOf(Color(0xFFFF4E8A), Color(0xFFFF9A3C), Color(0xFFE0245E), Color(0xFF6A1B9A))),
    // Ocean.
    MeshPalette(listOf(Color(0xFF1E88E5), Color(0xFF00BFA5), Color(0xFF5E35B1), Color(0xFF0D47A1))),
    // Aurora.
    MeshPalette(listOf(Color(0xFF00C853), Color(0xFF00B8D4), Color(0xFF7C4DFF), Color(0xFF1A237E))),
    // Grape and rose.
    MeshPalette(listOf(Color(0xFFD500F9), Color(0xFFFF4081), Color(0xFF651FFF), Color(0xFF4A148C))),
    // Gold.
    MeshPalette(listOf(Color(0xFFFFB300), Color(0xFFFF5252), Color(0xFFF06292), Color(0xFF6D2E46))),
)

/**
 * Steps through [palettes] for as long as the caller is composed, one every
 * [stepMillis]. "Reduce animation" would make every step a snap, so there it
 * stays on the first palette instead.
 */
@Composable
fun cyclingMeshPalette(palettes: List<MeshPalette>, stepMillis: Int = MESH_CYCLE_MILLIS): MeshPalette {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduceAnimation, stepMillis) {
        if (reduceAnimation) return@LaunchedEffect
        while (isActive) {
            delay(stepMillis.toLong())
            step++
        }
    }
    return palettes[step % palettes.size]
}

/**
 * Loads the artwork with Coil (software bitmap, thumbnail-sized) and pulls a
 * 4-colour palette out of it. Recomputes when [imageUrl] changes.
 *
 * A track's motion artwork is frequently lit nothing like its still sleeve —
 * a different shot, a different grade. [canvasFrame], a frame captured off
 * the playing clip once one is available, is quantised the same way and
 * takes over from there, crossfading in exactly like a track skip.
 */
@Composable
fun rememberArtworkColors(imageUrl: String?, canvasFrame: ImageBitmap? = null): MeshPalette {
    val context = LocalPlatformContext.current
    var palette by remember(imageUrl) { mutableStateOf(MeshPalette(FallbackColors)) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128) // palette quality is fine at thumbnail size, and it's fast
            .forPixelAccess() // Palette needs pixel access
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return@LaunchedEffect
        palette = MeshPalette(paletteOf(bitmap))
    }

    LaunchedEffect(canvasFrame) {
        val frame = canvasFrame ?: return@LaunchedEffect
        // Reject near-black frames (first read after surface recreation, before
        // ExoPlayer decodes real content).  A dark sleeve will still exceed the
        // threshold because compression noise pushes mean luminance above ~12.
        if (isLikelyBlackFrame(frame)) return@LaunchedEffect
        val colors = withContext(Dispatchers.Default) { paletteOf(frame) }
        palette = MeshPalette(colors)
    }
    return palette
}

/**
 * How far the blobs travel in one settle. A shade under half a turn: enough
 * that the backdrop visibly reacts to a track change, short of a full orbit
 * that would land the blobs back where they started.
 */
private const val DRIFT_RADIANS = (PI * 0.45f).toFloat()

/**
 * The player's four mesh colours for [bitmap], for surfaces that draw the mesh
 * somewhere this code doesn't run — the watch app is sent these rather than
 * running Palette on a watch.
 */
fun artworkMeshColors(bitmap: ImageBitmap): List<Color> = paletteOf(bitmap)

/**
 * Four mesh colours drawn from the artwork.
 *
 * The named swatches — vibrant, muted and friends — are a convenience over the
 * full set, and on dark or desaturated sleeves every vibrant slot comes back
 * null: Karan Aujla's marble interior fills two of the five. Topping the rest
 * up from [FallbackColors] is what left those covers sitting under the stock
 * purple. So the whole swatch list is read instead, and any shortfall is
 * derived from the art's own colours rather than borrowed.
 */
private fun paletteOf(bitmap: ImageBitmap): List<Color> {
    fun swatchesOf(clearFilters: Boolean): List<Color> =
        paletteSwatches(bitmap, 24, clearFilters)
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found = swatchesOf(clearFilters = false).ifEmpty {
        // The default filter discards near-black and near-white, which on a
        // monochrome sleeve can be everything there is.
        swatchesOf(clearFilters = true)
    }

    val distinct = found.distinctEnough()
    return when {
        distinct.isEmpty() -> FallbackColors
        distinct.size >= 4 -> distinct.take(4)
        else -> distinct.expandedToFour()
    }
}

/** Drop near-duplicates, so the four blobs don't collapse into one wash. */
private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

/** Fill the empty slots off the art itself, fanning hue and lightness out. */
private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

/** Boost saturation and clamp lightness so any artwork yields a rich, non-muddy mesh. */
private fun Color.tuned(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
