package com.dungeonmasterai.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCatalogTest {
    @Test
    fun thePublicMenuPublishesOnlyValdumbria() {
        val published = publishedAdventures()
        assertEquals(listOf(VALDUMBRIA_ID), published.map { it.id })
        assertEquals("Terror gótico", published.single().genre)
        assertEquals(PlayerCopy.VALDUMBRIA_PREMISE, published.single().premise)
        assertFalse(published.any { it.id.contains("bridge") })
    }

    @Test
    fun theSeriesLineUsesTheCatalogPosition() {
        assertEquals("Aventura I · Terror gótico", seriesLabel(0, "Terror gótico"))
        assertEquals("Aventura II · Otro", seriesLabel(1, "Otro"))
    }

    @Test
    fun aMissingTitleIsNotReplacedWithTheInternalId() {
        assertEquals("Las campanas de Valdumbría", adventureTitle(VALDUMBRIA_ID, mapOf(VALDUMBRIA_ID to "Las campanas de Valdumbría")))
        assertEquals("", adventureTitle(VALDUMBRIA_ID, emptyMap()))
    }

    @Test
    fun aNewGameAsksBeforeItErasesTheMatchingSave() {
        val saved = MenuResume(VALDUMBRIA_ID, "Iglesia", "01:40")
        assertEquals(MenuPrimary.Continue("Iglesia · 01:40"), menuPrimary(VALDUMBRIA_ID, saved))
        assertTrue(newGameErasesSave(VALDUMBRIA_ID, saved))
        assertFalse(newGameErasesSave(VALDUMBRIA_ID, null))
        assertFalse(newGameErasesSave(VALDUMBRIA_ID, MenuResume("other", "Plaza", "22:00")))
        assertFalse(menuPrimary(VALDUMBRIA_ID, saved).let { it is MenuPrimary.Continue && it.detail.contains("Capítulo") })
    }
}
