package com.seasonyuu.fnmusic.feature.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistHeaderScrollStateTest {
    @Test
    fun collapseConsumesOnlyAvailableHeaderDistance() {
        val state = ArtistHeaderScrollState().apply { rangePx = 300f }
        assertEquals(-120f, state.consume(-120f), .001f)
        assertEquals(120f, state.collapsedPx, .001f)
        assertEquals(-180f, state.consume(-250f), .001f)
        assertEquals(1f, state.fraction, .001f)
        assertEquals(0f, state.consume(-50f), .001f)
        assertEquals(300f, state.consume(400f), .001f)
        assertEquals(0f, state.collapsedPx, .001f)
    }

    @Test
    fun restoredCollapseSurvivesRemeasurementAndNewList() {
        val state = ArtistHeaderScrollState(.5f)
        assertEquals(0f, state.consume(-50f), .001f)
        state.rangePx = 300f
        assertEquals(150f, state.collapsedPx, .001f)
        // Each tab may cause remeasurement, but never owns or resets this fraction.
        state.rangePx = 300f
        assertEquals(150f, state.collapsedPx, .001f)
        state.rangePx = 500f
        assertEquals(250f, state.collapsedPx, .001f)
    }
}
