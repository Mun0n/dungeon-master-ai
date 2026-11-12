package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.engine.ActionAccess
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.FEATURED_LIMIT
import com.dungeonmasterai.domain.adventure.engine.canBeAttempted
import com.dungeonmasterai.domain.adventure.engine.decisionOffer
import com.dungeonmasterai.domain.adventure.engine.offersConclusion
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.narration.narrationView
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionOfferTest {
    private val adventure = loadAdventure(File("src/main/assets/adventures/valdumbria.json").readText())

    @Test
    fun theOpeningShowsTheLetterAndDoesNotInventASecondChoice() {
        val engine = engine()
        val state = engine.newGame(scholar())
        val offer = offer(engine, state)
        assertEquals(listOf("read_elias_letter"), offer.actionIds)
        assertTrue(offer.freeAction)
        assertTrue(offer.actionIds.size <= FEATURED_LIMIT)
    }

    @Test
    fun afterTheLetterThePageIsTheSituationAndNotTheFirstThreeActions() {
        val engine = engine()
        val state = play(engine, "read_elias_letter")
        val available = available(engine, state)
        val offer = offer(engine, state)
        assertEquals(
            listOf(
                "speak_with_ines",
                "study_the_dark_tower",
                "follow_elias_directions",
                "go_to_tavern",
            ),
            offer.actionIds,
        )
        assertFalse(offer.actionIds.contains("look_over_the_square"))
        assertFalse(offer.actionIds.contains("note_suspicion"))
        assertFalse(offer.actionIds.contains("accuse_beltran"))
        assertFalse(offer.actionIds.contains("leave_valdumbria"))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "leave_valdumbria"))
        assertEquals(available.take(3), listOf("speak_with_ines", "look_over_the_square", "go_to_tavern"))
        assertFalse(offer.actionIds == available.take(3))
        assertTrue(available.containsAll(offer.actionIds))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "look_over_the_square"))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "follow_elias_directions"))
        val view = checkNotNull(engine.narrationView(state, emptyList()))
        assertEquals(available, view.availableActions.map { it.id })
    }

    @Test
    fun theTavernShowsOneRumorOnePathAndTheLinen() {
        val engine = engine()
        val state = play(engine, "read_elias_letter", "go_to_tavern")
        val offer = offer(engine, state)
        assertEquals(
            listOf("ask_mara", "follow_the_path_behind_the_inn", "ask_mara_for_linen"),
            offer.actionIds,
        )
        assertFalse(offer.actionIds.contains("sit_with_the_regulars"))
        assertFalse(offer.actionIds.contains("accuse_mara"))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "sit_with_the_regulars"))
        assertTrue(offer.freeAction)
    }

    @Test
    fun aRescueStaysVisibleWhenThreeDoorsAreAlreadyOpen() {
        val engine = engine()
        val state = play(
            engine,
            "read_elias_letter",
            "speak_with_ines",
            "study_the_dark_tower",
            "follow_elias_directions",
            "go_to_tavern",
            "sit_with_the_regulars",
            "follow_the_path_behind_the_inn",
            "go_to_plaza",
        )
        val offer = offer(engine, state)
        assertEquals(
            listOf(
                "warn_ines",
                "enter_the_church",
                "enter_the_archive",
                "enter_the_cemetery",
            ),
            offer.actionIds,
        )
        assertEquals(FEATURED_LIMIT, offer.actionIds.size)
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "speak_with_beltran"))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "leave_valdumbria"))
        assertTrue(offer.freeAction)
    }

    @Test
    fun theArchiveShowsEveryActionWhenTheSceneForbidsAWrittenOne() {
        val engine = engine()
        val state = play(engine, "read_elias_letter", "follow_elias_directions", "enter_the_archive")
        val contract = adventure.sceneContracts.single { it.locationId == "archive" }
        assertFalse(contract.freeAction)
        val offer = offer(engine, state)
        assertEquals(
            listOf("read_elias_notes", "compare_registers", "leave_the_archive"),
            offer.actionIds,
        )
        assertFalse(offer.freeAction)
    }

    @Test
    fun anOfferedConclusionOpensTheWrittenActionWithoutHidingTheArchive() {
        val engine = engine()
        val state = play(engine, "read_elias_letter", "follow_elias_directions", "enter_the_archive")
            .copy(
                evidenceIds = setOf(
                    "evidence_record",
                    "evidence_crypt",
                    "evidence_elia",
                    "evidence_alden",
                ),
            )
        assertTrue(adventure.deductions.single().offersConclusion(state))
        val available = available(engine, state)
        val offer = decisionOffer(
            adventure = adventure,
            state = state,
            availableActionIds = available,
            sceneAllowsFreeAction = false,
            conclusionOffered = true,
        )
        assertEquals(available, offer.actionIds)
        assertTrue(offer.freeAction)
    }

    @Test
    fun theCryptShowsOneProofTheDropAndTheWayBack() {
        val engine = engine()
        val state = play(
            engine,
            "read_elias_letter",
            "go_to_tavern",
            "sit_with_the_regulars",
            "follow_the_path_behind_the_inn",
            "go_to_plaza",
            "enter_the_cemetery",
            "follow_the_sinking_ground",
            "enter_the_crypt",
        )
        val offer = offer(engine, state)
        assertEquals(
            listOf("trace_the_scratches", "descend_the_open_drop", "return_to_the_cemetery"),
            offer.actionIds,
        )
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "examine_sealed_niche"))
        assertEquals(ActionAccess.AVAILABLE, engine.access(state, "break_the_seal"))
        assertTrue(offer.freeAction)
    }

    @Test
    fun theSolvedChurchShowsTheThreeEndingsAndTheDoor() {
        val engine = engine()
        val started = engine.newGame(scholar())
        val ready = started.copy(
            locationId = "church",
            evidenceIds = setOf(
                "evidence_record",
                "evidence_crypt",
                "evidence_elia",
                "evidence_alden",
            ),
            discoveredLocationIds = started.discoveredLocationIds + "church",
            minutesElapsed = 130,
            midnightFired = true,
        )
        val deduced = engine.deduce(ready, "name_the_crime") as AdventureResolution.Accepted
        val offer = offer(engine, deduced.state)
        assertEquals(
            listOf(
                "publish_the_truth",
                "take_vengeance",
                "keep_the_secret",
                "leave_the_church",
            ),
            offer.actionIds,
        )
        assertEquals(ActionAccess.AVAILABLE, engine.access(deduced.state, "search_vestry"))
        assertFalse(offer.actionIds.contains("search_vestry"))
    }

    @Test
    fun aFailedRegisterCheckDoesNotHideTheBurialList() {
        val engine = AdventureEngine(adventure, ScriptedDie(listOf(1)))
        val state = play(
            engine,
            "read_elias_letter",
            "follow_elias_directions",
            "go_to_tavern",
            "sit_with_the_regulars",
            "follow_the_path_behind_the_inn",
            "go_to_plaza",
            "enter_the_archive",
            "compare_registers",
            "leave_the_archive",
        )
        assertFalse(adventure.actions.single { it.id == "compare_registers" }.canBeAttempted(state))
        val offer = offer(engine, state)
        assertTrue(offer.actionIds.toString(), offer.actionIds.contains("enter_the_cemetery"))
        assertTrue(offer.actionIds.size <= FEATURED_LIMIT)
    }

    @Test
    fun aWithdrawnMaraDoesNotHideTheArchiveNotes() {
        val engine = engine()
        val state = play(
            engine,
            "read_elias_letter",
            "follow_elias_directions",
            "go_to_tavern",
            "accuse_mara",
            "leave_the_tavern",
        )
        assertEquals("withdrawn", state.npcStates["mara"])
        val offer = offer(engine, state)
        assertTrue(offer.actionIds.toString(), offer.actionIds.contains("enter_the_archive"))
        assertTrue(offer.actionIds.size <= FEATURED_LIMIT)
    }

    @Test
    fun featuredButtonsStillReachAConclusionAndAnEnding() {
        val engine = AdventureEngine(adventure, ScriptedDie(List(30) { 20 }))
        var state = engine.newGame(scholar())
        val taken = mutableListOf<String>()
        var steps = 0
        while (steps < 40 && !adventure.deductions.any { it.offersConclusion(state) }) {
            val offer = offer(engine, state)
            assertTrue(offer.actionIds.isNotEmpty())
            assertTrue(taken.toString(), offer.actionIds.size <= FEATURED_LIMIT)
            assertEquals(offer.actionIds, offer.actionIds.distinct())
            val next = pick(offer.actionIds, concluding = false)
            taken += next
            state = accepted(engine, state, next)
            steps += 1
        }
        assertTrue(taken.toString(), adventure.deductions.single().offersConclusion(state))
        state = (engine.deduce(state, "name_the_crime") as AdventureResolution.Accepted).state
        steps = 0
        while (steps < 12 && state.endingId == null) {
            val offer = offer(engine, state)
            assertTrue(offer.freeAction)
            val next = pick(offer.actionIds, concluding = true)
            taken += next
            state = accepted(engine, state, next)
            steps += 1
        }
        assertEquals(taken.toString(), "justice", state.endingId)
    }

    private fun pick(ids: List<String>, concluding: Boolean): String {
        if (concluding) ids.firstOrNull { it == "publish_the_truth" }?.let { return it }
        val skip = setOf(
            "leave_valdumbria",
            "descend_the_open_drop",
            "note_suspicion",
            "accuse_beltran",
            "accuse_mara",
            "accuse_tomas",
            "accuse_alden_early",
            "take_vengeance",
            "keep_the_secret",
        )
        return ids.firstOrNull { it !in skip } ?: ids.first()
    }

    private fun offer(engine: AdventureEngine, state: AdventureState) = decisionOffer(
        adventure = adventure,
        state = state,
        availableActionIds = available(engine, state),
        sceneAllowsFreeAction = adventure.sceneContracts.single { it.locationId == state.locationId }.freeAction,
        conclusionOffered = adventure.deductions.any { it.offersConclusion(state) },
    )

    private fun available(engine: AdventureEngine, state: AdventureState): List<String> {
        val contract = adventure.sceneContracts.single { it.locationId == state.locationId }
        return contract.allowedActionIds.filter { engine.access(state, it) == ActionAccess.AVAILABLE }
    }

    private fun play(engine: AdventureEngine, vararg steps: String): AdventureState =
        steps.fold(engine.newGame(scholar())) { current, actionId -> accepted(engine, current, actionId) }

    private fun engine(): AdventureEngine = AdventureEngine(adventure, ScriptedDie(emptyList()))
}
