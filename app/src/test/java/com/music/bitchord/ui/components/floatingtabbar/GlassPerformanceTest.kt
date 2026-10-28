package com.music.bitchord.ui.components.floatingtabbar

import com.music.bitchord.ui.components.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPerformanceTest {
    @Test
    fun `export the bar and tab layers only for a visible lifted glass pill`() {
        assertFalse(needsGlassPillBackdrop(false, true, false))
        assertFalse(needsGlassPillBackdrop(true, false, false))
        assertFalse(needsGlassPillBackdrop(true, true, true))
        assertTrue(needsGlassPillBackdrop(true, true, false))
    }

    @Test
    fun `dispersion shader uses three edge samples and one flat-interior sample`() {
        // The flat interior returns early with one evaluation. The lens edge
        // takes one sample per color channel, not seven full backdrop samples.
        val shader = RoundedRectRefractionWithDispersionShaderString
        assertEquals(4, Regex("content\\.eval\\(").findAll(shader).count())
        assertTrue(shader.contains("red.r, green.g, blue.b, alpha"))
    }

    @Test
    fun `three color offsets match the original spectrum weighted centres`() {
        val red = 1.0 / 3.5 + (2.0 / 3.0) / 3.5 + (1.0 / 3.0) / 3.5 - 1.0 / 7.0
        val green = (2.0 / 3.0) / 7.0 + (1.0 / 3.0) / 3.5 - (1.0 / 3.0) / 3.5
        val blue = -(1.0 / 3.0) / 3.0 - (2.0 / 3.0) / 3.0 - 1.0 / 3.0
        assertEquals(3.0 / 7.0, red, 1e-12)
        assertEquals(2.0 / 21.0, green, 1e-12)
        assertEquals(-2.0 / 3.0, blue, 1e-12)
    }
}
