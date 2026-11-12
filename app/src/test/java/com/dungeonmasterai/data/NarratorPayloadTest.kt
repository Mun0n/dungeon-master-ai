package com.dungeonmasterai.data

import com.dungeonmasterai.domain.game.PlayerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarratorPayloadTest {
    @Test
    fun aTwoFieldPayloadMapsOntoAnExistingIntent() {
        val proposal = parseNarratorPayload("""{"narration":"  The spear dips.  ","intent":"ATTACK"}""")

        assertEquals(NarratorProposal("The spear dips.", PlayerIntent.Attack), proposal)
    }

    @Test
    fun everyIntentNameMaps() {
        val names = mapOf(
            "PERSUADE" to PlayerIntent.Persuade,
            "PAY" to PlayerIntent.Pay,
            "ATTACK" to PlayerIntent.Attack,
            "UNRECOGNIZED" to PlayerIntent.Unrecognized,
            "AMBIGUOUS" to PlayerIntent.Ambiguous,
        )

        names.forEach { (name, intent) ->
            val proposal = parseNarratorPayload("""{"narration":"A line.","intent":"$name"}""")
            assertEquals(intent, proposal?.intent)
        }
    }

    @Test
    fun anExtraKeyIsRejected() {
        assertNull(parseNarratorPayload("""{"narration":"A line.","intent":"PAY","health":10}"""))
    }

    @Test
    fun anUnknownIntentIsRejected() {
        assertNull(parseNarratorPayload("""{"narration":"A line.","intent":"HEAL"}"""))
    }

    @Test
    fun aBlankNarrationIsRejected() {
        assertNull(parseNarratorPayload("""{"narration":"   ","intent":"PAY"}"""))
    }

    @Test
    fun aNarrationPastFourHundredCharactersIsRejected() {
        val narration = "a".repeat(MAX_NARRATION_LENGTH + 1)
        assertNull(parseNarratorPayload("""{"narration":"$narration","intent":"PAY"}"""))
        assertEquals(
            PlayerIntent.Pay,
            parseNarratorPayload(
                """{"narration":"${"a".repeat(MAX_NARRATION_LENGTH)}","intent":"PAY"}""",
            )?.intent,
        )
    }

    @Test
    fun aUnicodeEscapeDecodesAndASignedOneIsRejected() {
        assertEquals("A", parseNarratorPayload("""{"narration":"\u0041","intent":"PAY"}""")?.narration)
        assertNull(parseNarratorPayload("""{"narration":"a\u+041","intent":"PAY"}"""))
    }

    @Test
    fun escapedQuotesStayInsideTheNarration() {
        val proposal = parseNarratorPayload("""{"narration":"say \"pass\"","intent":"PAY"}""")

        assertEquals("say \"pass\"", proposal?.narration)
    }

    @Test
    fun malformedJsonIsRejected() {
        assertNull(parseNarratorPayload("not json"))
        assertNull(parseNarratorPayload("""["PAY"]"""))
        assertNull(parseNarratorPayload("""{"narration":"A line.","intent":1}"""))
        assertNull(parseNarratorPayload("""{"intent":"PAY"}"""))
    }
}
