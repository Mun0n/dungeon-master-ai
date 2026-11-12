package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.narration.BARE_PLAYER_TOKENS
import com.dungeonmasterai.domain.adventure.narration.activeForbiddenPhrases
import com.dungeonmasterai.domain.adventure.narration.containsPlayerTechnicalToken
import com.dungeonmasterai.domain.adventure.narration.redactSummary
import com.dungeonmasterai.presentation.PlayerCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonGuardTest {
    private val document = listOf(
        "Elia está muerta",
        "Elia is dead",
        "Elia sigue viva",
        "Elia is alive",
        "has salvado a Inés",
        "you saved Ines",
        "la criatura del cementerio existe",
        "the crypt creature is real",
        "hay una segunda medianoche",
        "a second midnight",
        "No hidden solution is stored here.",
    )

    @Test
    fun aDeadNpcWithoutEvidenceStillForbidsTheDeathSentence() {
        val active = activeForbiddenPhrases(
            documentPhrases = document,
            evidenceIds = emptySet(),
            truthKnown = false,
            inesLine = null,
        )
        assertTrue(active.any { it == "Elia está muerta" })
        assertTrue(active.any { it.equals("Elia is dead", ignoreCase = true) })
        assertTrue(active.any { it == "Elia sigue viva" })
        assertTrue(active.any { it == "Padre Aldren acabó con ella" })
        assertTrue(active.any { it == "No hidden solution is stored here." })
    }

    @Test
    fun theBurialClueReleasesTheDeathAndKeepsAldrenHidden() {
        val active = activeForbiddenPhrases(
            documentPhrases = document,
            evidenceIds = emptySet(),
            truthKnown = false,
            inesLine = null,
            clueIds = setOf("clue_elia_dead"),
        )
        assertFalse(active.any { it.equals("Elia está muerta", ignoreCase = true) })
        assertFalse(active.any { it.equals("Elia is dead", ignoreCase = true) })
        assertTrue(active.any { it == "Padre Aldren acabó con ella" })
        assertTrue(active.any { it == "Elia sigue viva" })
    }

    @Test
    fun holdingEliasEvidenceReleasesTheDeathAndKeepsTheContradictions() {
        val active = activeForbiddenPhrases(
            documentPhrases = document,
            evidenceIds = setOf("evidence_elia"),
            truthKnown = false,
            inesLine = null,
        )
        assertFalse(active.any { it.equals("Elia está muerta", ignoreCase = true) })
        assertFalse(active.any { it.equals("Elia is dead", ignoreCase = true) })
        assertFalse(active.any { it == "Padre Aldren acabó con ella" })
        assertTrue(active.any { it == "Elia sigue viva" })
        assertTrue(active.any { it.equals("Elia is alive", ignoreCase = true) })
        assertTrue(active.any { it == "la criatura del cementerio existe" })
        assertTrue(active.any { it == "hay una segunda medianoche" })
        assertTrue(active.any { it == "personas sanas encerradas" })
        assertTrue(active.any { it == "No hidden solution is stored here." })
    }

    @Test
    fun contradictionsStayForbiddenAfterEveryProof() {
        val active = activeForbiddenPhrases(
            documentPhrases = document,
            evidenceIds = setOf("evidence_elia", "evidence_crypt", "evidence_alden"),
            truthKnown = true,
            inesLine = PlayerCopy.INES_SAVED,
        )
        assertTrue(active.any { it == "Elia sigue viva" })
        assertTrue(active.any { it == "Elia is alive" })
        assertTrue(active.any { it == "la criatura del cementerio existe" })
        assertTrue(active.any { it == "the crypt creature is real" })
        assertTrue(active.any { it == "hay una segunda medianoche" })
        assertTrue(active.any { it == "a second midnight" })
        assertFalse(active.any { it == "personas sanas encerradas" })
        assertFalse(active.any { it == "incrimina a Padre Aldren" })
        assertFalse(active.any { it == "Las cuatro pruebas coinciden" })
        assertFalse(active.any { it == "hizo alterar los registros" })
        assertFalse(active.any { it == "Inés está a salvo" })
        assertTrue(active.any { it == "Inés está perdida" })
    }

    @Test
    fun inesPhrasesFollowTheClockSentence() {
        val saved = activeForbiddenPhrases(emptyList(), emptySet(), false, PlayerCopy.INES_SAVED)
        assertFalse(saved.any { it == "has salvado a Inés" })
        assertFalse(saved.any { it == "you saved Ines" })
        assertFalse(saved.any { it == "Inés está a salvo" })
        assertTrue(saved.any { it == "Inés está perdida" })

        val lost = activeForbiddenPhrases(emptyList(), emptySet(), false, PlayerCopy.INES_LOST)
        assertTrue(lost.any { it == "Inés está a salvo" })
        assertFalse(lost.any { it == "Inés está perdida" })

        val warned = activeForbiddenPhrases(emptyList(), emptySet(), false, PlayerCopy.INES_WARNED)
        assertTrue(warned.any { it == "has salvado a Inés" })
        assertTrue(warned.any { it == "Inés está perdida" })
    }

    @Test
    fun summaryRedactionKeepsTheCalmSentenceAndDropsTheFollower() {
        val secret = "Elia está muerta. Ella no regresará. La plaza sigue en calma."
        val hidden = redactSummary(secret, listOf("Elia está muerta"))
        assertEquals("La plaza sigue en calma.", hidden)

        val shown = redactSummary(secret, emptyList())
        assertEquals(secret, shown)
    }

    @Test
    fun anaphoricFollowersDropUntilTheGapCloses() {
        val text = "Elia está muerta. Ella no regresará. Ella insiste. La plaza sigue en calma."
        assertEquals(
            "La plaza sigue en calma.",
            redactSummary(text, listOf("Elia está muerta")),
        )
    }

    @Test
    fun punctuationAfterAnAnaphoraStillDropsTheFollower() {
        val text = "Elia está muerta. Ella, no regresará. Tampoco. Ellas siguen en la plaza."
        assertEquals(
            "Ellas siguen en la plaza.",
            redactSummary(text, listOf("Elia está muerta")),
        )
    }

    @Test
    fun aTechnicalSentenceDropsAndAPlazaSentenceStays() {
        val text = "Lees read_elias_letter. La plaza sigue en calma. Elia espera."
        assertEquals(
            "La plaza sigue en calma. Elia espera.",
            redactSummary(text, emptyList()),
        )
        assertTrue(containsPlayerTechnicalToken("read_elias_letter"))
        assertTrue(containsPlayerTechnicalToken("SET_FLAG"))
        assertTrue(containsPlayerTechnicalToken("El justice del pueblo."))
        assertTrue(containsPlayerTechnicalToken("the crypt is shut"))
        assertTrue(containsPlayerTechnicalToken("Inés stays unwarned."))
        assertFalse(containsPlayerTechnicalToken("La plaza sigue en calma."))
        assertFalse(containsPlayerTechnicalToken("Elia espera en la cripta."))
        assertFalse(containsPlayerTechnicalToken("cryptic marks"))
        assertTrue(BARE_PLAYER_TOKENS.none { it.equals("plaza", ignoreCase = true) })
        assertTrue(BARE_PLAYER_TOKENS.none { it.equals("Elia", ignoreCase = true) })
    }
}
