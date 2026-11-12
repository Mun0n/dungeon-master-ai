package com.dungeonmasterai.domain.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterTest {
    @Test
    fun classesFixTheStartingAttributes() {
        assertEquals(Attributes(4, 3, 2), Attributes.of(CharacterClass.WARRIOR))
        assertEquals(Attributes(2, 4, 3), Attributes.of(CharacterClass.SCOUT))
        assertEquals(Attributes(2, 2, 5), Attributes.of(CharacterClass.SCHOLAR))
    }

    @Test
    fun newCharactersStartAtFullHealthWithACoinPurse() {
        val character = Character.create("Ada", CharacterClass.WARRIOR)

        assertEquals("Ada", character.name)
        assertEquals(10, character.health)
        assertEquals(10, character.maxHealth)
        assertEquals(listOf(Item("coin_purse", "Coin Purse")), character.inventory)
        assertEquals(Presentation.MASCULINE, character.presentation)
    }

    @Test
    fun presentationDoesNotChangeTheClassNumbers() {
        val feminine = Character.create("Ada", CharacterClass.WARRIOR, presentation = Presentation.FEMININE)
        val masculine = Character.create("Ada", CharacterClass.WARRIOR)

        assertEquals(Presentation.FEMININE, feminine.presentation)
        assertEquals(masculine.attributes, feminine.attributes)
        assertEquals(masculine.health, feminine.health)
    }

    @Test
    fun attributesOutsideOneToFiveAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Attributes(strength = 0, agility = 1, intelligence = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Attributes(strength = 6, agility = 1, intelligence = 1)
        }
    }

    @Test
    fun attributesMustMatchTheClass() {
        assertThrows(IllegalArgumentException::class.java) {
            character(attributes = Attributes.of(CharacterClass.SCHOLAR))
        }
    }

    @Test
    fun healthOutsideZeroToTenIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            character(health = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            character(health = 11)
        }
        character(health = 0)
    }

    @Test
    fun aSeventhItemIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            character(inventory = List(7) { Item(id = "item-$it", name = "Item $it") })
        }
    }

    @Test
    fun duplicateItemIdsAreRejected() {
        val purse = Item("coin_purse", "Coin Purse")
        assertThrows(IllegalArgumentException::class.java) {
            character(inventory = listOf(purse, purse.copy()))
        }
    }

    private fun character(
        health: Int = 10,
        attributes: Attributes = Attributes.of(CharacterClass.WARRIOR),
        inventory: List<Item> = listOf(Item("coin_purse", "Coin Purse")),
    ): Character = Character(
        name = "Ada",
        characterClass = CharacterClass.WARRIOR,
        attributes = attributes,
        health = health,
        inventory = inventory,
    )
}
