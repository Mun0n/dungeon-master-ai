package com.dungeonmasterai.domain.dice

import kotlin.random.Random

enum class Difficulty(val threshold: Int) {
    EASY(8),
    MODERATE(12),
    HARD(16),
    VERY_HARD(20),
}

fun interface Die {
    fun roll(): Int
}

class RandomDie(
    private val random: Random = Random.Default,
) : Die {
    override fun roll(): Int = random.nextInt(1, 21)
}

class ScriptedDie(
    rolls: List<Int>,
) : Die {
    private val pending = ArrayDeque(rolls)

    override fun roll(): Int {
        check(pending.isNotEmpty()) { "No scripted rolls left." }
        val value = pending.removeFirst()
        require(value in 1..20) { "Die roll must be 1..20, was $value." }
        return value
    }
}

data class CheckResult(
    val roll: Int,
    val modifier: Int,
    val total: Int,
    val difficulty: Int,
    val success: Boolean,
)

/**
 * A check an available action would roll.
 * Reading it does not roll and does not change state.
 */
data class OfferedCheck(
    val attribute: String,
    val modifier: Int,
    val difficulty: Int,
)

fun resolveCheck(
    roll: Int,
    attribute: Int,
    difficulty: Difficulty,
): CheckResult {
    require(roll in 1..20) { "Die roll must be 1..20, was $roll." }
    require(attribute in 1..5) { "Attribute must be 1..5, was $attribute." }
    val total = roll + attribute
    return CheckResult(
        roll = roll,
        modifier = attribute,
        total = total,
        difficulty = difficulty.threshold,
        success = total >= difficulty.threshold,
    )
}
