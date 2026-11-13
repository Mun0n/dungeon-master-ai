package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureCommit
import com.dungeonmasterai.data.AdventureGameStore
import com.dungeonmasterai.data.AdventureRecord
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.InterpretationDecision
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.NarrationDecision
import com.dungeonmasterai.data.NarrationDelivery
import com.dungeonmasterai.data.NpcBody
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.PendingNarration
import com.dungeonmasterai.data.RecentInteraction
import com.dungeonmasterai.data.ResolutionCommit
import com.dungeonmasterai.data.ResolvedActionBody
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.confirmedCheck
import com.dungeonmasterai.data.fitsPlayerText
import com.dungeonmasterai.data.interpretReply
import com.dungeonmasterai.data.localActionId
import com.dungeonmasterai.data.narrationReply
import com.dungeonmasterai.data.revisionToken
import com.dungeonmasterai.data.sceneReply
import com.dungeonmasterai.data.toContextBody
import com.dungeonmasterai.data.toContractBody
import com.dungeonmasterai.domain.adventure.engine.ActionAccess
import com.dungeonmasterai.domain.adventure.engine.ConclusionVerdict
import com.dungeonmasterai.domain.adventure.engine.classifyConclusion
import com.dungeonmasterai.domain.adventure.engine.offersConclusion
import com.dungeonmasterai.domain.adventure.narration.activeForbiddenPhrases
import com.dungeonmasterai.domain.adventure.narration.containsListedPhrase
import com.dungeonmasterai.domain.adventure.narration.containsPlayerTechnicalToken
import com.dungeonmasterai.domain.adventure.narration.technicalIdentifiers
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.pendingPrologueBeat
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.narration.NarrationView
import com.dungeonmasterai.domain.adventure.narration.RecentLine
import com.dungeonmasterai.domain.adventure.narration.narrationView
import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.OfferedCheck
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AdventurePhase {
    LOADING,
    IDLE,
    NARRATION_PENDING,
    SCENE_PAUSED,
}

sealed interface PreparedText {
    data class Settled(val snapshot: AdventureSessionSnapshot) : PreparedText

    data class Action(val actionId: String, val sentence: String) : PreparedText
}

data class ParkedCheck(
    val check: OfferedCheck,
    val revision: Int,
)

data class AdventureSessionSnapshot(
    val ready: Boolean = false,
    val record: AdventureRecord? = null,
    val narration: String? = null,
    val sceneText: String? = null,
    val phase: AdventurePhase = AdventurePhase.LOADING,
    val notice: String? = null,
)

/**
 * One adventure turn: interpret, resolve, commit, then narrate.
 * The narration text is stored before this session exposes it.
 * Restoring a stored narration does not roll and does not call the model.
 */
class AdventureSession(
    private val engine: AdventureEngine,
    private val store: AdventureGameStore,
    private val backend: AdventureBackend?,
    private val newRequestId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) {
    private val gate = Mutex()
    private var snapshot = AdventureSessionSnapshot()

    suspend fun restore(): AdventureSessionSnapshot = gate.withLock {
        loadIntoSnapshot()
        snapshot
    }

    suspend fun start(character: Character): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        if (!snapshot.ready || snapshot.record != null) return@withLock snapshot
        val state = try {
            engine.newGame(character)
        } catch (error: IllegalArgumentException) {
            snapshot = snapshot.copy(notice = PlayerCopy.CHARACTER_CANNOT_START)
            return@withLock snapshot
        }
        val commit = store.commit(
            state = state,
            deathEndingId = engine.adventure.deathEndingId,
            midnightEventId = engine.adventure.clock.midnightEventId,
            expectedRevision = null,
            pendingNarration = null,
            sceneProse = emptyMap(),
            recentInteractions = emptyList(),
        )
        snapshot = when (commit) {
            is AdventureCommit.Saved -> display(commit.record, notice = null)
            is AdventureCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.GAME_ALREADY_SAVED)
        }
        snapshot
    }

    suspend fun submitText(text: String): AdventureSessionSnapshot = gate.withLock {
        when (val prepared = prepareLocked(text)) {
            is PreparedText.Settled -> prepared.snapshot
            is PreparedText.Action -> {
                val current = playableRecord() ?: return@withLock snapshot
                resolve(current, prepared.actionId, prepared.sentence, viewOf(current))
            }
        }
    }

    /**
     * Interprets one sentence and stops before the rules run.
     * A recognized action is returned so the screen can wait for a roll.
     */
    suspend fun prepareText(text: String): PreparedText = gate.withLock { prepareLocked(text) }

    suspend fun choose(actionId: String): AdventureSessionSnapshot = resolveChoice(actionId, actionId)

    suspend fun resolveChoice(actionId: String, playerText: String): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = playableRecord() ?: return@withLock snapshot
        if (prologueOpen(current)) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return@withLock snapshot
        }
        val view = viewOf(current)
        if (!actionAllowed(current, actionId, view)) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return@withLock snapshot
        }
        resolve(current, actionId, playerText, view)
    }

    /**
     * The check the player would roll, plus the revision that offer belongs to.
     * It does not roll.
     */
    suspend fun parkedCheck(actionId: String): ParkedCheck? = gate.withLock {
        val current = snapshot.record ?: return@withLock null
        if (!snapshot.ready || snapshot.phase != AdventurePhase.IDLE) return@withLock null
        if (prologueOpen(current)) return@withLock null
        val view = viewOf(current)
        if (!actionAllowed(current, actionId, view)) return@withLock null
        val offer = engine.offeredCheck(current.state, actionId) ?: return@withLock null
        ParkedCheck(check = offer, revision = current.revision)
    }

    suspend fun noteHypothesis(text: String): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = playableRecord() ?: return@withLock snapshot
        when (val resolution = engine.noteHypothesis(current.state, text)) {
            is AdventureResolution.Accepted -> {
                val commit = try {
                    store.commit(
                        state = resolution.state,
                        deathEndingId = engine.adventure.deathEndingId,
                        midnightEventId = engine.adventure.clock.midnightEventId,
                        expectedRevision = current.revision,
                        pendingNarration = current.pendingNarration,
                        sceneProse = current.sceneProse,
                        recentInteractions = current.recentInteractions,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    snapshot = snapshot.copy(notice = PlayerCopy.SAVE_FAILED)
                    return@withLock snapshot
                }
                snapshot = when (commit) {
                    is AdventureCommit.Saved -> display(commit.record, notice = null)
                    is AdventureCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.OTHER_ACTION_SAVED)
                }
            }
            is AdventureResolution.Rejected -> {
                snapshot = snapshot.copy(notice = hypothesisNotice(resolution.reason))
            }
        }
        snapshot
    }

    suspend fun continuePrologue(): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = playableRecord() ?: return@withLock snapshot
        val beat = pendingPrologueBeat(engine.adventure, current.state)
        if (beat == null) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return@withLock snapshot
        }
        val accepted = engine.advancePrologue(current.state) as? AdventureResolution.Accepted
        if (accepted == null) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return@withLock snapshot
        }
        val commit = try {
            store.commit(
                state = accepted.state,
                deathEndingId = engine.adventure.deathEndingId,
                midnightEventId = engine.adventure.clock.midnightEventId,
                expectedRevision = current.revision,
                pendingNarration = null,
                sceneProse = current.sceneProse,
                recentInteractions = current.recentInteractions.plusLine("narration", beat.prose),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot = snapshot.copy(notice = PlayerCopy.SAVE_FAILED)
            return@withLock snapshot
        }
        snapshot = when (commit) {
            is AdventureCommit.Saved -> display(commit.record, notice = null)
            is AdventureCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.OTHER_ACTION_SAVED)
        }
        snapshot
    }

    suspend fun retryNarration(): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = snapshot.record
        if (!snapshot.ready || current == null || snapshot.phase != AdventurePhase.NARRATION_PENDING) {
            return@withLock snapshot
        }
        narrate(current)
    }

    suspend fun retryScene(): AdventureSessionSnapshot = gate.withLock {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = snapshot.record
        if (!snapshot.ready || current == null || snapshot.phase != AdventurePhase.SCENE_PAUSED) {
            return@withLock snapshot
        }
        val view = viewOf(current)
        if (view == null || !view.contract.generationRequired || backend == null) {
            snapshot = snapshot.copy(notice = PlayerCopy.SCENE_STILL_WAITING)
            return@withLock snapshot
        }
        if (current.sceneProse[view.contract.id] != null) {
            snapshot = display(current, notice = null)
            return@withLock snapshot
        }
        val requestId = newRequestId()
        val call = call {
            backend.scene(
                SceneRequest(
                    contractVersion = 1,
                    requestId = requestId,
                    stateRevision = revisionToken(current.revision),
                    generationRequired = true,
                    sceneContract = view.toContractBody(activeForbidden(current)),
                    authorizedContext = view.toContextBody(revealedLines(current, outcomeProse = null)),
                    narrativeLanguage = engine.adventure.narrativeLanguage,
                ),
            )
        }
        val text = sceneReply(
            call = call,
            requestId = requestId,
            stateRevision = revisionToken(current.revision),
            sceneContractId = view.contract.id,
            forbidden = activeForbidden(current),
            technicalIds = technicalIdentifiers(engine.adventure),
        )
        if (text == null) {
            snapshot = snapshot.copy(notice = PlayerCopy.SCENE_STILL_WAITING)
            return@withLock snapshot
        }
        val commit = store.commit(
            state = current.state,
            deathEndingId = engine.adventure.deathEndingId,
            midnightEventId = engine.adventure.clock.midnightEventId,
            expectedRevision = current.revision,
            pendingNarration = current.pendingNarration,
            sceneProse = current.sceneProse + (view.contract.id to text),
            recentInteractions = current.recentInteractions,
        )
        snapshot = when (commit) {
            is AdventureCommit.Saved -> display(commit.record, notice = null)
            is AdventureCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.SCENE_NOT_SAVED)
        }
        snapshot
    }

    /**
     * A lost commit is not written. The session replaces its snapshot with the
     * confirmed record. Narration or scene prose that lost the race is discarded.
     * An awaiting narration that is still stored can be retried. Validated text
     * that is already stored is shown. A missing required scene stays paused.
     */
    private suspend fun adoptConfirmed(notice: String): AdventureSessionSnapshot {
        snapshot = try {
            display(store.load(), notice = notice)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AdventureSessionSnapshot(
                ready = true,
                phase = AdventurePhase.IDLE,
                notice = PlayerCopy.CONFIRMED_UNREADABLE,
            )
        }
        return snapshot
    }

    private suspend fun loadIntoSnapshot() {
        val loaded = try {
            store.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot = AdventureSessionSnapshot(
                ready = true,
                phase = AdventurePhase.IDLE,
                notice = PlayerCopy.SAVE_UNREADABLE,
            )
            return
        }
        snapshot = display(loaded, notice = null)
    }

    private suspend fun resolve(
        current: AdventureRecord,
        actionId: String,
        playerText: String,
        view: NarrationView?,
    ): AdventureSessionSnapshot {
        if (view != null && actionId !in view.contract.allowedActionIds) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return snapshot
        }
        if (engine.access(current.state, actionId) != ActionAccess.AVAILABLE) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return snapshot
        }
        val action = engine.adventure.actions.firstOrNull { it.id == actionId }
        val result = try {
            store.commitIfCurrent(
                expectedRevision = current.revision,
                deathEndingId = engine.adventure.deathEndingId,
                midnightEventId = engine.adventure.clock.midnightEventId,
                sceneProse = current.sceneProse,
                recentInteractions = current.recentInteractions.plusLine("player", playerText),
                resolve = { state -> engine.resolve(state, actionId) },
                pendingNarration = { accepted, revision ->
                    awaiting(accepted, revision, actionId, view, action?.check?.attribute)
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot = snapshot.copy(notice = PlayerCopy.SAVE_FAILED)
            return snapshot
        }
        when (result) {
            is ResolutionCommit.RevisionConflict -> {
                snapshot = adoptConfirmed(PlayerCopy.OTHER_ACTION_SAVED)
            }
            is ResolutionCommit.Unchanged -> {
                snapshot = display(result.record, notice = PlayerCopy.ACTION_REJECTED)
            }
            is ResolutionCommit.Saved -> {
                snapshot = display(result.record, notice = null).copy(narration = null)
                snapshot = narrate(result.record)
            }
        }
        return snapshot
    }

    private suspend fun conclude(
        current: AdventureRecord,
        playerText: String,
        view: NarrationView,
        decision: InterpretationDecision.Conclusion,
    ) {
        val deduction = engine.adventure.deductions.firstOrNull { it.id == decision.deductionId }
        if (deduction == null || !deduction.offersConclusion(current.state)) {
            snapshot = snapshot.copy(notice = CONCLUSION_REJECTED)
            return
        }
        val answers = decision.answers.toMap()
        if (classifyConclusion(deduction, answers) != ConclusionVerdict.ACCEPTED) {
            snapshot = snapshot.copy(notice = CONCLUSION_REJECTED)
            return
        }
        val prose = deduction.localProse.trim()
        val result = try {
            store.commitIfCurrent(
                expectedRevision = current.revision,
                deathEndingId = engine.adventure.deathEndingId,
                midnightEventId = engine.adventure.clock.midnightEventId,
                sceneProse = current.sceneProse,
                recentInteractions = current.recentInteractions
                    .plusLine("player", playerText)
                    .plusLine("narration", prose),
                resolve = { state -> engine.deduce(state, deduction.id) },
                pendingNarration = { _, revision ->
                    PendingNarration(
                        delivery = NarrationDelivery.VALIDATED,
                        requestId = newRequestId(),
                        stateRevision = revisionToken(revision),
                        sceneContractId = view.contract.id,
                        actionId = deduction.id,
                        outcome = "SUCCEEDED",
                        success = true,
                        attribute = null,
                        check = null,
                        appliedEffects = emptyList(),
                        text = prose,
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot = snapshot.copy(notice = PlayerCopy.SAVE_FAILED)
            return
        }
        snapshot = when (result) {
            is ResolutionCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.OTHER_ACTION_SAVED)
            is ResolutionCommit.Unchanged -> display(result.record, notice = CONCLUSION_REJECTED)
            is ResolutionCommit.Saved -> display(result.record, notice = null)
        }
    }

    private suspend fun narrate(record: AdventureRecord): AdventureSessionSnapshot {
        val pending = record.pendingNarration
        if (pending == null || pending.delivery != NarrationDelivery.AWAITING) {
            return display(record, notice = snapshot.notice)
        }
        if (pending.stateRevision != revisionToken(record.revision)) {
            snapshot = display(record, notice = PlayerCopy.NARRATION_MISMATCH)
            return snapshot
        }
        val view = engine.narrationView(
            record.state,
            pending.sceneContractId,
            record.recentInteractions.map { RecentLine(it.speaker, it.text) },
            inesClockLine(record.state),
        )
        val local = localProse(pending)
        val decision = if (view == null || backend == null) {
            null
        } else {
            val requestId = newRequestId()
            val place = narrationPlace(record, view)
            val call = call {
                backend.narrate(
                    NarrationRequest(
                        contractVersion = 1,
                        requestId = requestId,
                        stateRevision = pending.stateRevision,
                        sceneContract = view.toContractBody(activeForbidden(record)).copy(
                            purpose = place.contract.purpose,
                            revealableInformation = place.contract.revealableInformation,
                            presentNpcIds = place.presentNpcs.map { npc -> npc.id },
                        ),
                        authorizedContext = view.toContextBody(
                            revealedLines(record, outcomeProse = local),
                        ).copy(
                            locationName = place.locationName,
                            presentNpcs = place.presentNpcs.map { npc ->
                                NpcBody(id = npc.id, name = npc.name, state = npc.state)
                            },
                        ),
                        resolvedAction = ResolvedActionBody(
                            actionId = pending.actionId,
                            outcome = pending.outcome,
                            check = confirmedCheck(pending.attribute, pending.check),
                            appliedEffects = pending.appliedEffects,
                        ),
                        lifeAfter = record.state.character.health,
                        endingId = record.state.endingId,
                        hora = clockLabel(record.state),
                        hecho = local.takeIf { it.isNotEmpty() },
                        allowedAuxiliaryKeys = listOf("narrativeSummary"),
                        narrativeLanguage = engine.adventure.narrativeLanguage,
                    ),
                )
            }
            val reply = narrationReply(
                call = call,
                requestId = requestId,
                stateRevision = pending.stateRevision,
                sceneContractId = pending.sceneContractId,
                outcome = pending.outcome,
                forbidden = activeForbidden(record),
                technicalIds = technicalIdentifiers(engine.adventure),
            )
            reply as? NarrationDecision.Accepted
        }
        val text = decision?.text ?: local.takeIf { it.isNotEmpty() }
        if (text == null) {
            snapshot = display(record, notice = PlayerCopy.NARRATION_RETRY)
            return snapshot
        }
        val summary = decision?.summary ?: record.state.narrativeSummary
        val state = if (summary == record.state.narrativeSummary) {
            record.state
        } else {
            record.state.copy(narrativeSummary = summary)
        }
        val validated = pending.copy(
            delivery = NarrationDelivery.VALIDATED,
            text = text,
        )
        val commit = try {
            store.commit(
                state = state,
                deathEndingId = engine.adventure.deathEndingId,
                midnightEventId = engine.adventure.clock.midnightEventId,
                expectedRevision = record.revision,
                pendingNarration = validated,
                sceneProse = record.sceneProse,
                recentInteractions = record.recentInteractions.plusLine("narration", text),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot = display(record, notice = PlayerCopy.NARRATION_NOT_SAVED)
            return snapshot
        }
        snapshot = when (commit) {
            is AdventureCommit.Saved -> display(commit.record, notice = null)
            is AdventureCommit.RevisionConflict -> adoptConfirmed(PlayerCopy.NARRATION_NOT_SAVED)
        }
        return snapshot
    }

    private fun awaiting(
        accepted: AdventureResolution.Accepted,
        revision: Int,
        actionId: String,
        view: NarrationView?,
        attribute: String?,
    ): PendingNarration = PendingNarration(
        delivery = NarrationDelivery.AWAITING,
        requestId = newRequestId(),
        stateRevision = revisionToken(revision),
        sceneContractId = view?.contract?.id ?: UNCONTRACTED_SCENE,
        actionId = actionId,
        outcome = if (accepted.success) "SUCCEEDED" else "FAILED",
        success = accepted.success,
        attribute = if (accepted.check == null) null else attribute,
        check = accepted.check,
        appliedEffects = accepted.appliedEffects,
        text = null,
    )

    private suspend fun prepareLocked(text: String): PreparedText {
        if (!snapshot.ready) loadIntoSnapshot()
        val current = playableRecord() ?: return PreparedText.Settled(snapshot)
        if (prologueOpen(current)) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_UNAVAILABLE)
            return PreparedText.Settled(snapshot)
        }
        val sentence = text.trim()
        if (!fitsPlayerText(sentence)) {
            snapshot = snapshot.copy(notice = PlayerCopy.ACTION_LENGTH)
            return PreparedText.Settled(snapshot)
        }
        val view = viewOf(current)
        val available = availableIds(current, view)
        val actionId = if (view == null || backend == null) {
            localActionId(sentence, available)
        } else {
            val requestId = newRequestId()
            val call = call {
                backend.interpret(
                    InterpretationRequest(
                        contractVersion = 1,
                        requestId = requestId,
                        stateRevision = revisionToken(current.revision),
                        sceneContract = view.toContractBody(activeForbidden(current)),
                        authorizedContext = view.toContextBody(),
                        playerText = sentence,
                        narrativeLanguage = engine.adventure.narrativeLanguage,
                    ),
                )
            }
            if (call is BackendCall.Failed) {
                snapshot = snapshot.copy(notice = interpretationFailureNotice(call.code))
                return PreparedText.Settled(snapshot)
            }
            when (
                val decision = interpretReply(
                    call = call,
                    requestId = requestId,
                    stateRevision = revisionToken(current.revision),
                    sceneContractId = view.contract.id,
                    available = view.availableActions.map { it.id }.toSet(),
                    unavailable = view.unavailableActionIds.toSet(),
                    blocked = view.blockedActionIds.toSet(),
                    offered = view.availableConclusions,
                )
            ) {
                is InterpretationDecision.Recognized -> decision.actionId
                is InterpretationDecision.Conclusion -> {
                    conclude(current, sentence, view, decision)
                    return PreparedText.Settled(snapshot)
                }
                is InterpretationDecision.UnusableConclusion -> {
                    snapshot = snapshot.copy(notice = CONCLUSION_REJECTED)
                    return PreparedText.Settled(snapshot)
                }
                is InterpretationDecision.Declined -> {
                    snapshot = snapshot.copy(notice = PlayerCopy.NO_ACTION)
                    return PreparedText.Settled(snapshot)
                }
                is InterpretationDecision.Unusable -> localActionId(sentence, available)
            }
        }
        if (actionId == null) {
            snapshot = snapshot.copy(notice = PlayerCopy.NO_ACTION)
            return PreparedText.Settled(snapshot)
        }
        return PreparedText.Action(actionId, sentence)
    }

    private fun actionAllowed(
        record: AdventureRecord,
        actionId: String,
        view: NarrationView?,
    ): Boolean = if (view == null) {
        engine.access(record.state, actionId) == ActionAccess.AVAILABLE
    } else {
        actionId in view.availableActions.map { it.id }
    }

    private fun playableRecord(): AdventureRecord? {
        val current = snapshot.record
        if (!snapshot.ready || current == null) return null
        if (snapshot.phase != AdventurePhase.IDLE) {
            snapshot = snapshot.copy(notice = PlayerCopy.SCENE_WAITING)
            return null
        }
        return current
    }

    private fun display(record: AdventureRecord?, notice: String?): AdventureSessionSnapshot {
        if (record == null) {
            return AdventureSessionSnapshot(ready = true, phase = AdventurePhase.IDLE, notice = notice)
        }
        val pending = record.pendingNarration
        val beat = pendingPrologueBeat(engine.adventure, record.state)
        val storedNarration = pending?.takeIf { it.delivery == NarrationDelivery.VALIDATED }?.text
        val narration = when {
            beat != null -> beat.prose
            storedNarration != null -> storedNarration
            else -> null
        }
        val phase = when {
            pending?.delivery == NarrationDelivery.AWAITING -> AdventurePhase.NARRATION_PENDING
            beat != null -> AdventurePhase.IDLE
            sceneMissing(record) -> AdventurePhase.SCENE_PAUSED
            else -> AdventurePhase.IDLE
        }
        return AdventureSessionSnapshot(
            ready = true,
            record = record,
            narration = narration,
            sceneText = sceneText(record),
            phase = phase,
            notice = notice,
        )
    }

    private fun sceneMissing(record: AdventureRecord): Boolean {
        val view = viewOf(record) ?: return false
        return view.contract.generationRequired && record.sceneProse[view.contract.id] == null
    }

    private fun sceneText(record: AdventureRecord): String? {
        val view = viewOf(record) ?: return null
        return record.sceneProse[view.contract.id]
    }

    private fun interpretationFailureNotice(code: String): String =
        if (code == "CONTRACT_VIOLATION") PlayerCopy.NO_ACTION else PlayerCopy.NARRATOR_UNAVAILABLE

    private fun viewOf(record: AdventureRecord): NarrationView? =
        engine.narrationView(
            record.state,
            record.recentInteractions.map { RecentLine(it.speaker, it.text) },
            inesClockLine(record.state),
        )

    private fun narrationPlace(record: AdventureRecord, authorizing: NarrationView): NarrationView {
        val here = viewOf(record)
        if (here == null || here.contract.locationId == authorizing.contract.locationId) {
            return authorizing
        }
        return here
    }

    private fun activeForbidden(record: AdventureRecord): List<String> {
        val contract = viewOf(record)?.contract ?: return emptyList()
        return activeForbiddenPhrases(
            documentPhrases = contract.forbiddenInformation,
            evidenceIds = record.state.evidenceIds,
            truthKnown = record.state.flags["truth_known"] == true,
            inesLine = inesClockLine(record.state),
            clueIds = record.state.clueIds,
        )
    }

    private fun revealedLines(record: AdventureRecord, outcomeProse: String?): List<String> {
        val secrets = activeForbidden(record)
        val ending = record.state.endingId?.let { endingId ->
            endingTitle(endingId).takeUnless { title -> title == "Final." || endingId in title }
        }
        return (
            revealedFacts(engine.adventure, record.state).clues +
                revealedFacts(engine.adventure, record.state).evidence +
                listOfNotNull(outcomeProse?.trim()?.takeIf { it.isNotEmpty() }, inesClockLine(record.state), ending)
            )
            .filter { line ->
                line.isNotBlank() &&
                    line.length <= AdventureRules.LOCAL_PROSE_MAX &&
                    !containsListedPhrase(line, secrets) &&
                    !containsPlayerTechnicalToken(line)
            }
            .distinct()
            .take(24)
    }

    private fun availableIds(record: AdventureRecord, view: NarrationView?): Set<String> =
        if (view != null) {
            view.availableActions.map { it.id }.toSet()
        } else {
            engine.adventure.actions
                .map { it.id }
                .filter { engine.access(record.state, it) == ActionAccess.AVAILABLE }
                .toSet()
        }

    private fun hypothesisNotice(reason: AdventureRejection): String = when (reason) {
        AdventureRejection.TERMINAL -> PlayerCopy.HYPOTHESIS_CLOSED
        AdventureRejection.HYPOTHESIS_DUPLICATE -> PlayerCopy.HYPOTHESIS_DUPLICATE
        else -> PlayerCopy.HYPOTHESIS_LENGTH
    }

    private fun prologueOpen(record: AdventureRecord): Boolean =
        pendingPrologueBeat(engine.adventure, record.state) != null

    private fun localProse(pending: PendingNarration): String {
        val action = engine.adventure.actions.firstOrNull { it.id == pending.actionId } ?: return ""
        val outcome = if (pending.success) action.success else action.failure
        val text = outcome?.localProse?.trim().orEmpty()
        return if (text.length > AdventureRules.LOCAL_PROSE_MAX) "" else text
    }

    private suspend fun <T> call(block: suspend () -> BackendCall<T>): BackendCall<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            BackendCall.Failed(code = "PROVIDER_ERROR", retryable = true)
        }

    private fun List<RecentInteraction>.plusLine(speaker: String, text: String): List<RecentInteraction> {
        val clean = text.trim()
        if (clean.isEmpty()) return this
        val cap = if (speaker == "player") AdventureRules.PLAYER_TEXT_MAX else AdventureRules.LOCAL_PROSE_MAX
        if (clean.length > cap) return this
        return (this + RecentInteraction(speaker = speaker, text = clean)).takeLast(AdventureRules.RECENT_LINES)
    }

    internal companion object {
        const val UNCONTRACTED_SCENE: String = "uncontracted"
        const val CONCLUSION_REJECTED: String = "Esa conclusión no se sostiene."
    }
}
