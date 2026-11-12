package com.dungeonmasterai.domain.adventure.narration

import com.dungeonmasterai.domain.adventure.model.EFFECT_TYPE_NAMES
import java.util.Locale

/**
 * Phrases the narrator may not say until a revealed source already states them.
 * The adventure document keeps its own list. This table decides which of those
 * phrases, and which extra phrases, are still active for one call.
 */
private val NEVER_FORBIDDEN: List<String> = listOf(
    "Elia sigue viva",
    "Elia is alive",
    "la criatura del cementerio existe",
    "the crypt creature is real",
    "hay una segunda medianoche",
    "a second midnight",
)

private val RELEASED_WHEN_ELIA_IS_DEAD: List<String> = listOf(
    "Elia está muerta",
    "Elia is dead",
)

private val RELEASED_WITH_EVIDENCE_ELIA: List<String> = listOf(
    "Padre Aldren acabó con ella",
)

private val RELEASED_WITH_EVIDENCE_CRYPT: List<String> = listOf(
    "personas sanas encerradas",
)

private val RELEASED_WITH_EVIDENCE_ALDEN: List<String> = listOf(
    "incrimina a Padre Aldren",
)

private val RELEASED_WITH_TRUTH: List<String> = listOf(
    "Las cuatro pruebas coinciden",
    "hizo alterar los registros",
)

private val RELEASED_WHEN_INES_IS_SAVED: List<String> = listOf(
    "has salvado a Inés",
    "you saved Ines",
    "Inés está a salvo",
)

private val RELEASED_WHEN_INES_IS_LOST: List<String> = listOf(
    "Inés está perdida",
)

private val ANAPHORA: List<String> = listOf(
    "Por esa razón",
    "Por ello",
    "Por eso",
    "Tampoco",
    "Ella",
    "Él",
    "Ese",
    "Esa",
    "Eso",
)

/**
 * English state words and ending ids. Matched as whole words, so `plaza` is untouched.
 */
val BARE_PLAYER_TOKENS: List<String> = listOf(
    "justice",
    "vengeance",
    "silence",
    "failure",
    "death",
    "dead",
    "present",
    "absent",
    "working",
    "withdrawn",
    "guarded",
    "digging",
    "unwarned",
    "saved",
    "lost",
    "tavern",
    "church",
    "cemetery",
    "archive",
    "crypt",
    "STRENGTH",
    "AGILITY",
    "INTELLIGENCE",
)

/**
 * The forbidden list for one model call.
 * A phrase leaves the list only when the revealed facts already authorize it.
 * Phrases on the document that this table does not release stay on the list.
 */
fun activeForbiddenPhrases(
    documentPhrases: List<String>,
    evidenceIds: Set<String>,
    truthKnown: Boolean,
    inesLine: String?,
    clueIds: Set<String> = emptySet(),
): List<String> {
    val released = releasedPhrases(evidenceIds, truthKnown, inesLine, clueIds)
    val active = linkedSetOf<String>()
    documentPhrases.forEach { phrase ->
        if (phrase.isNotBlank() && released.none { it.equals(phrase, ignoreCase = true) }) {
            active += phrase
        }
    }
    tablePhrases().forEach { phrase ->
        if (released.none { it.equals(phrase, ignoreCase = true) }) {
            val already = active.any { it.equals(phrase, ignoreCase = true) }
            if (!already) active += phrase
        }
    }
    return active.toList()
}

fun containsListedPhrase(text: String, phrases: List<String>): Boolean {
    if (text.isEmpty() || phrases.isEmpty()) return false
    val haystack = text.lowercase(Locale.ROOT)
    return phrases.any { phrase ->
        phrase.isNotBlank() && phrase.lowercase(Locale.ROOT) in haystack
    }
}

fun containsPlayerTechnicalToken(text: String): Boolean {
    if (text.isEmpty()) return false
    if (UNDERSCORE_IDENTIFIER.containsMatchIn(text)) return true
    if (EFFECT_TYPE_NAMES.any { containsWholeWord(text, it) }) return true
    return BARE_PLAYER_TOKENS.any { containsWholeWord(text, it) }
}

/**
 * Drops secret and technical sentences, then drops an anaphoric follower of a gap.
 * An empty result means the summary should be omitted.
 */
fun redactSummary(text: String, secrets: List<String>): String {
    val sentences = splitSentences(text)
    if (sentences.isEmpty()) return ""
    val dropped = BooleanArray(sentences.size) { index ->
        sentenceIsUnsafe(sentences[index], secrets)
    }
    var changed = true
    while (changed) {
        changed = false
        for (index in 1 until sentences.size) {
            if (!dropped[index] && dropped[index - 1] && startsWithAnaphora(sentences[index])) {
                dropped[index] = true
                changed = true
            }
        }
    }
    return sentences.filterIndexed { index, _ -> !dropped[index] }.joinToString(" ")
}

internal fun splitSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < text.length) {
        val character = text[index]
        current.append(character)
        if (character == '.' || character == '?' || character == '!') {
            val next = text.getOrNull(index + 1)
            if (next == null || next.isWhitespace()) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) sentences += sentence
                current.clear()
            }
        }
        index += 1
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) sentences += tail
    return sentences
}

private fun tablePhrases(): List<String> = NEVER_FORBIDDEN +
    RELEASED_WHEN_ELIA_IS_DEAD +
    RELEASED_WITH_EVIDENCE_ELIA +
    RELEASED_WITH_EVIDENCE_CRYPT +
    RELEASED_WITH_EVIDENCE_ALDEN +
    RELEASED_WITH_TRUTH +
    RELEASED_WHEN_INES_IS_SAVED +
    RELEASED_WHEN_INES_IS_LOST

private fun releasedPhrases(
    evidenceIds: Set<String>,
    truthKnown: Boolean,
    inesLine: String?,
    clueIds: Set<String>,
): List<String> {
    val released = mutableListOf<String>()
    if ("evidence_elia" in evidenceIds || "clue_elia_dead" in clueIds) {
        released += RELEASED_WHEN_ELIA_IS_DEAD
    }
    if ("evidence_elia" in evidenceIds) released += RELEASED_WITH_EVIDENCE_ELIA
    if ("evidence_crypt" in evidenceIds) released += RELEASED_WITH_EVIDENCE_CRYPT
    if ("evidence_alden" in evidenceIds) released += RELEASED_WITH_EVIDENCE_ALDEN
    if (truthKnown) released += RELEASED_WITH_TRUTH
    if (inesLine != null && "Inés está a salvo" in inesLine) released += RELEASED_WHEN_INES_IS_SAVED
    if (inesLine != null && "Inés está perdida" in inesLine) released += RELEASED_WHEN_INES_IS_LOST
    return released
}

private fun sentenceIsUnsafe(sentence: String, secrets: List<String>): Boolean =
    containsListedPhrase(sentence, secrets) || containsPlayerTechnicalToken(sentence)

private fun startsWithAnaphora(sentence: String): Boolean {
    val trimmed = sentence.trim()
    return ANAPHORA.any { phrase ->
        trimmed.startsWith(phrase, ignoreCase = true) &&
            (trimmed.length == phrase.length || !isWordCharacter(trimmed[phrase.length]))
    }
}

private fun isWordCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '_'

private fun containsWholeWord(text: String, word: String): Boolean {
    val pattern = Regex(
        "(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE,
    )
    return pattern.containsMatchIn(text)
}

private val UNDERSCORE_IDENTIFIER: Regex = Regex("(?<![\\p{L}\\p{N}])[\\p{L}\\p{N}]*_[\\p{L}\\p{N}_]*")
