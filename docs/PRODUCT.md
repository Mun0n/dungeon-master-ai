# Product

Dungeon Master AI is a native Android gamebook. The player creates a fantasy character and faces a scene told in short prose. They pick a suggested action or write their own. Dice and character stats decide the result. The narrator, later an AI, explains it.

## MVP

- One character: health, three attributes from a class, and a small inventory.
- One scene, The Guardian's Bridge, with fixed prose.
- Two or three suggested actions. A written action stays available when that scene allows it, and the field opens from a secondary choice.
- A d20 skill check when the action calls for one.
- The result updates health, inventory, and whether the scene is still open.

## Not in the MVP

Experience, levels, multiplayer, a combat system, maps, accounts, and AI-generated story. Free text is matched against a fixed word list until an AI interpreter exists. That list is not a new rule. It is a temporary parser, documented in [GAME_RULES.md](GAME_RULES.md).
