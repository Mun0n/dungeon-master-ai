# Milestone 9 design — revised

Status: approved and implemented. Completion is recorded in [ROADMAP.md](ROADMAP.md). This document does not by itself change that record.

Depends on milestone 8, which is completed. Milestone 10 has a design awaiting approval and is not started. Milestone 11 is not started. Portrait orientation stays a written requirement for milestone 10. This design does not change the manifest.

The numbers and the effect order stay the rules in [GAME_RULES.md](GAME_RULES.md). This revision adds three rule changes, all required by the review: repeatable travel, a retry that watches the facts named by that action, and a structured conclusion that Kotlin checks before `deduce`. Schema version stays 1. The new JSON fields are optional, so the artificial fixture stays valid when it omits them.

## Decisions from the review

1. Repeatable travel is approved. `repeatable` defaults to false and is only for a pure move.
2. Two false accusations must not make one of the four mandatory proofs unreachable. Accusing someone still makes that person withdraw. A route that does not need the withdrawn person remains.
3. Returning `name_the_crime` is not enough. The model fills four authored claims. Kotlin matches them to the authored key and checks the four evidence ids. Only then does it call `AdventureEngine.deduce`. The model has no effect list and cannot set `truth_known`.
4. A conclusion does not reopen a failed check. A retry opens only when a fact named by that action's own retry requirement has changed, and the requirement now holds.
5. Narration, the narrative summary, and generated scene text still reject internal ids and effect names. The player reads natural Spanish.
6. Acceptance includes one recorded Groq run: a correct Spanish conclusion, an incomplete one, and a false one. The two incorrect ones do not change the confirmed state.
7. This document is the approved design. It does not by itself mark the milestone complete.

## Objective

The authored mystery in `app/src/main/assets/adventures/valdumbria.json` becomes playable through the session: three acts, six locations, the four evidence items, one midnight, three ways to save Inés, a free-text conclusion, and the five terminal endings.

Milestone 8 kept the investigation out of the plaza and tavern scene contracts. Milestone 9 opens those contracts, adds the two actions below, and connects a player sentence to `AdventureEngine.deduce` only after the claim check. It does not add a location, an evidence item, an ending, or a combat rule.

## What this milestone does not do

- It does not implement milestone 10 or 11. The portrait lock stays out of the manifest. The illustrated screens stay out.
- It does not grant the deduction from a button. `choose` keeps calling `resolve`. It never calls `deduce`.
- It does not add an action that sets `truth_known`. `state_the_conclusion` stays absent.
- It does not let the model roll dice, write `AdventureState`, decide Inés's fate, or grant the deduction by naming an id.
- It does not change The Guardian's Bridge, its narrator, or its save.
- It does not add a dependency. Groq uses the existing OpenAI-compatible adapter. The key stays in the environment.

## Canon the prose has to keep

Elia is dead when the game starts. No action brings her back. Her death is stated only by the effects on `read_elias_notes` and `mara_kept_the_packet`, and by the text of `evidence_elia`. A scene purpose or a revealable line still must not announce it.

Padre Aldren is the culprit. Don Beltrán, Mara, Tomás, and the rumored creature are not. Accusing one of them withdraws that person. It is not the deduction.

The conclusion `name_the_crime` has four claims. All four must match, and the state must already hold all four evidence ids:

- Recent murders: Padre Aldren is responsible, including for Elia's death.
- Confinement: during the epidemic he deliberately shut healthy people in with the sick.
- Records: the historical registers were altered to conceal that crime.
- Connection: the recent murders keep that older confinement hidden.

Inés can be saved or lost at midnight. Saving her is not required for JUSTICE. The creature under the cemetery is a tavern rumor. The bells ring once.

The player-facing line, stored on the deduction and ignored by the engine:

> Las cuatro pruebas coinciden. Durante la epidemia, Padre Aldren encerró a personas sanas con los enfermos e hizo alterar los registros para ocultar aquel crimen. Los asesinatos recientes, incluida la muerte de Elia, mantienen ese encierro en secreto. La lista escondida lo señala a él. Don Beltrán, Mara, Tomás y la criatura de la taberna no son la causa. Elia no puede volver. Lo que le ocurra a Inés no cambia esta conclusión.

The same text is the `localProse` shown when the deduction is accepted. It contains no identifier. It is not shown when a conclusion is rejected, so a wrong guess does not reveal the answer.

## Repeatable movement

Approved. An action may set `repeatable` to true. Omitted, it stays false, so the fixture and every current check keep today's behavior until an action opts in.

A repeatable success does not close the action. Each resolution still spends `timeCostMinutes`, still requires `availability`, and still applies its effects. A repeatable action has no check, no failure outcome, no retry, and no ending. Its success effects are a single `MOVE_TO`. The validator rejects any other use. It cannot grant evidence, an item, a clue, or a flag.

The repeatable actions are the ten moves already in the document, plus the new tavern exit:

`go_to_tavern`, `go_to_plaza`, `enter_the_church`, `leave_the_church`, `enter_the_archive`, `leave_the_archive`, `enter_the_cemetery`, `leave_the_cemetery`, `enter_the_crypt`, `return_to_the_cemetery`, and `leave_the_tavern`.

`follow_elias_directions` is not a move and is not repeatable. It succeeds once.

This is a rule change. Implementation updates [GAME_RULES.md](GAME_RULES.md) and adds a unit test in the same change. The obtainability proof further down resolves each of the old travel ids once, so it is legal before and after the flag. The accusation path uses `go_to_tavern` and `leave_the_tavern` twice, so that path depends on the flag. A separate test enters the church, leaves, enters again, and then resolves `take_the_hidden_list`.

## Accusations and the archive

Accusing Beltrán before he has revealed the archive, and accusing Mara, closes both current routes to `evidence_elia`. He is the only action that discovers the archive. Her packet is the other grant, and it needs her to be working. That lock is rejected.

Two additions keep the social cost and leave a route that does not need either of them.

`follow_elias_directions` is a plaza action. It requires `letter_read`. It does not require an NPC, a check, or a withdrawal. Its only effect is `DISCOVER_LOCATION` of the archive. The letter already names that building. Beltrán can still discover it, and he can still give `clue_founders`. If he has withdrawn, or if his check can no longer be retried, the letter's directions remain. `read_elias_notes` then grants `evidence_elia` with no NPC. `mara_kept_the_packet` stays the other grant, and it still closes when Mara withdraws.

`leave_the_tavern` is the exit used when accusing Mara has closed her rumor. `go_to_plaza` still requires `clue_rumor_creature`, so a player who has not accused her cannot skip those clues. Requirement has no OR. A new optional list, `absentClueIds`, means those clues must be absent. Empty means no such constraint, so every current requirement is unchanged. `leave_the_tavern` requires Mara `withdrawn` and `absentClueIds: ["clue_rumor_creature"]`. The two exits cannot be available together, so a sentence about leaving is not ambiguous between them. While Mara is working, `leave_the_tavern` is unavailable. Accusing her withdraws her, grants no clue, and opens that exit. `sit_with_the_regulars` still requires her to be working, so the room does not replace her information after the accusation.

The other mandatory proofs already have a route that survives these withdrawals:

| evidence | route that needs no withdrawn helper | what the accusation closes |
| --- | --- | --- |
| `evidence_elia` | `follow_elias_directions`, then `read_elias_notes` | Beltrán's archive lead and Mara's packet |
| `evidence_record` | `read_burial_list` at the cemetery | `clue_founders` and the retry of `compare_registers` |
| `evidence_crypt` | `follow_the_sinking_ground`, then `trace_the_scratches` | Tomás's lockpick and his guided entrance |
| `evidence_alden` | `take_the_hidden_list` after midnight, or a `search_vestry` whose retry fact changes | neither Beltrán, Mara, nor Tomás |

`follow_the_path_behind_the_inn` discovers the cemetery and needs only `letter_read`. The church is discovered by `study_the_dark_tower`, which needs only `letter_read`. Midnight still sets `alden_away` on both of its branches.

## Retries

Approved direction. Today a failed check stays closed until `reopenWhen` holds and `worldStamp` is greater than the stamp stored on that attempt. The stamp moves when a later effect changes a flag, an item, evidence, a clue, a relationship, an NPC, a discovery, or a place. A deduction also moves it. Health, time, and `RECORD_ATTEMPT` do not. An unrelated change can therefore reopen a check whose retry fact was already true, and stating the conclusion can do the same.

The new gate ignores `worldStamp`. On a failure, the attempt stores a canonical projection of the facts named by that action's `reopenWhen`: the listed flags, items, evidence, clues, relationships, NPC states, discoveries, and deductions, read after that resolution. The retry opens only when `reopenWhen` holds and that projection is different from the stored one. If the named facts already held at the failure and nothing named there has changed, the check stays closed. Another route has to exist, and for the four proofs it does.

`deduce` no longer increments `worldStamp`. It still sets the deduction flag and appends the deduction id. Neither of those facts is named by a Valdumbría retry or by a fixture retry, and the projection would ignore them anyway. Removing the stamp bump keeps a conclusion from counting as a world change.

`worldStamp` stays on the saved state so existing rows still load. Effects that change the world still advance it. Retries do not read it. Room moves from version 3 to version 4. `adventure_attempt` gains `relevantFacts`, a non-null text column defaulting to empty. Migration 3 to 4 does not touch the bridge tables. A failed attempt loaded with an empty projection stays closed: the snapshot of the old failure was not stored. The alternate route remains. A new game stores the projection from the start.

The fixture test `aConditionAlreadyTrueDoesNotReopenTheAttempt` changes with this rule. `read_archive` fails while `clue_bell` is already held. `ask_clerk` changes other facts and must leave `read_archive` closed. `copy_archive` still grants that fixture evidence. `failedAttemptDoesNotRetryUntilTheWorldChanges` stays: `force_hatch` reopens when the brass key appears, and not when an unrelated action moves the old stamp. This is the same change as [GAME_RULES.md](GAME_RULES.md), and the unit test lands with it.

For Valdumbría, a retry that names a fact the player does not yet have still opens when that fact appears. `compare_registers` reopens when `clue_founders` is gained after the failure. It does not reopen when the clue was already held, nor when the player states the conclusion. `read_burial_list` is the other route. `search_vestry` reopens when `alden_away` becomes true after the failure. `look_over_the_square` and `ask_mara` name `letter_read`, which is already required to attempt them, so those two checks do not reopen. `sit_with_the_regulars` still grants the clues a failed question missed.

## Structured conclusion

The model classifies the player's Spanish sentence into a closed vocabulary. Kotlin decides whether that classification is the authored conclusion. The model does not see which option is accepted, and it does not see the contradiction pairs. It sees each claim id, a Spanish question, and every option id with a short Spanish gloss. The glosses are natural language and contain no underscore id. The option ids are engine data. They are not shown to the player.

`Deduction` gains optional `statement`, `localProse`, `claims`, and `contradictions`. Empty claims are allowed, so the fixture deduction stays a direct `deduce` for engine tests and is never offered for free text. A claim offered to the model must have a non-empty prompt, at least two options, and a non-empty `accept` list whose values are options of that claim. `statement` and `localProse`, when present, are at most 1,200 characters and must not contain an underscore id or an effect type name. The session offers `name_the_crime` only when both strings are non-empty, every claim is complete, the four evidence ids are held, the deduction is not already recorded, and the game is not terminal.

The four claims:

| claim | question the model is asked | accepted value | other options |
| --- | --- | --- | --- |
| `recent_killings` | Whom does the sentence blame for the recent murders? | `alden` | `beltran`, `mara`, `tomas`, `creature`, `unknown` |
| `confinement` | What does the sentence say happened to healthy people during the epidemic? | `shut_in_with_the_sick` | `treated_and_released`, `never_happened`, `unknown` |
| `records` | What does the sentence say about the historical registers? | `altered_to_conceal` | `records_are_accurate`, `unknown` |
| `connection` | How does the sentence relate the recent murders to the older crime? | `recent_killings_conceal_the_epidemic` | `recent_killings_are_unrelated`, `there_was_no_older_crime`, `unknown` |

`recent_killings` lists `beltran`, `mara`, `tomas`, and `creature` as `falseValues`. Those are accusations of the wrong culprit.

Contradiction pairs, stored on the deduction and not sent to the model:

- `confinement` is `never_happened` and `connection` is `recent_killings_conceal_the_epidemic`.
- `records` is `records_are_accurate` and `connection` is `recent_killings_conceal_the_epidemic`.
- `confinement` is `never_happened` and `records` is `altered_to_conceal`.
- `connection` is `there_was_no_older_crime` and `records` is `altered_to_conceal`.

Interpretation gains one status, `CONCLUSION`, and one optional `claims` list. Each entry is a claim id and one option id. The list defaults to empty, so an older reply still decodes. There is no `candidateDeductionId`. Contract version stays 1. A response still has no effect list and no next state.

| status | required shape |
| --- | --- |
| `CONCLUSION` | `claims` contains each offered claim id once. Every value is an option of that claim. `candidateActionId` is null. `candidateActionIds` is empty. `parameters` is empty. |
| every other status | `claims` is empty. The existing action rules stay. |

The backend checks that shape against the offered claims. It does not know the accept list, and it does not decide that the conclusion is true. Kotlin classifies a well-formed `CONCLUSION` in this order:

1. A `falseValues` hit on `recent_killings` is a false accusation, even if another pair is also contradictory.
2. Otherwise a matched contradiction pair is a contradiction.
3. Otherwise a value inside `accept` for every claim is accepted.
4. Otherwise it is partial. `unknown`, a missing part of the story, or an unrelated account lands here.

A malformed reply is unusable. It is not a partial conclusion. Only an accepted classification may call `deduce`, and only for the offered deduction id. The session then calls `engine.deduce`. The engine still rejects missing evidence, a finished game, and a repeated deduction. A false, partial, contradictory, unusable, or ineligible reply does not call `deduce`.

One reply does one thing. A conclusion does not also resolve an action. An action reply does not also carry claims.

## Architecture

`AdventureEngine.deduce` stays the only writer of a deduction. It requires every listed evidence id, rejects a terminal game, rejects a repeated deduction, spends no time, and rolls no die. It sets `truth_known` and appends `name_the_crime`. It does not read claims, statement, or prose.

`AdventureSession.submitText` is the only player path into `deduce`. The call happens inside `commitIfCurrent`, after the claim classification is accepted and the deduction is still eligible. A rejected deduction writes nothing and comes back `Unchanged`. Clock, life, inventory, location, evidence, flags, and ending stay as they were.

The authorized view gains `availableConclusions`. It is empty until `name_the_crime` would be eligible and its claims are complete. Each entry has the deduction id, for Kotlin, and the claims without `accept` and without `contradictions`, for the model. The Spanish statement is not included, so the prompt does not hand over the solution.

The pending narration for an acceptance is stored `VALIDATED` in that same transaction, with the deduction's `localProse` as the text. The same transaction appends that line to the recent interactions. The session writes that prose itself. The action lookup cannot find it, because `name_the_crime` is not an action. The session does not call the model for that line. Restoring it does not roll and does not deduce again. `PendingNarration.actionId` holds `name_the_crime`. The stored outcome is `SUCCEEDED`, `success` is true, and there is no check. The codec already accepts a success without a check. `choose("name_the_crime")` stays unavailable because it is not an action.

Without a backend, a sentence is still matched only to an available action id. It does not call `deduce`. JUSTICE in a live game needs the interpreter. Engine tests do not. They call `deduce` directly.

Scene contracts stay data. Resolution still ignores them. The session still refuses an action the current contract does not list before `resolve` runs. The church, cemetery, archive, and crypt contracts already list their actions.

## Deduction flow

1. The player writes a sentence in the current scene. There is no deduction button.
2. The session builds the narration view. `availableConclusions` is included only when `deduce` would be eligible.
3. The interpreter returns `CONCLUSION` and one value per claim, or it returns an ordinary action status.
4. Kotlin accepts the conclusion branch only when the reply matches the current request id, revision, and scene contract, the action fields are empty, and the claims are well-formed against the offered set.
5. If the deduction is not eligible, the session stops. It does not call `deduce`. A perfect claim list without the four proofs changes nothing.
6. Kotlin classifies the claims. Only the accepted class continues.
7. `commitIfCurrent` calls `engine.deduce("name_the_crime")`. Missing evidence, a finished game, or a second conclusion comes back `Unchanged`.
8. An acceptance stores `truth_known`, the deduction id, and the validated Spanish line. Time stays where it was. No die is rolled. `worldStamp` stays where it was.
9. The moral actions stay blocked until that acceptance. They are `publish_the_truth`, `take_vengeance`, and `keep_the_secret`, at the church. Each one is an ordinary action. A button may choose one of them after the deduction. A button still may not perform the deduction.

A rejected conclusion uses one Spanish notice, "Esa conclusión no se sostiene." The notice does not say which claim failed and does not name the technical ids. Tests distinguish false accusation, contradiction, partial, and ineligible internally. The player does not receive that classification, because the missing piece would be a hint.

No Room migration is required for the deduction itself. A deduction is already a row the snapshot can store. The attempt projection is the migration in the retry section. The new narration is the existing pending-narration row with delivery `VALIDATED`.

A sentence that names Beltrán, Mara, Tomás, or the creature is a false accusation when it is sent as a conclusion, and an ordinary action when the interpreter recognizes `accuse_beltran`, `accuse_mara`, or `accuse_tomas`. The action applies that action's authored effects. The conclusion does not call `deduce`.

Before the four evidence items are held, the conclusion is not offered. A model that returns `CONCLUSION` anyway is declined. The state does not change.

After the deduction, a second conclusion is rejected by the engine if it is called, and the session does not call it again because the deduction is no longer eligible. A sentence that then asks to publish, to take revenge, or to keep the secret is an action at the church.

## Actions and conditions

The plaza contract adds the authored ids it currently omits: `warn_ines`, `speak_with_beltran`, `note_suspicion`, `accuse_beltran`, `leave_valdumbria`, `study_the_dark_tower`, `follow_elias_directions`, `enter_the_church`, `enter_the_archive`, `enter_the_cemetery`. The opening four stay: `read_elias_letter`, `speak_with_ines`, `look_over_the_square`, `go_to_tavern`.

The tavern contract adds `follow_the_path_behind_the_inn`, `ask_mara_for_linen`, `bind_a_wound`, `mara_kept_the_packet`, `accuse_mara`, `leave_the_tavern`. The opening three stay: `ask_mara`, `sit_with_the_regulars`, `go_to_plaza`.

Travel that is already authored, plus the new exit:

| move | from | requires | repeatable |
| --- | --- | --- | --- |
| `go_to_tavern` | plaza | tavern discovered, which the letter does | yes |
| `go_to_plaza` | tavern | `clue_rumor_creature` | yes |
| `leave_the_tavern` | tavern | Mara `withdrawn`, and `clue_rumor_creature` absent | yes |
| `enter_the_church` | plaza | church discovered by `study_the_dark_tower` | yes |
| `leave_the_church` | church | nothing else | yes |
| `enter_the_archive` | plaza | archive discovered by `speak_with_beltran` or `follow_elias_directions` | yes |
| `leave_the_archive` | archive | nothing else | yes |
| `enter_the_cemetery` | plaza | cemetery discovered by `follow_the_path_behind_the_inn` | yes |
| `leave_the_cemetery` | cemetery | nothing else | yes |
| `enter_the_crypt` | cemetery | crypt discovered by `ask_tomas` or `follow_the_sinking_ground` | yes |
| `return_to_the_cemetery` | crypt | nothing else | yes |

`note_suspicion` sets `suspicion_noted`. Nothing requires that flag. The action stays, because it is already authored. Milestone 9 does not give it a new meaning.

`follow_elias_directions` costs 10 minutes. `leave_the_tavern` costs 10 minutes. Both can be the action that crosses midnight. Neither sets `ines_protected`.

### Four evidence items

Each id below is granted by at least two authored actions. The validator already requires that shape. These tests play the actions.

| evidence | route | where | condition | check |
| --- | --- | --- | --- | --- |
| `evidence_record` | `compare_registers` | archive | `letter_read` | Intelligence 12. Retry after `clue_founders` is gained later. |
| `evidence_record` | `read_burial_list` | cemetery | none beyond being there | none |
| `evidence_crypt` | `examine_sealed_niche` | crypt | none beyond being there | Intelligence 12. Retry when `iron_pin` is gained later. |
| `evidence_crypt` | `trace_the_scratches` | crypt | none beyond being there | none |
| `evidence_crypt` | `break_the_seal` | crypt | none beyond being there | Strength 16. Retry when `iron_pin` is gained later. |
| `evidence_elia` | `read_elias_notes` | archive | `letter_read` | none |
| `evidence_elia` | `mara_kept_the_packet` | tavern | `clue_elia_at_church`, Mara `working` | none |
| `evidence_alden` | `search_vestry` | church | none beyond being there | Intelligence 16. Retry when `alden_away` becomes true later. |
| `evidence_alden` | `take_the_hidden_list` | church | after midnight and `alden_away` | none |

`clue_founders` comes from a successful `speak_with_beltran`. `iron_pin` comes from `ask_tomas`. Midnight sets `alden_away` on both of its branches. `accuse_alden_early` also sets it, and that accusation does not protect Inés.

All four stay obtainable after midnight. None of the granting actions require `beforeMidnight`, except that `take_the_hidden_list` requires the opposite. `read_burial_list`, `read_elias_notes`, `mara_kept_the_packet`, and `trace_the_scratches` have no midnight gate.

### Midnight and Inés

The clock starts at 22:00. Midnight is minute 120. Time moves only when an action resolves. The event `midnight_bells` fires once, on the action that crosses minute 120, and it reads flags from before that action's effects.

If `ines_protected` is already true, Inés becomes `saved`. Otherwise she becomes `lost`. Both branches set `alden_away` and Aldren `absent`. A later action does not fire the event again.

The three rescues set the flag and do not set Inés's state. The event does that, on a later resolution.

| action | where | when it is available | check | success |
| --- | --- | --- | --- | --- |
| `warn_ines` | plaza | before midnight, `clue_ines_summoned`, Inés `unwarned` | none | sets `ines_protected` |
| `bar_the_vestry` | church | before midnight, `clue_ines_summoned`, Aldren `present` | Strength 16 | sets `ines_protected`. Failure deals 2 damage. Retry when `iron_pin` is gained later. |
| `cut_alden_off` | cemetery | before midnight, `clue_ines_summoned` | Agility 12 | sets `ines_protected`. Failure deals 1 damage. Retry when `tomas_showed_path` is gained later. |

A rescue action that itself crosses minute 120 does not save her. The event has already chosen the unprotected branch. The opening test locks this for `warn_ines`. Milestone 9 locks the same order for `bar_the_vestry` and `cut_alden_off`.

`accuse_alden_early` is not a fourth rescue. It removes Aldren and does not set `ines_protected`.

Elia's state stays `dead` on every path.

### Five endings

All five are terminal. The next action and the next deduction are rejected. No die is rolled.

| ending | how it is reached | what else must already be true |
| --- | --- | --- |
| `justice` | `publish_the_truth` at the church | `name_the_crime`, `truth_known`, and the four evidence ids. The action sets `truth_public` and the ending. |
| `vengeance` | `take_vengeance` at the church | the same deduction and the four evidence ids. The action sets `chose_violence`. |
| `silence` | `keep_the_secret` at the church | the same deduction and the four evidence ids. The action sets `chose_silence`. |
| `failure` | `leave_valdumbria` at the plaza | `letter_read`. The action sets `gave_up`. The four evidence items are not required. |
| `death` | life reaches 0 | no requirement. `descend_the_open_drop` failing from full health deals 10 damage and is the authored way down. A `SET_ENDING` of `death` stays illegal. |

JUSTICE, VENGEANCE, and SILENCE are the explicit moral choice. They are three actions, played from three copies of the same post-deduction state. One state cannot take two of them, because the first ending stops the game. Saving Inés is not among their requirements.

`publish_the_truth` with the four evidence items and without the deduction is rejected. The state is unchanged. The same holds for vengeance and silence.

## Narration constraint

The interpreter's JSON may contain ids. The player never sees that JSON. Narration, the narrative summary, and generated scene text are unusable when they contain either of these:

- a document id that includes `_`, such as `clue_letter`, `letter_read`, `evidence_elia`, `name_the_crime`, `read_elias_letter`, or `shut_in_with_the_sick`
- an effect type name: `SET_FLAG`, `CHANGE_LIFE`, `ADD_ITEM`, `REMOVE_ITEM`, `DISCOVER_LOCATION`, `MOVE_TO`, `SET_RELATIONSHIP`, `ADD_EVIDENCE`, `ADD_CLUE`, `SET_NPC_STATE`, `RECORD_ATTEMPT`, `TRIGGER_EVENT`, `SET_ENDING`

The check is the existing forbidden-substring test, fed by that generated list. It does not ban a bare word that is also prose or a name, such as `plaza`, `ines`, or `dead`. NPC states without an underscore, such as `unwarned`, remain a residual leak. Milestone 11 can judge them, and it can judge tone. A substring ban would also ban ordinary words.

The Spanish narration instruction stops saying "Do not translate technical ids." It tells the model to write natural Spanish and not to copy identifiers, flag names, action ids, claim ids, option ids, or effect names. The model still receives ids in the interpretation request so it can map a sentence onto an action or a claim. That request is not player-facing text.

Unusable model text falls back to the action's `localProse`, including when that authored line contains a forbidden story phrase. That fallback already exists. Authored `localProse` in this adventure is Spanish and has no underscore ids. A reply that only fails the new id check uses that same fallback. The deduction line does not go through the model. When a deduction's statement or local prose is non-empty, validation rejects it if it contains an underscore id or an effect type name.

`forbiddenInformation` stays the spoiler list. The id check does not replace it. Scene purposes stay author instructions. They are not shown as player text.

## Tests

Dice are a `ScriptedDie`. No test calls `Random`. One path below is the obtainability proof. Ending tests may copy the state after `take_the_hidden_list`, once all four evidence items are held, and branch. Copying evidence ids into a fresh state does not count as that proof.

The proof does not use `repeatable`. Each of its travel ids occurs once. The actions that only spend time let midnight fall on `enter_the_archive` before any evidence is granted. `accuse_beltran` is safe here because `speak_with_beltran` has already discovered the archive. `read_elias_notes` and `read_burial_list` do not need Beltrán. This path does not use `follow_elias_directions`.

Scholar is the from-scratch character: Strength 2, Agility 2, Intelligence 5. The proof scripts three rolls, in order: 7, 1, 1.

- `speak_with_beltran` is Intelligence 12. Roll 7 plus 5 is 12, so it succeeds.
- `ask_mara` is Intelligence 8. Roll 1 plus 5 is 6, so it fails and grants nothing.
- `look_over_the_square` is Agility 8. Roll 1 plus 2 is 3, so it fails and grants nothing.

`bar_the_vestry` needs Strength 16, so its own test scripts a roll of 14 or higher. `cut_alden_off` needs Agility 12, so its own test scripts a roll of 10 or higher. `descend_the_open_drop` is failed with roll 1. `compare_registers` is not on the proof path. Its retry is a separate test.

### After midnight, all four evidence items, Inés saved, then three endings

`warn_ines` sets the flag at minute 15. `enter_the_archive` is a later action and moves the clock from 115 to 125, so the event sees the flag and Inés becomes `saved`. That move grants no evidence.

| step | action | minutes after | roll |
| --- | --- | --- | --- |
| 1 | `read_elias_letter` | 5 | |
| 2 | `speak_with_ines` | 10 | |
| 3 | `warn_ines` | 15 | |
| 4 | `study_the_dark_tower` | 20 | |
| 5 | `speak_with_beltran` | 30 | 7, success |
| 6 | `go_to_tavern` | 40 | |
| 7 | `sit_with_the_regulars` | 55 | |
| 8 | `follow_the_path_behind_the_inn` | 65 | |
| 9 | `ask_mara_for_linen` | 70 | |
| 10 | `bind_a_wound` | 75 | |
| 11 | `ask_mara` | 85 | 1, failure |
| 12 | `go_to_plaza` | 95 | |
| 13 | `look_over_the_square` | 100 | 1, failure |
| 14 | `note_suspicion` | 105 | |
| 15 | `accuse_beltran` | 115 | |
| 16 | `enter_the_archive` | 125 | |

Minute 125 has fired midnight once. Inés is `saved`. Aldren is `absent`. The location is the archive. Evidence is still empty. Health is 10, because the bandage clamps at the maximum. There is no ending. The coin purse is the only item, because the bandage was removed.

| step | action | minutes after | grants |
| --- | --- | --- | --- |
| 17 | `read_elias_notes` | 140 | `evidence_elia` |
| 18 | `leave_the_archive` | 150 | |
| 19 | `enter_the_cemetery` | 160 | |
| 20 | `read_burial_list` | 175 | `evidence_record` |
| 21 | `follow_the_sinking_ground` | 190 | crypt discovered |
| 22 | `enter_the_crypt` | 200 | |
| 23 | `trace_the_scratches` | 220 | `evidence_crypt` |
| 24 | `return_to_the_cemetery` | 230 | |
| 25 | `leave_the_cemetery` | 240 | |
| 26 | `enter_the_church` | 250 | |
| 27 | `take_the_hidden_list` | 260 | `evidence_alden` |

No travel id in those tables is resolved twice. Then `deduce("name_the_crime")` leaves the clock at 260, leaves `worldStamp` unchanged, sets `truth_known`, and does not roll. Three copies of that state resolve `publish_the_truth`, `take_vengeance`, and `keep_the_secret`. The endings are `justice`, `vengeance`, and `silence`. A fourth copy resolves `publish_the_truth` before `deduce` and stays unchanged, with no ending.

### Accusation combinations

These paths use no die. They assert the blocked route and the route that remains. The long path also plays every grant.

- Mara alone, before her packet: `mara_kept_the_packet` and `sit_with_the_regulars` are unavailable. `follow_elias_directions` and `read_elias_notes` still grant `evidence_elia`.
- Beltrán alone, before any success of `speak_with_beltran`: that conversation is unavailable and the archive is not yet discovered. `follow_elias_directions` discovers it. `read_elias_notes` grants `evidence_elia`. `read_burial_list` grants `evidence_record`.
- Tomás alone, before `ask_tomas`: that conversation is unavailable. `follow_the_sinking_ground` and `trace_the_scratches` still grant `evidence_crypt`.
- Mara and Beltrán together, before either has helped: both of the old `evidence_elia` leads are closed. The letter's directions and the notes still grant it. `leave_the_tavern` is available only after the accusation, and `go_to_plaza` stays rejected while the rumor is absent.
- All three, before any of them has helped. This path is the combination proof. `go_to_tavern` and `leave_the_tavern` each occur twice, so it requires `repeatable`.

| step | action | minutes after | result |
| --- | --- | --- | --- |
| 1 | `read_elias_letter` | 5 | tavern discovered |
| 2 | `go_to_tavern` | 15 | |
| 3 | `accuse_mara` | 25 | Mara withdrawn, no rumor |
| 4 | `leave_the_tavern` | 35 | plaza. `go_to_plaza` was rejected before this |
| 5 | `accuse_beltran` | 45 | Beltrán withdrawn, archive still unknown |
| 6 | `follow_elias_directions` | 55 | archive discovered, no evidence |
| 7 | `study_the_dark_tower` | 60 | church discovered |
| 8 | `go_to_tavern` | 70 | |
| 9 | `follow_the_path_behind_the_inn` | 80 | cemetery discovered |
| 10 | `leave_the_tavern` | 90 | |
| 11 | `enter_the_cemetery` | 100 | |
| 12 | `accuse_tomas` | 110 | Tomás withdrawn, no lockpick |
| 13 | `follow_the_sinking_ground` | 125 | midnight, Inés `lost`, crypt discovered |
| 14 | `enter_the_crypt` | 135 | |
| 15 | `trace_the_scratches` | 155 | `evidence_crypt` |
| 16 | `return_to_the_cemetery` | 165 | |
| 17 | `read_burial_list` | 180 | `evidence_record` |
| 18 | `leave_the_cemetery` | 190 | |
| 19 | `enter_the_archive` | 200 | |
| 20 | `read_elias_notes` | 215 | `evidence_elia` |
| 21 | `leave_the_archive` | 225 | |
| 22 | `enter_the_church` | 235 | |
| 23 | `take_the_hidden_list` | 245 | `evidence_alden` |

At minute 245 the four evidence ids are held. Mara, Beltrán, and Tomás are withdrawn. Inés is `lost` because nobody warned her. That does not block the deduction. `speak_with_beltran`, `mara_kept_the_packet`, and `ask_tomas` were never resolved. A separate assertion, after step 5 and before Tomás is accused, checks that the archive route is already open and Mara's packet is not.

`leave_the_tavern` while Mara is working changes nothing.

### Conclusion cases

The session tests start from the proof-path state at minute 260, before `deduce`, unless a case says otherwise. The backend is scripted. It does not grade Spanish. A counting engine records whether `deduce` was entered. The player-facing line is not the solution when the conclusion is rejected.

| case | reply | `deduce` | state |
| --- | --- | --- | --- |
| correct | all four accepted values | called once | `truth_known`, clock 260, stamp unchanged, narration is the authored line, `narrate` is not called |
| partial | `connection` is `unknown`, the other three accepted | not called | unchanged |
| partial | `confinement` is `treated_and_released`, the other three accepted | not called | unchanged |
| false | `recent_killings` is `beltran`, the other three accepted | not called | unchanged |
| false | `recent_killings` is `creature`, the other three accepted | not called | unchanged |
| false | `recent_killings` is `mara` | not called | unchanged |
| false | `recent_killings` is `tomas` | not called | unchanged |
| contradiction | confinement `never_happened` and connection `recent_killings_conceal_the_epidemic`, with Aldren and the altered records | not called | unchanged |
| contradiction | records `records_are_accurate` and connection `recent_killings_conceal_the_epidemic` | not called | unchanged |
| contradiction that is also false | `recent_killings` is `beltran` and a contradiction pair is present | not called | classified as a false accusation |
| ineligible | the correct claims, with one evidence id removed | not called | unchanged |
| unusable | `CONCLUSION` plus a `candidateActionId` | not called | unchanged |
| unusable | a claim id that was not offered, or a value outside that claim's options | not called | unchanged |
| action id | `RECOGNIZED` with `name_the_crime` | not called | unchanged, because it is not an action |
| button | `choose("name_the_crime")` | not called | `truth_known` stays false |
| second conclusion | the correct claims again, after an accepted deduction | not called | clock and deduction stay as they were |

No action effect sets `truth_known`.

### Retry cases

A prepared state records a failed `compare_registers` while `clue_founders` is already held. `deduce` leaves that action closed and does not move the clock or the stamp. A later action that changes an unrelated flag also leaves it closed. A paired state fails `compare_registers` before the clue exists. A later `speak_with_beltran` success opens the retry. The deduction does not.

A failed `search_vestry` before `alden_away` opens after midnight sets that flag. The same failure after the flag is already true stays closed, and `take_the_hidden_list` still grants `evidence_alden`.

### The other two rescues, and a rescue that arrives with the bells

Each path resolves the rescue, then resolves a later action that crosses minute 120. The later action must be one that has not already succeeded. Inés becomes `saved`. A paired path uses the rescue action itself as the crossing action. Inés becomes `lost` and the flag may still become true. `accuse_alden_early` before midnight, with no rescue flag, also leaves her `lost` after the bells.

`bar_the_vestry` and `cut_alden_off` each have one failed roll. The failure changes life and does not set the flag. The next attempt is rejected until the named retry fact changes.

The repeatable-movement test enters the church, leaves it, enters it again, and then resolves `take_the_hidden_list` after midnight. The second entrance is rejected before the rule and accepted after it. A repeatable action in the validator fixture cannot grant evidence or an ending.

### Failure, death, and the closed game

`leave_valdumbria` after the letter ends at `failure`, health 10, clock at 5. `descend_the_open_drop` with roll 1 from the crypt ends at `death`, health 0. A later action and a later deduction are rejected. Elia's state is `dead` after the justice path and after the death path.

### What the opening tests change

`theDocumentSeparatesThePlayableOpeningFromDeclaredContent` currently asserts that the plaza contract lists exactly the four opening actions and the tavern contract lists exactly the three. Those assertions change to the full lists in this design, including `follow_elias_directions` and `leave_the_tavern`. The test still asserts that `state_the_conclusion` is absent and that no action effect sets `truth_known`.

`theOpeningSessionRefusesDeclaredActions` no longer means the contract omits the investigation. At minute 0, `enter_the_church`, `warn_ines`, `publish_the_truth`, and `read_elias_notes` are still unavailable, and choosing them still changes nothing. After the letter, the tower, and the discovery of the church, `enter_the_church` resolves.

A model narration that contains `clue_letter` or `SET_FLAG` is unusable and the stored line is the action's `localProse`. A narration that says "la plaza" is not rejected for the word plaza. A narration that contains `shut_in_with_the_sick` is unusable.

The bridge tests stay green, apart from the fixture retry assertion named above. That assertion changes with the retry rule, in the same change.

Commands for the implementation, when it is approved:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`assembleDebug` is required because the session, the backend contract, the adventure document, and the Room migration are on the app path. Backend tests for `CONCLUSION` run with the backend's existing test command. The Groq run is separate and is an acceptance criterion. It is not part of the default unit-test command.

## Groq run

One live run is required before this milestone can be called complete. It uses the same shape as `FixtureWatchLiveLlmTest`: it runs only when `ADVENTURE_LIVE_LLM=1`, it talks to the local backend, and the backend uses the existing OpenAI-compatible adapter pointed at Groq. The key and the model name stay in the environment. The test does not read them and does not print them.

The save is the proof-path state at minute 260, before the deduction, restored separately for each sentence. That state would accept a correct conclusion. The incorrect sentences run first, each from that clean save.

- Correct: "Padre Aldren es quien mata ahora en el pueblo. Durante la epidemia encerró a personas sanas con los enfermos, hizo manipular los registros para ocultar aquel crimen, y los asesinatos recientes sirven para que ese encierro no salga a la luz." The confirmed state gains `truth_known` and `name_the_crime`. The clock stays 260. The history contains one accepted deduction.
- Incomplete: "Padre Aldren encerró a personas sanas con los enfermos. No sé qué pasó con los registros ni por qué siguen los asesinatos." The confirmed state is unchanged. The history has no accepted deduction.
- False: "Don Beltrán es el asesino. La criatura del cementerio hizo el resto. Los registros del archivo están bien." The confirmed state is unchanged. The history has no accepted deduction.

The milestone report quotes the three sentences, the claim values Groq returned, and the state after each one. If Groq maps the correct sentence to a non-accepted classification, or maps either incorrect sentence to the accepted classification, the run fails and the milestone is not accepted. The scripted tests do not replace this run. A unit-test suite that could not reach Groq does not satisfy this criterion.

The deduction's own narration is the authored line, so this run does not ask Groq to narrate the conclusion.

## Risks

- Kotlin does not read Spanish. It compares option ids. A model that ignores the player and picks the accepted glosses from the option list would pass the Kotlin gate. The prompt tells it to use `unknown` when the sentence does not assert a claim. That instruction is not the guarantee. The Groq run is the check for the three sentences above. It does not prove every paraphrase.
- The option glosses describe the true claims and the false ones in the same list. They have to, or the model would have no vocabulary. They are withheld until the four proofs are held. They are not player-facing text.
- Opening the contracts shows the model every action id on that scene. The id check rejects a player-facing copy. It cannot stop the model from knowing the ids.
- Snake_case is the banned shape. A state word without an underscore can still appear. That includes location ids such as `plaza` and `crypt`, ending ids such as `justice` and `death`, option ids such as `alden`, and NPC states such as `unwarned` and `dead`. Milestone 11 can judge those words.
- Live narration may fall back to `localProse` often, because the prompt forbids copying the ids the request still contains. That fallback is the accepted behavior for ordinary actions. The check stays.
- `follow_elias_directions` can discover the archive before the player has met Beltrán or Mara. The notes inside can then state Elia's death earlier than the tavern path. The death is still confined to that action's prose and to `evidence_elia`. The scene contracts do not announce it.
- `leave_the_tavern` and `follow_elias_directions` spend time and can be the action that crosses midnight. Neither protects Inés. The accusation path above loses her on purpose and still reaches the four proofs.
- A save from milestone 8 has no retry projection. A failed check in that save stays closed after the migration. The other authored route remains. The bridge save is untouched.
- The fixture's `read_archive` retry no longer opens after an unrelated `ask_clerk`. That is the rule change, not a content accident. `copy_archive` remains.
- `note_suspicion` still affects no requirement. Leaving it available can spend time and can be the action that crosses midnight.
- `descend_the_open_drop` is an easy death at difficulty 20. It grants a clue on success, not evidence. The justice path does not use it.
- A full inventory rejects the whole of `ask_tomas` or `ask_mara_for_linen`. A new character carries the coin purse only, so the authored paths fit. The tests should not fill the bag.
- The deduction is impossible offline. JUSTICE in a live game needs the interpreter. The milestone report should say that.
- Scene purposes remain English instructions. Player-facing `localProse` remains Spanish. The milestone does not rewrite the purposes.
- Three Groq sentences do not measure tone. Milestone 11 still reviews tone and unrevealed canon.

## Acceptance

- The from-scratch path grants all four evidence items after midnight, fires midnight once, and leaves Inés `saved`.
- JUSTICE, VENGEANCE, and SILENCE are reached from that deduced state, and JUSTICE is rejected without the deduction or without any one of the four evidence items.
- FAILURE and DEATH are reached on their short paths. Every ending rejects the next action and the next deduction.
- Each of the three rescue actions saves Inés when the flag is set before the crossing action, and none saves her when that same action crosses midnight. None saves Elia.
- Accusing Mara, Beltrán, or Tomás alone leaves another authored route to the proof that person can block. Accusing Mara and Beltrán before either has helped still leaves `follow_elias_directions` and `read_elias_notes`. Accusing all three before they have helped still grants all four proofs after midnight.
- A failed check stays closed until a fact named by its own retry requirement changes and the requirement holds. A conclusion does not reopen it. An unrelated flag does not reopen it.
- With `repeatable` on the eleven travel actions, the church can be left and entered again, and `take_the_hidden_list` still resolves after midnight. A repeatable action cannot grant evidence, an item, a clue, a flag, or an ending.
- A free-text conclusion calls `deduce` only when the four claims match and the four evidence ids are held. A false accusation, a partial conclusion, a contradiction, a malformed reply, a named action id, and a button do not call it and do not change the confirmed state.
- Model prose that contains an underscore id or an effect type name is not what the player sees. The accepted deduction shows the authored Spanish line, which contains neither.
- A rejected deduction or a rejected action returns the same adventure state.
- `domain` still imports neither Android nor Compose.
- The bridge totals and the bridge damage stay in place.
- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` have been run, and the backend tests for `CONCLUSION` have been run. Results are quoted in the milestone report.
- The Groq run above has been recorded. The incomplete sentence and the false sentence left the confirmed state unchanged. The correct sentence was the one that called the deduction.

## Files the implementation is expected to touch

- `app/src/main/assets/adventures/valdumbria.json`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/model/AdventureModels.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/model/AdventureRequirements.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/engine/AdventureEngine.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/engine/RequirementChecks.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/load/AdventureValidator.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/state/AdventureState.kt`
- `app/src/main/java/com/dungeonmasterai/domain/adventure/narration/NarrationView.kt`
- `app/src/main/java/com/dungeonmasterai/presentation/AdventureSession.kt`
- `app/src/main/java/com/dungeonmasterai/data/AdventureBackend.kt`
- `app/src/main/java/com/dungeonmasterai/data/AdventureReplyPolicy.kt`
- the Room attempt entity, mapper, and migration 3 to 4
- `backend/src/dungeon_master_ai/schemas/interpret.py`
- `backend/src/dungeon_master_ai/context/builder.py`
- the matching backend tests
- `app/src/test/java/com/dungeonmasterai/presentation/ValdumbriaOpeningTest.kt`
- `app/src/test/java/com/dungeonmasterai/domain/adventure/AdventureEngineTest.kt`
- a new JVM test for the paths and the claim matrix in this design
- a live test, skipped unless `ADVENTURE_LIVE_LLM=1`, for the three Groq sentences
- [GAME_RULES.md](GAME_RULES.md), [ADVENTURE_SCHEMA.md](ADVENTURE_SCHEMA.md), and [ARCHITECTURE.md](ARCHITECTURE.md), so they match the code

[GAME_RULES.md](GAME_RULES.md) changes for repeatable movement, the retry projection, and the deduction no longer moving `worldStamp`. Those edits land with their unit tests. No other rule in that file changes.
