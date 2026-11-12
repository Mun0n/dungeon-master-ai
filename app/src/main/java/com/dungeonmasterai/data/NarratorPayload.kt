package com.dungeonmasterai.data

import com.dungeonmasterai.domain.game.PlayerIntent

const val MAX_NARRATION_LENGTH: Int = 400

data class NarratorProposal(
    val narration: String,
    val intent: PlayerIntent,
)

fun parseNarratorPayload(raw: String): NarratorProposal? {
    val fields = try {
        JsonObjectReader(raw).readStringFields()
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (fields.size != 2 || "narration" !in fields || "intent" !in fields) {
        return null
    }
    val narration = fields.getValue("narration").trim()
    if (narration.isBlank() || narration.length > MAX_NARRATION_LENGTH) {
        return null
    }
    val intent = when (fields.getValue("intent")) {
        "PERSUADE" -> PlayerIntent.Persuade
        "PAY" -> PlayerIntent.Pay
        "ATTACK" -> PlayerIntent.Attack
        "UNRECOGNIZED" -> PlayerIntent.Unrecognized
        "AMBIGUOUS" -> PlayerIntent.Ambiguous
        else -> return null
    }
    return NarratorProposal(narration, intent)
}

internal fun jsonString(value: String): String = buildString {
    append('"')
    for (char in value) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}

private class JsonObjectReader(private val raw: String) {
    private var index: Int = 0

    fun readStringFields(): Map<String, String> {
        skip()
        expect('{')
        val fields = linkedMapOf<String, String>()
        skip()
        if (peek('}')) {
            index++
            finish()
            return fields
        }
        while (index < raw.length) {
            skip()
            val key = readString()
            skip()
            expect(':')
            skip()
            val value = readString()
            if (key in fields) {
                throw IllegalArgumentException("Duplicate key.")
            }
            fields[key] = value
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    finish()
                    return fields
                }
                else -> throw IllegalArgumentException("Expected a comma or the end of the object.")
            }
        }
        throw IllegalArgumentException("Unterminated object.")
    }

    private fun finish() {
        skip()
        if (index != raw.length) {
            throw IllegalArgumentException("Trailing data.")
        }
    }

    private fun readString(): String {
        expect('"')
        val text = StringBuilder()
        while (index < raw.length) {
            val char = raw[index++]
            when {
                char == '"' -> return text.toString()
                char == '\\' -> text.append(readEscape())
                char.code < 0x20 -> throw IllegalArgumentException("Unescaped control character.")
                else -> text.append(char)
            }
        }
        throw IllegalArgumentException("Unterminated string.")
    }

    private fun readEscape(): Char {
        if (index >= raw.length) {
            throw IllegalArgumentException("Truncated escape.")
        }
        return when (val char = raw[index++]) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > raw.length) {
                    throw IllegalArgumentException("Truncated unicode escape.")
                }
                val hex = raw.substring(index, index + 4)
                index += 4
                if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
                    throw IllegalArgumentException("Bad unicode escape.")
                }
                hex.toInt(16).toChar()
            }
            else -> throw IllegalArgumentException("Bad escape.")
        }
    }

    private fun expect(char: Char) {
        if (!peek(char)) {
            throw IllegalArgumentException("Expected $char.")
        }
        index++
    }

    private fun peek(char: Char): Boolean = index < raw.length && raw[index] == char

    private fun skip() {
        while (index < raw.length && raw[index] in " \t\n\r") {
            index++
        }
    }
}
