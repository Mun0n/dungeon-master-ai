package com.dungeonmasterai.domain.scenario

import com.dungeonmasterai.domain.game.PlayerIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordIntentParserTest {
    private val parser = KeywordIntentParser()

    @Test
    fun eachTokenMapsToItsGroup() {
        listOf("persuade", "convince", "talk", "reason").forEach {
            assertEquals(PlayerIntent.Persuade, parser.parse(it))
        }
        listOf("pay", "bribe", "offer").forEach {
            assertEquals(PlayerIntent.Pay, parser.parse(it))
        }
        listOf("attack", "fight", "strike", "charge").forEach {
            assertEquals(PlayerIntent.Attack, parser.parse(it))
        }
    }

    @Test
    fun uppercaseAndPunctuationStillMatchTheToken() {
        assertEquals(PlayerIntent.Attack, parser.parse("ATTACK"))
        assertEquals(PlayerIntent.Attack, parser.parse("Attack the guardian!"))
        assertEquals(PlayerIntent.Pay, parser.parse("I offer the purse"))
    }

    @Test
    fun emptyAndUnknownTextAreUnrecognized() {
        assertEquals(PlayerIntent.Unrecognized, parser.parse(""))
        assertEquals(PlayerIntent.Unrecognized, parser.parse("   "))
        assertEquals(PlayerIntent.Unrecognized, parser.parse("I try to fly"))
    }

    @Test
    fun partialWordsDoNotMatch() {
        assertEquals(PlayerIntent.Unrecognized, parser.parse("attacker"))
        assertEquals(PlayerIntent.Unrecognized, parser.parse("talking"))
        assertEquals(PlayerIntent.Unrecognized, parser.parse("payé"))
    }

    @Test
    fun twoTokensFromTheSameGroupAreThatIntent() {
        assertEquals(PlayerIntent.Attack, parser.parse("attack fight"))
    }

    @Test
    fun tokensFromTwoGroupsAreAmbiguous() {
        assertEquals(PlayerIntent.Ambiguous, parser.parse("attack pay"))
    }
}
