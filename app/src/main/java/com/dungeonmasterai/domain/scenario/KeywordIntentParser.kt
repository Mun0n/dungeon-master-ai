package com.dungeonmasterai.domain.scenario

import com.dungeonmasterai.domain.game.PlayerIntent

/**
 * Fallback interpreter used when the narrator is absent or its reply is unusable.
 * Lowercases text, splits on non-Unicode-letters, and matches whole tokens.
 * One keyword group becomes an intent. Several groups are ambiguous.
 */
class KeywordIntentParser {
    fun parse(text: String): PlayerIntent {
        val groups = text
            .lowercase()
            .split(NON_LETTERS)
            .filter { it.isNotEmpty() }
            .mapNotNull { TOKEN_GROUPS[it] }
            .toSet()
        return when (groups.size) {
            0 -> PlayerIntent.Unrecognized
            1 -> groups.single()
            else -> PlayerIntent.Ambiguous
        }
    }

    private companion object {
        val NON_LETTERS = Regex("\\P{L}+")
        val TOKEN_GROUPS: Map<String, PlayerIntent> = buildMap {
            listOf("persuade", "convince", "talk", "reason").forEach { put(it, PlayerIntent.Persuade) }
            listOf("pay", "bribe", "offer").forEach { put(it, PlayerIntent.Pay) }
            listOf("attack", "fight", "strike", "charge").forEach { put(it, PlayerIntent.Attack) }
        }
    }
}
