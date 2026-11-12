package com.dungeonmasterai.domain.character

enum class Attribute {
    STRENGTH,
    AGILITY,
    INTELLIGENCE,
}

enum class Presentation {
    MASCULINE,
    FEMININE,
}

enum class CharacterClass(
    val strength: Int,
    val agility: Int,
    val intelligence: Int,
) {
    WARRIOR(strength = 4, agility = 3, intelligence = 2),
    SCOUT(strength = 2, agility = 4, intelligence = 3),
    SCHOLAR(strength = 2, agility = 2, intelligence = 5),
}

data class Attributes(
    val strength: Int,
    val agility: Int,
    val intelligence: Int,
) {
    init {
        require(strength in RANGE) { "Strength must be 1..5." }
        require(agility in RANGE) { "Agility must be 1..5." }
        require(intelligence in RANGE) { "Intelligence must be 1..5." }
    }

    fun value(attribute: Attribute): Int = when (attribute) {
        Attribute.STRENGTH -> strength
        Attribute.AGILITY -> agility
        Attribute.INTELLIGENCE -> intelligence
    }

    companion object {
        val RANGE: IntRange = 1..5

        fun of(characterClass: CharacterClass): Attributes = Attributes(
            strength = characterClass.strength,
            agility = characterClass.agility,
            intelligence = characterClass.intelligence,
        )
    }
}

data class Item(
    val id: String,
    val name: String,
) {
    init {
        require(id.isNotBlank()) { "Item id must not be blank." }
        require(name.isNotBlank()) { "Item name must not be blank." }
    }
}

data class Character(
    val name: String,
    val characterClass: CharacterClass,
    val attributes: Attributes,
    val health: Int,
    val inventory: List<Item>,
    val maxHealth: Int = MAX_HEALTH,
    val presentation: Presentation = Presentation.MASCULINE,
) {
    init {
        require(name.isNotBlank()) { "Character name must not be blank." }
        require(maxHealth == MAX_HEALTH) { "Maximum health is $MAX_HEALTH." }
        require(health in 0..maxHealth) { "Health must be 0..$maxHealth." }
        require(inventory.size <= MAX_INVENTORY) { "Inventory holds at most $MAX_INVENTORY items." }
        require(inventory.distinctBy { it.id }.size == inventory.size) { "Item ids must be unique." }
        require(attributes == Attributes.of(characterClass)) { "Attributes must match the character class." }
    }

    companion object {
        const val MAX_HEALTH: Int = 10
        const val MAX_INVENTORY: Int = 6
        const val COIN_PURSE_ID: String = "coin_purse"
        const val COIN_PURSE_NAME: String = "Coin Purse"

        fun create(
            name: String,
            characterClass: CharacterClass,
            health: Int = MAX_HEALTH,
            presentation: Presentation = Presentation.MASCULINE,
        ): Character = Character(
            name = name.trim(),
            characterClass = characterClass,
            attributes = Attributes.of(characterClass),
            health = health,
            inventory = listOf(Item(id = COIN_PURSE_ID, name = COIN_PURSE_NAME)),
            presentation = presentation,
        )
    }
}
