package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureGameStore
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationDelivery
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.data.requireAdventureBackendUrl
import com.dungeonmasterai.domain.adventure.FixtureWatch
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureSessionTest {
    @Test
    fun theFixtureDoesNotAskForANarrativeLanguage() = runBlocking {
        val backend = scriptedBackend()
        var language: String? = "unset"
        val narrate = backend.onNarrate
        backend.onNarrate = { request ->
            language = request.narrativeLanguage
            narrate(request)
        }
        val session = session(MemoryAdventureGameStore(), backend)
        session.restore()
        session.start(scholar())

        session.submitText("search_yard")

        assertNull(language)
    }

    @Test
    fun aResolvedActionIsSavedBeforeItsNarrationIsShown() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("search_yard")

        assertEquals(AdventurePhase.IDLE, shown.phase)
        assertEquals("You search the yard.", shown.narration)
        assertTrue(shown.record?.state?.character?.inventory?.any { it.id == "brass_key" } == true)
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
        assertEquals("You search the yard.", store.load()?.pendingNarration?.text)
        assertEquals(1, backend.narrateCount.get())
    }

    @Test
    fun restoringAValidatedNarrationDoesNotRollOrNarrateAgain() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val die = ScriptedDie(listOf(1))
        val first = session(store, backend, rolls = listOf(1), die = die, edit = ::withClimb)
        first.restore()
        first.start(scholar())
        first.submitText("reckless_climb")
        val narrations = backend.narrateCount.get()
        val interpretations = backend.interpretCount.get()

        val restored = session(store, backend, rolls = emptyList(), die = die, edit = ::withClimb).restore()

        assertEquals("The climb fails.", restored.narration)
        assertEquals(0, restored.record?.state?.character?.health)
        assertEquals(narrations, backend.narrateCount.get())
        assertEquals(interpretations, backend.interpretCount.get())
    }

    @Test
    fun anAwaitingNarrationSurvivesRestartWithoutAnotherRequest() = runBlocking {
        val backend = scriptedBackend()
        backend.onNarrate = { BackendCall.Failed("PROVIDER_TIMEOUT", true) }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())
        val pending = session.submitText("search_yard")

        assertEquals(AdventurePhase.NARRATION_PENDING, pending.phase)
        assertNull(pending.narration)
        assertTrue(pending.record?.state?.character?.inventory?.any { it.id == "brass_key" } == true)
        val narrations = backend.narrateCount.get()

        val restored = session(store, backend).restore()

        assertEquals(AdventurePhase.NARRATION_PENDING, restored.phase)
        assertNull(restored.narration)
        assertEquals(narrations, backend.narrateCount.get())
        assertEquals("brass_key", restored.record?.state?.character?.inventory?.last()?.id)
    }

    @Test
    fun authoredLocalProseIsShownEvenWhenItMatchesAForbiddenLine() = runBlocking {
        val backend = scriptedBackend()
        backend.onNarrate = { BackendCall.Failed("PROVIDER_ERROR", true) }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, rolls = listOf(1), edit = ::withForbiddenClimb)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("reckless_climb")

        assertEquals(AdventurePhase.IDLE, shown.phase)
        assertEquals("No hidden solution is stored here.", shown.narration)
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
        assertEquals(0, shown.record?.state?.character?.health)
    }

    @Test
    fun aFailedSaveKeepsThePreviousStateAndAllowsAnotherRoll() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, rolls = listOf(1, 1), edit = ::withClimb)
        session.restore()
        session.start(scholar())
        store.failuresLeft = 1

        val failed = session.submitText("reckless_climb")

        assertEquals(PlayerCopy.SAVE_FAILED, failed.notice)
        assertEquals(10, failed.record?.state?.character?.health)
        assertEquals(0, store.load()?.revision)

        val saved = session.submitText("reckless_climb")

        assertEquals(0, saved.record?.state?.character?.health)
        assertEquals("The climb fails.", saved.narration)
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
    }

    @Test
    fun aProviderFailureBeforeCommitDoesNotResolve() = runBlocking {
        val backend = scriptedBackend()
        backend.onInterpret = { BackendCall.Failed("PROVIDER_ERROR", true) }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("look at the stones")

        assertEquals(PlayerCopy.NARRATOR_UNAVAILABLE, shown.notice)
        assertFalse(shown.notice.orEmpty().contains("Failed to validate JSON"))
        assertEquals(0, shown.record?.revision)
        assertEquals(0, backend.narrateCount.get())
        assertTrue(shown.record?.state?.character?.inventory?.none { it.id == "brass_key" } == true)
    }

    @Test
    fun aRejectedReadingStillSaysThatNoActionWasTaken() = runBlocking {
        val backend = scriptedBackend()
        backend.onInterpret = { BackendCall.Failed("CONTRACT_VIOLATION", false) }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("look at the stones")

        assertEquals(PlayerCopy.NO_ACTION, shown.notice)
        assertEquals(0, shown.record?.revision)
        assertEquals(0, backend.narrateCount.get())
    }

    @Test
    fun narrationFailureAfterACheckKeepsTheRollAndDoesNotResolveAgain() = runBlocking {
        val backend = scriptedBackend()
        backend.onNarrate = { BackendCall.Failed("INVALID_MODEL_OUTPUT", true) }
        val store = MemoryAdventureGameStore()
        val die = CountingDie(listOf(1, 20))
        val session = session(store, backend, die = die, edit = ::withSilentClimb)
        session.restore()
        session.start(scholar())

        val failed = session.submitText("reckless_climb")

        assertEquals(AdventurePhase.NARRATION_PENDING, failed.phase)
        assertEquals(PlayerCopy.NARRATION_RETRY, failed.notice)
        assertEquals(1, die.count)
        assertEquals(0, failed.record?.state?.character?.health)
        assertNull(failed.narration)
        val revision = failed.record?.revision
        val narrations = backend.narrateCount.get()

        val restored = session(store, backend, die = die, edit = ::withSilentClimb).restore()

        assertEquals(AdventurePhase.NARRATION_PENDING, restored.phase)
        assertEquals(revision, restored.record?.revision)
        assertEquals(0, restored.record?.state?.character?.health)
        assertEquals(1, die.count)
        assertEquals(narrations, backend.narrateCount.get())
        assertNull(restored.narration)
    }

    @Test
    fun aLateReplyDoesNotResolveTheAction() = runBlocking {
        val backend = scriptedBackend()
        backend.onInterpret = { request ->
            val body = (recognized(request, "search_yard") as BackendCall.Ready).body
            BackendCall.Ready(body.copy(requestId = "stale-request"))
        }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("look at the stones")

        assertNull(shown.narration)
        assertEquals(0, shown.record?.revision)
        assertTrue(shown.record?.state?.character?.inventory?.none { it.id == "brass_key" } == true)
        assertEquals(0, backend.narrateCount.get())
    }

    @Test
    fun aStaleRevisionAndADifferentSceneAreDiscarded() = runBlocking {
        val backend = scriptedBackend()
        backend.onInterpret = { request ->
            val body = (recognized(request, "search_yard") as BackendCall.Ready).body
            BackendCall.Ready(body.copy(stateRevision = "r9", sceneContractId = "other_scene"))
        }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("look at the stones")

        assertEquals(0, shown.record?.revision)
        assertEquals(0, backend.narrateCount.get())
    }

    @Test
    fun anAmbiguousReplyAppliesNothing() = runBlocking {
        val backend = scriptedBackend()
        backend.onInterpret = { request ->
            BackendCall.Ready(
                InterpretationResponse(
                    contractVersion = 1,
                    requestId = request.requestId,
                    stateRevision = request.stateRevision,
                    sceneContractId = request.sceneContract.id,
                    status = "AMBIGUOUS",
                    candidateActionId = null,
                    candidateActionIds = listOf("search_yard", "walk_away"),
                    parameters = emptyMap(),
                ),
            )
        }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend)
        session.restore()
        session.start(scholar())

        val shown = session.submitText("do something")

        assertEquals(PlayerCopy.NO_ACTION, shown.notice)
        assertEquals(0, shown.record?.revision)
        assertEquals(0, backend.narrateCount.get())
    }

    @Test
    fun twoSessionsCannotResolveTheSameRevision() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val die = CountingDie(listOf(1, 1))
        val engine = AdventureEngine(withClimb(FixtureWatch.adventure()), die)
        val first = AdventureSession(engine, store, backend)
        val second = AdventureSession(engine, store, backend)
        first.restore()
        first.start(scholar())
        second.restore()

        coroutineScope {
            listOf(first, second).map { player ->
                async(Dispatchers.Default) { player.submitText("reckless_climb") }
            }.awaitAll()
        }

        val saved = store.load()
        assertEquals(0, saved?.state?.character?.health)
        assertEquals(2, saved?.revision)
        assertEquals(1, die.count)
        assertEquals(1, backend.narrateCount.get())
        val again = AdventureSession(engine, store, backend).restore()
        assertEquals("The climb fails.", again.narration)
        assertEquals(1, backend.narrateCount.get())
    }

    @Test
    fun aSecondStartAdoptsTheSavedGame() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val late = session(store, backend)
        late.restore()
        val winner = session(store, backend)
        winner.restore()
        val started = winner.start(scholar())

        val adopted = late.start(scholar())

        assertEquals(PlayerCopy.GAME_ALREADY_SAVED, adopted.notice)
        assertEquals(started.record?.state, adopted.record?.state)
        assertEquals(started.record?.revision, adopted.record?.revision)
        val restored = session(store, backend).restore()
        assertEquals(started.record?.state, restored.record?.state)
        assertEquals(started.record?.revision, restored.record?.revision)
        assertNull(restored.notice)
    }

    @Test
    fun twoSessionsCannotOverwriteNarrationAndTheLoserRestoresTheWinner() = runBlocking {
        val backend = scriptedBackend()
        backend.onNarrate = { BackendCall.Failed("PROVIDER_TIMEOUT", true) }
        val store = MemoryAdventureGameStore()
        val opener = session(store, backend)
        opener.restore()
        opener.start(scholar())
        val pending = opener.submitText("search_yard")
        assertEquals(AdventurePhase.NARRATION_PENDING, pending.phase)
        val before = backend.narrateCount.get()
        val arrived = CountDownLatch(2)
        backend.onNarrate = { request ->
            arrived.countDown()
            check(arrived.await(5, TimeUnit.SECONDS))
            BackendCall.Ready(
                NarrationResponse(
                    contractVersion = 1,
                    requestId = request.requestId,
                    stateRevision = request.stateRevision,
                    sceneContractId = request.sceneContract.id,
                    outcome = request.resolvedAction.outcome,
                    text = "Narrated ${request.requestId}",
                    auxiliary = emptyMap(),
                ),
            )
        }
        val first = session(store, backend)
        val second = session(store, backend)
        first.restore()
        second.restore()

        val shown = coroutineScope {
            listOf(first, second).map { player ->
                async(Dispatchers.Default) { player.retryNarration() }
            }.awaitAll()
        }

        val saved = store.load()
        val stored = saved?.pendingNarration?.text
        assertEquals(NarrationDelivery.VALIDATED, saved?.pendingNarration?.delivery)
        assertEquals(2, saved?.revision)
        assertTrue(shown.any { it.notice == PlayerCopy.NARRATION_NOT_SAVED })
        assertTrue(shown.all { it.narration == stored })
        assertEquals(before + 2, backend.narrateCount.get())
        val restored = session(store, backend).restore()
        assertEquals(stored, restored.narration)
        assertEquals(AdventurePhase.IDLE, restored.phase)
        assertEquals(before + 2, backend.narrateCount.get())
        assertEquals(saved?.state, restored.record?.state)
    }

    @Test
    fun twoSessionsCannotOverwriteSceneProseAndTheLoserRestoresTheWinner() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val opener = session(store, backend, edit = ::requiredScene)
        opener.restore()
        opener.start(scholar())
        val arrived = CountDownLatch(2)
        backend.onScene = { request ->
            arrived.countDown()
            check(arrived.await(5, TimeUnit.SECONDS))
            BackendCall.Ready(
                SceneResponse(
                    contractVersion = 1,
                    requestId = request.requestId,
                    stateRevision = request.stateRevision,
                    sceneContractId = request.sceneContract.id,
                    text = "Scene ${request.requestId}",
                    auxiliary = emptyMap(),
                ),
            )
        }
        val first = session(store, backend, edit = ::requiredScene)
        val second = session(store, backend, edit = ::requiredScene)
        first.restore()
        second.restore()

        val shown = coroutineScope {
            listOf(first, second).map { player ->
                async(Dispatchers.Default) { player.retryScene() }
            }.awaitAll()
        }

        val saved = store.load()
        val stored = saved?.sceneProse?.get("yard_contract")
        assertEquals(1, saved?.revision)
        assertTrue(shown.any { it.notice == PlayerCopy.SCENE_NOT_SAVED })
        assertTrue(shown.all { it.sceneText == stored && it.phase == AdventurePhase.IDLE })
        assertEquals(2, backend.sceneCount.get())
        val restored = session(store, backend, edit = ::requiredScene).restore()
        assertEquals(stored, restored.sceneText)
        assertEquals(AdventurePhase.IDLE, restored.phase)
        assertEquals(2, backend.sceneCount.get())
        assertEquals(saved?.state, restored.record?.state)
    }

    @Test
    fun aRequiredSceneStaysPausedUntilValidProseIsSaved() = runBlocking {
        val backend = scriptedBackend()
        backend.onScene = { BackendCall.Failed("PROVIDER_TIMEOUT", true) }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, edit = ::requiredScene)
        session.restore()
        val started = session.start(scholar())

        assertEquals(AdventurePhase.SCENE_PAUSED, started.phase)
        val blocked = session.submitText("search_yard")
        assertEquals(AdventurePhase.SCENE_PAUSED, blocked.phase)
        assertEquals(0, blocked.record?.revision)
        assertEquals(0, backend.interpretCount.get())

        val stillWaiting = session.retryScene()
        assertEquals(AdventurePhase.SCENE_PAUSED, stillWaiting.phase)
        assertNull(stillWaiting.sceneText)

        backend.onScene = { request ->
            BackendCall.Ready(
                SceneResponse(
                    contractVersion = 1,
                    requestId = request.requestId,
                    stateRevision = request.stateRevision,
                    sceneContractId = request.sceneContract.id,
                    text = "The yard is empty.",
                    auxiliary = emptyMap(),
                ),
            )
        }
        val opened = session.retryScene()
        assertEquals(AdventurePhase.IDLE, opened.phase)
        assertEquals("The yard is empty.", opened.sceneText)
        val scenes = backend.sceneCount.get()

        val restored = session(store, backend, edit = ::requiredScene).restore()
        assertEquals("The yard is empty.", restored.sceneText)
        assertEquals(AdventurePhase.IDLE, restored.phase)
        assertEquals(scenes, backend.sceneCount.get())
    }

    @Test
    fun anActionOutsideTheSceneContractDoesNotResolve() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, rolls = listOf(1))
        session.restore()
        session.start(scholar())

        val refused = session.choose("reckless_climb")

        assertEquals(PlayerCopy.ACTION_UNAVAILABLE, refused.notice)
        assertEquals(10, refused.record?.state?.character?.health)
        assertNull(refused.record?.state?.endingId)
        assertEquals(0, refused.record?.revision)
        assertEquals(0, backend.narrateCount.get())
    }

    @Test
    fun aLethalFailureIsNarratedAndAnEmptySummaryDoesNotReplaceIt() = runBlocking {
        val backend = scriptedBackend()
        backend.onNarrate = { request ->
            BackendCall.Ready(
                NarrationResponse(
                    contractVersion = 1,
                    requestId = request.requestId,
                    stateRevision = request.stateRevision,
                    sceneContractId = request.sceneContract.id,
                    outcome = request.resolvedAction.outcome,
                    text = "The climb ends the watch.",
                    auxiliary = mapOf("narrativeSummary" to "  "),
                ),
            )
        }
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, rolls = listOf(1), edit = ::withClimb)
        session.restore()
        session.start(scholar())

        val shown = session.choose("reckless_climb")

        assertEquals(AdventurePhase.IDLE, shown.phase)
        assertEquals(0, shown.record?.state?.character?.health)
        assertEquals("death", shown.record?.state?.endingId)
        assertEquals("The climb fails.", shown.narration)
        assertEquals("", shown.record?.state?.narrativeSummary)
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
        assertEquals("FAILED", store.load()?.pendingNarration?.outcome)
        val narrations = backend.narrateCount.get()
        val restored = session(store, backend, rolls = emptyList(), edit = ::withClimb).restore()
        assertEquals("The climb fails.", restored.narration)
        assertEquals(0, restored.record?.state?.character?.health)
        assertEquals(narrations, backend.narrateCount.get())
    }

    @Test
    fun aMoveIsNarratedFromTheContractThatAuthorizedIt() = runBlocking {
        val backend = scriptedBackend()
        val store = MemoryAdventureGameStore()
        val session = session(store, backend, edit = ::withDoor)
        session.restore()
        session.start(scholar())
        session.choose("search_yard")

        val moved = session.choose("open_hall")

        assertEquals("hall", moved.record?.state?.locationId)
        assertEquals(AdventurePhase.IDLE, moved.phase)
        assertEquals("You search the yard.", moved.narration)
        assertEquals("yard_contract", store.load()?.pendingNarration?.sceneContractId)
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
    }

    @Test
    fun cleartextUrlsAreRejectedUnlessDebugAllowsThem() {
        assertThrowsMessage("debug-only") {
            requireAdventureBackendUrl("http://10.0.2.2:8000", allowCleartext = false)
        }
        requireAdventureBackendUrl("http://10.0.2.2:8000", allowCleartext = true)
        requireAdventureBackendUrl("https://example.com", allowCleartext = false)
        assertThrowsMessage("credentials") {
            requireAdventureBackendUrl("https://user:secret@example.com", allowCleartext = false)
        }
    }

    @Test
    fun releaseManifestDisablesCleartextAndDebugEnablesIt() {
        val main = java.io.File("src/main/AndroidManifest.xml").readText()
        val debug = java.io.File("src/debug/AndroidManifest.xml").readText()
        assertTrue(main.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(debug.contains("android:usesCleartextTraffic=\"true\""))
    }

    private fun session(
        store: AdventureGameStore,
        backend: ScriptedAdventureBackend,
        rolls: List<Int> = emptyList(),
        die: com.dungeonmasterai.domain.dice.Die? = null,
        edit: (Adventure) -> Adventure = { it },
    ): AdventureSession {
        val engine = AdventureEngine(edit(FixtureWatch.adventure()), die ?: ScriptedDie(rolls))
        return AdventureSession(engine, store, backend)
    }
}

private class ScriptedAdventureBackend : AdventureBackend {
    val interpretCount = AtomicInteger(0)
    val narrateCount = AtomicInteger(0)
    val sceneCount = AtomicInteger(0)
    var onInterpret: (InterpretationRequest) -> BackendCall<InterpretationResponse> = { request ->
        recognized(request, request.playerText)
    }
    var onNarrate: (NarrationRequest) -> BackendCall<NarrationResponse> = { request ->
        BackendCall.Ready(
            NarrationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                outcome = request.resolvedAction.outcome,
                text = "You search the yard.",
                auxiliary = emptyMap(),
            ),
        )
    }
    var onScene: (SceneRequest) -> BackendCall<SceneResponse> = { request ->
        BackendCall.Failed("PROVIDER_TIMEOUT", true)
    }

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> {
        interpretCount.incrementAndGet()
        return onInterpret(request)
    }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        narrateCount.incrementAndGet()
        val outcome = request.resolvedAction.outcome
        val text = if (outcome == "FAILED") "The climb fails." else "You search the yard."
        return onNarrate(request).let { call ->
            if (call is BackendCall.Ready && outcome == "FAILED") {
                BackendCall.Ready(call.body.copy(text = text, outcome = outcome))
            } else {
                call
            }
        }
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> {
        sceneCount.incrementAndGet()
        return onScene(request)
    }
}

private fun scriptedBackend(): ScriptedAdventureBackend = ScriptedAdventureBackend()

private fun recognized(request: InterpretationRequest, actionId: String): BackendCall<InterpretationResponse> =
    BackendCall.Ready(
        InterpretationResponse(
            contractVersion = 1,
            requestId = request.requestId,
            stateRevision = request.stateRevision,
            sceneContractId = request.sceneContract.id,
            status = "RECOGNIZED",
            candidateActionId = actionId,
            candidateActionIds = emptyList(),
            parameters = emptyMap(),
        ),
    )

private fun withClimb(adventure: Adventure): Adventure =
    adventure.copy(
        sceneContracts = adventure.sceneContracts.map { contract ->
            if (contract.id != "yard_contract") {
                contract
            } else {
                contract.copy(allowedActionIds = contract.allowedActionIds + "reckless_climb")
            }
        },
        actions = adventure.actions.map { action ->
            if (action.id != "reckless_climb") {
                action
            } else {
                action.copy(failure = action.failure?.copy(localProse = "The climb fails."))
            }
        },
    )

private fun withSilentClimb(adventure: Adventure): Adventure =
    withClimb(adventure).let { edited ->
        edited.copy(
            actions = edited.actions.map { action ->
                if (action.id != "reckless_climb") {
                    action
                } else {
                    action.copy(failure = action.failure?.copy(localProse = ""))
                }
            },
        )
    }

private fun withForbiddenClimb(adventure: Adventure): Adventure =
    withClimb(adventure).let { edited ->
        edited.copy(
            actions = edited.actions.map { action ->
                if (action.id != "reckless_climb") {
                    action
                } else {
                    action.copy(failure = action.failure?.copy(localProse = "No hidden solution is stored here."))
                }
            },
        )
    }

private fun withDoor(adventure: Adventure): Adventure =
    adventure.copy(
        sceneContracts = adventure.sceneContracts.map { contract ->
            if (contract.id == "yard_contract") {
                contract.copy(allowedActionIds = contract.allowedActionIds + "open_hall")
            } else {
                contract
            }
        },
    )

private fun requiredScene(adventure: Adventure): Adventure =
    adventure.copy(
        sceneContracts = adventure.sceneContracts.map { contract ->
            if (contract.id == "yard_contract") contract.copy(generationRequired = true) else contract
        },
    )

private fun assertThrowsMessage(fragment: String, body: () -> Unit) {
    val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { body() }
    assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(fragment))
}

private class CountingDie(
    private val rolls: List<Int>,
) : Die {
    var count: Int = 0

    override fun roll(): Int {
        val value = rolls[count]
        count += 1
        return value
    }
}
