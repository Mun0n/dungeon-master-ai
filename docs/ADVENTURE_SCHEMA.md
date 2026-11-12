# Adventure schema

Schema version 1. The decoder is Kotlinx Serialization with unknown fields rejected. `type` is the effect discriminator. After decoding, `validateAdventure` checks ids, references, and the rules in [GAME_RULES.md](GAME_RULES.md).

The test fixture is `app/src/test/resources/adventures/fixture_watch.json`. It is not a story. The app does not load it.

## Document

`schemaVersion`, `id`, `title`, optional `narrativeLanguage`, `clock`, `startLocationId`, `deathEndingId`, then `acts`, `locations`, `npcs`, `items`, `flags`, `evidences`, `clues`, `actions`, `events`, `endings`, `deductions`, `sceneContracts`, and optional `prologue`.

`narrativeLanguage` is omitted or `"es"`. Any other value fails validation. Omitted, the client sends no language preference. `"es"` tells the narration service to read the player sentence as Spanish and to write action narration and generated scenes in Spanish. The reply is not rejected for its language. Proper names and technical ids stay as they are.

Ids match `^[a-z][a-z0-9_]*$` and are unique across the document. NPC state names are local to that NPC.

`clock.startHour` is 22 and `clock.startMinute` is 0. `clock.midnightEventId` points at an event. Only the engine fires that event.

A flag may set `"default": true`. Omitted, the default is false.

## Requirement

Every listed constraint must hold. An omitted field is empty, and an empty requirement always holds.

`flagIds`, `absentFlagIds`, `itemIds`, `evidenceIds`, `clueIds`, `absentClueIds`, `discoveredLocationIds`, `deductionIds`, `relationships` (`npcId`, `atLeast`), `npcStates` (`npcId`, `state`), `atLeastMinutes`, `beforeMidnight`, `afterMidnight`.

`absentClueIds` holds only when those clues are not in state. Empty means no such constraint. A clue cannot be required present and absent. `beforeMidnight: true` and `afterMidnight: true` together are rejected. The value `false` does not add a constraint.

## Action

`id`, `locationId`, `timeCostMinutes` (0 or more), `availability`, optional `check` (`attribute`, `difficulty`), `success`, optional `failure`, optional `retry`, optional `repeatable`.

A check requires a failure outcome. No check forbids one. The attribute is `STRENGTH`, `AGILITY`, or `INTELLIGENCE`. The difficulty is 8, 12, 16, or 20.

`repeatable` defaults to false. True is allowed only when the action has no check, no failure, no retry, and no ending, and its success is exactly one `MOVE_TO`. A repeatable success does not close the action.

Each outcome may include `localProse`. Empty is allowed. Longer than 1,200 characters fails validation. Resolution does not read it. The adventure client shows it when a confirmed result has no usable narration from the model.

`retry.reopenWhen` is a requirement. A failure stores the facts that requirement names. The check opens again only when the requirement holds and that projection has changed. An empty stored projection stays closed. The engine records the current attempt. An effect must not record that same action.

## Effect

Each object has `type` and the fields for that type.

| type | fields |
| --- | --- |
| `SET_FLAG` | `key`, `value` |
| `CHANGE_LIFE` | `amount` (not 0; the stored life is then kept inside 0..10) |
| `ADD_ITEM` | `itemId` |
| `REMOVE_ITEM` | `itemId` |
| `DISCOVER_LOCATION` | `locationId` |
| `MOVE_TO` | `locationId`. The engine also marks that location discovered. |
| `SET_RELATIONSHIP` | `npcId`, `value` |
| `ADD_EVIDENCE` | `evidenceId` |
| `ADD_CLUE` | `clueId` |
| `SET_NPC_STATE` | `npcId`, `state` |
| `RECORD_ATTEMPT` | `actionId` |
| `TRIGGER_EVENT` | `eventId` |
| `SET_ENDING` | `endingId` |

`CHANGE_LIFE`, the clock, and `RECORD_ATTEMPT` do not move `worldStamp`. The other state changes do, when the value actually changes. A deduction does not move it. Retries do not read it. `TRIGGER_EVENT` cannot name the midnight event, and an event cannot contain `TRIGGER_EVENT`.

The first ending stops every effect still pending in that resolution. That includes the rest of a nested event and the rest of the action that triggered it. A life change that reaches 0 sets death and stops in the same way. Effects after that point are not applied and do not count as routes. A later `SET_ENDING` does not replace the first ending. The full order is in [GAME_RULES.md](GAME_RULES.md).

A `SET_ENDING` is valid only when that action establishes the ending's requirements by the time the effect runs. The death ending is the exception: it has no requirements, and life 0 sets it.

## Event

`id`, `effects`, optional `whenFlag`, optional `whenFlagEffects`.

If `whenFlag` is already true when the event fires, the engine applies `whenFlagEffects`. Otherwise it applies `effects`. The event is recorded once either way. There is no second condition.

## Deduction and ending

A deduction lists `evidenceIds` and the `flag` it sets. The engine accepts it only when those evidence ids are already in state. It does not read `statement`, `localProse`, `claims`, or `contradictions`.

Those four fields are optional. Empty claims leave the deduction as a direct engine call, which is how the fixture stays testable. A claim that is present needs a prompt, at least two options, and a non-empty `accept` list whose values are options of that claim. Option ids are local to the claim, so the same `unknown` may appear on more than one claim. `falseValues` are options and are not accepted values. `contradictions` name two claim values that cannot stand together. `statement` and `localProse`, when present, are at most 1,200 characters and must not contain an underscore id or an effect type name. The client offers the deduction for free text only when both strings are present, every claim is complete, the evidence is held, the deduction is not already recorded, and the game is not terminal. The model sees the prompts and the option glosses. It does not see `accept`, `falseValues`, `contradictions`, or `statement`.

An ending lists a requirement. Every ending except death must be the target of a `SET_ENDING` that establishes that requirement. Evidence listed by a deduction needs two granting actions or events. Every other evidence and every clue needs one. A clue granted by the last prologue passage counts as granted.

## Scene contract

`id`, `locationId`, `purpose`, `allowedActionIds`, `presentNpcIds`, `revealableInformation`, `forbiddenInformation`, `generationRequired`, `freeAction`.

`purpose` is 1 to 500 characters. Each revealable line is 1 to 500. Each forbidden line is 8 to 500. `generationRequired` defaults to false. `freeAction` defaults to false. It tells the adventure client that this scene accepts a written action. The client opens the field from a secondary choice. An offered conclusion opens that field even when `freeAction` is false. The engine does not read it. The buttons are a featured subset of the actions the contract already allows. They are not a second list in the document. A location has at most one scene contract. The contract is inert during resolution. The adventure client uses `allowedActionIds` to refuse an action before resolution. A confirmed action is narrated with the contract from that resolution, including after a move or an ending. Narration also receives the confirmed `lifeAfter` and, when the game has ended, `endingId`. Those fields are read-only. When `generationRequired` is true, the adventure client waits for valid scene prose before it accepts the next action. An empty optional `narrativeSummary` is omitted and does not reject the narration. That wait does not apply while a prologue passage is still unread.

## Prologue

`prologue` is omitted or a list. Each passage has `id`, `locationId`, `prose`, and optional `effects`.

The passage id is a flag id. It is not a second id. That flag defaults to false. `locationId` names a location, and the client uses it only to choose the picture. `prose` is 1 to 1,200 characters and must not contain an identifier. Passages before the last one have no effects. The last passage may list `SET_FLAG`, `ADD_CLUE`, `DISCOVER_LOCATION`, and `MOVE_TO`. Any other effect type is rejected. The engine marks the passage flag when the player continues. It does not roll and it does not add minutes.
