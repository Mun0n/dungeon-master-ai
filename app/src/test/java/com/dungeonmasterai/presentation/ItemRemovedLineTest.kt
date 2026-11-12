package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.character.Character
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ItemRemovedLineTest {
    @Test
    fun coinPurseUsesTheItemName() {
        assertEquals("Pierdes Bolsa de monedas.", itemRemovedLine(Character.COIN_PURSE_ID))
    }

    @Test
    fun unknownItemDoesNotShowItsId() {
        val line = itemRemovedLine("rope")
        assertEquals(PlayerCopy.LOST_OBJECT, line)
        assertFalse(line.contains("rope"))
    }
}
