# Roadmap

Statuses used below: Completed, In progress, Pending.

Do not start the next milestone without an explicit request. Milestone 11 is completed. Its design is in [MILESTONE_11_DESIGN.md](MILESTONE_11_DESIGN.md). Milestone 10 is completed. Its design is in [MILESTONE_10_DESIGN.md](MILESTONE_10_DESIGN.md).

The product decisions for milestones 3 through 11 come from the approved master prompt and from the milestone 3 audit. This file records that plan. It does not authorize the pending milestones.

## 0. Bootstrap — Completed

Depends on: nothing.

Objective: a single-module Android app that plays The Guardian's Bridge with fixed prose and deterministic rules.

Scope:

- One `:app` module.
- Character creation, d20 checks, and the three bridge actions.
- `GuardiansBridgeEngine` as the writer of health, inventory, and outcome.
- JVM tests.
- This documentation set.
- No AI, Room, or Hilt.

Acceptance:

- The bridge scene resolves Persuade, Pay, and Attack with the numbers in [GAME_RULES.md](GAME_RULES.md).
- A rejected intent does not change health, inventory, scene, or outcome.
- `domain` does not import Android or Compose.

## 1. One saved game — Completed

Depends on: milestone 0.

Objective: persist the current bridge `GameState` and restore it after process death.

Scope:

- One active game in Room.
- The engine remains the writer of rules state.
- A corrupt snapshot is dropped and character creation shows.
- A failed save leaves the in-memory state in place.
- Hilt was not added. `DungeonMasterApp` owns the database and `MainActivity` passes a factory.

Acceptance:

- The process can restore the engine's last `GameState`.
- Corrupt data does not crash the next launch.
- A write failure does not replace the valid in-memory state.

## 2. Narrator behind the engine — Completed

Depends on: milestones 0 and 1.

Objective: an optional HTTP narrator proposes narration and a structured intent. The engine still accepts or rejects the transition.

Scope:

- `narrator.baseUrl` in `local.properties`. An empty URL leaves the narrator absent.
- The device sends no API key.
- `parseNarratorPayload` accepts only `narration` (at most 400 characters) and `intent` (`PERSUADE`, `PAY`, `ATTACK`, `UNRECOGNIZED`, `AMBIGUOUS`). Extra fields are unusable.
- `KeywordIntentParser` remains the fallback.
- Suggested buttons do not call the narrator.
- A reply that arrives after a newer action is dropped.

Acceptance:

- An unusable payload, a thrown call, or a missing narrator does not change the bridge numbers or the scene's fixed lines.
- The engine writes narration only when it accepts the transition.
- The model never returns the next `GameState`.

## 3. Audit and transition architecture — Completed

Depends on: milestones 0, 1, and 2.

Objective: inspect the project and decide how the bridge engine grows into a generic adventure engine, without rewriting it.

Scope that was delivered:

- Inventory of the code, tests, and dependencies.
- Status of milestones 0, 1, and 2.
- Differences from the Valdumbría specification.
- A proposed package layout and migration order.
- Risks, acceptance criteria, and the blocking questions for milestone 4.

Decisions accepted with milestone 4:

- Stay on one `:app` module. `domain` still imports neither Android nor Compose.
- Keep `GuardiansBridgeEngine` as the production bridge. Do not rewrite that scene as JSON in the same change.
- Add the adventure engine beside it, with its own state, and prove it on an artificial fixture.
- Use `kotlinx.serialization` to decode versioned adventure JSON. The bridge narrator payload stays on its current parser.
- Do not start Valdumbría content, FastAPI, expanded Room, or UI changes in the engine milestone.

Acceptance:

- The audit described the code that is actually in the repo.
- No product code was rewritten as part of the audit.
- The two blocking questions were answered before implementation: serialization is allowed, and the bridge engine stays in place.

## 4. Generic adventure engine — Completed

Depends on: milestone 3, plus the two decisions recorded there.

Objective: resolve a small artificial adventure from versioned data, including checks, time, evidence, retries, deductions, and endings. The bridge remains the path the app runs, and its numbers do not change.

Scope:

- Schema version 1 for an adventure document. Models cover the adventure, acts, locations, scene contracts, actions, requirements, checks, the closed effect list, outcomes, evidence, clues, events, the narrative clock, NPCs, and endings.
- A decoder and a validator for structure, unique ids, references, attributes, the difficulties 8, 12, 16, and 20, known effects, requirements, reachable endings, obtainable evidence, alternative routes for evidence a deduction requires, and a consistent midnight event.
- `AdventureEngine` and `AdventureState`, separate from `GameState`.
- Effects are data. Kotlin applies a closed list. The document contains no executable logic.
- Dice go through the existing `Die` port and `resolveCheck`.
- Narrative time starts at 22:00. The clock moves only when an action resolves. Crossing 00:00 fires one event once.
- Evidence, clues, retries, deductions, and terminal endings.
- One artificial fixture, loaded from the test classpath. It is not Valdumbría and it does not name that story.
- Rules and architecture docs updated to match the code.

Out of this milestone:

- Valdumbría content, FastAPI, Room changes, narrator-contract changes, and UI changes.
- Migrating The Guardian's Bridge to JSON.
- Wiring `AdventureEngine` into `GameViewModel`.

Acceptance:

- `./gradlew :app:testDebugUnitTest` passes, including the existing bridge tests.
- `./gradlew :app:assembleDebug` passes, because the serialization plugin changes the build.
- A rejected action returns the same adventure state. Health, inventory, location, clock, evidence, relationships, and ending stay as they were.
- Midnight in the fixture fires once, including when another resolved action happens later.
- Each fixture ending is reached through its declared conditions, and a later action is rejected.
- A difficulty outside 8, 12, 16, and 20 fails validation. An unknown effect type fails decoding.
- Life stays inside 0 through 10. Life 0 sets the adventure's death ending.
- `domain` still has no Android or Compose imports.
- The bridge totals 12, 11, 16, and 15, and the bridge damage 1 and 4, stay in place.

The first ending stops every pending effect of that resolution, including a nested event and the action that fired it. Life 0 sets death and stays at 0. That order is recorded in [GAME_RULES.md](GAME_RULES.md).

## 5. Persistence and recovery — Completed

Depends on: milestone 4. The saved shape has to follow `AdventureState` and the closed effect list.

This milestone left Room at version 2. Migration 1 to 2 creates the adventure tables and does not alter or delete the bridge tables. `AdventureGameStore` saved one confirmed `AdventureState` in a single transaction. The screen still loads the bridge save. Milestone 7 moves Room to version 3.

Objective: Room stores one active adventure and restores the last confirmed state. An interrupted action does not leave partial effects.

Scope:

- Room adapted to the adventure state, including character, life, attributes, inventory, location, time, midnight, evidence, clues, relationships, attempts, retry conditions, flags, NPC state, history, the narrative summary, the current scene, the ending, and a schema version.
- Narrative memory stored with the game.
- Migrations from the current bridge snapshot.
- Consistent autosave.
- Recovery returns to the last state confirmed before the interrupted action. A roll that was not confirmed may be rolled again. Effects of one action are not applied twice.
- The current bridge save continues to function until the app is actually switched over. This milestone does not replace the running screen.
- Tests for save, restore, restart, a corrupt snapshot, a failed write, interruption mid-action, and version compatibility.

Acceptance:

- A corrupt snapshot does not trap the player in an error loop.
- A failed write keeps the valid in-memory state.
- Restarting discards the previous game only when the player starts a new one.
- No accounts and no cloud sync.
- Process death in the middle of an action restores the pre-action state, not a partial effect list.

## 6. FastAPI backend — Completed

Depends on: milestone 4 for the action and state contract. Ordered after milestone 5. It does not depend on Room.

Objective: a stateless Python backend interprets a player sentence and, afterwards, narrates the result Kotlin already resolved.

Approved for this implementation: player text is at most 500 characters, action narration at most 1,200, generated scene text at most 2,000, and the narrative summary at most 3,000. The bridge narrator stays at 400 characters. The model receives the authorized view only. `forbiddenInformation` is an extra substring check, not a semantic guarantee. Android is not wired in this milestone. Development addresses for the emulator, a physical device, and the local process are in `backend/README.md`.

Scope:

- Interpretation API. Input is the authorized context, the scene, the player text, and the available actions. Output is a structured intent, a candidate action, allowed parameters, and an interpretation status.
- Narration API. It runs only after Kotlin has resolved the action. The text must match the real result.
- Scene generation only if a scene contract requires it. The model cannot add canonical evidence, change the culprit, revive Elia, change Inés's fate, add a principal location, change rules, invent an ending, move the clock, deal damage, or grant items.
- An Ollama adapter and one configurable external-provider adapter.
- Strict schemas, validation, one automatic retry when validation fails, timeouts, and errors.
- No API keys in the repo or the Android app. Configuration stays in the environment.
- The backend does not own the game.

Acceptance:

- Tests cover a valid interpretation, an ambiguous one, a malformed reply, an unauthorized action, forbidden information, the validation retry, timeout, fallback, a stale reply, and a scene change during a request.
- A model reply cannot instruct the engine to apply an effect.
- Invalid output is not presented as valid.

## 7. Android and AI integration — Completed

Depends on: milestones 4, 5, and 6.

Objective: the app calls the two-phase backend. Kotlin still validates and applies every consequence.

Approved with the design: one resolution is active per saved game, and every commit of an existing adventure advances `stateRevision` before another writer can replace narration or scene prose. A conflict reloads the confirmed record. The attempt that lost is not stored. A validated narration is stored before it is shown. Restoring that text does not roll again and does not request another narration. Cleartext HTTP is limited to the debug build. The release build rejects an `http` URL. The bridge screen stays on `GuardiansBridgeEngine`. Valdumbría content stays out of this milestone.

Real run, recorded after that integration. `FixtureWatchLiveLlmTest` passed once against a local FastAPI process on 10 November 2026. The run is `fixtureWatchAgainstARealModel`: 1 test, 0 failures, 7.28 seconds. `GET /health` returned `openai_compatible` and `openai/gpt-oss-20b`. The adventure was the artificial fixture, not Valdumbría. The test does not read or print an API key. Dice in the climb were a `ScriptedDie`, not the model.

- Interpretation. "I search the yard for anything useful." was `RECOGNIZED` as `search_yard` in 957 ms. "I search the yard and warn the warden." was `AMBIGUOUS` between `search_yard` and `warn_keeper` in 1821 ms. "I cast a fireball at the moon." was `UNRECOGNIZED` in 682 ms. "I open the hall door." was `UNRECOGNIZED` in 550 ms.
- Success. The search sentence resolved in 1171 ms. Health stayed 10. Inventory became `coin_purse` and `brass_key`. Narration was "You search the yard and discover a bell clue and a brass key." Interpretation took 431 ms and narration 688 ms. Delivery was `VALIDATED` on the stored phrase.
- Failure ending. "I give up and leave the yard." resolved in 1004 ms to ending `failure`, health 10, and inventory `coin_purse`. Narration was "You give up and walk away from the yard. The game records that you have given up and ends with the failure ending." Delivery was `VALIDATED`. Restoring that save returned the same narration and the same ending, and it made no further model call.
- Death. `reckless_climb` resolved in 599 ms. The stored check was roll 1, modifier 2, total 3, difficulty 20, outcome `FAILED`, effect `ChangeLife`. Health was 0 and the ending was `death`. Narration was "Your reckless climb failed. You took 10 damage, bringing your life to 0. You have died." The narration call took 583 ms and delivery was `VALIDATED`. Restoring that save returned the same narration, health 0, and ending `death`, and it made no further model call.

Scope:

- HTTP client for interpretation and narration.
- Strict local validation of both replies against the scene contract.
- Fallback when interpretation fails: no effects are applied, and the player can retry or use a safe local interpretation when one exists.
- Fallback when narration fails after a confirmed resolution: local prose if it exists, and no second roll.
- If a required scene cannot be generated, progression pauses until a valid retry. The app does not invent a canonical scene.
- A reply for an older action or a previous scene is dropped.
- Loading and errors stay off the main thread.
- Tests for the contract, rejection, fallback, stale replies, and coordination with `AdventureEngine`.

Acceptance:

- The model never rolls dice, never writes `AdventureState`, and never decides Inés's fate.
- A failed interpretation leaves the confirmed state unchanged.
- A failed narration does not repeat a resolved action.
- The Guardian's Bridge prototype still runs.

## 8. Valdumbría, first playable section — Completed

Depends on: milestone 7.

Objective: the player can start Las campanas de Valdumbría and play the opening.

Approved with the design, and with these limits. The file `app/src/main/assets/adventures/valdumbria.json` holds the whole adventure so `validateAdventure` can accept it. That is declared content. It is not a claim that the mystery has been played or proved.

Playable in this milestone: the plaza, Elia's letter, the meeting with Inés, the tavern, Mara, the opening clues, the opening checks, the clock, and save and restore. Valdumbría sets `narrativeLanguage` to `es`, so interpretation, action narration, and generated scenes ask for Spanish. The Guardian's Bridge narrator is unchanged. The plaza and tavern scene contracts list only those actions. The return to the square stays closed until Mara's rumor is known, so leaving the tavern early cannot hide those clues. The session refuses every other action before `AdventureEngine` runs. A provisional picker chooses between The Guardian's Bridge and Valdumbría. It is not the illustrated interface.

Tested in this milestone: that opening path, including a failed check that does not block the clues; the scene lines, which do not reveal Elia's death, and a model line that does is replaced by the letter; the midnight event, including a protection that crosses 00:00 and does not save Inés; and a Room save and restore of the letter that does not roll again. `state_the_conclusion` is not in the document. No action sets `truth_known`. `AdventureEngine.deduce` still requires the four evidence ids. A button cannot grant the deduction.

Declared and not playable here: the church, the cemetery, the archive, the crypt, the four evidence routes, the three ways to protect Inés, the five endings, and the rest of the cast beyond the opening scenes. Elia's death is authored on `read_elias_notes`, `mara_kept_the_packet`, and `evidence_elia`. It is not in a scene purpose or a revealable line, and Elia is not a present NPC. The rules line for Inés reads her state after the midnight event. It does not treat `ines_protected` as a rescue when that flag was set by the action that rang the bells.

Left for milestone 9, and not started: the revised design in [MILESTONE_9_DESIGN.md](MILESTONE_9_DESIGN.md). A free-text conclusion is a structured reading of four claims. Kotlin calls `AdventureEngine.deduce` only when those claims match the authored conclusion and the four evidence ids are already in state. The model cannot grant the deduction. Choosing a button must not grant it either. Milestone 9 also has to prove the five endings, the post-midnight evidence routes, the three rescues, and that false accusations do not make a mandatory proof unreachable. Milestones 10 and 11 stay unstarted.

Scope:

- New game, Elia's letter, and arrival in the village.
- The first locations, NPCs, and clues of that opening.
- The narrative clock, checks, and saving.
- Rules, evidence, and solutions come from the adventure document. The model does not invent them.
- No interactive map.

Acceptance:

- The opening is playable from a new character through the first authored clues.
- Time advances only on resolved actions.
- A save and restore round-trip keeps that opening state.
- The bridge prototype still passes its tests.

The opening report was accepted with this milestone. The accepted checks are the automated opening tests, an emulator pass, and one real Groq narration in Spanish. That acceptance covers the opening designed above. It does not cover the church, the cemetery, the archive, the crypt, the four evidence routes, the three rescues, the five endings, or the free-text deduction.

## 9. Complete adventure — Completed

Depends on: milestone 8.

The approved design is [MILESTONE_9_DESIGN.md](MILESTONE_9_DESIGN.md). This entry records the close accepted on 11 November 2026. It uses the runs already recorded that day. Closing the milestone did not repeat the vengeance, silence, or failure walks on the emulator.

Objective: the full authored mystery is playable through its three acts and five endings.

Player-facing narration must not contain technical identifiers: clue ids, flag names, action ids, or internal effect names. Those values belong to the engine. The prose stays in natural Spanish. The milestone 9 design specifies that identifier check. Tone, and canon the confirmed state has not revealed, remain milestone 11.

Scope:

- Three acts: arrival, investigation, and midnight with its consequences.
- Six principal locations: Plaza, Taberna, Iglesia, Cementerio, Archivo, and Cripta.
- Six principal characters: Elia, Padre Aldren, Mara, Don Beltrán, Tomás, and Inés. Elia is already dead when the game starts. Aldren is the culprit.
- Four mandatory evidence categories: the manipulated historical record, the crypt evidence, Elia's final investigation, and the incriminating evidence against Aldren.
- Midnight once. Inés's fate is fixed then. She can be saved by more than one authored strategy, or lost. Saving her is not required for JUSTICE. Elia cannot be saved.
- A correct deduction is explicit. It requires the four evidence items and a structured conclusion: Aldren's responsibility for the recent murders, the confinement of healthy people with the sick, the altered records, and the link between those murders and that older crime. The model may classify the sentence into authored claim values. Kotlin checks that classification and the evidence before it calls `AdventureEngine.deduce`. The model cannot write the state or grant the deduction.
- Endings JUSTICE, VENGEANCE, SILENCE, FAILURE, and DEATH. All five are terminal. There is an explicit moral choice for vengeance and for silence.
- Alternative routes so a failed check cannot make the four evidence items unreachable by accident.
- No experience, levels, tactical combat, or further adventures.

Acceptance:

- Deterministic simulations show each of the five endings is reachable.
- JUSTICE is impossible without the four evidence items and the correct deduction.
- Those four remain obtainable after midnight.
- More than one strategy saves Inés. None saves Elia.
- The same check cannot be retried forever without a relevant authored change. Stating the conclusion does not reopen a failed check.
- No accidental lock from a broken reference, an unreachable condition, or a pair of false accusations.
- A recorded Groq run tries one correct Spanish conclusion, one incomplete one, and one false one. An incorrect conclusion does not change the confirmed state.

Verified on the emulator `emulator-5554`, by tapping the installed debug app. These walks are not engine tests.

- Death, from a new Scholar. The taps were Leer la carta de Elia, Hablar con Inés, Ir a la taberna, Sentarse con los habituales, Seguir el camino detrás de la taberna, Volver a la plaza, Entrar en el cementerio, Seguir el suelo hundido, Entrar en la cripta, and Bajar por el hueco abierto. The die was the app's die. Agility 2 cannot beat difficulty 20. The accessibility dump immediately after the last tap showed the crypt, 23:40, health 0/10, and "Final: la caída acaba contigo", with no action buttons. The screenshot `death-10.png` was taken a moment earlier and still showed the crypt actions. Narration for those steps came from the local backend.
- Justice, from a cleared save and a new Scholar, on the route that does not roll. The walk warned Inés, went through the tavern and the cemetery, read the burial list, entered the crypt, followed the marks, read Elia's notes in the archive, and returned to the church. After taking the hidden list the screen showed the church at 01:40, health 10/10, Inés safe, and the four proofs in Spanish. The correct conclusion was pasted from the emulator clipboard, because `adb input text` does not type the accent in "encerró". The first send was `CONTEXT_REJECTED`: the stored narration contained "Elia está muerta". After the narration view omitted that forbidden phrase, the same save was accepted. Groq returned HTTP 200. The app showed the authored deduction line and the buttons Publicar la verdad, Tomar venganza, and Guardar el secreto. Tapping Publicar la verdad left the church at 01:50, health 10/10, "Final: la verdad queda publicada", and no action buttons. The app resolved the moves, the proofs, and the ending.
- Opening, after the Spanish labels. A fresh Scholar saw "Leer la carta de Elia", then the nine actions offered after the letter, in Spanish, with no technical identifier on a button. That pass did not replay an ending. The interface check reported PASS for that opening.

Verified by the engine and the session, with a `ScriptedDie` unless a line below names Groq. These tests do not tap the screen. Vengeance, silence, and failure were accepted from this group and were not repeated on the emulator.

- `ValdumbriaInvestigationTest.theProofPathSavesInesAndGrantsEveryProofAfterMidnight` grants the four proofs after one midnight, saves Inés, deduces `name_the_crime`, and reaches `justice`, `vengeance`, and `silence` from three copies of that state. The same test rejects publishing, vengeance, and silence without the deduction or without any one of the four proofs.
- `failureAndDeathAreTerminalAndEliaStaysDead` reaches `failure` by leaving after the letter, at health 10 and minute 5, and reaches `death` by failing the open drop. Both reject the next action and the next deduction. Elia stays dead.
- `eachRescueSavesInesOnARealClock` plays `warn_ines`, `bar_the_vestry`, and `cut_alden_off` from a new game. Each sets the flag before a later action crosses minute 120, and Inés becomes `saved`. The emulator justice walk used only the early warning. The vestry bar and the cemetery cut were not tapped on the emulator.
- `aRescueThatItselfCrossesMidnightDoesNotSaveInes` lets the rescue action be the one that crosses minute 120. The flag can become true and Inés becomes `lost`.
- `aFailedBarCannotFetchThePinAndReturnBeforeMidnight` fails the vestry bar, receives the lockpick from Tomás at minute 105, and still cannot return to the church before midnight.
- The accusation, retry, and repeatable-travel tests in the same class cover the routes that remain after a withdrawal, a retry that opens only when its own fact changes, and entering the church twice.
- `ValdumbriaConclusionLiveTest.threeSpanishConclusionsAgainstGroq` ran with `ADVENTURE_LIVE_LLM=1` against the local backend and `openai/gpt-oss-20b`. The save was minute 260, world stamp 34, four proofs, no deduction. The false sentence was classified `FALSE_ACCUSATION` (`recent_killings=beltran`) and left that save unchanged. The incomplete sentence was `PARTIAL` on the second attempt and also left the save unchanged. Its first attempt was `CONTRACT_VIOLATION` and was not used as a classification. The correct sentence was `ACCEPTED`, with the four authored values, `truth_known`, clock 260, stamp 34, and one deduction, `name_the_crime`. An earlier HTTP 400 was Groq's "Failed to validate JSON", mapped to `PROVIDER_ERROR`. That response was not a classification. Reviewing those provider failures, and the latency around them, is milestone 11.

Recorded commands, from the same day. The unit-test command left `ADVENTURE_LIVE_LLM` unset, so the live tests stayed skipped.

- `./gradlew :app:testDebugUnitTest --rerun-tasks` finished BUILD SUCCESSFUL: 185 tests, 0 failures, 0 errors, 3 skipped.
- `./gradlew :app:assembleDebug` finished BUILD SUCCESSFUL. That APK was the one installed for the Spanish opening and the justice ending.
- `backend/.venv/bin/pytest -q backend/tests` finished with 56 passed.

The code review of that follow-up found no defect. The domain boundary still holds. The action labels and the five ending titles stay in the screen. The buttons still send the internal id. A rejected conclusion shows "Esa conclusión no se sostiene."

Two limits were accepted with the close. The general interface chrome was still in English. That translation is milestone 10. A narrative summary that contains a forbidden phrase is dropped as a whole, so the model loses the rest of that summary. The player still sees the line. Reviewing that discard is milestone 11.

## 10. Visual experience — Completed

Depends on: milestone 9.

The approved design is [MILESTONE_10_DESIGN.md](MILESTONE_10_DESIGN.md). This entry records the close accepted on 11 November 2026, after the correction that keeps a restored reading position still. Closing the milestone did not change the engines, Room, or the backend.

Objective: present the adventure as an illustrated gamebook in portrait. The picture and the animation do not decide rules. The Kotlin engines stay the writers of state, and the existing saves stay in place.

The phone check used `dm_phone`. Its smallest width is 411dp, below 600dp, so the portrait lock applies. These checks were made on that phone:

- The picture, the narration, and the decisions share one scroll.
- Opening Inventario and Diario, then closing them, keeps the column where the player left it.
- Restoring a game does not move that column on its own.
- A visible roll uses the check already stored. The screen does not roll.
- An action with no check leaves the die hidden.
- The activity stays portrait on that phone.

Reduced motion is only partly verified. The stored numbers of a check were confirmed on that phone. The first frame, with the animator scale at zero and no settling animation, was not captured. This close does not call that behavior verified. Milestone 11 records the missing frame.

The six location pictures stay provisional resources. They ship in `app/src/main/res/drawable-nodpi/` under the names in the design. The screen does not call them final art. Replacing a file later keeps the same resource name.

The large display remains the earlier observation in [ARCHITECTURE.md](ARCHITECTURE.md): `spider_tablet`, smallest width 800dp, stayed in a centered portrait window after rotation.

Recorded commands, run after the reading-position correction. `ADVENTURE_LIVE_LLM` and `ADVENTURE_HTTP_IT` were unset, so the three live tests stayed skipped.

- `./gradlew :app:testDebugUnitTest` finished BUILD SUCCESSFUL in 3s. The report is 198 tests, 0 failures, 0 errors, 3 skipped.
- `./gradlew :app:assembleDebug` finished BUILD SUCCESSFUL. All 39 tasks were up to date with that same code.

## 11. Quality and stabilization — Completed

Depends on: milestone 10, which is completed.

The approved design is [MILESTONE_11_DESIGN.md](MILESTONE_11_DESIGN.md). This entry records the close accepted on 11 November 2026. Closing the milestone did not change the engines, Room, or the backend.

Objective: a playable build whose narration stays inside the confirmed state, and whose known limits are documented.

Accepted validation. `ADVENTURE_LIVE_LLM` was unset for the suite, so the tests that need a real model stayed skipped. No emulator was started and no screenshot was taken for this close.

- `./gradlew :app:testDebugUnitTest` finished BUILD SUCCESSFUL. The report is 220 tests, 0 failures, 0 errors, and 6 skipped. Those six depend on a real model.
- `backend/.venv/bin/pytest -q backend/tests` finished with 65 passed.
- `./gradlew :app:assembleDebug` finished BUILD SUCCESSFUL.
- Recovery from `INVALID_MODEL_OUTPUT` while discovering the archive is verified by `invalidNarrationOfTheDiscoveryKeepsThePlazaAndTheAuthoredLine`. The player stays in the plaza, the archive is discovered, the authored line is shown, and that line does not describe the interior. A second choice does not repeat the action or the effect. Restoring the save does not call the model again.
- Location and check continuity are verified by the continuity tests and by Groq observations. A passed Agility check was narrated as what the character perceives, without reciting the clock, the life, or the die. The confirmed move into the archive placed the character inside, at 22:30, in the Archivo.

These risks stay open. They are not claimed as solved.

- Generated narration can still be semantically inconsistent. A paraphrase can carry a secret, and an earlier model reply described arriving inside the archive while the confirmed place was still the plaza.
- The real narration of discovering the archive was not verified. Groq returned `INVALID_MODEL_OUTPUT`. That call was not repeated. The deterministic fallback above is what this close accepts for that step.
- Groq can return transient errors, including rate limits and invalid JSON. The player can continue from the saved result. Those limits remain.
- The first reduced-motion frame, with the animator scale at zero, was not captured. Milestone 10 left that frame partial, and this close does not call it verified.

## Still out of scope

These stay out of the plan until a later product decision adds them:

- Accounts, cloud sync, purchases, and advertising.
- Multiplayer, interactive maps, and live image generation.
- Experience, levels, skill trees, and tactical combat.
- A second adventure, or an open-ended world.
- Checkpoints and undo. Death is permanent inside the current game. A new game starts over.
