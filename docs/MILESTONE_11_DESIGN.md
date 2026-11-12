# Milestone 11 design — revised

Status: approved on 11 November 2026. Implementation is in progress and is not marked complete. This document remains the specification.

Depends on milestone 10, which is completed. The numbers, the effect order, the saves, and the bridge narrator stay as they are in [GAME_RULES.md](GAME_RULES.md) and [ARCHITECTURE.md](ARCHITECTURE.md). This design changes what the narrator is allowed to see and say. It does not change a resolution.

The seven lines approved with the first draft stay. The review made the following points mandatory. They replace any looser wording in that draft.

## Decisions this approval would make

1. Valdumbría narration stays in the second person and the present tense, with mystery and dread. The model may add sensory and emotional detail. It may not invent a canonical fact.
2. A fact that exists only in the internal state is not narration. A fact may be narrated when the revealed facts already authorize it. Elia's death is one of those facts after `evidence_elia` is held, and not before. "Elia sigue viva" is never authorized.
3. Summaries are redacted by sentence, then checked for a dangling reference. The summary stays off the screen.
4. Groq's invalid JSON uses the one retry that already exists. An interpretation failure commits nothing. A narration failure keeps the committed action, roll, and effects. A restore of a pending narration does not run the action again.
5. The backend timeout stays 30 seconds and the Android read stays 60. The wait keeps the previous narration and shows a short activity line. The player-facing failure sentence is fixed.
6. Player-visible text rejects technical identifiers, effect names, and bare internal words as whole words. `plaza` stays. The narration prompt is built from revealed prose. The output filter is a second defense.
7. `dm_phone` captures the first reduced-motion frame. The six pictures stay provisional.
8. Android tests, backend tests, and one Groq run cover the cases below. The close writes down what is deterministic and what still depends on the model.
9. No new mechanic, place, principal character, or ending. The Kotlin engines, the rules, and persistence stay the writers of state.

## Objective

The player reads Las campanas de Valdumbría as a mystery told in natural Spanish. The prose can be uneasy and sensory. It can say a canonical fact only when that fact has been revealed to the player. A failure of the provider, or a reply that is not valid JSON, leaves a game the player can continue. The screen still does not roll dice or write the outcome.

## What this milestone does not do

- It does not add a mechanic, a location, a principal character, or an ending.
- It does not add a rule, an action, an item, a class, or a difficulty.
- It does not migrate Room, change `AdventureState`, or change the bridge narrator.
- It does not rewrite `localProse`, clue text, evidence text, or any other static adventure content.
- It does not let the model roll dice, grant a clue, change life, set an ending, or call `AdventureEngine.deduce`.
- It does not replace the six provisional pictures, and it does not call them final.
- It does not add a dependency, a settings screen, or a second adventure.
- It does not treat a substring check as a complete semantic guarantee.

## 1. Voice

The authored lines already speak in the second person and the present tense. "Cruzas la plaza y entras en la taberna." Valdumbría narration and generated scene text stay in that voice, and the mood is mystery and dread.

The model may enrich what the player senses and feels: cold, fog, a lowered voice, the weight of a closed door. That color has to sit on facts the revealed set already contains.

The model may not invent any of these:

- a canonical fact
- a relevant character
- a culprit
- a proof
- a consequence
- an event

unless the revealed facts and the scene contract already authorize it. A rumor stays a rumor. The prose does not explain the puzzle and does not tell the player which action to take next.

`SPANISH_NARRATE` and `SPANISH_SCENE` carry that instruction, beside the instruction they already have about natural Spanish and proper names. `SPANISH_INTERPRET` stays a mapping instruction. Interpretation returns ids, not atmosphere.

The fallback the player sees, when the model text is unusable, remains the action's `localProse`. This milestone does not rewrite that static content. The Groq reading judges the model text against this section. A flat reading is written down. Revising the authored lines would need a later approval.

## 2. Internal state and revealed facts

The adventure state holds facts the player has not discovered. Elia begins dead. That npc state is internal. It does not authorize the sentence "Elia está muerta." Flags, attempt counts, raw npc states, and the solution statement are internal in the same way.

A canonical fact may appear in narration only when one of these revealed sources already states it. `revealedFacts` is the existing list of clue texts and evidence texts whose ids are held. The other sources are prose the player can already be shown:

- the scene purpose and its `revealableInformation`
- clue and evidence text from `revealedFacts`
- the `localProse` of the outcome being narrated
- `inesClockLine`, when that function returns a sentence
- the current location's Spanish name
- the confirmed life, and the stored check in Spanish words
- the Spanish ending title, when an ending is already stored
- recent player-visible lines, and the summary after the redaction in the next section

The action that grants `evidence_elia` is narrated from the state after that resolution, so its own `localProse` and the evidence text authorize the death. A later narration may repeat it. A narration before that evidence is held may not. "Elia sigue viva", "Elia is alive", a real crypt creature, and a second midnight are contradictions of the canon. No revealed source authorizes them, and the active phrase list never releases them.

`ines_protected` is not a rescue. Inés's fate is narratable when `inesClockLine` returns the saved sentence or the lost sentence, because that is the sentence the player can already see. The raw tokens `saved`, `lost`, and `unwarned` are not a source.

Padre Aldren's name is sayable because the church already introduces him as the priest. Naming him as the culprit waits until a revealed source says so: the evidence text, the outcome line that grants it, or the deduction line after `truth_known`. The name alone is not the accusation.

The substring table below is a deterministic filter for known phrases. It is not a guarantee that the prose understood the story. A paraphrase such as "El cura la mató" can pass the table. The Groq reading records that kind of sentence. The close calls it a model-dependent limit, not a pass.

| phrase | released only when this revealed source exists |
| --- | --- |
| "Elia está muerta", "Elia is dead" | `evidence_elia` is held, including on the narration of the resolution that granted it |
| "Padre Aldren acabó con ella" | the same evidence |
| "personas sanas encerradas" | `evidence_crypt` is held |
| "incrimina a Padre Aldren" | `evidence_alden` is held |
| "Las cuatro pruebas coinciden", "hizo alterar los registros" | the deduction has been accepted and `truth_known` is set |
| "has salvado a Inés", "you saved Ines", "Inés está a salvo" | `inesClockLine` returns the saved sentence |
| "Inés está perdida" | `inesClockLine` returns a lost sentence |
| "Elia sigue viva", "Elia is alive" | never |
| "la criatura del cementerio existe", "the crypt creature is real" | never |
| "hay una segunda medianoche", "a second midnight" | never |

Every added phrase is at least 8 characters, which is the minimum the request schema already accepts. The session sends this active list as `forbiddenInformation` for that call. The document on disk stays as it is. The backend still applies a case-insensitive substring check to the list it receives. It does not learn the evidence ids.

The JVM test that currently strips "Elia está muerta" from a save that already holds `evidence_elia` changes with this rule. That save may send the sentence. A save that does not hold the evidence still drops it. Elia's initial npc state `dead`, with empty evidence, still drops it.

## 3. Narrative summaries

The summary is memory for the model. Reciente and the narration on screen stay the lines the player has already been shown. Redaction does not edit those lines and does not write Room. The stored summary remains one string of at most 3,000 characters. No screen reads it. The journal mapping does not receive it.

On the way out, a copy is redacted in two passes.

The first pass splits on `.`, `?`, and `!` when the mark ends the text or is followed by whitespace. The mark stays with its sentence. A sentence is dropped when it contains an active secret, an identifier with `_`, an effect type name, or a bare token from the list below.

The second pass walks the remaining sentences in order. A sentence is dropped when the sentence before it was dropped and this one begins with a closed anaphora: "Ella", "Él", "Ese", "Esa", "Eso", "Por eso", "Por ello", "Por esa razón", "Tampoco". The pass repeats until none of those openings follow a gap. This is the check against a fragment that only made sense because of the forbidden sentence.

"Elia está muerta. Ella no regresará. La plaza sigue en calma." therefore sends "La plaza sigue en calma." while the death is unrevealed. "Ella no regresará." does not survive on its own. After `evidence_elia` is held, the death sentence can remain, and the following sentence can remain with it. A sentence the anaphora list does not catch, such as "La archivista ya no respira.", can still carry the secret in other words. That remainder is recorded as model-dependent. It is not claimed as solved.

On the way back, the backend applies the same two passes to `narrativeSummary` before the forbidden check:

- If the narration `text` still contains an active secret or a technical token, the reply stays unusable. Android shows `localProse` when that line exists. The previous summary stays stored.
- If the narration `text` is clean, the reply is usable. The summary stored with it is the cleaned one. An empty cleaned summary is omitted, and Android keeps the summary already on the state.
- One bad sentence does not throw away a clean narration, and it does not throw away the safe sentences around it.

## 4. What the model is shown

The request body still carries the ids the server uses to validate the call: the action id, the outcome, the check, and the applied effects. Those fields stay available to the validator. They are not the prose the model is asked to write from.

`build_narration_prompt` and `build_scene_prompt` project a prose view:

- location name, without `locationId`
- present characters by Spanish name, without npc id or npc state
- purpose, revealable lines, and the other revealed sentences from section 2
- the outcome in words: completed, not completed, or rejected
- a stored check as dado, Fuerza or Agilidad or Inteligencia, modificador, total, dificultad, and whether it succeeded
- life after the resolution
- the Spanish ending title, when an ending is stored, without `justice`, `vengeance`, `silence`, `failure`, or `death`
- `hora`, the confirmed clock the player can already see, as `HH:MM`
- `hecho`, the outcome's `localProse`, when that line exists

`location`, `hora`, `hecho`, the outcome, and the check come before the summary. The location and the purpose are those of the place the resolution left the player in. The scene contract id and its allowed actions stay the contract that authorized the action, so a move can still be narrated. The clock, the life, and the check stay in that prompt as confirmed context. The instruction tells the model that `location` is the only confirmed place, that a later hour is not happening now and is not about to happen, and that the narration describes what the character does or experiences. It gives the status line as an example of what not to write. The same reminder follows the narration JSON, after the confirmed values. It does not recite the clock, the life total, the die, the attribute, or the difficulty, because the interface already shows them. Finding a path or an entrance is not the same as moving there. The narration enters a place only when `hecho` says so, or when `location` is already that place. Until then it does not describe the interior. A sensory image from the recent lines is not repeated when this resolution does not need it. That guidance is not a list of forbidden words. The voice stays the second person, the present tense, and mystery.

The projection does not include action ids, flag names, effect type names, item ids, clue ids, evidence ids, or ending ids. A newly granted clue or proof reaches the model as its Spanish text, which is the text the journal can already show. A flag or an npc-state effect reaches the model only through the outcome's `localProse`, when that line exists.

Interpretation still receives action ids, because it has to return one. That request is not player-facing text. It does not receive unrevealed evidence text or the solution statement.

The output filter in the next section remains. It is the defense for a token that still reaches the player. It is not the only defense. The first defense is this projection.

## 5. Technical identifiers

Player-visible narration, generated scene text, and the summary reject:

- a document id that contains `_`
- every name in `EFFECT_TYPE_NAMES`
- these bare tokens, as whole words, case-insensitive: `justice`, `vengeance`, `silence`, `failure`, `death`, `dead`, `present`, `absent`, `working`, `withdrawn`, `guarded`, `digging`, `unwarned`, `saved`, `lost`, `tavern`, `church`, `cemetery`, `archive`, `crypt`, `STRENGTH`, `AGILITY`, `INTELLIGENCE`

The match is not a substring. `plaza` is not on the list, because it is a Spanish word the prose uses. "Elia" is not on the list. Buttons, the journal, and TalkBack already use the Spanish labels from milestone 10, and those labels are not run through this list.

A narration that contains one of these words is unusable and falls back to `localProse`. A summary sentence that contains one is dropped, and the anaphora pass then runs on what remains.

## 6. JSON errors and recovery

`call_with_one_retry` already retries a schema or contract failure once. A timeout or any other provider error stops immediately. Groq's HTTP 400 "Failed to validate JSON" currently becomes `PROVIDER_ERROR` on that immediate path.

The OpenAI-compatible transport classifies that message as invalid model output. It reads the body only to choose the path. The body is not attached to the exception, the correction note, the API response, or the Android notice.

The existing retry then applies:

- The correction says the response is not a JSON object. It does not include the provider body.
- A second failure of the same kind returns `INVALID_MODEL_OUTPUT`, retryable, attempt 2.
- A timeout, a connection failure, and any other non-200 stay `PROVIDER_TIMEOUT` or `PROVIDER_ERROR` and are not retried.

There is no third attempt. Android does not add an automatic retry of its own. One player action produces one HTTP call. The backend's single retry happens inside that call.

Three failures stay distinct.

- Interpretation fails before resolve. `commitIfCurrent` is not called. Health, inventory, clock, clues, evidence, ending, and the roll stay as they were. The notice is the sentence in the next section.
- Narration fails after resolve. The action, the stored roll, the applied effects, and the saved state stay. When `localProse` exists, that line is stored `VALIDATED` and shown. When it does not, the pending narration stays awaiting and the screen shows "El resultado está guardado. La narración se puede reintentar." Neither path rolls again or applies the effects again.
- Restore finds a pending narration. The launch shows the confirmed state and does not call the engine. It does not call the model until the player retries narration. Retry narrates the stored result. It does not resolve the action again.

A client read timeout is the first of those failures when it happens before the commit, and the second when the commit has already returned. It does not start a second resolution. A game already past midnight, or already ended, is not replayed to recover a sentence.

An interpretation the model did answer, and that the contract rejected, still uses "No se ha hecho ninguna acción." That is a rejected reading, not a provider failure.

## 7. Waiting

These limits stay for this milestone:

| side | limit |
| --- | --- |
| Backend `LLM_TIMEOUT_SECONDS` | default 30, maximum 120 |
| Android connect | 15 seconds |
| Android read | 60 seconds |

The Groq run uses the default of 30 seconds, so the one validation retry can finish inside the Android read. This milestone does not raise either timeout. The acceptance tests check that the pair does not create a second player-level retry, does not leave the controls disabled after the call returns, and does not drop a commit that already succeeded.

While the call is in flight, the previous narration stays on screen. Decisions and Enviar are disabled. Under that narration the column shows "La partida está resolviendo la acción." That sentence is the activity indicator. It is text, so it is not only a motion or a color. It does not open a dialog and it does not replace the narration. The new narration replaces the previous line only after a validated string is stored.

When the provider cannot be consulted, the player sees:

> La partida no ha podido consultar al narrador. Puedes intentarlo de nuevo.

The provider's English text, the HTTP status, and the words "Failed to validate JSON" do not appear. Approving this design approves that sentence. The other notices stay as they are.

## 8. Reduced motion and pictures

Milestone 10 confirmed the stored numbers of a check on `dm_phone` and did not capture the first frame. This milestone closes that gap on the same phone.

Set the animator duration scale to 0. Resolve one action that stores a check. Capture the first frame of the check strip. The frame shows the stored roll, the Spanish attribute, the modifier, the total, the difficulty, and "Supera la prueba" or "No supera la prueba". It does not show another face on the way to that roll.

The pure function already returns the stored result for that scale. The capture is the missing observation. If the first frame shows a settling face, the repair stays in the presentation. It does not call `Die`.

The six location pictures stay provisional resources under the names milestone 10 already uses. This milestone does not replace them and does not call them final.

## 9. What is deterministic, and what is not

The close records this split. A check in the first group is a failing test when it breaks. A check in the second group is a written observation.

Deterministic:

- revealed facts authorize a phrase, and internal state alone does not
- "Elia está muerta" is absent before `evidence_elia` and allowed after it
- "Elia sigue viva" is rejected in both saves
- sentence redaction keeps the safe sentence and drops the anaphoric follower
- the narration prompt projection contains no action id, effect name, npc state, or ending id
- player-visible text rejects `_` ids, effect names, and the bare-token list, and it keeps `plaza`
- "Failed to validate JSON" produces one provider call from Android and two model attempts inside the backend
- a timeout is not retried
- interpretation failure leaves the stored revision unchanged
- narration failure keeps the stored roll and effects and does not resolve again
- restoring an awaiting narration does not resolve again
- the summary string is absent from the UI model
- the previous narration is still the visible line while `resolving` is true

Model-dependent, recorded from the Groq text and not promoted to a token proof:

- mystery, dread, and the quality of the sensory detail
- a paraphrase that states a secret without using a listed phrase
- a sentence that remains after redaction and still implies the dropped fact without a listed anaphora

## 10. Acceptance and tests

Acceptance is the list in section 9, plus these runs.

Automated, with no new library. Android tests are the JVM tests under `app/src/test`. Backend tests are pytest.

- Revealed and unrevealed Elia: a new game with her npc state `dead` and no `evidence_elia` does not authorize the death sentence. The save after the granting resolution does.
- Canon contradictions: "Elia sigue viva", a real creature, and a second midnight are rejected before and after the evidence is held.
- A summary with one forbidden sentence, one anaphoric follower, and one calm sentence stores and sends only the calm sentence while the secret is unrevealed. After the reveal, the death sentence may remain. The UI model has no summary field.
- `read_elias_letter`, `SET_FLAG`, `justice`, `crypt`, and `unwarned` fail the player-visible check. `plaza` and "Elia" pass. The narration prompt fixture contains the Spanish location and the Spanish check words, and it does not contain those tokens.
- A provider body of "Failed to validate JSON" is attempted twice inside one request. A timeout is attempted once. The correction and the Android notice do not contain the provider body. Android's client is called once.
- Interpretation `Failed` does not call `commitIfCurrent` and shows the new sentence. Narration `Failed` after a saved resolution keeps the roll and the effects, shows `localProse` when it exists, and does not resolve again.
- Loading a stored awaiting narration returns the committed state, makes no engine call, and makes no model call until retry.
- While a call is blocked, the presented state still contains the previous narration and `resolving` is true. When the call returns, `resolving` is false. A timeout after the commit does not change the stored revision except to validate prose or leave the row awaiting.
- The voice paragraph is in the narration and scene instructions and absent from interpretation.
- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.
- `backend/.venv/bin/pytest -q backend/tests`.

Groq, with `ADVENTURE_LIVE_LLM=1`. The test does not read or print an API key. Dice are a `ScriptedDie`. `GET /health` is recorded with the provider and the model name. The same acceptance record includes one simulated provider failure, so the run does not depend on Groq happening to fail.

- One real narration before Elia's death is revealed. Record the Spanish text and the milliseconds. The text does not contain the death phrase, a listed culprit phrase, or a bare token.
- One real narration after `evidence_elia` is held. The death may be said. The text does not say that she is alive. The prompt's summary, if it mixed a secret sentence with a calm one before the reveal, keeps the calm sentence.
- One real narration of an action that stored a check. The prompt still carries the clock, the life, and the check. The prose describes what the character does or experiences. It does not recite the hour, the life total, the die, or the difficulty.
- One simulated "Failed to validate JSON", before resolve and after a committed check. Record the attempt count, the public code, and that the failed interpretation added no effect. The failed narration kept the stored roll and did not resolve again.

The reduced-motion frame is a capture on `dm_phone`, not a Groq call.

## Files the implementation is expected to touch

These files are listed so the review can see the boundary. This document does not edit them.

- `backend/src/dungeon_master_ai/context/builder.py`, for the prose projection and the voice instruction
- `backend/src/dungeon_master_ai/providers/transport.py`
- `backend/src/dungeon_master_ai/validation/retry.py`
- `backend/src/dungeon_master_ai/validation/validator.py`, for summary sentence redaction
- the backend tests for language, the prompt projection, validation retry, and forbidden text
- `app/src/main/java/com/dungeonmasterai/domain/adventure/narration/NarrationView.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/narration/TechnicalIdentifiers.kt`
- `app/src/main/java/com/dungeonmasterai/data/AdventureReplyPolicy.kt`
- `app/src/main/java/com/dungeonmasterai/presentation/AdventureSession.kt`, for the active list, the revealed sentences, and the new notice
- `app/src/main/java/com/dungeonmasterai/presentation/PlayerCopy.kt`
- the JVM tests for the view, the reply, the session, the prompt, and restore
- [ARCHITECTURE.md](ARCHITECTURE.md) and [backend/README.md](../backend/README.md), to match the revealed-fact rule, the prose projection, the sentence redaction, and the JSON retry
- the presentation of the check strip, only if the first reduced-motion frame is wrong

The implementation is expected to leave these alone: `AdventureEngine`, `GuardiansBridgeEngine`, the static text and effects in `valdumbria.json`, Room, the bridge narrator parser, and the six picture files.
