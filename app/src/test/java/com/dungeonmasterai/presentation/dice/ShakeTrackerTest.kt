package com.dungeonmasterai.presentation.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeTrackerTest {
    @Test
    fun gravityAndASlowTiltDoNotRoll() {
        val tracker = ShakeTracker()
        tracker.arm(0L)
        assertFalse(still(tracker, 1_000L))
        var x = 0f
        var y = 9.8f
        for (step in 1..40) {
            x += 9.8f / 40f
            y -= 9.8f / 40f
            val fired = tracker.sample(x, y, 0f, 1_000L + step * 50L)
            assertFalse(fired)
        }
    }

    @Test
    fun oneShakeFiresOnce() {
        val tracker = ShakeTracker()
        tracker.arm(0L)
        still(tracker, 500L)
        assertTrue(tracker.sample(0f, 30f, 0f, 600L))
        assertFalse(tracker.sample(0f, 30f, 0f, 700L))
        tracker.arm(800L)
        still(tracker, 1_300L)
        assertTrue(tracker.sample(18f, 0f, 4f, 1_400L))
    }

    @Test
    fun aJoltDuringTheArmingDelayIsIgnored() {
        val tracker = ShakeTracker()
        tracker.arm(0L)
        assertFalse(tracker.sample(0f, 9.8f, 0f, 10L))
        assertFalse(tracker.sample(0f, 40f, 0f, 80L))
        still(tracker, 500L)
        assertFalse(tracker.sample(0f, 9.8f, 0f, 560L))
    }

    @Test
    fun anEmptyStreamNeverFires() {
        val tracker = ShakeTracker()
        tracker.arm(0L)
        assertFalse(tracker.sample(0f, 0f, 0f, 1_000L))
    }

    private fun still(tracker: ShakeTracker, until: Long): Boolean {
        var time = 20L
        var fired = false
        while (time <= until) {
            fired = tracker.sample(0f, 9.8f, 0f, time) || fired
            time += 20L
        }
        return fired
    }
}
