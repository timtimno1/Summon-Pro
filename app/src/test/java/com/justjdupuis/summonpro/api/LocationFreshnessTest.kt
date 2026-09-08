package com.justjdupuis.summonpro.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    @Test
    fun acceptsSecondAndMillisecondTimestamps() {
        val now = 1_700_000_000_000L
        assertTrue(LocationFreshness.isFresh(1_699_999_990L, 30_000, now))
        assertTrue(LocationFreshness.isFresh(1_699_999_990_000L, 30_000, now))
    }

    @Test
    fun rejectsOldAndFutureLocations() {
        val now = 1_700_000_000_000L
        assertFalse(LocationFreshness.isFresh(1_699_999_000_000L, 30_000, now))
        assertFalse(LocationFreshness.isFresh(1_700_000_001_000L, 30_000, now))
    }
}
