package com.minova.cinema.tapo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapoFadeTest {
    @Test
    fun `cosine fade is monotonic smooth and reaches exact target`() {
        val values = (0..40).map { step -> easedBrightness(100, 0, step, 40) }

        assertEquals(100, values.first())
        assertEquals(0, values.last())
        assertTrue(values.zipWithNext().all { (before, after) -> after <= before })
        assertTrue(values.distinct().size >= 30)
        // Easing should avoid the old, visibly abrupt five-percent first jump.
        assertTrue(values[1] >= 99)
    }
}
