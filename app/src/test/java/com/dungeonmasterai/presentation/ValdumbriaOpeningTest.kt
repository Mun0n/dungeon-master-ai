package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureCommit
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationDelivery
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.RoomAdventureGameStore
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.data.openMemoryDatabase
import com.dungeonmasterai.domain.adventure.accepted
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.load.validateAdventure
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.narration.RecentLine
import com.dungeonmasterai.domain.adventure.narration.narrationView
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValdumbriaOpeningTest {
    @Test
    fun theDocumentSeparatesThePlayableOpeningFromDeclaredContent() {
        val adventure = valdumbria()
        val playable = setOf(
            "read_elias_letter",
            "speak_with_ines",
            "look_over_the_square",
            "go_to_tavern",
            "ask_mara",
            "sit_with_the_regulars",
            "go_to_plaza",
        )
        val plaza = adventure.sceneContracts.single { it.id == "plaza_contract" }
        val tavern = adventure.sceneContracts.single { it.id == "tavern_contract" }
        assertEquals(
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "look_over_the_square",
                "go_to_tavern",
                "warn_ines",
                "speak_with_beltran",
                "note_suspicion",
                "accuse_beltran",
                "leave_valdumbria",
                "study_the_dark_tower",
                "follow_elias_directions",
                "enter_the_church",
                "enter_the_archive",
                "enter_the_cemetery",
            ),
            plaza.allowedActionIds,
        )
        assertEquals(
            listOf(
                "ask_mara",
                "sit_with_the_regulars",
                "go_to_plaza",
                "follow_the_path_behind_the_inn",
                "ask_mara_for_linen",
                "bind_a_wound",
                "mara_kept_the_packet",
                "accuse_mara",
                "leave_the_tavern",
            ),
            tavern.allowedActionIds,
        )
        assertTrue(adventure.actions.map { it.id }.containsAll(playable))
        assertNull(adventure.actions.find { it.id == "state_the_conclusion" })
        assertTrue(adventure.actions.size > playable.size)
        adventure.actions.forEach { action ->
            val grantsDeduction = action.success.effects.any { effect ->
                effect is Effect.SetFlag && effect.key == "truth_known"
            } || action.failure?.effects?.any { effect ->
                effect is Effect.SetFlag && effect.key == "truth_known"
            } == true
            assertFalse(action.id, grantsDeduction)
        }
    }

    @Test
    fun theOpeningCanBePlayedAndAFailedCheckDoesNotBlockTheClues() {
        val engine = AdventureEngine(valdumbria(), ScriptedDie(listOf(6, 1)))
        var state = engine.newGame(scholar())
        assertEquals("plaza", state.locationId)
        assertEquals(0, state.minutesElapsed)
        assertEquals("22:00", clockLabel(state))

        val tooSoon = engine.resolve(state, "speak_with_ines")
        assertTrue(tooSoon is AdventureResolution.Rejected)
        assertEquals(0, (tooSoon as AdventureResolution.Rejected).state.minutesElapsed)

        state = accepted(engine, state, "read_elias_letter")
        assertEquals(setOf("clue_letter"), state.clueIds)
        assertEquals(true, state.flags["letter_read"])
        assertEquals(setOf("plaza", "tavern"), state.discoveredLocationIds)
        assertEquals(5, state.minutesElapsed)
        assertEquals("22:05", clockLabel(state))
        assertFalse(revealedFacts(engine.adventure, state).clues.joinToString().containsDeath())

        state = accepted(engine, state, "speak_with_ines")
        assertTrue("clue_ines_summoned" in state.clueIds)
        assertEquals("unwarned", state.npcStates["ines"])

        state = accepted(engine, state, "look_over_the_square")
        assertTrue("clue_bells" in state.clueIds)

        state = accepted(engine, state, "go_to_tavern")
        assertEquals("tavern", state.locationId)

        state = accepted(engine, state, "ask_mara")
        assertFalse("clue_rumor_creature" in state.clueIds)
        assertEquals(10, state.character.health)

        val beforeMara = engine.resolve(state, "go_to_plaza")
        assertTrue(beforeMara is AdventureResolution.Rejected)
        assertEquals("tavern", (beforeMara as AdventureResolution.Rejected).state.locationId)

        state = accepted(engine, state, "sit_with_the_regulars")
        assertTrue("clue_rumor_creature" in state.clueIds)
        assertTrue("clue_elia_at_church" in state.clueIds)
        assertTrue("clue_bells" in state.clueIds)
        assertEquals(50, state.minutesElapsed)
        assertEquals("22:50", clockLabel(state))
        assertFalse(state.midnightFired)
        assertEquals(10, state.character.health)
        assertNull(state.endingId)
        assertFalse(revealedFacts(engine.adventure, state).clues.joinToString().containsDeath())
    }

    @Test
    fun sceneLinesDoNotRevealEliasDeathUntilAConfirmedAction() {
        val adventure = valdumbria()
        adventure.sceneContracts.forEach { contract ->
            assertFalse(contract.generationRequired)
            assertFalse(contract.id, contract.presentNpcIds.contains("elia"))
            val scene = (listOf(contract.purpose) + contract.revealableInformation).joinToString("\n")
            assertFalse(contract.id, scene.containsDeath())
            assertTrue(contract.forbiddenInformation.any { it.equals("Elia is dead", ignoreCase = true) })
            assertTrue(contract.forbiddenInformation.any { it == "Elia está muerta" })
        }
        val openingClues = setOf(
            "clue_letter",
            "clue_ines_summoned",
            "clue_bells",
            "clue_rumor_creature",
            "clue_elia_at_church",
        )
        adventure.clues.filter { it.id in openingClues }.forEach { clue ->
            assertFalse(clue.id, clue.text.containsDeath())
        }
        listOf("read_elias_notes", "mara_kept_the_packet").forEach { actionId ->
            val action = adventure.actions.single { it.id == actionId }
            assertTrue(action.success.localProse.contains("Elia está muerta"))
            assertTrue(action.success.effects.any { it is Effect.AddEvidence && it.evidenceId == "evidence_elia" })
        }
        assertTrue(adventure.evidences.single { it.id == "evidence_elia" }.text.contains("Elia está muerta"))
        val held = engineOf().newGame(scholar()).copy(evidenceIds = setOf("evidence_elia"))
        assertTrue(revealedFacts(adventure, held).evidence.joinToString().contains("Elia está muerta"))
        assertFalse(revealedFacts(adventure, engineOf().newGame(scholar())).evidence.joinToString().containsDeath())
    }

    @Test
    fun aRevealedDeathMayBeSentAndAnUnrevealedDeathIsRedacted() {
        val engine = engineOf()
        val held = engine.newGame(scholar()).copy(
            locationId = "church",
            evidenceIds = setOf(
                "evidence_record",
                "evidence_crypt",
                "evidence_elia",
                "evidence_alden",
            ),
            narrativeSummary = "Elia está muerta. Ella no regresará. La plaza sigue en calma.",
        )
        val revealed = engine.narrationView(
            held,
            listOf(
                RecentLine("narration", "Las notas cierran la investigación. Elia está muerta."),
                RecentLine("player", "Quiero mirar la lista."),
            ),
        )
        assertNotNull(revealed)
        assertEquals(
            "Elia está muerta. Ella no regresará. La plaza sigue en calma.",
            revealed!!.narrativeSummary,
        )
        assertEquals(
            listOf(
                "Las notas cierran la investigación. Elia está muerta.",
                "Quiero mirar la lista.",
            ),
            revealed.recentLines.map { it.text },
        )
        assertTrue(revealed.availableConclusions.any { it.id == "name_the_crime" })

        val hiddenState = engine.newGame(scholar()).copy(
            narrativeSummary = "Elia está muerta. Ella no regresará. La plaza sigue en calma.",
        )
        assertEquals("dead", hiddenState.npcStates["elia"])
        assertFalse("evidence_elia" in hiddenState.evidenceIds)
        val hidden = engine.narrationView(
            hiddenState,
            listOf(RecentLine("narration", "Elia está muerta. Ella no regresará.")),
        )
        assertNotNull(hidden)
        assertEquals("La plaza sigue en calma.", hidden!!.narrativeSummary)
        assertTrue(hidden.recentLines.isEmpty())
    }

    @Test
    fun aNarrationThatRevealsEliasDeathIsReplacedByTheLetter() = runBlocking {
        val store = MemoryAdventureGameStore()
        val session = openLegacy(store, SpoilingBackend())
        val shown = session.choose("read_elias_letter")
        val letter = valdumbria().actions.single { it.id == "read_elias_letter" }.success.localProse
        assertEquals(letter, shown.narration)
        assertFalse(shown.narration.orEmpty().containsDeath())
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)
    }

    @Test
    fun aProtectionThatCrossesMidnightDoesNotSaveInes() {
        val engine = AdventureEngine(valdumbria(), ScriptedDie(emptyList()))
        val started = engine.newGame(scholar())
        val late = started.copy(minutesElapsed = 115, clueIds = setOf("clue_ines_summoned"))
        val lost = accepted(engine, late, "warn_ines")
        assertTrue(lost.midnightFired)
        assertEquals(true, lost.flags["ines_protected"])
        assertEquals("lost", lost.npcStates["ines"])
        assertEquals(
            PlayerCopy.INES_LOST_WITH_BELLS,
            inesClockLine(lost),
        )

        val early = accepted(
            engine,
            started.copy(minutesElapsed = 10, clueIds = setOf("clue_ines_summoned")),
            "warn_ines",
        )
        assertFalse(early.midnightFired)
        assertEquals("unwarned", early.npcStates["ines"])
        assertEquals(
            PlayerCopy.INES_WARNED,
            inesClockLine(early),
        )
        val saved = accepted(
            engine,
            early.copy(
                minutesElapsed = 110,
                discoveredLocationIds = early.discoveredLocationIds + "tavern",
            ),
            "go_to_tavern",
        )
        assertEquals("saved", saved.npcStates["ines"])
        assertEquals("tavern", saved.locationId)
        assertEquals(PlayerCopy.INES_SAVED, inesClockLine(saved))
        listOf("warn_ines", "cut_alden_off", "bar_the_vestry").forEach { actionId ->
            val action = engine.adventure.actions.single { it.id == actionId }
            assertTrue(
                actionId,
                action.success.effects.none { it is Effect.SetNpcState && it.npcId == "ines" },
            )
        }
    }

    @Test
    fun theDeductionIsNotGrantedByAnAction() {
        val engine = AdventureEngine(valdumbria(), ScriptedDie(emptyList()))
        val started = engine.newGame(scholar())
        val denied = engine.deduce(started, "name_the_crime")
        assertTrue(denied is AdventureResolution.Rejected)
        val four = setOf("evidence_record", "evidence_crypt", "evidence_elia", "evidence_alden")
        val accepted = engine.deduce(started.copy(evidenceIds = four), "name_the_crime")
        assertTrue(accepted is AdventureResolution.Accepted)
        val proved = (accepted as AdventureResolution.Accepted).state
        assertEquals(setOf("name_the_crime"), proved.deductionIds)
        assertEquals(true, proved.flags["truth_known"])
        val unpublished = engine.resolve(
            proved.copy(locationId = "church"),
            "publish_the_truth",
        )
        assertTrue(unpublished is AdventureResolution.Accepted)
        assertEquals("justice", (unpublished as AdventureResolution.Accepted).state.endingId)
    }

    @Test
    fun theOpeningSurvivesSaveAndRestoreWithoutAnotherRoll() = runBlocking {
        val database = openMemoryDatabase()
        val store = RoomAdventureGameStore(database.adventureGameDao())
        val engine = AdventureEngine(valdumbria(), ScriptedDie(listOf(20)))
        val seeded = store.commit(
            state = engine.newGame(scholar()).copy(
                history = listOf(AdventureLog.ClockAdvanced(0, 0)),
            ),
            deathEndingId = engine.adventure.deathEndingId,
            midnightEventId = engine.adventure.clock.midnightEventId,
            expectedRevision = null,
            pendingNarration = null,
            sceneProse = emptyMap(),
            recentInteractions = emptyList(),
        )
        check(seeded is AdventureCommit.Saved)
        val first = AdventureSession(engine, store, backend = null)
        first.restore()
        val shown = first.choose("read_elias_letter")
        val letter = shown.narration
        assertEquals(NarrationDelivery.VALIDATED, store.load()?.pendingNarration?.delivery)

        val restored = AdventureSession(
            AdventureEngine(valdumbria(), ScriptedDie(emptyList())),
            store,
            backend = null,
        ).restore()
        assertEquals(letter, restored.narration)
        assertEquals(setOf("clue_letter"), restored.record?.state?.clueIds)
        assertEquals(5, restored.record?.state?.minutesElapsed)
        assertEquals(10, restored.record?.state?.character?.health)
        assertEquals("22:05", restored.record?.state?.let { clockLabel(it) })
    }

    @Test
    fun theOpeningSessionRefusesDeclaredActions() = runBlocking {
        val session = AdventureSession(
            AdventureEngine(valdumbria(), ScriptedDie(emptyList())),
            MemoryAdventureGameStore(),
            backend = null,
        )
        session.restore()
        session.start(scholar())
        assertEquals("es", valdumbria().narrativeLanguage)
        listOf("enter_the_church", "warn_ines", "publish_the_truth", "read_elias_notes").forEach { actionId ->
            val refused = session.choose(actionId)
            assertEquals(PlayerCopy.ACTION_UNAVAILABLE, refused.notice)
            assertEquals(0, refused.record?.state?.minutesElapsed)
            assertEquals(false, refused.record?.state?.flags?.get("ines_protected"))
            assertNull(refused.record?.state?.endingId)
        }
        repeat(3) { assertNull(session.continuePrologue().notice) }
        assertNull(session.choose("leave_the_cemetery").notice)
        assertNull(session.choose("study_the_dark_tower").notice)
        val entered = session.choose("enter_the_church")
        assertNull(entered.notice)
        assertEquals("church", entered.record?.state?.locationId)
    }

    @Test
    fun spanishRequestsAskForSpanishNarrationAndScenes() = runBlocking {
        val backend = LanguageRecordingBackend()
        val session = openLegacy(MemoryAdventureGameStore(), backend)

        val shown = session.submitText("Quiero leer la carta de Elia")

        assertEquals("es", backend.interpretation?.narrativeLanguage)
        assertEquals("es", backend.narration?.narrativeLanguage)
        assertEquals("read_elias_letter", backend.narration?.resolvedAction?.actionId)
        assertEquals("Lees la carta.", shown.narration)

        val leaked = openLegacy(
            MemoryAdventureGameStore(),
            object : AdventureBackend by backend {
                override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
                    BackendCall.Ready(
                        NarrationResponse(
                            contractVersion = 1,
                            requestId = request.requestId,
                            stateRevision = request.stateRevision,
                            sceneContractId = request.sceneContract.id,
                            outcome = request.resolvedAction.outcome,
                            text = "En la plaza aparece clue_letter y SET_FLAG.",
                        ),
                    )
            },
        )
        val replaced = leaked.choose("read_elias_letter")
        val letter = valdumbria().actions.single { it.id == "read_elias_letter" }.success.localProse
        assertEquals(letter, replaced.narration)
        assertFalse(replaced.narration.orEmpty().contains("clue_letter"))
        assertFalse(replaced.narration.orEmpty().contains("SET_FLAG"))

        val plain = openLegacy(
            MemoryAdventureGameStore(),
            object : AdventureBackend by backend {
                override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
                    BackendCall.Ready(
                        NarrationResponse(
                            contractVersion = 1,
                            requestId = request.requestId,
                            stateRevision = request.stateRevision,
                            sceneContractId = request.sceneContract.id,
                            outcome = request.resolvedAction.outcome,
                            text = "La plaza queda en silencio.",
                        ),
                    )
            },
        )
        assertEquals("La plaza queda en silencio.", plain.choose("read_elias_letter").narration)

        val optionId = openLegacy(
            MemoryAdventureGameStore(),
            object : AdventureBackend by backend {
                override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
                    BackendCall.Ready(
                        NarrationResponse(
                            contractVersion = 1,
                            requestId = request.requestId,
                            stateRevision = request.stateRevision,
                            sceneContractId = request.sceneContract.id,
                            outcome = request.resolvedAction.outcome,
                            text = "La frase nombra shut_in_with_the_sick.",
                        ),
                    )
            },
        )
        assertEquals(letter, optionId.choose("read_elias_letter").narration)

        val paused = valdumbria().copy(
            sceneContracts = valdumbria().sceneContracts.map { contract ->
                if (contract.id == "cemetery_contract") {
                    contract.copy(generationRequired = true)
                } else {
                    contract
                }
            },
        )
        val sceneBackend = LanguageRecordingBackend()
        val sceneSession = AdventureSession(
            AdventureEngine(paused, ScriptedDie(emptyList())),
            MemoryAdventureGameStore(),
            sceneBackend,
        )
        sceneSession.restore()
        assertEquals(AdventurePhase.IDLE, sceneSession.start(scholar()).phase)
        repeat(2) { assertEquals(AdventurePhase.IDLE, sceneSession.continuePrologue().phase) }
        assertEquals(AdventurePhase.SCENE_PAUSED, sceneSession.continuePrologue().phase)

        val scene = sceneSession.retryScene()

        assertEquals("es", sceneBackend.scene?.narrativeLanguage)
        assertEquals("La plaza espera.", scene.sceneText)
    }

    @Test
    fun anotherNarrativeLanguageIsRejected() {
        val errors = validateAdventure(valdumbria().copy(narrativeLanguage = "en"))
        assertTrue(errors.any { it.contains("Narrative language") })
    }

    @Test
    fun everyChoiceLabelIsReadable() {
        val adventure = valdumbria()
        val technicalIds = buildSet {
            add(adventure.id)
            add(adventure.deathEndingId)
            adventure.actions.forEach { add(it.id) }
            adventure.endings.forEach { add(it.id) }
            adventure.clues.forEach { add(it.id) }
            adventure.evidences.forEach { add(it.id) }
            adventure.flags.forEach { add(it.id) }
            adventure.locations.forEach { add(it.id) }
            adventure.npcs.forEach { add(it.id) }
            adventure.items.forEach { add(it.id) }
            adventure.deductions.forEach { add(it.id) }
        }
        assertEquals(46, adventure.actions.size)
        adventure.actions.forEach { action ->
            val label = choiceLabel(action.id)
            assertTrue(label.isNotBlank())
            assertFalse(label.contains('_'))
            assertFalse(label in technicalIds)
        }
        assertEquals("Leer la carta de Elia", choiceLabel("read_elias_letter"))
        assertEquals(5, adventure.endings.size)
        adventure.endings.forEach { ending ->
            val title = endingTitle(ending.id)
            assertTrue(title.startsWith("Final: "))
            assertFalse(title.contains('_'))
            assertFalse(ending.id in title)
            assertFalse(title in technicalIds)
        }
        assertEquals("Final: la verdad queda publicada", endingTitle("justice"))
        assertEquals("Final: la caída acaba contigo", endingTitle("death"))
        assertEquals("Esa conclusión no se sostiene.", AdventureSession.CONCLUSION_REJECTED)
        assertFalse(AdventureSession.CONCLUSION_REJECTED.contains('_'))
    }
}

private suspend fun openLegacy(
    store: MemoryAdventureGameStore,
    backend: AdventureBackend?,
    die: Die = ScriptedDie(emptyList()),
): AdventureSession {
    val engine = AdventureEngine(valdumbria(), die)
    val saved = store.commit(
        state = engine.newGame(scholar()).copy(
            history = listOf(AdventureLog.ClockAdvanced(0, 0)),
        ),
        deathEndingId = engine.adventure.deathEndingId,
        midnightEventId = engine.adventure.clock.midnightEventId,
        expectedRevision = null,
        pendingNarration = null,
        sceneProse = emptyMap(),
        recentInteractions = emptyList(),
    )
    check(saved is AdventureCommit.Saved)
    val session = AdventureSession(engine, store, backend)
    session.restore()
    return session
}

private fun valdumbria() = loadAdventure(
    File("src/main/assets/adventures/valdumbria.json").readText(),
)

private fun engineOf() = AdventureEngine(valdumbria(), ScriptedDie(emptyList()))

private fun String.containsDeath(): Boolean {
    val folded = lowercase()
    return folded.contains("elia está muerta") ||
        folded.contains("elia is dead") ||
        folded.contains("asesin") ||
        folded.contains("cadáver") ||
        folded.contains("cadaver")
}

private class LanguageRecordingBackend : AdventureBackend {
    var interpretation: InterpretationRequest? = null
    var narration: NarrationRequest? = null
    var scene: SceneRequest? = null

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> {
        interpretation = request
        return BackendCall.Ready(
            InterpretationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                status = "RECOGNIZED",
                candidateActionId = "read_elias_letter",
                candidateActionIds = emptyList(),
            ),
        )
    }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        narration = request
        return BackendCall.Ready(
            NarrationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                outcome = request.resolvedAction.outcome,
                text = "Lees la carta.",
            ),
        )
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> {
        scene = request
        return BackendCall.Ready(
            SceneResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                text = "La plaza espera.",
            ),
        )
    }
}

private class SpoilingBackend : AdventureBackend {
    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        BackendCall.Failed("ABSENT", false)

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
        BackendCall.Ready(
            NarrationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                outcome = request.resolvedAction.outcome,
                text = "Elia is dead. You saved Ines before a second midnight.",
            ),
        )

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        BackendCall.Failed("ABSENT", false)
}
