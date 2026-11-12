package com.dungeonmasterai.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dungeonmasterai.data.Narrator
import com.dungeonmasterai.data.NarratorRequest
import com.dungeonmasterai.data.SavedGameStore
import com.dungeonmasterai.data.parseNarratorPayload
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.PlayerIntent
import com.dungeonmasterai.domain.scenario.GuardiansBridge
import com.dungeonmasterai.domain.scenario.GuardiansBridgeEngine
import com.dungeonmasterai.domain.scenario.KeywordIntentParser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameUiState(
    val game: GameState? = null,
    val ready: Boolean = false,
)

class GameViewModel(
    private val engine: GuardiansBridgeEngine,
    private val store: SavedGameStore,
    private val narrator: Narrator? = null,
    private val parser: KeywordIntentParser = KeywordIntentParser(),
) : ViewModel() {
    constructor(die: Die, store: SavedGameStore, narrator: Narrator? = null) : this(
        GuardiansBridgeEngine(die),
        store,
        narrator,
    )

    private val snapshots = Channel<GameState>(Channel.CONFLATED)
    private val _uiState = MutableStateFlow(GameUiState())
    private var narratorJob: Job? = null
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            for (state in snapshots) {
                try {
                    store.save(state)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Could not save the game.", error)
                }
            }
        }
        viewModelScope.launch {
            val saved = store.load()
            if (_uiState.value.game == null) {
                _uiState.value = GameUiState(game = saved, ready = true)
            }
        }
    }

    fun start(name: String, characterClass: CharacterClass) {
        narratorJob?.cancel()
        val current = _uiState.value
        if (!current.ready || current.game != null || name.isBlank()) {
            return
        }
        val opening = GuardiansBridge.opening(name, characterClass)
        _uiState.value = current.copy(game = opening)
        snapshots.trySend(opening)
    }

    fun choose(intent: PlayerIntent) {
        narratorJob?.cancel()
        apply(intent)
    }

    fun submitText(text: String) {
        val current = _uiState.value
        val game = current.game ?: return
        if (!current.ready) {
            return
        }
        val client = narrator
        narratorJob?.cancel()
        if (client == null) {
            apply(parser.parse(text))
            return
        }
        narratorJob = viewModelScope.launch {
            val proposal = try {
                parseNarratorPayload(
                    client.complete(
                        NarratorRequest(
                            sceneId = game.sceneId,
                            actionText = text,
                            characterName = game.character.name,
                            outcomeName = game.outcome.name,
                        ),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Narrator reply was unusable.", error)
                null
            }
            if (coroutineContext[Job]?.isActive == false || _uiState.value.game !== game) {
                return@launch
            }
            if (proposal == null) {
                apply(parser.parse(text))
            } else {
                apply(proposal.intent, proposal.narration)
            }
        }
    }

    private fun apply(intent: PlayerIntent, narration: String? = null) {
        val current = _uiState.value
        val game = current.game ?: return
        if (!current.ready) {
            return
        }
        val next = engine.resolve(game, intent, narration).state
        _uiState.value = current.copy(game = next)
        snapshots.trySend(next)
    }

    private companion object {
        const val TAG: String = "GameViewModel"
    }
}
