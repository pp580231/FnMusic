package com.seasonyuu.fnmusic.feature.music

import org.junit.Assert.*
import org.junit.Test

class ArtistLoadingTest {
    @Test fun unknownCountsDoNotPretendToBeEmpty() {
        assertNull(artistVisibleCount(0, false, 0, false))
        assertNull(artistVisibleCount(0, false, 30, false))
        assertNull(artistVisibleCount(0, true, 30, false))
    }
    @Test fun knownCountsSurviveLoadingAndEmptyResultsAreAuthoritative() {
        assertEquals(65, artistVisibleCount(65, false, 0, false))
        assertEquals(65, artistVisibleCount(65, false, 30, false))
        assertEquals(0, artistVisibleCount(65, false, 0, true))
        assertEquals(0, artistVisibleCount(0, true, 0, false))
    }
}
