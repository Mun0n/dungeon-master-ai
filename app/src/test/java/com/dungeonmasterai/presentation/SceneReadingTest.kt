package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.CheckResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneReadingTest {
    private val stored = CheckResult(
        roll = 14,
        modifier = 5,
        total = 19,
        difficulty = 12,
        success = true,
    )

    @Test
    fun theActTitleIsTheOneStoredForThatPlace() {
        val acts = listOf(
            "act_arrival" to "Llegada",
            "act_investigation" to "Investigación",
            "act_consequences" to "Medianoche y consecuencias",
        )
        assertEquals("Llegada", actTitleOf(acts, "act_arrival"))
        assertEquals("Investigación", actTitleOf(acts, "act_investigation"))
        assertEquals("", actTitleOf(acts, null))
        assertEquals("", actTitleOf(acts, "missing"))
    }

    @Test
    fun aCheckReadingUsesTheStoredNumbers() {
        val reading = checkReading(stored, "INTELLIGENCE")
        assertEquals(14, reading.roll)
        assertEquals(PlayerCopy.INTELLIGENCE, reading.attributeLabel)
        assertEquals(5, reading.modifier)
        assertEquals(19, reading.total)
        assertEquals(12, reading.difficulty)
        assertEquals(PlayerCopy.CHECK_SUCCESS, reading.successLabel)
    }

    @Test
    fun aFailedCheckKeepsTheStoredRoll() {
        val failed = stored.copy(roll = 1, modifier = 2, total = 3, difficulty = 20, success = false)
        val reading = checkReading(failed, "AGILITY")
        assertEquals(1, reading.roll)
        assertEquals(3, reading.total)
        assertEquals(PlayerCopy.CHECK_FAILURE, reading.successLabel)
        assertEquals(PlayerCopy.AGILITY, reading.attributeLabel)
    }

    @Test
    fun restoreDoesNotAnimateOrRealign() {
        assertFalse(shouldAnimateCheck(playerActed = false, previousHistorySize = 2, historySize = 4, check = stored))
        assertEquals(3, nextAlignToken(3, playerActed = false, previousNarration = "antes", narration = "después"))
        assertNull(lifeChangeLine(playerActed = false, previous = 10, next = 8))
        assertFalse(announceMidnight(playerActed = false, wasFired = false, isFired = true))
    }

    @Test
    fun aNewNarrationAlignsOnceAndLaterReadingDoesNot() {
        assertEquals(1, nextAlignToken(0, playerActed = true, previousNarration = null, narration = "La plaza."))
        assertEquals(1, nextAlignToken(1, playerActed = true, previousNarration = "La plaza.", narration = "La plaza."))
        assertEquals(1, nextAlignToken(1, playerActed = true, previousNarration = "La plaza.", narration = null))
    }

    @Test
    fun aPlayerActionCanAnnounceMidnightAndALifeChange() {
        assertEquals("Vida 10 → 8", lifeChangeLine(playerActed = true, previous = 10, next = 8))
        assertNull(lifeChangeLine(playerActed = true, previous = 10, next = 10))
        assertTrue(announceMidnight(playerActed = true, wasFired = false, isFired = true))
        assertFalse(announceMidnight(playerActed = true, wasFired = true, isFired = true))
        assertTrue(shouldAnimateCheck(playerActed = true, previousHistorySize = 2, historySize = 3, check = stored))
        assertFalse(shouldAnimateCheck(playerActed = true, previousHistorySize = 3, historySize = 3, check = stored))
    }

    @Test
    fun theJournalKeepsNarrationAndDropsActionIds() {
        val lines = recentJournalLines(
            listOf(
                "player" to "read_elias_letter",
                "narration" to "Elia te escribe desde el archivo.",
                "player" to "",
            ),
        )
        assertEquals(listOf("Elia te escribe desde el archivo."), lines)
        assertFalse(lines.any { it.contains('_') })
    }

    @Test
    fun aLaterActionWithoutACheckHidesThePreviousRoll() {
        val earlier = stored
        val history = listOf(
            AdventureLog.ActionResolved("search", success = true, check = earlier),
            AdventureLog.ClockAdvanced(fromMinutes = 0, toMinutes = 5),
            AdventureLog.ActionResolved("read_elias_letter", success = true, check = null),
        )
        assertNull(checkOnLatestAction(history))
        assertEquals(earlier, checkOnLatestAction(history.dropLast(1)))
    }

    @Test
    fun theJournalShowsOnlyDiscoveredSpanishText() {
        val journal = journalReading(
            cluesInOrder = listOf("clue_letter" to "La carta", "clue_hidden" to "Secreto"),
            heldClues = setOf("clue_letter"),
            evidenceInOrder = listOf("evidence_elia" to "Las notas"),
            heldEvidence = emptySet(),
            placesInOrder = listOf("plaza" to "Plaza", "crypt" to "Cripta"),
            discoveredPlaces = setOf("plaza"),
            recentLines = listOf("Has leído la carta.", ""),
        )
        assertEquals(listOf("La carta"), journal.clues)
        assertTrue(journal.evidence.isEmpty())
        assertEquals(listOf(DiscoveredPlace("plaza", "Plaza")), journal.places)
        assertEquals(listOf("Has leído la carta."), journal.recent)
        assertFalse(journal.clues.any { it.contains('_') })
        assertFalse(journal.places.any { it.name == "plaza" || it.name.contains('_') })
        assertTrue(journal.hypotheses.isEmpty())
        assertTrue(journal.conclusions.isEmpty())
    }

    @Test
    fun aHypothesisStaysUnconfirmedAndAConclusionAppearsOnlyWhenHeld() {
        val journal = journalReading(
            cluesInOrder = listOf("clue_letter" to "La carta"),
            heldClues = setOf("clue_letter"),
            evidenceInOrder = listOf("evidence_elia" to "Las notas"),
            heldEvidence = emptySet(),
            placesInOrder = listOf("plaza" to "Plaza"),
            discoveredPlaces = setOf("plaza"),
            recentLines = emptyList(),
            hypotheses = listOf(" Tomás lo hizo. "),
            conclusionsInOrder = listOf(
                "name_the_crime" to "Las cuatro pruebas coinciden.",
                "hidden_claim" to "evidence_elia",
            ),
            heldConclusions = setOf("name_the_crime"),
        )
        assertEquals(listOf("La carta"), journal.clues)
        assertTrue(journal.evidence.isEmpty())
        assertEquals(listOf("Tomás lo hizo."), journal.hypotheses)
        assertEquals(listOf("Las cuatro pruebas coinciden."), journal.conclusions)
        assertFalse(journal.conclusions.any { it.contains('_') })
        assertFalse(PlayerCopy.HYPOTHESIS_NOTE.contains("correcta", ignoreCase = true))
        assertFalse(PlayerCopy.HYPOTHESIS_NOTE.contains("falsa", ignoreCase = true))
    }

    @Test
    fun clueNumeralsCountOnlyTheVisibleList() {
        assertEquals("I", clueNumeral(1))
        assertEquals("IV", clueNumeral(4))
        assertEquals("VIII", clueNumeral(8))
        assertEquals("IX", clueNumeral(9))
    }

    @Test
    fun inventoryUsesTheSpanishPurseNameAndTheSixItemLimit() {
        assertEquals(PlayerCopy.COIN_PURSE, itemLabel(Character.COIN_PURSE_ID, Character.COIN_PURSE_NAME))
        assertEquals("Ganzúa de Tomás", itemLabel("iron_pin", "Ganzúa de Tomás"))
        assertEquals("Llevas 1 de 6", PlayerCopy.carrying(1))
        assertEquals("LLEVAS 1 / 6", PlayerCopy.carryingMark(1))
        assertEquals(6, Character.MAX_INVENTORY)
        val slots = inventorySlots(
            listOf(
                CarriedItem(Character.COIN_PURSE_ID, PlayerCopy.COIN_PURSE),
                CarriedItem("iron_pin", "Ganzúa de Tomás"),
                CarriedItem("linen_bandage", "Lino de Mara"),
            ),
        )
        assertEquals(6, slots.size)
        assertEquals("I", slots[0].numeral)
        assertEquals(PlayerCopy.COIN_PURSE, slots[0].name)
        assertEquals(PlayerCopy.COIN_PURSE_GLOSS, slots[0].gloss)
        assertEquals(PlayerCopy.IRON_PIN_GLOSS, slots[1].gloss)
        assertEquals(PlayerCopy.LINEN_GLOSS, slots[2].gloss)
        assertEquals("IV", slots[3].numeral)
        assertNull(slots[3].name)
        assertEquals("VI", slots[5].numeral)
        val unknown = inventorySlots(listOf(CarriedItem("rope", "Cuerda")))
        assertEquals("Cuerda", unknown[0].name)
        assertNull(unknown[0].gloss)
        assertNull(itemGloss("rope"))
    }

    @Test
    fun fiveEndingsKeepTheirTitlesAndPlates() {
        assertEquals(EndingPlate.JUSTICE, endingPlate("justice"))
        assertEquals(EndingPlate.VENGEANCE, endingPlate("vengeance"))
        assertEquals(EndingPlate.SILENCE, endingPlate("silence"))
        assertEquals(EndingPlate.FAILURE, endingPlate("failure"))
        assertEquals(EndingPlate.DEATH, endingPlate("death"))
        assertEquals("Final: la verdad queda publicada", endingTitle("justice"))
        assertEquals("Final: la venganza cae sobre el culpable", endingTitle("vengeance"))
        assertEquals("Final: el secreto permanece", endingTitle("silence"))
        assertEquals("Final: Valdumbría queda atrás", endingTitle("failure"))
        assertEquals("Final: la caída acaba contigo", endingTitle("death"))
        assertFalse(endingTitle("justice").contains('_'))
    }

    @Test
    fun aMissingActionLabelDoesNotShowTheId() {
        val label = choiceLabel("not_a_real_action")
        assertEquals(PlayerCopy.ACTION_FALLBACK, label)
        assertFalse(label.contains('_'))
    }

    @Test
    fun sixLocationPicturesAreNamedAndPresent() {
        assertEquals(
            setOf("plaza", "tavern", "church", "cemetery", "archive", "crypt"),
            LOCATION_ART_NAMES.keys,
        )
        val root = locateDrawableDir()
        LOCATION_ART_NAMES.values.forEach { name ->
            val file = File(root, "$name.webp")
            assertTrue(file.absolutePath, file.isFile && file.length() > 0L)
        }
    }

    @Test
    fun classCardsStayOnTheAuthoredNumbers() {
        assertEquals(
            "Erudito. Fuerza 2, Agilidad 2, Inteligencia 5.",
            PlayerCopy.classCard(CharacterClass.SCHOLAR),
        )
        assertEquals("Guerrero", PlayerCopy.className(CharacterClass.WARRIOR))
        assertEquals("Explorador", PlayerCopy.className(CharacterClass.SCOUT))
        assertEquals("Guerrera", PlayerCopy.className(CharacterClass.WARRIOR, feminine = true))
        assertEquals("Exploradora", PlayerCopy.className(CharacterClass.SCOUT, feminine = true))
        assertEquals("Erudita", PlayerCopy.className(CharacterClass.SCHOLAR, feminine = true))
        assertEquals("Clase sigilo", PlayerCopy.classRole(CharacterClass.SCOUT))
        assertEquals("Lugar", PlayerCopy.PLACE)
        assertEquals("Estado vital", PlayerCopy.VITAL_STATE)
        assertEquals("Atributos", PlayerCopy.ATTRIBUTES_HEADING)
        assertEquals("Escala /5", PlayerCopy.ATTRIBUTE_SCALE)
    }

    private fun locateDrawableDir(): File {
        val candidates = listOf(
            File("src/main/res/drawable-nodpi"),
            File("app/src/main/res/drawable-nodpi"),
        )
        return candidates.first { it.isDirectory }
    }
}
