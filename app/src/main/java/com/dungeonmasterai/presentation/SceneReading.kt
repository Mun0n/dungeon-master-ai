package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.CheckResult

/**
 * Reads a confirmed check. It does not roll.
 * The pictures named here are provisional stills. The screen does not say so.
 */
data class CheckReading(
    val roll: Int,
    val attributeLabel: String,
    val modifier: Int,
    val total: Int,
    val difficulty: Int,
    val successLabel: String,
)

data class DiscoveredPlace(
    val id: String,
    val name: String,
)

internal data class JournalReading(
    val clues: List<String>,
    val evidence: List<String>,
    val places: List<DiscoveredPlace>,
    val recent: List<String>,
    val hypotheses: List<String> = emptyList(),
    val conclusions: List<String> = emptyList(),
)

internal enum class EndingPlate {
    JUSTICE,
    VENGEANCE,
    SILENCE,
    FAILURE,
    DEATH,
    QUIET,
}

internal val LOCATION_ART_NAMES: Map<String, String> = mapOf(
    "plaza" to "valdumbria_plaza",
    "tavern" to "valdumbria_tavern",
    "church" to "valdumbria_church",
    "cemetery" to "valdumbria_cemetery",
    "archive" to "valdumbria_archive",
    "crypt" to "valdumbria_crypt",
)

internal fun actTitleOf(acts: List<Pair<String, String>>, actId: String?): String =
    if (actId == null) "" else acts.firstOrNull { it.first == actId }?.second.orEmpty()

internal fun attributeLabel(raw: String): String = when (raw.uppercase()) {
    "STRENGTH" -> PlayerCopy.STRENGTH
    "AGILITY" -> PlayerCopy.AGILITY
    "INTELLIGENCE" -> PlayerCopy.INTELLIGENCE
    else -> PlayerCopy.ATTRIBUTE
}

internal fun checkReading(result: CheckResult, attribute: String): CheckReading = CheckReading(
    roll = result.roll,
    attributeLabel = attributeLabel(attribute),
    modifier = result.modifier,
    total = result.total,
    difficulty = result.difficulty,
    successLabel = if (result.success) PlayerCopy.CHECK_SUCCESS else PlayerCopy.CHECK_FAILURE,
)

internal fun checkOnLatestAction(history: List<AdventureLog>): CheckResult? {
    val latest = history.lastOrNull { it is AdventureLog.ActionResolved } as? AdventureLog.ActionResolved
    return latest?.check
}

internal fun recentJournalLines(lines: List<Pair<String, String>>): List<String> =
    lines.filter { (speaker, text) -> speaker == "narration" && text.isNotBlank() }.map { it.second }

internal fun openingDieFace(roll: Int, animate: Boolean, reduceMotion: Boolean): Int =
    if (animate && !reduceMotion) 1 else roll

internal fun shouldAnimateCheck(
    playerActed: Boolean,
    previousHistorySize: Int,
    historySize: Int,
    check: CheckResult?,
): Boolean = playerActed && check != null && historySize > previousHistorySize

internal fun nextAlignToken(
    current: Int,
    playerActed: Boolean,
    previousNarration: String?,
    narration: String?,
): Int {
    if (!playerActed || narration.isNullOrBlank() || narration == previousNarration) return current
    return current + 1
}

internal fun lifeChangeLine(playerActed: Boolean, previous: Int?, next: Int?): String? {
    if (!playerActed || previous == null || next == null || previous == next) return null
    return PlayerCopy.lifeChange(previous, next)
}

internal fun announceMidnight(playerActed: Boolean, wasFired: Boolean, isFired: Boolean): Boolean =
    playerActed && !wasFired && isFired

internal fun clueNumeral(position: Int): String {
    val glyphs = listOf(
        1000 to "M",
        900 to "CM",
        500 to "D",
        400 to "CD",
        100 to "C",
        90 to "XC",
        50 to "L",
        40 to "XL",
        10 to "X",
        9 to "IX",
        5 to "V",
        4 to "IV",
        1 to "I",
    )
    var remaining = position
    val numeral = StringBuilder()
    for ((value, glyph) in glyphs) {
        while (remaining >= value) {
            numeral.append(glyph)
            remaining -= value
        }
    }
    return numeral.toString()
}

internal fun journalReading(
    cluesInOrder: List<Pair<String, String>>,
    heldClues: Set<String>,
    evidenceInOrder: List<Pair<String, String>>,
    heldEvidence: Set<String>,
    placesInOrder: List<Pair<String, String>>,
    discoveredPlaces: Set<String>,
    recentLines: List<String>,
    hypotheses: List<String> = emptyList(),
    conclusionsInOrder: List<Pair<String, String>> = emptyList(),
    heldConclusions: Set<String> = emptySet(),
): JournalReading = JournalReading(
    clues = cluesInOrder.filter { it.first in heldClues }.map { it.second },
    evidence = evidenceInOrder.filter { it.first in heldEvidence }.map { it.second },
    places = placesInOrder.filter { it.first in discoveredPlaces }.map { (id, name) ->
        DiscoveredPlace(id, name)
    },
    recent = recentLines.filter { it.isNotBlank() },
    hypotheses = hypotheses.map { it.trim() }.filter { it.isNotEmpty() },
    conclusions = conclusionsInOrder
        .filter { (id, prose) -> id in heldConclusions && prose.isNotBlank() && !prose.contains('_') }
        .map { it.second.trim() },
)

internal fun itemLabel(id: String, storedName: String): String = when (id) {
    Character.COIN_PURSE_ID -> PlayerCopy.COIN_PURSE
    else -> storedName
}

data class CarriedItem(
    val id: String,
    val name: String,
)

internal data class InventorySlot(
    val numeral: String,
    val itemId: String?,
    val name: String?,
    val gloss: String?,
)

internal fun itemGloss(id: String): String? = when (id) {
    Character.COIN_PURSE_ID -> PlayerCopy.COIN_PURSE_GLOSS
    "iron_pin" -> PlayerCopy.IRON_PIN_GLOSS
    "linen_bandage" -> PlayerCopy.LINEN_GLOSS
    else -> null
}

internal fun inventorySlots(items: List<CarriedItem>): List<InventorySlot> {
    val filled = items.mapIndexed { index, item ->
        InventorySlot(
            numeral = inventoryNumeral(index),
            itemId = item.id,
            name = item.name,
            gloss = itemGloss(item.id),
        )
    }
    val empty = (filled.size until Character.MAX_INVENTORY).map { index ->
        InventorySlot(numeral = inventoryNumeral(index), itemId = null, name = null, gloss = null)
    }
    return filled + empty
}

private val INVENTORY_NUMERALS: List<String> = listOf("I", "II", "III", "IV", "V", "VI")

internal fun inventoryNumeral(index: Int): String =
    INVENTORY_NUMERALS.getOrElse(index) { (index + 1).toString() }

internal fun endingPlate(endingId: String?): EndingPlate = when (endingId) {
    "justice" -> EndingPlate.JUSTICE
    "vengeance" -> EndingPlate.VENGEANCE
    "silence" -> EndingPlate.SILENCE
    "failure" -> EndingPlate.FAILURE
    "death" -> EndingPlate.DEATH
    else -> EndingPlate.QUIET
}

internal fun inesPlateIsStrong(midnightFired: Boolean, line: String?): Boolean =
    midnightFired && !line.isNullOrBlank()
