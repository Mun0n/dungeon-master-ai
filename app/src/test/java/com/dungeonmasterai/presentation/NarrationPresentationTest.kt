package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.NarrationDecision
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.narrationReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationPresentationTest {
    @Test
    fun resolvingKeepsThePreviousNarration() {
        val waiting = beginResolution(
            ValdumbriaUiState(narration = "Cruzas la plaza.", resolving = false),
        )
        assertTrue(waiting.resolving)
        assertEquals("Cruzas la plaza.", waiting.narration)
        val done = finishResolution(waiting.copy(narration = "La taberna está en silencio."))
        assertFalse(done.resolving)
        assertEquals("La taberna está en silencio.", done.narration)
    }

    @Test
    fun resolvingPlatePulsesTheMarksInTurn() {
        assertEquals(
            "LA PARTIDA ESTÁ RESOLVIENDO LA ACCIÓN...",
            resolvingPlateLabel(PlayerCopy.RESOLVING),
        )
        assertEquals(0f, glyphCrest(cycle = 0f, delayFraction = 0f), 0.001f)
        assertEquals(1f, glyphCrest(cycle = 0.5f, delayFraction = 0f), 0.001f)
        assertEquals(0f, glyphCrest(cycle = 0.2f, delayFraction = 0.2f), 0.001f)
        assertEquals(
            glyphCrest(cycle = 0.5f, delayFraction = 0f),
            glyphCrest(cycle = 0.7f, delayFraction = 0.2f),
            0.001f,
        )
        assertEquals(0.25f, glyphPulse(0f).alpha, 0.001f)
        assertEquals(0.92f, glyphPulse(0f).scale, 0.001f)
        assertEquals(1f, glyphPulse(1f).alpha, 0.001f)
        assertEquals(1.08f, glyphPulse(1f).scale, 0.001f)
        assertEquals(0.8f, resolvingMark(null).alpha, 0.001f)
        assertEquals(1f, resolvingMark(null).scale, 0.001f)
        assertEquals(0.35f, quillAlpha(0f), 0.001f)
        assertEquals(0.85f, quillAlpha(0.5f), 0.001f)
        assertEquals(0.8f, quillMarkAlpha(null), 0.001f)
        assertEquals(listOf(0f, 0.2f, 0.4f), RESOLVING_MARK_DELAYS)
        assertEquals(1500, RESOLVING_PERIOD_MILLIS)
    }

    @Test
    fun resolvingClearsThePreviousNotice() {
        val waiting = beginResolution(
            ValdumbriaUiState(
                narration = "Cruzas la plaza.",
                notice = PlayerCopy.NARRATOR_UNAVAILABLE,
            ),
        )
        assertTrue(waiting.resolving)
        assertEquals("Cruzas la plaza.", waiting.narration)
        assertNull(waiting.notice)
    }

    @Test
    fun reducedMotionOpensOnTheStoredFace() {
        assertEquals(14, openingDieFace(roll = 14, animate = true, reduceMotion = true))
        assertEquals(1, openingDieFace(roll = 14, animate = true, reduceMotion = false))
        assertEquals(14, openingDieFace(roll = 14, animate = false, reduceMotion = false))
    }

    @Test
    fun theScreenModelHasNoSummaryField() {
        val names = ValdumbriaUiState::class.java.declaredFields.map { it.name }
        assertTrue(names.none { it.contains("summary", ignoreCase = true) })
    }

    @Test
    fun aCleanNarrationKeepsTheSafeSummarySentences() {
        val decision = narrationReply(
            call = BackendCall.Ready(
                NarrationResponse(
                    contractVersion = 1,
                    requestId = "req",
                    stateRevision = "rev",
                    sceneContractId = "plaza_contract",
                    outcome = "SUCCEEDED",
                    text = "La plaza sigue en calma.",
                    auxiliary = mapOf(
                        "narrativeSummary" to
                            "Elia está muerta. Ella no regresará. La plaza sigue en calma.",
                    ),
                ),
            ),
            requestId = "req",
            stateRevision = "rev",
            sceneContractId = "plaza_contract",
            outcome = "SUCCEEDED",
            forbidden = listOf("Elia está muerta"),
            technicalIds = listOf("read_elias_letter", "SET_FLAG"),
        )
        val accepted = decision as NarrationDecision.Accepted
        assertEquals("La plaza sigue en calma.", accepted.text)
        assertEquals("La plaza sigue en calma.", accepted.summary)
    }

    @Test
    fun technicalNarrationFallsBack() {
        val decision = narrationReply(
            call = BackendCall.Ready(
                NarrationResponse(
                    contractVersion = 1,
                    requestId = "req",
                    stateRevision = "rev",
                    sceneContractId = "plaza_contract",
                    outcome = "SUCCEEDED",
                    text = "Lees read_elias_letter en la plaza y Elia espera.",
                    auxiliary = emptyMap(),
                ),
            ),
            requestId = "req",
            stateRevision = "rev",
            sceneContractId = "plaza_contract",
            outcome = "SUCCEEDED",
            forbidden = emptyList(),
            technicalIds = listOf("read_elias_letter"),
        )
        assertEquals(NarrationDecision.Unusable, decision)
    }
}
