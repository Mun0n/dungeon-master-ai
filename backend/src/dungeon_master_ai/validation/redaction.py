"""Sentence redaction for narrative memory.

The stored summary is not edited here. A copy is split, unsafe sentences are
dropped, and an anaphoric follower of a dropped sentence is dropped too.
"""

import re

EFFECT_TYPE_NAMES = (
    "SET_FLAG",
    "CHANGE_LIFE",
    "ADD_ITEM",
    "REMOVE_ITEM",
    "DISCOVER_LOCATION",
    "MOVE_TO",
    "SET_RELATIONSHIP",
    "ADD_EVIDENCE",
    "ADD_CLUE",
    "SET_NPC_STATE",
    "RECORD_ATTEMPT",
    "TRIGGER_EVENT",
    "SET_ENDING",
)

BARE_PLAYER_TOKENS = (
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

_ANAPHORA = (
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

_IDENTIFIER = re.compile(r"(?<![\w])[\w]*_[\w]*")

ENDING_TITLES = {
    "justice": "Final: la verdad queda publicada",
    "vengeance": "Final: la venganza cae sobre el culpable",
    "silence": "Final: el secreto permanece",
    "failure": "Final: Valdumbría queda atrás",
    "death": "Final: la caída acaba contigo",
}

ATTRIBUTE_WORDS = {
    "STRENGTH": "Fuerza",
    "AGILITY": "Agilidad",
    "INTELLIGENCE": "Inteligencia",
}

OUTCOME_WORDS = {
    "SUCCEEDED": "completed",
    "FAILED": "not completed",
    "REJECTED": "rejected",
}


def redact_summary(text: str, secrets: list[str]) -> str:
    sentences = split_sentences(text)
    if not sentences:
        return ""
    dropped = [sentence_is_unsafe(sentence, secrets) for sentence in sentences]
    changed = True
    while changed:
        changed = False
        for index in range(1, len(sentences)):
            if not dropped[index] and dropped[index - 1] and starts_with_anaphora(sentences[index]):
                dropped[index] = True
                changed = True
    return " ".join(sentence for index, sentence in enumerate(sentences) if not dropped[index])


def split_sentences(text: str) -> list[str]:
    sentences: list[str] = []
    current: list[str] = []
    for index, character in enumerate(text):
        current.append(character)
        if character in ".?!" and (index + 1 == len(text) or text[index + 1].isspace()):
            sentence = "".join(current).strip()
            if sentence:
                sentences.append(sentence)
            current = []
    tail = "".join(current).strip()
    if tail:
        sentences.append(tail)
    return sentences


def contains_player_technical_token(text: str) -> bool:
    if not text:
        return False
    if _IDENTIFIER.search(text):
        return True
    return any(contains_whole_word(text, token) for token in (*EFFECT_TYPE_NAMES, *BARE_PLAYER_TOKENS))


def _is_word_character(character: str) -> bool:
    return character.isalnum() or character == "_"


def contains_whole_word(text: str, word: str) -> bool:
    pattern = re.compile(rf"(?<![\w]){re.escape(word)}(?![\w])", re.IGNORECASE)
    return pattern.search(text) is not None


def sentence_is_unsafe(sentence: str, secrets: list[str]) -> bool:
    folded = sentence.casefold()
    if any(secret.casefold() in folded for secret in secrets if secret):
        return True
    return contains_player_technical_token(sentence)


def starts_with_anaphora(sentence: str) -> bool:
    trimmed = sentence.strip()
    folded = trimmed.casefold()
    for phrase in _ANAPHORA:
        prefix = phrase.casefold()
        if folded.startswith(prefix) and (
            len(trimmed) == len(phrase) or not _is_word_character(trimmed[len(phrase)])
        ):
            return True
    return False
