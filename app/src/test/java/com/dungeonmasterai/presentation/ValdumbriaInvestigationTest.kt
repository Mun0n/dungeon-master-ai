package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.accepted
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.validateAdventure
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.dice.ScriptedDie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ValdumbriaInvestigationTest {
    @Test
    fun theProofPathSavesInesAndGrantsEveryProofAfterMidnight() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(listOf(7, 1, 1)))
        var state = engine.newGame(scholar())
        PROOF_BEFORE_EVIDENCE.forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        assertEquals(125, state.minutesElapsed)
        assertEquals("archive", state.locationId)
        assertTrue(state.midnightFired)
        assertEquals(1, state.history.count { it is AdventureLog.MidnightFired })
        assertEquals("saved", state.npcStates["ines"])
        assertEquals("absent", state.npcStates["alden"])
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(10, state.character.health)
        assertNull(state.endingId)
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })

        PROOF_EVIDENCE.forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        assertEquals(260, state.minutesElapsed)
        assertEquals("church", state.locationId)
        assertEquals(
            setOf("evidence_elia", "evidence_record", "evidence_crypt", "evidence_alden"),
            state.evidenceIds,
        )
        assertEquals("dead", state.npcStates["elia"])
        val stamp = state.worldStamp
        val deduced = engine.deduce(state, "name_the_crime") as AdventureResolution.Accepted
        assertEquals(260, deduced.state.minutesElapsed)
        assertEquals(stamp, deduced.state.worldStamp)
        assertEquals(true, deduced.state.flags["truth_known"])
        assertNull(deduced.check)
        assertEquals(setOf("name_the_crime"), deduced.state.deductionIds)

        val justice = accepted(engine, deduced.state, "publish_the_truth")
        val vengeance = accepted(engine, deduced.state, "take_vengeance")
        val silence = accepted(engine, deduced.state, "keep_the_secret")
        assertEquals("justice", justice.endingId)
        assertEquals("vengeance", vengeance.endingId)
        assertEquals("silence", silence.endingId)
        assertEquals("dead", justice.npcStates["elia"])
        listOf(justice, vengeance, silence).forEach { finished ->
            assertTerminal(engine, finished)
        }

        val unpublished = engine.resolve(state, "publish_the_truth")
        assertSame(state, unpublished.state)
        assertEquals(AdventureRejection.REQUIREMENTS_NOT_MET, (unpublished as AdventureResolution.Rejected).reason)
        assertNull(unpublished.state.endingId)
        setOf("evidence_record", "evidence_crypt", "evidence_elia", "evidence_alden").forEach { missing ->
            val partial = deduced.state.copy(evidenceIds = deduced.state.evidenceIds - missing)
            val blocked = engine.resolve(partial, "publish_the_truth")
            assertSame(partial, blocked.state)
            val also = engine.resolve(partial, "take_vengeance")
            assertSame(partial, also.state)
            val quiet = engine.resolve(partial, "keep_the_secret")
            assertSame(partial, quiet.state)
        }
    }

    @Test
    fun accusingOnePersonLeavesAnotherRoute() {
        val mara = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "go_to_tavern",
                "accuse_mara",
            ),
        )
        assertRejected(mara.first, mara.second, "mara_kept_the_packet")
        assertRejected(mara.first, mara.second, "sit_with_the_regulars")
        val elia = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "go_to_tavern",
                "accuse_mara",
                "leave_the_tavern",
                "follow_elias_directions",
                "enter_the_archive",
                "read_elias_notes",
            ),
        )
        assertTrue("evidence_elia" in elia.second.evidenceIds)

        val beltran = playValdumbria(
            emptyList(),
            listOf("read_elias_letter", "accuse_beltran"),
        )
        assertRejected(beltran.first, beltran.second, "speak_with_beltran")
        assertFalse("archive" in beltran.second.discoveredLocationIds)
        val record = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "accuse_beltran",
                "follow_elias_directions",
                "enter_the_archive",
                "read_elias_notes",
                "leave_the_archive",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_cemetery",
                "read_burial_list",
            ),
        )
        assertTrue("evidence_elia" in record.second.evidenceIds)
        assertTrue("evidence_record" in record.second.evidenceIds)

        val tomas = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_cemetery",
                "accuse_tomas",
            ),
        )
        assertRejected(tomas.first, tomas.second, "ask_tomas")
        val crypt = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_cemetery",
                "accuse_tomas",
                "follow_the_sinking_ground",
                "enter_the_crypt",
                "trace_the_scratches",
            ),
        )
        assertTrue("evidence_crypt" in crypt.second.evidenceIds)
        assertFalse(resolvedIds(crypt.second).contains("ask_tomas"))
    }

    @Test
    fun accusingMaraAndBeltranStillLeavesTheLetter() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        var state = engine.newGame(scholar())
        listOf("read_elias_letter", "go_to_tavern", "accuse_mara").forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        val blockedExit = engine.resolve(state, "go_to_plaza")
        assertSame(state, blockedExit.state)
        state = accepted(engine, state, "leave_the_tavern")
        state = accepted(engine, state, "accuse_beltran")
        assertFalse("archive" in state.discoveredLocationIds)
        assertRejected(engine, state, "mara_kept_the_packet")
        state = accepted(engine, state, "follow_elias_directions")
        assertTrue("archive" in state.discoveredLocationIds)
        assertTrue(state.evidenceIds.isEmpty())
        state = accepted(engine, state, "enter_the_archive")
        state = accepted(engine, state, "read_elias_notes")
        assertEquals(setOf("evidence_elia"), state.evidenceIds)
    }

    @Test
    fun accusingAllThreeBeforeTheyHelpStillGrantsTheFourProofs() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        var state = engine.newGame(scholar())
        listOf("read_elias_letter", "go_to_tavern", "accuse_mara").forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        assertSame(state, engine.resolve(state, "go_to_plaza").state)
        val working = engine.newGame(scholar())
        val inTavern = accepted(engine, accepted(engine, working, "read_elias_letter"), "go_to_tavern")
        assertSame(inTavern, engine.resolve(inTavern, "leave_the_tavern").state)

        state = accepted(engine, state, "leave_the_tavern")
        state = accepted(engine, state, "accuse_beltran")
        assertFalse("archive" in state.discoveredLocationIds)
        assertRejected(engine, state, "mara_kept_the_packet")
        listOf(
            "follow_elias_directions",
            "study_the_dark_tower",
            "go_to_tavern",
            "follow_the_path_behind_the_inn",
            "leave_the_tavern",
            "enter_the_cemetery",
            "accuse_tomas",
            "follow_the_sinking_ground",
        ).forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        assertEquals(125, state.minutesElapsed)
        assertEquals("lost", state.npcStates["ines"])
        assertTrue("crypt" in state.discoveredLocationIds)
        listOf(
            "enter_the_crypt",
            "trace_the_scratches",
            "return_to_the_cemetery",
            "read_burial_list",
            "leave_the_cemetery",
            "enter_the_archive",
            "read_elias_notes",
            "leave_the_archive",
            "enter_the_church",
            "take_the_hidden_list",
        ).forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        assertEquals(245, state.minutesElapsed)
        assertEquals(
            setOf("evidence_crypt", "evidence_record", "evidence_elia", "evidence_alden"),
            state.evidenceIds,
        )
        assertEquals("withdrawn", state.npcStates["mara"])
        assertEquals("withdrawn", state.npcStates["beltran"])
        assertEquals("withdrawn", state.npcStates["tomas"])
        assertEquals("lost", state.npcStates["ines"])
        val played = resolvedIds(state)
        assertFalse(played.contains("speak_with_beltran"))
        assertFalse(played.contains("mara_kept_the_packet"))
        assertFalse(played.contains("ask_tomas"))
        val deduced = engine.deduce(state, "name_the_crime") as AdventureResolution.Accepted
        assertEquals(true, deduced.state.flags["truth_known"])
        assertEquals(245, deduced.state.minutesElapsed)
    }

    @Test
    fun aRetryOpensOnlyWhenItsOwnFactChanges() {
        val early = playValdumbria(
            listOf(1),
            listOf(
                "read_elias_letter",
                "follow_elias_directions",
                "enter_the_archive",
                "compare_registers",
            ),
        )
        val failed = early.second.history.filterIsInstance<AdventureLog.ActionResolved>().last()
        assertEquals("compare_registers", failed.actionId)
        assertFalse(failed.success)
        val stamped = early.second
        val withProofs = stamped.copy(
            evidenceIds = setOf("evidence_record", "evidence_crypt", "evidence_elia", "evidence_alden"),
        )
        val stamp = withProofs.worldStamp
        val minutes = withProofs.minutesElapsed
        val deduced = early.first.deduce(withProofs, "name_the_crime") as AdventureResolution.Accepted
        assertEquals(stamp, deduced.state.worldStamp)
        assertEquals(minutes, deduced.state.minutesElapsed)
        assertRejected(early.first, deduced.state, "compare_registers")
        var unrelated = accepted(early.first, stamped, "leave_the_archive")
        unrelated = accepted(early.first, unrelated, "enter_the_archive")
        assertRejected(early.first, unrelated, "compare_registers")

        val opened = playValdumbria(
            listOf(1, 7, 7),
            listOf(
                "read_elias_letter",
                "follow_elias_directions",
                "enter_the_archive",
                "compare_registers",
                "leave_the_archive",
                "speak_with_beltran",
                "enter_the_archive",
            ),
        )
        val retried = opened.first.resolve(opened.second, "compare_registers")
        assertTrue(retried is AdventureResolution.Accepted)

        val beforeMidnight = playValdumbria(
            listOf(1, 11),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "study_the_dark_tower",
                "enter_the_church",
                "search_vestry",
            ),
        )
        assertRejected(beforeMidnight.first, beforeMidnight.second, "search_vestry")
        val late = beforeMidnight.second.copy(minutesElapsed = 110)
        val crossed = accepted(beforeMidnight.first, late, "leave_the_church")
        assertEquals(true, crossed.flags["alden_away"])
        val back = accepted(beforeMidnight.first, crossed, "enter_the_church")
        val searched = beforeMidnight.first.resolve(back, "search_vestry") as AdventureResolution.Accepted
        assertTrue(searched.success)
        assertTrue("evidence_alden" in searched.state.evidenceIds)

        val after = playValdumbria(
            listOf(1),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "warn_ines",
                "study_the_dark_tower",
            ),
        )
        val waiting = after.second.copy(minutesElapsed = 110)
        val inside = accepted(after.first, waiting, "enter_the_church")
        assertEquals("saved", inside.npcStates["ines"])
        val missed = after.first.resolve(inside, "search_vestry") as AdventureResolution.Accepted
        assertFalse(missed.success)
        val away = accepted(after.first, missed.state, "leave_the_church")
        val returned = accepted(after.first, away, "enter_the_church")
        assertRejected(after.first, returned, "search_vestry")
        val listed = accepted(after.first, returned, "take_the_hidden_list")
        assertTrue("evidence_alden" in listed.evidenceIds)
    }

    @Test
    fun eachRescueSavesInesOnARealClock() {
        val warned = playValdumbria(
            emptyList(),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "warn_ines",
                "study_the_dark_tower",
                "follow_elias_directions",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_archive",
                "read_elias_notes",
                "leave_the_archive",
                "enter_the_church",
            ),
        )
        assertEquals(120, warned.second.minutesElapsed)
        assertEquals(true, warned.second.flags["ines_protected"])
        assertEquals("saved", warned.second.npcStates["ines"])

        val barred = playValdumbria(
            listOf(14),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "study_the_dark_tower",
                "enter_the_church",
                "bar_the_vestry",
                "leave_the_church",
                "follow_elias_directions",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_archive",
                "leave_the_archive",
            ),
        )
        assertEquals(120, barred.second.minutesElapsed)
        assertEquals(true, barred.second.flags["ines_protected"])
        assertEquals("saved", barred.second.npcStates["ines"])

        val cut = playValdumbria(
            listOf(10),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_cemetery",
                "cut_alden_off",
                "read_burial_list",
                "leave_the_cemetery",
                "study_the_dark_tower",
                "follow_elias_directions",
                "enter_the_archive",
            ),
        )
        assertEquals(125, cut.second.minutesElapsed)
        assertEquals(true, cut.second.flags["ines_protected"])
        assertEquals("saved", cut.second.npcStates["ines"])
    }

    @Test
    fun aRescueThatItselfCrossesMidnightDoesNotSaveInes() {
        val lateBar = playValdumbria(
            listOf(14),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "study_the_dark_tower",
                "follow_elias_directions",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_archive",
                "read_elias_notes",
                "leave_the_archive",
                "enter_the_church",
                "bar_the_vestry",
            ),
        )
        assertEquals(125, lateBar.second.minutesElapsed)
        assertEquals(true, lateBar.second.flags["ines_protected"])
        assertEquals("lost", lateBar.second.npcStates["ines"])

        val lateCut = playValdumbria(
            listOf(10),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "go_to_tavern",
                "sit_with_the_regulars",
                "follow_the_path_behind_the_inn",
                "go_to_plaza",
                "enter_the_cemetery",
                "read_burial_list",
                "follow_the_sinking_ground",
                "leave_the_cemetery",
                "enter_the_cemetery",
                "cut_alden_off",
            ),
        )
        assertEquals(125, lateCut.second.minutesElapsed)
        assertEquals(true, lateCut.second.flags["ines_protected"])
        assertEquals("lost", lateCut.second.npcStates["ines"])
    }

    @Test
    fun aFailedBarCannotFetchThePinAndReturnBeforeMidnight() {
        val failed = playValdumbria(
            listOf(1),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "study_the_dark_tower",
                "enter_the_church",
                "bar_the_vestry",
                "leave_the_church",
                "go_to_tavern",
                "follow_the_path_behind_the_inn",
                "accuse_mara",
                "leave_the_tavern",
                "enter_the_cemetery",
                "ask_tomas",
            ),
        )
        val withPin = failed.second
        assertEquals(8, withPin.character.health)
        assertEquals(105, withPin.minutesElapsed)
        assertTrue(withPin.character.inventory.any { it.id == "iron_pin" })
        assertFalse(withPin.flags["ines_protected"] == true)
        assertRejected(failed.first, withPin, "bar_the_vestry")
        val returned = playValdumbria(
            listOf(1),
            listOf(
                "read_elias_letter",
                "speak_with_ines",
                "study_the_dark_tower",
                "enter_the_church",
                "bar_the_vestry",
                "leave_the_church",
                "go_to_tavern",
                "follow_the_path_behind_the_inn",
                "accuse_mara",
                "leave_the_tavern",
                "enter_the_cemetery",
                "ask_tomas",
                "leave_the_cemetery",
                "enter_the_church",
            ),
        )
        assertEquals(125, returned.second.minutesElapsed)
        assertEquals("lost", returned.second.npcStates["ines"])
        assertRejected(returned.first, returned.second, "bar_the_vestry")
        assertEquals("dead", returned.second.npcStates["elia"])
    }

    @Test
    fun repeatableTravelCanEnterTheChurchTwice() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        var state = engine.newGame(scholar())
        listOf(
            "read_elias_letter",
            "speak_with_ines",
            "warn_ines",
            "study_the_dark_tower",
        ).forEach { actionId ->
            state = accepted(engine, state, actionId)
        }
        state = accepted(engine, state.copy(minutesElapsed = 110), "enter_the_church")
        assertEquals("saved", state.npcStates["ines"])
        state = accepted(engine, state, "leave_the_church")
        state = accepted(engine, state, "enter_the_church")
        state = accepted(engine, state, "take_the_hidden_list")
        assertTrue("evidence_alden" in state.evidenceIds)
        assertEquals("church", state.locationId)

        val travel = valdumbriaDocument().actions.single { it.id == "go_to_tavern" }
        val illegal = travel.copy(
            id = "grant_while_moving",
            success = travel.success.copy(
                effects = listOf(Effect.AddEvidence(evidenceId = "evidence_elia")),
            ),
        )
        val errors = validateAdventure(
            valdumbriaDocument().copy(actions = valdumbriaDocument().actions + illegal),
        )
        assertTrue(errors.any { it.contains("repeatable") && it.contains("MOVE_TO") })
    }

    @Test
    fun failureAndDeathAreTerminalAndEliaStaysDead() {
        val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(listOf(1)))
        val left = accepted(engine, engine.newGame(scholar()), "read_elias_letter")
        val failure = accepted(engine, left, "leave_valdumbria")
        assertEquals("failure", failure.endingId)
        assertEquals(10, failure.character.health)
        assertEquals(5, failure.minutesElapsed)
        assertTerminal(engine, failure)

        var crypt = engine.newGame(scholar())
        listOf(
            "read_elias_letter",
            "go_to_tavern",
            "sit_with_the_regulars",
            "follow_the_path_behind_the_inn",
            "go_to_plaza",
            "enter_the_cemetery",
            "follow_the_sinking_ground",
            "enter_the_crypt",
        ).forEach { actionId ->
            crypt = accepted(engine, crypt, actionId)
        }
        val death = engine.resolve(crypt, "descend_the_open_drop") as AdventureResolution.Accepted
        assertEquals("death", death.state.endingId)
        assertEquals(0, death.state.character.health)
        assertEquals("dead", death.state.npcStates["elia"])
        assertTerminal(engine, death.state)
    }

    private fun assertTerminal(engine: AdventureEngine, state: AdventureState) {
        val action = engine.resolve(state, "look_over_the_square")
        assertSame(state, action.state)
        assertEquals(AdventureRejection.TERMINAL, (action as AdventureResolution.Rejected).reason)
        val deduced = engine.deduce(state, "name_the_crime")
        assertSame(state, deduced.state)
        assertEquals(AdventureRejection.TERMINAL, (deduced as AdventureResolution.Rejected).reason)
    }

    private fun assertRejected(engine: AdventureEngine, state: AdventureState, actionId: String) {
        val rejected = engine.resolve(state, actionId)
        assertSame(state, rejected.state)
        assertTrue(rejected is AdventureResolution.Rejected)
    }

    private fun resolvedIds(state: AdventureState): List<String> =
        state.history.filterIsInstance<AdventureLog.ActionResolved>().map { it.actionId }
}
