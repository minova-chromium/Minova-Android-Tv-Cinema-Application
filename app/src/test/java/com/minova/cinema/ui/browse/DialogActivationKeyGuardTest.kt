package com.minova.cinema.ui.browse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogActivationKeyGuardTest {
    @Test fun openingHoldRepeatsAndReleaseAreConsumed() {
        val guard = DialogActivationKeyGuard()
        for (repeat in 1..20) assertTrue(guard.consume(23, isDown = true, repeatCount = repeat))
        assertTrue(guard.consume(23, isDown = false, repeatCount = 0))
        assertFalse(guard.consume(23, isDown = true, repeatCount = 0))
        assertFalse(guard.consume(23, isDown = false, repeatCount = 0))
    }

    @Test fun orphanReleaseIsConsumedWithoutEatingTheNextClick() {
        val guard = DialogActivationKeyGuard()
        assertTrue(guard.consume(23, isDown = false, repeatCount = 0))
        repeat(3) {
            assertFalse(guard.consume(23, isDown = true, repeatCount = 0))
            assertFalse(guard.consume(23, isDown = false, repeatCount = 0))
        }
    }

    @Test fun activationKeysAreTrackedIndependently() {
        val guard = DialogActivationKeyGuard()
        assertFalse(guard.consume(66, isDown = true, repeatCount = 0))
        assertTrue(guard.consume(23, isDown = false, repeatCount = 0))
        assertFalse(guard.consume(66, isDown = false, repeatCount = 0))
    }

    @Test fun canceledGestureCannotActivate() {
        val guard = DialogActivationKeyGuard()
        assertFalse(guard.consume(23, isDown = true, repeatCount = 0))
        assertTrue(guard.consume(23, isDown = false, repeatCount = 0, canceled = true))
        assertTrue(guard.consume(23, isDown = false, repeatCount = 0))
    }
}
