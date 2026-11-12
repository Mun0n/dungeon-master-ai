# Dungeon Master AI

Native Android gamebook. The player acts. A Kotlin rules engine decides what those actions do to the character. A future AI may narrate and interpret free text. It must never be the source of truth for game state.

## Where knowledge lives

- Product scope: [docs/PRODUCT.md](docs/PRODUCT.md)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Rules and scenario numbers: [docs/GAME_RULES.md](docs/GAME_RULES.md)
- Milestones: [docs/ROADMAP.md](docs/ROADMAP.md)

Do not copy those documents into prompts, rules, or skills. Link to them.

## Boundaries

- `domain` owns characters, dice, scenes, intents, and state transitions. It does not import `android.*`, `androidx.*`, or Compose.
- `presentation` draws the UI and forwards intents. It does not roll dice or change health, inventory, or outcome.
- `data` does not exist yet. Persistence and AI clients land there later.
- Suggested choices and parsed free text both become intents. Only `GuardiansBridgeEngine` applies them.

## Workflow

The main agent plans, implements, and coordinates. It does not close its own non-trivial change.

How deep a check has to be is in [docs/DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md). Use the smallest level that covers the change.

Project subagents live in `.cursor/agents/`. `android-qa` verifies behavior. `code-reviewer` reviews the diff and does not edit. `debugger` repairs a confirmed defect. Their prompts do not repeat this policy.

For every approved non-trivial task:

1. Plan a small scope, the files, and the acceptance checks. Review that plan in a separate context when the change is non-trivial.
2. Implement only the approved scope.
3. Delegate verification to `android-qa` when the change affects UI, gameplay presentation, or navigation. Otherwise run the commands below and quote the result.
4. Delegate review of the actual diff to `code-reviewer`.
5. When QA, tests, or review find a defect, delegate the repair to `debugger`, or fix it in the main agent when that is the smaller path.
6. Rerun the affected checks. Repeat the repair at most three times.
7. Mark the task complete only when its required quality gates pass. If a required check cannot be run, report NOT VERIFIED and do not claim completion.
8. Report what changed, what was executed, the remaining risks, and the next task.

After the quality gate passes, continue with the next task in the approved milestone. Do not start the next roadmap milestone unless it was requested.

Do not spawn these agents for a rename or a one-line fix. Parallelize only work that does not share files. Do not treat a green summary as evidence. Quote the command and its result.

## Autonomy

Do without asking: choose an implementation inside the approved design, split an approved milestone, write the approved code, run tests and builds, fix defects, and update the doc that the change invalidates.

Ask before: an architecture change, a new gameplay rule or feature, a new dependency or external service, a destructive git command, or anything involving credentials, billing, or a paid API.

Do not start the next roadmap milestone unless it was requested.

## Conventions

- Simple Kotlin. Immutable models. State changes through `copy` inside the engine.
- One class, one job. No new abstraction until a second caller needs it.
- English identifiers, UI copy, and docs.
- A rule change updates `docs/GAME_RULES.md` and a unit test in the same change.
- No API keys in the repo or the app.

## Commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

JDK 17. The Android SDK platform is `android-37.0`, so `compileSdk` uses API 37 minor 0. `local.properties` points at the SDK and is gitignored.

## Task template

- Objective and scope.
- Files.
- Acceptance criteria.
- Tests to run.
- Done when the checks have been executed and the diff has been reviewed.

## Definition of done

- The change stays inside the approved scope.
- `./gradlew :app:testDebugUnitTest` passed, and `./gradlew :app:assembleDebug` passed when UI or Gradle files changed.
- `domain` still has no Android or Compose imports.
- Rules or architecture docs match the code.
- `code-reviewer` looked at the diff, and confirmed findings were fixed and rechecked.
- `android-qa` reported PASS for the checks it could run when the change required it. Checks left NOT VERIFIED are listed and block a claim of full completion.

## Review checklist

- Rules live in `domain`, and rejected intents do not change health, inventory, scene, or outcome.
- Dice are injected. Tests do not call `Random`.
- No new dependency, gameplay number, or silent clamp beyond the documented rules.
- No secrets, no unused layer, no roadmap work mixed in.

## Testing

JVM unit tests under `app/src/test` cover dice boundaries, character validation, the bridge transitions, and the keyword parser. Pass a `ScriptedDie`. Do not add instrumented tests until the UI has behavior those tests would lock.
