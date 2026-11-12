package com.dungeonmasterai.domain.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCheckTest {
    @Test
    fun totalEqualToTheThresholdSucceeds() {
        val result = resolveCheck(roll = 7, attribute = 1, difficulty = Difficulty.EASY)

        assertEquals(8, result.total)
        assertTrue(result.success)
    }

    @Test
    fun totalOneBelowTheThresholdFails() {
        val result = resolveCheck(roll = 6, attribute = 1, difficulty = Difficulty.EASY)

        assertEquals(7, result.total)
        assertFalse(result.success)
    }

    @Test
    fun naturalTwentyPlusFiveBeatsVeryHard() {
        val result = resolveCheck(roll = 20, attribute = 5, difficulty = Difficulty.VERY_HARD)

        assertEquals(25, result.total)
        assertTrue(result.success)
    }

    @Test
    fun naturalTwentyPlusOneBeatsEasy() {
        val result = resolveCheck(roll = 20, attribute = 1, difficulty = Difficulty.EASY)

        assertEquals(21, result.total)
        assertTrue(result.success)
    }

    @Test
    fun rollOutsideTheDieIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveCheck(roll = 0, attribute = 1, difficulty = Difficulty.EASY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveCheck(roll = 21, attribute = 1, difficulty = Difficulty.EASY)
        }
    }

    @Test
    fun randomDieStaysInsideOneToTwenty() {
        val die = RandomDie()

        repeat(200) {
            val roll = die.roll()
            assertTrue(roll in 1..20)
        }
    }
}
