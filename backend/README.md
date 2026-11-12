# Dungeon Master AI backend

Stateless interpreter and narrator. Kotlin remains the authority for rules and saved state. This process does not store a game, roll dice, or apply effects.

Milestone 7 wires the Android client. This service only answers HTTP.

## Run locally

Python 3.12.

```bash
cd backend
python3.12 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

Copy `.env.example` to `.env` and set the provider there, or export the variables in the shell. `.env` is gitignored. A variable that is already set in the environment wins over the file.

Ollama on this machine:

```bash
export LLM_PROVIDER=ollama
export OLLAMA_MODEL=your-model
.venv/bin/dungeon-master-ai-backend
```

The process listens on `http://127.0.0.1:8000`. `GET /health` reports the provider and the model name. It does not call the model and it does not reveal a key. FastAPI telemetry is turned off, so this process does not export traces, metrics, or logs.

External provider:

```bash
export LLM_PROVIDER=openai_compatible
export LLM_BASE_URL=https://api.example.com/v1
export LLM_MODEL=your-model
export LLM_API_KEY=your-key
```

`LLM_BASE_URL` includes the version prefix. The adapter posts to `{LLM_BASE_URL}/chat/completions`. A provider URL cannot include a user, a password, a query, or a fragment. Put the key in `LLM_API_KEY`.

## Routes

- `POST /v1/interpret` reads the authorized view and the player sentence. It returns a status and, when the status is `RECOGNIZED`, one candidate action. When the status is `CONCLUSION`, it returns one option id for each offered claim. It does not decide that the conclusion is true.
- `POST /v1/narrate` describes a resolution the caller already confirmed. The outcome in the response is copied from the request.
- `POST /v1/scenes` writes scene prose when `generationRequired` is true. Otherwise it returns `GENERATION_NOT_REQUIRED` and does not call the model.

Every model reply is parsed as one JSON object, validated, and checked against the contract. A schema or contract failure is retried once. Groq's HTTP 400 `Failed to validate JSON` is that kind of failure: the transport reads the body only to choose the path, and the body is not attached to the exception, the correction, or the response. The correction says the response is not a JSON object. A second failure of the same kind returns `INVALID_MODEL_OUTPUT` on attempt 2. A timeout or any other provider error is not retried. There is no third attempt. Invalid output is returned as an error envelope, not as narration or as an intent.

## Limits

These limits belong to this service. The bridge narrator in the Android app stays at 400 characters.

| Text | Maximum |
| --- | --- |
| Player sentence | 500 |
| Action narration | 1,200 |
| Generated scene | 2,000 |
| Narrative summary | 3,000 |
| Recent lines | 8, using the player or narration limit of that line |
| Other auxiliary values | 1,200 |
| `narrativeSummary` auxiliary value | 3,000 |

A forbidden string must be from 8 through 500 characters. The check is a case-insensitive substring. It is an extra filter. It does not understand the story.

## What the model is allowed to see

Interpretation is built from the fields the caller listed, including action ids, because it has to return one. It does not receive unrevealed evidence text or the solution statement. Narration and scene prompts are a prose projection: location name, character names, purpose, revealable lines, `revealedLines`, the redacted summary, and recent lines. Narration also projects `hora` as the confirmed `HH:MM`, `hecho` as the outcome's confirmed prose, the outcome as `completed`, `not completed`, or `rejected`, a stored check as dado, Fuerza or Agilidad or Inteligencia, modificador, total, dificultad, and whether it succeeded, the life after the resolution, and the Spanish ending title when the ending id is one of the five known endings. Those current facts come before the summary. The location is the place the resolution left the player in. The narration instruction keeps the clock, the life, and the check as context. It asks for what the character does or experiences, and it does not ask the model to recite the hour, the life total, the die, or the difficulty. Finding a path is not the same as moving into that place. The narration user message repeats that reminder after the JSON. The projection does not include action ids, effect names, npc state, or ending ids. The request body still carries those ids for validation.

The forbidden list is not a field of that prompt. The caller sends the phrases that are still active. A summary is redacted by sentence before it is shown to the model, and again on the way back before the forbidden check. One unsafe summary sentence does not discard a clean narration. If a forbidden string remains in a field that would be sent, the service returns `CONTEXT_REJECTED` and does not call the model.

Applied effects carry identifiers, not the prose of a clue or a piece of evidence. To let the model mention a newly granted clue, the caller puts that public line in `revealedLines`. `revealableInformation` stays the scene contract's static lines.

A player sentence that itself contains a forbidden string is rejected. The substring check can also trip on an ordinary phrase. Those are limits of the filter, not a claim that the prose was understood.

## How Android will reach this process

The app does not send the bridge screen to these routes. `AdventureSession` is the caller. It reads `adventure.backendUrl` from the Android `local.properties`. Leave that value empty to keep the client absent. Do not put a key in it.

`narrator.baseUrl` in `local.properties` is the bridge narrator. It accepts only `narration` and `intent`. Do not point it at these routes.

- **Same machine.** `http://127.0.0.1:8000`. Leave `HOST` at `127.0.0.1`.
- **Emulator.** The emulator's own loopback is not the computer. The host loopback is `10.0.2.2`, so the client uses `http://10.0.2.2:8000`. The backend can stay bound to `127.0.0.1`.
- **Phone over USB.** Run `adb reverse tcp:8000 tcp:8000`. The phone then uses `http://127.0.0.1:8000`, and the backend stays on loopback.
- **Phone on Wi-Fi only.** Start the backend with `HOST=0.0.0.0` and call `http://<lan-address-of-the-computer>:8000`. That opens an unauthenticated port on the local network. Use a trusted network and stop the process afterwards.

The provider key stays in the computer's environment. The app does not receive it.

A debug build may call these `http` addresses. A release build accepts `https` only.

## Tests

```bash
cd backend
.venv/bin/pytest
```

The suite uses a fake provider and recorded HTTP calls. It does not need Ollama, a network connection, or an emulator.

## Known limits

- The service can reject a forbidden substring, a sentence that follows a dropped secret, and a listed bare token. It cannot prove that a paraphrase understood the story.
- Two narration calls for one confirmed action can return different prose. They cannot repeat the roll or the effects, because the response has nowhere to put them.
- Listening on `0.0.0.0` exposes the interpreter without an application key.
- A live Ollama model is not part of the automated check.
