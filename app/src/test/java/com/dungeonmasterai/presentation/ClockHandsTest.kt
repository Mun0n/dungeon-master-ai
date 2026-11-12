package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.state.clockFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockHandsTest {
    @Test
    fun twentyTwoHundredPointsAtTen() {
        val hands = clockHands(22, 0)
        assertEquals(300f, hands.hourDegrees, 0.01f)
        assertEquals(0f, hands.minuteDegrees, 0.01f)
    }

    @Test
    fun halfPastMovesTheHourHandHalfwayTowardTheNextHour() {
        val hands = clockHands(22, 30)
        assertEquals(315f, hands.hourDegrees, 0.01f)
        assertEquals(180f, hands.minuteDegrees, 0.01f)
    }

    @Test
    fun midnightStacksBothHandsAtTwelve() {
        val hands = clockHands(clockFace(120).hour, clockFace(120).minute)
        assertEquals(0, clockFace(120).hour)
        assertEquals(0, clockFace(120).minute)
        assertEquals(0f, hands.hourDegrees, 0.01f)
        assertEquals(0f, hands.minuteDegrees, 0.01f)
    }

    @Test
    fun quarterToMidnightFollowsTheStoredFace() {
        val face = clockFace(105)
        val hands = clockHands(face.hour, face.minute)
        assertEquals(23, face.hour)
        assertEquals(45, face.minute)
        assertEquals(352.5f, hands.hourDegrees, 0.01f)
        assertEquals(270f, hands.minuteDegrees, 0.01f)
    }

    @Test
    fun handsPivotOnTheMedallionAndTheTailIsAShortStub() {
        assertTrue(HourPicture.scale < MinutePicture.scale)
        assertTrue(MinutePicture.scale < 1f)
        val clock = 200f
        val center = clock / 2f
        val hour = handLayout(HourPicture, clock)
        val minute = handLayout(MinutePicture, clock)
        assertEquals(center, hour.origin.x + HourPicture.axle.x * hour.side, 0.01f)
        assertEquals(center, hour.origin.y + HourPicture.axle.y * hour.side, 0.01f)
        assertEquals(center, minute.origin.x + MinutePicture.axle.x * minute.side, 0.01f)
        assertEquals(center, minute.origin.y + MinutePicture.axle.y * minute.side, 0.01f)
        assertEquals(HourPicture.tailFraction * hour.side, hour.clipBottom, 0.01f)
        assertEquals(MinutePicture.tailFraction * minute.side, minute.clipBottom, 0.01f)
        assertTrue(hour.clipBottom < hour.side)
        assertTrue(minute.clipBottom < minute.side)
        assertTrue(tailReach(HourPicture) > 0f)
        assertTrue(tailReach(MinutePicture) > 0f)
        assertTrue(tailReach(HourPicture) < 0.2f)
        assertTrue(tailReach(MinutePicture) < 0.2f)
    }

    @Test
    fun headerHandsOverlapIntoOneStrokeAndTheLargeClockIsDrawnOnce() {
        assertEquals(1, handOffsets(bold = false).size)
        assertEquals(0f, handOffsets(bold = false)[0], 0.01f)
        val bold = handOffsets(bold = true)
        assertTrue(bold.size > 1)
        assertEquals(0f, bold[bold.size / 2], 0.01f)
        assertTrue(bold.max() > 1f)
        assertTrue(bold.max() - bold.min() < 4f)
    }

    /** Painted tail past the medallion, as a fraction of the clock radius. */
    private fun tailReach(picture: HandPicture): Float {
        return (picture.tailFraction - picture.axle.y) * picture.scale * 2f
    }
}
