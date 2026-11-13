package com.dungeonmasterai.presentation.dice

import com.dungeonmasterai.presentation.CheckReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceOverlayModelTest {
    @Test
    fun oneWaitingRollAcceptsASingleThrow() {
        val model = DiceOverlayModel()
        model.open(pending())

        val first = model.requestThrow()
        val second = model.requestThrow()

        assertEquals("reckless_climb", first?.actionId)
        assertNull(second)
        assertTrue(model.overlay is DiceOverlay.Throwing)
    }

    @Test
    fun withdrawingBeforeTheThrowClosesWithoutAResult() {
        val model = DiceOverlayModel()
        model.open(pending())

        model.close()

        assertTrue(model.overlay is DiceOverlay.Closed)
        assertNull(model.requestThrow())
    }

    @Test
    fun theCommittedFaceIsTheFaceThatStaysOnScreen() {
        val model = DiceOverlayModel()
        model.open(pending())
        model.requestThrow()
        val reading = reading(roll = 12)

        model.committed(reading, nowMs = 50L)
        model.hold()

        val holding = model.overlay as DiceOverlay.Holding
        assertEquals(12, holding.reading.roll)
        assertEquals(14, holding.reading.total)
        model.close()
        assertTrue(model.overlay is DiceOverlay.Closed)
    }

    @Test
    fun aRestoreKeepsAnUnthrownRollOnlyAtTheSameRevision() {
        val model = DiceOverlayModel()
        model.open(pending(revision = 4))

        assertTrue(model.keepAfterRestore(4))
        assertTrue(model.overlay is DiceOverlay.Waiting)

        assertTrue(!model.keepAfterRestore(5))
        assertTrue(model.overlay is DiceOverlay.Closed)
    }

    @Test
    fun aRestoreDuringTheTumbleDoesNotOpenAnotherThrow() {
        val model = DiceOverlayModel()
        model.open(pending(revision = 2))
        model.requestThrow()
        model.committed(reading(roll = 8), nowMs = 10L)

        assertTrue(model.keepAfterRestore(9))
        assertNull(model.requestThrow())
        assertEquals(8, (model.overlay as DiceOverlay.Playing).reading.roll)
    }

    private fun pending(revision: Int = 1): PendingThrow = PendingThrow(
        actionId = "reckless_climb",
        playerText = "trepar",
        attribute = "STRENGTH",
        modifier = 2,
        difficulty = 20,
        revision = revision,
    )

    private fun reading(roll: Int): CheckReading = CheckReading(
        roll = roll,
        attributeLabel = "Fuerza",
        modifier = 2,
        total = roll + 2,
        difficulty = 20,
        successLabel = "No supera la prueba",
    )
}
