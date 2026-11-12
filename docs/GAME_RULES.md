# Game rules

These numbers are the rules. Changing them requires a change here and a unit test.

## Character

- Maximum health is 10. A new character starts at 10. Legal health is 0 through 10.
- Attributes are Strength, Agility, and Intelligence. Each is an integer from 1 to 5.
- The class sets the attributes. They do not change in this milestone.
- Presentation is masculine or feminine. It chooses the Spanish class title. It does not change attributes, checks, health, or inventory. A save without a presentation restores as masculine.
  - Warrior: 4 / 3 / 2
  - Scout: 2 / 4 / 3
  - Scholar: 2 / 2 / 5
- Inventory holds at most 6 items. Item ids are unique.
- Every new character carries one `coin_purse` named Coin Purse.

Agility is unused in The Guardian's Bridge. It is still a real attribute.

## Dice

- One d20, inclusive range 1 through 20.
- A skill check is the roll plus the relevant attribute.
- Difficulties are 8, 12, 16, and 20.
- The check succeeds when the total is greater than or equal to the difficulty.
- There is no critical hit and no automatic failure.

The engine rolls the die. Callers pass a `Die`. They do not supply the success flag.

## The Guardian's Bridge

Scene id: `guardians_bridge`. Outcomes: `ONGOING`, `CROSSED`, `DEFEATED`.

- Persuade uses Intelligence against 12. Success crosses the bridge and deals no damage. Failure leaves the character on the bridge.
- Pay does not roll. It requires `coin_purse`, removes it, and crosses. Without the item, the action is rejected.
- Attack uses Strength against 16. Success deals 1 damage and crosses. Failure deals 4 damage and stays. Health never goes below 0. If health is 0 after the damage, the outcome is `DEFEATED`, including when the successful blow spends the last point.
- `CROSSED` and `DEFEATED` reject every later intent. No die is rolled.

A rejected intent does not change health, inventory, scene, or outcome. It appends one `ActionRejected` event.

## Free text

`KeywordIntentParser` is a temporary interpreter, not a rule of the world. It lowercases the text, splits on everything that is not a Unicode letter, and matches whole tokens. `payé` is not `pay`.

- Persuade: `persuade`, `convince`, `talk`, `reason`
- Pay: `pay`, `bribe`, `offer`
- Attack: `attack`, `fight`, `strike`, `charge`

Several tokens from the same group are that one intent. Tokens from more than one group are ambiguous. No matching token is unrecognized. `attacker` and `talking` do not match.

A narrator may propose one narration line and one of those intents, including unrecognized and ambiguous. The line is at most 400 characters after trimming. The engine still applies the numbers in this scene. The proposal is used only when its payload is valid. If the narrator is absent or the reply is unusable, `KeywordIntentParser` interprets the text and the scene keeps its fixed lines.

## Not rules

No experience, levels, critical hits, fumbles, or generic combat. Damage numbers above belong to this scene only.

## Generic adventure engine

These rules belong to `AdventureEngine`. They do not change The Guardian's Bridge. An adventure document may name amounts and difficulties. It may not name a new rule or carry executable code.

- The document schema is version 1. Decoding rejects unknown fields and unknown effect types. `validateAdventure` rejects a broken reference, an attribute outside Strength, Agility, and Intelligence, and a difficulty outside 8, 12, 16, and 20.
- Effects are a closed list: `SET_FLAG`, `CHANGE_LIFE`, `ADD_ITEM`, `REMOVE_ITEM`, `DISCOVER_LOCATION`, `MOVE_TO`, `SET_RELATIONSHIP`, `ADD_EVIDENCE`, `ADD_CLUE`, `SET_NPC_STATE`, `RECORD_ATTEMPT`, `TRIGGER_EVENT`, and `SET_ENDING`. Kotlin applies them in order. `MOVE_TO` also marks that location discovered. A story event may name one flag. When that flag is already true, the engine applies the event's other effect list instead of the first. That choice is the engine's. The lists do not branch again.
- The clock starts at 22:00. State stores minutes from that instant. Midnight is minute 120, which is 00:00. Time is added only when an action resolves. A rejection does not move it. The engine then applies the midnight event if this action crossed minute 120 and it has not fired before. The action's effects run only when that event did not set an ending. A flag set by the action does not change which list the event uses. A later resolved action does not fire the event again. An action cannot trigger it.
- A rejection returns the same state. Health, inventory, location, clock, flags, evidence, clues, relationships, attempts, hypotheses, and ending stay as they were. A failed check is not a rejection. The action resolved, so its time cost and its failure effects apply.
- Health stays inside 0 through 10. The death ending has no extra requirements. The first ending stops the resolution, as the next section says.

### Terminal state

The first ending reached is final. It stops every effect still pending in that resolution. The stop covers the rest of the list that set the ending, the rest of a nested event, and the rest of the action that triggered the event. When midnight sets the ending, the action's own effects do not run.

Effects that ran before the ending stay applied. The time cost, the attempt, and the history of that resolution are committed with them. Nothing after the ending is applied. A pending effect cannot restore life, add or remove an item, record evidence or a clue, change a flag, a relationship, a place, or any other part of the state, or set a second ending.

When a life change leaves health at 0 and no ending is set yet, the engine sets the death ending. Death does not coexist with health above 0. A `SET_ENDING` that names the death ending is illegal. Death is reached only through life at 0. A saved adventure restores that death ending only when health is 0, and health 0 only with that ending. Any other combination is a corrupt snapshot and is dropped. A later `SET_ENDING` does not replace the first ending, because that later effect does not run. The next action and the next deduction are rejected.

An effect the engine cannot apply legally, such as a full inventory or a `SET_ENDING` whose requirements do not hold, discards the whole resolution. The state from before the action is kept. Stopping at an ending is a committed resolution. It is a different result from a rejection.

The Guardian's Bridge does not use this sequence. Its attack still applies its own damage, sets `DEFEATED` at health 0, and keeps that outcome's narration.

- Inventory still holds at most 6 items, and item ids stay unique. If an add would break either rule, the whole action is rejected and nothing from it is kept.
- A success completes the action, unless the action is repeatable. A repeatable action has no check, no failure outcome, no retry, and no ending. Its success is one `MOVE_TO`. That success does not close the action. Each resolution still spends its time cost, still requires availability, and still applies that move. Omitted, `repeatable` is false.
- A failure stays closed until that action's own retry requirement holds and a fact named by that requirement has changed since the failure. The attempt stores a projection of those flags, items, evidence, clues, relationships, NPC states, discoveries, and deductions. Health, time, and `RECORD_ATTEMPT` do not reopen it. An unrelated change does not reopen it. A failure loaded without that projection stays closed. `RECORD_ATTEMPT` closes another action permanently. The engine records the current attempt itself. `worldStamp` still advances when a world change is applied, and saved games still store it. Retries do not read it.
- A deduction is accepted only when every evidence id it lists is already in state. It spends no time and rolls no die. It does not interpret a sentence, and it does not advance `worldStamp`. The adventure client may ask a model to fill authored claim values. Kotlin compares those values with the document before it calls the deduction. A false, partial, contradictory, or malformed reply does not call it.
- A hypothesis stores one trimmed sentence of at most 500 characters. It spends no time and rolls no die. It does not change health, inventory, location, clock, flags, evidence, clues, relationships, attempts, deductions, `worldStamp`, history, or ending, and it does not mark the sentence true or false. A blank sentence, a longer sentence, a duplicate, and a note after an ending or at health 0 are rejected and return the same state. The journal shows accepted deductions as demonstrated conclusions. A hypothesis does not become one of those conclusions.
- `SET_ENDING` applies only when that ending's requirements already hold, including effects earlier in the same list. Every ending, including death, rejects every later action and deduction. No die is rolled.
- Evidence required by a deduction must be granted by at least two different actions or events. The validator checks that structure. It does not play every possible path.
- Scene contracts are data for the narrator. Resolution ignores them, including `localProse` and `generationRequired`. The adventure client refuses an action the current contract does not list before it asks the engine to resolve it. An accepted resolution reports the effects that ran. Effects after the first ending are not in that list.
- A prologue is an optional list of authored passages. Each passage id is a flag that defaults to false. Advancing marks that flag. It does not roll, spend minutes, or change life or inventory. Passages before the last one have no effects. The last passage may apply only `SET_FLAG`, `ADD_CLUE`, `DISCOVER_LOCATION`, and `MOVE_TO`. It cannot add evidence, an item, or an ending. The client shows the next unread passage on a new game, and on a save that has already passed an earlier passage. A save that already has history does not enter the prologue. The shown text is the passage itself. The model is not called to write it. While a passage is unread, the client does not accept another action, and it does not wait for generated scene prose. When the last passage has been read, that text leaves the scene and the choices for the place take its place. The passage stays among the recent lines.
- The narrator may say that Elia is dead when `clue_elia_dead` or `evidence_elia` is already held. The phrase that names Padre Aldren as the one who killed her stays forbidden until `evidence_elia` is held. "Elia sigue viva" stays forbidden.

The numbers inside a fixture or a future adventure are content. They are not a second combat system.
