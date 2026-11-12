package com.dungeonmasterai.domain.adventure.model

/**
 * Numbers the generic adventure engine enforces.
 * The Guardian's Bridge keeps its own scene numbers.
 */
object AdventureRules {
    const val SCHEMA_VERSION: Int = 1

    /** The only narrative language the adventure client may request. Omitted means no preference. */
    const val NARRATIVE_SPANISH: String = "es"
    const val START_HOUR: Int = 22
    const val START_MINUTE: Int = 0

    /** Minutes from 22:00 until 00:00. */
    const val MIDNIGHT_MINUTES: Int = 120

    val DIFFICULTY_THRESHOLDS: Set<Int> = setOf(8, 12, 16, 20)

    val ID_PATTERN: Regex = Regex("^[a-z][a-z0-9_]*$")

    /** Narration limits shared with the backend contract. Resolution does not read them. */
    const val PLAYER_TEXT_MAX: Int = 500
    const val LOCAL_PROSE_MAX: Int = 1_200
    const val SCENE_TEXT_MAX: Int = 2_000
    const val SUMMARY_MAX: Int = 3_000
    const val PURPOSE_MAX: Int = 500
    const val FORBIDDEN_MIN: Int = 8
    const val RECENT_LINES: Int = 8
}
