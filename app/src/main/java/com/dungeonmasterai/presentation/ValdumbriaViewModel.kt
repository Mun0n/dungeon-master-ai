package com.dungeonmasterai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureGameStore
import com.dungeonmasterai.domain.adventure.engine.ActionAccess
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.decisionOffer
import com.dungeonmasterai.domain.adventure.engine.pendingPrologueBeat
import com.dungeonmasterai.domain.adventure.engine.offersConclusion
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import android.os.SystemClock
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.clockFace
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Presentation
import com.dungeonmasterai.domain.dice.CheckResult
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.RandomDie
import com.dungeonmasterai.presentation.dice.DiceOverlay
import com.dungeonmasterai.presentation.dice.DiceOverlayModel
import com.dungeonmasterai.presentation.dice.PendingThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ValdumbriaUiState(
    val ready: Boolean = false,
    val phase: AdventurePhase = AdventurePhase.LOADING,
    val locationId: String = "",
    val locationName: String = "",
    val actTitle: String = "",
    val adventureTitle: String = "",
    val clock: String = "22:00",
    val clockHour: Int = 22,
    val clockMinute: Int = 0,
    val health: Int? = null,
    val characterName: String = "",
    val characterClass: CharacterClass? = null,
    val feminine: Boolean = false,
    val strength: Int = 0,
    val agility: Int = 0,
    val intelligence: Int = 0,
    val items: List<CarriedItem> = emptyList(),
    val narration: String? = null,
    val revealNarration: Boolean = false,
    val notice: String? = null,
    val inesLine: String? = null,
    val inesStrong: Boolean = false,
    val lifeChange: String? = null,
    val announceMidnight: Boolean = false,
    val midnightKnown: Boolean = false,
    val check: CheckReading? = null,
    val animateCheck: Boolean = false,
    val clues: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val hypotheses: List<String> = emptyList(),
    val conclusions: List<String> = emptyList(),
    val places: List<DiscoveredPlace> = emptyList(),
    val recentLines: List<String> = emptyList(),
    val actionIds: List<String> = emptyList(),
    val freeAction: Boolean = false,
    val prologue: Boolean = false,
    val endingId: String? = null,
    val alignToken: Int = 0,
    val historySize: Int = 0,
    val resolving: Boolean = false,
    val dice: DiceOverlay = DiceOverlay.Closed,
)

class ValdumbriaViewModel(
    private val session: AdventureSession,
    private val engine: AdventureEngine,
) : ViewModel() {
    private val gate = Mutex()
    private val dice = DiceOverlayModel()
    private var heldRoll: HeldRoll? = null
    private val _uiState = MutableStateFlow(ValdumbriaUiState())
    val uiState: StateFlow<ValdumbriaUiState> = _uiState.asStateFlow()

    init {
        turn(playerActed = false) { session.restore() }
    }

    fun start(name: String, characterClass: CharacterClass, feminine: Boolean) {
        val character = try {
            Character.create(
                name,
                characterClass,
                presentation = if (feminine) Presentation.FEMININE else Presentation.MASCULINE,
            )
        } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(notice = PlayerCopy.CHARACTER_CANNOT_START)
            return
        }
        turn(playerActed = true) { session.start(character) }
    }

    fun reload() {
        if (_uiState.value.dice !is DiceOverlay.Closed) {
            viewModelScope.launch {
                gate.withLock {
                    val snapshot = session.restore()
                    if (!dice.keepAfterRestore(snapshot.record?.revision)) {
                        heldRoll = null
                        _uiState.value = present(_uiState.value, snapshot, playerActed = false)
                    }
                }
            }
            return
        }
        turn(playerActed = false) { session.restore() }
    }

    fun choose(actionId: String) {
        if (rollBusy()) return
        _uiState.value = beginResolution(_uiState.value)
        viewModelScope.launch {
            gate.withLock {
                val before = _uiState.value
                try {
                    val parked = session.parkedCheck(actionId)
                    if (parked != null) {
                        openRoll(before, actionId, actionId, parked)
                    } else {
                        val snapshot = session.choose(actionId)
                        _uiState.value = finishResolution(present(before, snapshot, playerActed = true))
                    }
                } catch (error: Exception) {
                    _uiState.value = before.copy(resolving = false, dice = DiceOverlay.Closed)
                    throw error
                }
            }
        }
    }

    fun continuePrologue() {
        turn(playerActed = true) { session.continuePrologue() }
    }

    fun submitText(text: String) {
        if (rollBusy()) return
        _uiState.value = beginResolution(_uiState.value)
        viewModelScope.launch {
            gate.withLock {
                val before = _uiState.value
                try {
                    when (val prepared = session.prepareText(text)) {
                        is PreparedText.Settled -> {
                            _uiState.value = finishResolution(present(before, prepared.snapshot, playerActed = true))
                        }
                        is PreparedText.Action -> {
                            val parked = session.parkedCheck(prepared.actionId)
                            if (parked != null) {
                                openRoll(before, prepared.actionId, prepared.sentence, parked)
                            } else {
                                val snapshot = session.resolveChoice(prepared.actionId, prepared.sentence)
                                _uiState.value = finishResolution(present(before, snapshot, playerActed = true))
                            }
                        }
                    }
                } catch (error: Exception) {
                    _uiState.value = before.copy(resolving = false, dice = DiceOverlay.Closed)
                    throw error
                }
            }
        }
    }

    fun confirmRoll() {
        viewModelScope.launch {
            gate.withLock {
                val pending = dice.requestThrow() ?: return@withLock
                val before = _uiState.value
                _uiState.value = before.copy(dice = dice.overlay, resolving = true, notice = null)
                try {
                    val snapshot = session.resolveChoice(pending.actionId, pending.playerText)
                    val check = freshCheck(before.historySize, snapshot, pending.actionId)
                    if (check == null) {
                        dice.close()
                        heldRoll = null
                        _uiState.value = finishResolution(present(before, snapshot, playerActed = true))
                    } else {
                        dice.committed(checkReading(check, pending.attribute), SystemClock.uptimeMillis())
                        heldRoll = HeldRoll(before, snapshot)
                        _uiState.value = before.copy(dice = dice.overlay, resolving = true, notice = null)
                    }
                } catch (error: Exception) {
                    dice.close()
                    heldRoll = null
                    _uiState.value = before.copy(resolving = false, dice = DiceOverlay.Closed)
                    throw error
                }
            }
        }
    }

    fun withdrawRoll() {
        viewModelScope.launch {
            gate.withLock {
                if (dice.overlay !is DiceOverlay.Waiting) return@withLock
                dice.close()
                heldRoll = null
                _uiState.value = _uiState.value.copy(dice = DiceOverlay.Closed, resolving = false)
            }
        }
    }

    fun holdRoll() {
        viewModelScope.launch {
            gate.withLock {
                dice.hold()
                _uiState.value = _uiState.value.copy(dice = dice.overlay)
            }
        }
    }

    fun finishRoll() {
        viewModelScope.launch {
            gate.withLock {
                val held = heldRoll
                if (held == null || dice.overlay !is DiceOverlay.Holding) return@withLock
                dice.close()
                heldRoll = null
                _uiState.value = finishResolution(present(held.before, held.snapshot, playerActed = true))
                    .copy(animateCheck = false)
            }
        }
    }

    fun noteHypothesis(text: String) {
        turn(playerActed = true) { session.noteHypothesis(text) }
    }

    fun retryNarration() {
        turn(playerActed = true) { session.retryNarration() }
    }

    private fun rollBusy(): Boolean =
        _uiState.value.resolving || _uiState.value.dice !is DiceOverlay.Closed

    private fun openRoll(before: ValdumbriaUiState, actionId: String, playerText: String, parked: ParkedCheck) {
        dice.open(
            PendingThrow(
                actionId = actionId,
                playerText = playerText,
                attribute = parked.check.attribute,
                modifier = parked.check.modifier,
                difficulty = parked.check.difficulty,
                revision = parked.revision,
            ),
        )
        _uiState.value = before.copy(resolving = false, notice = null, dice = dice.overlay)
    }

    private fun turn(playerActed: Boolean, block: suspend () -> AdventureSessionSnapshot) {
        if (playerActed && rollBusy()) return
        if (playerActed) _uiState.value = beginResolution(_uiState.value)
        viewModelScope.launch {
            gate.withLock {
                val before = _uiState.value
                if (!playerActed && before.dice !is DiceOverlay.Closed) {
                    val snapshot = block()
                    if (!dice.keepAfterRestore(snapshot.record?.revision)) {
                        heldRoll = null
                        _uiState.value = present(before, snapshot, playerActed = false)
                    }
                    return@withLock
                }
                try {
                    val snapshot = block()
                    val presented = present(before, snapshot, playerActed)
                    _uiState.value = if (playerActed) finishResolution(presented) else presented.copy(
                        resolving = _uiState.value.resolving,
                    )
                } catch (error: Exception) {
                    val resolving = if (playerActed) false else _uiState.value.resolving
                    _uiState.value = before.copy(resolving = resolving)
                    throw error
                }
            }
        }
    }

    private fun present(
        before: ValdumbriaUiState,
        snapshot: AdventureSessionSnapshot,
        playerActed: Boolean,
    ): ValdumbriaUiState {
        val state = snapshot.record?.state
        val adventure = engine.adventure
        val journal = if (state == null) {
            JournalReading(emptyList(), emptyList(), emptyList(), emptyList())
        } else {
            journalReading(
                cluesInOrder = adventure.clues.map { it.id to it.text },
                heldClues = state.clueIds,
                evidenceInOrder = adventure.evidences.map { it.id to it.text },
                heldEvidence = state.evidenceIds,
                placesInOrder = adventure.locations.map { it.id to it.name },
                discoveredPlaces = state.discoveredLocationIds,
                recentLines = recentJournalLines(
                    snapshot.record.recentInteractions.map { it.speaker to it.text },
                ),
                hypotheses = state.hypotheses.map { it.text },
                conclusionsInOrder = adventure.deductions.map { deduction ->
                    deduction.id to deduction.localProse.ifBlank { deduction.statement }
                },
                heldConclusions = state.deductionIds,
            )
        }
        val latestAction = state?.history?.lastOrNull { it is AdventureLog.ActionResolved } as? AdventureLog.ActionResolved
        val resolvedCheck = state?.history?.let { checkOnLatestAction(it) }
        val attribute = latestAction?.let { entry ->
            adventure.actions.firstOrNull { it.id == entry.actionId }?.check?.attribute
        } ?: ""
        val historySize = state?.history?.size ?: 0
        val fired = state?.midnightFired == true
        val narration = snapshot.narration
        val beat = state?.let { pendingPrologueBeat(adventure, it) }
        val contract = state?.let { current ->
            adventure.sceneContracts.singleOrNull { it.locationId == (beat?.locationId ?: current.locationId) }
        }
        val offer = if (beat != null) {
            null
        } else if (
            snapshot.phase == AdventurePhase.IDLE &&
            state != null &&
            contract != null &&
            state.endingId == null
        ) {
            val available = contract.allowedActionIds.filter { engine.access(state, it) == ActionAccess.AVAILABLE }
            decisionOffer(
                adventure = adventure,
                state = state,
                availableActionIds = available,
                sceneAllowsFreeAction = contract.freeAction,
                conclusionOffered = adventure.deductions.any { it.offersConclusion(state) },
            )
        } else {
            null
        }
        val place = adventure.locations.firstOrNull {
            it.id == (beat?.locationId ?: state?.locationId)
        }
        val face = state?.let { clockFace(it.minutesElapsed) }
        return ValdumbriaUiState(
            ready = snapshot.ready,
            phase = snapshot.phase,
            locationId = place?.id ?: state?.locationId.orEmpty(),
            locationName = place?.name.orEmpty(),
            actTitle = actTitleOf(adventure.acts.map { it.id to it.title }, place?.actId),
            adventureTitle = adventure.title,
            clock = state?.let { clockLabel(it) } ?: "22:00",
            clockHour = face?.hour ?: 22,
            clockMinute = face?.minute ?: 0,
            health = state?.character?.health,
            characterName = state?.character?.name.orEmpty(),
            characterClass = state?.character?.characterClass,
            feminine = state?.character?.presentation == Presentation.FEMININE,
            strength = state?.character?.attributes?.strength ?: 0,
            agility = state?.character?.attributes?.agility ?: 0,
            intelligence = state?.character?.attributes?.intelligence ?: 0,
            items = state?.character?.inventory?.map { item ->
                CarriedItem(id = item.id, name = itemLabel(item.id, item.name))
            }.orEmpty(),
            narration = narration,
            revealNarration = playerActed && !narration.isNullOrBlank() && narration != before.narration,
            notice = snapshot.notice,
            inesLine = state?.let { inesClockLine(it) },
            inesStrong = state?.let { inesPlateIsStrong(it.midnightFired, inesClockLine(it)) } == true,
            lifeChange = lifeChangeLine(playerActed, before.health, state?.character?.health),
            announceMidnight = announceMidnight(playerActed, before.midnightKnown, fired),
            midnightKnown = fired,
            check = resolvedCheck?.let { checkReading(it, attribute) },
            animateCheck = shouldAnimateCheck(playerActed, before.historySize, historySize, resolvedCheck),
            clues = journal.clues,
            evidence = journal.evidence,
            hypotheses = journal.hypotheses,
            conclusions = journal.conclusions,
            places = journal.places,
            recentLines = journal.recent,
            actionIds = offer?.actionIds.orEmpty(),
            freeAction = offer?.freeAction == true,
            prologue = beat != null,
            endingId = state?.endingId,
            alignToken = nextAlignToken(before.alignToken, playerActed, before.narration, narration),
            historySize = historySize,
        )
    }
}

private data class HeldRoll(
    val before: ValdumbriaUiState,
    val snapshot: AdventureSessionSnapshot,
)

private fun freshCheck(
    previousHistorySize: Int,
    snapshot: AdventureSessionSnapshot,
    actionId: String,
): CheckResult? {
    val state = snapshot.record?.state ?: return null
    return checkAddedByThisCommit(state.history, previousHistorySize, actionId)
}

internal fun checkAddedByThisCommit(
    history: List<AdventureLog>,
    previousHistorySize: Int,
    actionId: String,
): CheckResult? {
    if (history.size <= previousHistorySize) return null
    val added = history.subList(previousHistorySize, history.size)
    val resolved = added.lastOrNull {
        it is AdventureLog.ActionResolved && it.actionId == actionId
    } as? AdventureLog.ActionResolved
    return resolved?.check
}

internal fun beginResolution(state: ValdumbriaUiState): ValdumbriaUiState =
    state.copy(resolving = true, notice = null)

internal fun finishResolution(state: ValdumbriaUiState): ValdumbriaUiState = state.copy(resolving = false)

class ValdumbriaViewModelFactory(
    private val adventureText: String,
    private val store: AdventureGameStore,
    private val backend: AdventureBackend?,
    private val die: Die = RandomDie(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ValdumbriaViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        val adventure = loadAdventure(adventureText)
        val engine = AdventureEngine(adventure, die)
        @Suppress("UNCHECKED_CAST")
        return ValdumbriaViewModel(
            session = AdventureSession(engine, store, backend),
            engine = engine,
        ) as T
    }
}
