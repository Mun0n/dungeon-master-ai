package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.model.OutcomeDefinition
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureRejection
import com.dungeonmasterai.domain.dice.ScriptedDie
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.clockFace
import com.dungeonmasterai.domain.character.Character
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureEngineTest {
    @Test
    fun clockStartsAtTwentyTwoHundred() {
        assertEquals(22, clockFace(0).hour)
        assertEquals(0, clockFace(0).minute)
        assertEquals(23, clockFace(100).hour)
        assertEquals(40, clockFace(100).minute)
        assertEquals(0, clockFace(120).hour)
        assertEquals(0, clockFace(120).minute)
        assertEquals(0, clockFace(130).hour)
        assertEquals(10, clockFace(130).minute)
    }

    @Test
    fun newGameUsesTheClassAttributesAndTheStartingItem() {
        val state = engine().newGame(scholar())

        assertEquals(5, state.character.attributes.intelligence)
        assertEquals(2, state.character.attributes.strength)
        assertEquals(2, state.character.attributes.agility)
        assertEquals(10, state.character.health)
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
        assertEquals("yard", state.locationId)
        assertEquals(0, state.minutesElapsed)
        assertFalse(state.midnightFired)
        assertNull(state.endingId)
    }

    @Test
    fun newGameRejectsADeadCharacter() {
        assertThrows(IllegalArgumentException::class.java) {
            engine().newGame(scholar().copy(health = 0))
        }
    }

    @Test
    fun unknownActionDoesNotChangeStateOrRoll() {
        val running = engine(rolls = listOf(20))
        val state = running.newGame(scholar())

        val rejected = running.resolve(state, "no_such_action")

        assertSame(state, rejected.state)
        assertEquals(AdventureRejection.UNKNOWN_ACTION, (rejected as AdventureResolution.Rejected).reason)
        val climbed = accepted(running, state, "reckless_climb")
        assertTrue(climbed.flags["climbed"] == true)
        assertEquals(10, climbed.character.health)
    }

    @Test
    fun wrongLocationDoesNotChangeStateOrRoll() {
        val running = engine(rolls = listOf(20))
        val state = running.newGame(scholar())
        val waiting = accepted(running, state, "wait_long")

        val rejected = running.resolve(waiting, "dust_mark")

        assertSame(waiting, rejected.state)
        assertEquals(AdventureRejection.WRONG_LOCATION, (rejected as AdventureResolution.Rejected).reason)
        assertEquals(100, waiting.minutesElapsed)
        assertFalse(waiting.midnightFired)
        val climbed = running.resolve(waiting, "reckless_climb") as AdventureResolution.Accepted
        assertEquals(22, climbed.check?.total)
        assertTrue(climbed.check?.success == true)
    }

    @Test
    fun missingRequirementDoesNotSpendTime() {
        val running = engine()
        var state = running.newGame(scholar())
        state = accepted(running, state, "search_yard")
        state = accepted(running, state, "open_hall")
        val minutes = state.minutesElapsed

        val rejected = running.resolve(state, "trade_notes")

        assertSame(state, rejected.state)
        assertEquals(AdventureRejection.REQUIREMENTS_NOT_MET, (rejected as AdventureResolution.Rejected).reason)
        assertEquals(minutes, rejected.state.minutesElapsed)
        assertFalse("proof_notes" in rejected.state.evidenceIds)
    }

    @Test
    fun failedCheckDoesNotGrantEvidenceAndDoesSpendTime() {
        val running = engine(rolls = listOf(1))
        var state = running.newGame(scholar())
        state = accepted(running, state, "search_yard")
        state = accepted(running, state, "open_hall")
        val before = state.minutesElapsed

        val resolved = running.resolve(state, "read_archive")

        val accepted = resolved as AdventureResolution.Accepted
        assertFalse(accepted.success)
        assertEquals(6, accepted.check?.total)
        assertEquals(12, accepted.check?.difficulty)
        assertEquals(before + 20, accepted.state.minutesElapsed)
        assertFalse("proof_archive" in accepted.state.evidenceIds)
        assertEquals(state.character.health, accepted.state.character.health)
    }

    @Test
    fun equalTotalSucceedsAndALowerTotalFails() {
        val success = engine(rolls = listOf(6))
        var state = success.newGame(scholar())
        state = accepted(success, state, "search_yard")
        state = accepted(success, state, "open_hall")
        state = accepted(success, state, "descend")
        val marked = success.resolve(state, "dust_mark") as AdventureResolution.Accepted
        assertEquals(8, marked.check?.total)
        assertTrue(marked.success)
        assertTrue("proof_mark" in marked.state.evidenceIds)

        val failure = engine(rolls = listOf(5))
        var other = failure.newGame(scholar())
        other = accepted(failure, other, "search_yard")
        other = accepted(failure, other, "open_hall")
        other = accepted(failure, other, "descend")
        val missed = failure.resolve(other, "dust_mark") as AdventureResolution.Accepted
        assertEquals(7, missed.check?.total)
        assertFalse(missed.success)
        assertFalse("proof_mark" in missed.state.evidenceIds)
    }

    @Test
    fun theSameRollChangesWithTheAttribute() {
        val strong = engine(rolls = listOf(12))
        val warriorState = strong.resolve(strong.newGame(warrior()), "force_hatch") as AdventureResolution.Accepted
        assertEquals(16, warriorState.check?.total)
        assertTrue(warriorState.success)
        assertTrue(warriorState.state.flags["hatch_open"] == true)

        val slight = engine(rolls = listOf(12))
        val scholarState = slight.resolve(slight.newGame(scholar()), "force_hatch") as AdventureResolution.Accepted
        assertEquals(14, scholarState.check?.total)
        assertFalse(scholarState.success)
        assertEquals(9, scholarState.state.character.health)
        assertFalse(scholarState.state.flags["hatch_open"] == true)
    }

    @Test
    fun failedAttemptDoesNotRetryUntilTheWorldChanges() {
        val running = engine(rolls = listOf(1, 12))
        val started = running.newGame(warrior())
        val failed = running.resolve(started, "force_hatch") as AdventureResolution.Accepted
        assertFalse(failed.success)
        assertEquals(9, failed.state.character.health)

        assertEquals(started.worldStamp, failed.state.worldStamp)
        val closed = running.resolve(failed.state, "force_hatch")
        assertSame(failed.state, closed.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (closed as AdventureResolution.Rejected).reason)
        assertEquals(9, closed.state.character.health)

        val noted = accepted(running, failed.state, "ring_bell")
        assertTrue(noted.worldStamp > failed.state.worldStamp)
        val stillClosed = running.resolve(noted, "force_hatch")
        assertSame(noted, stillClosed.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (stillClosed as AdventureResolution.Rejected).reason)

        val keyed = accepted(running, noted, "search_yard")
        val opened = running.resolve(keyed, "force_hatch") as AdventureResolution.Accepted
        assertTrue(opened.success)
        assertEquals(16, opened.check?.total)
        assertTrue(opened.state.flags["hatch_open"] == true)
        assertEquals(9, opened.state.character.health)
    }

    @Test
    fun aConditionAlreadyTrueDoesNotReopenTheAttempt() {
        val running = engine(rolls = listOf(1))
        var state = running.newGame(scholar())
        state = accepted(running, state, "search_yard")
        state = accepted(running, state, "open_hall")
        val stamp = state.worldStamp
        val failed = running.resolve(state, "read_archive") as AdventureResolution.Accepted
        assertFalse(failed.success)
        assertEquals(stamp, failed.state.worldStamp)
        assertTrue("clue_bell" in failed.state.clueIds)
        assertTrue(failed.state.attempts.getValue("read_archive").relevantFacts.isNotEmpty())

        val closed = running.resolve(failed.state, "read_archive")
        assertSame(failed.state, closed.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (closed as AdventureResolution.Rejected).reason)

        val changed = accepted(running, failed.state, "ask_clerk")
        val stillClosed = running.resolve(changed, "read_archive")
        assertSame(changed, stillClosed.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (stillClosed as AdventureResolution.Rejected).reason)

        val copied = accepted(running, changed, "copy_archive")
        assertTrue("proof_archive" in copied.evidenceIds)
    }

    @Test
    fun anEmptyRetryProjectionStaysClosed() {
        val running = engine(rolls = listOf(1))
        val failed = running.resolve(running.newGame(warrior()), "force_hatch") as AdventureResolution.Accepted
        val keyed = accepted(running, failed.state, "search_yard")
        val stripped = keyed.copy(
            attempts = keyed.attempts + (
                "force_hatch" to keyed.attempts.getValue("force_hatch").copy(relevantFacts = "")
                ),
        )
        val closed = running.resolve(stripped, "force_hatch")
        assertSame(stripped, closed.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (closed as AdventureResolution.Rejected).reason)
    }

    @Test
    fun sealingAnActionStaysClosedAfterTheWorldChanges() {
        val running = engine(rolls = listOf(20))
        var state = running.newGame(warrior())
        state = accepted(running, state, "search_yard")
        state = accepted(running, state, "seal_hatch")
        state = accepted(running, state, "ring_bell")
        assertTrue(state.character.inventory.any { it.id == "brass_key" })

        val rejected = running.resolve(state, "force_hatch")

        assertSame(state, rejected.state)
        assertEquals(AdventureRejection.RETRY_CLOSED, (rejected as AdventureResolution.Rejected).reason)
        assertEquals(10, rejected.state.character.health)
    }

    @Test
    fun anEventFiresOnceWhenTwoActionsTriggerIt() {
        val running = engine()
        var state = running.newGame(scholar())
        state = accepted(running, state, "ring_bell")
        assertEquals(setOf("bell_note"), state.firedEventIds)
        assertEquals(setOf("clue_name"), state.clueIds)

        state = accepted(running, state, "ring_bell_again")

        assertEquals(setOf("bell_note"), state.firedEventIds)
        assertEquals(setOf("clue_name"), state.clueIds)
    }

    @Test
    fun healingAtFullHealthStaysAtTen() {
        val running = engine()
        var state = running.newGame(warrior())
        state = accepted(running, state, "find_bandage")
        val capped = accepted(running, state, "bind_wound")

        assertEquals(10, capped.character.health)
        assertFalse(capped.character.inventory.any { it.id == "bandage" })
    }

    @Test
    fun healingAfterDamageStopsAtTen() {
        val running = engine(rolls = listOf(1))
        var state = running.newGame(warrior())
        val hurt = running.resolve(state, "force_hatch") as AdventureResolution.Accepted
        assertEquals(9, hurt.state.character.health)
        state = accepted(running, hurt.state, "find_bandage")
        val healed = accepted(running, state, "bind_wound")

        assertEquals(10, healed.character.health)
        assertFalse(healed.character.inventory.any { it.id == "bandage" })
    }

    @Test
    fun aFullInventoryRejectsTheWholeAction() {
        val running = engine()
        val state = running.newGame(scholar())

        val overfilled = running.resolve(state, "overfill")
        assertSame(state, overfilled.state)
        assertEquals(AdventureRejection.INVENTORY_FULL, (overfilled as AdventureResolution.Rejected).reason)
        assertEquals(listOf(Character.COIN_PURSE_ID), overfilled.state.character.inventory.map { it.id })

        val filled = accepted(running, state, "fill_pack")
        assertEquals(Character.MAX_INVENTORY, filled.character.inventory.size)
        val extra = running.resolve(filled, "find_bandage")
        assertSame(filled, extra.state)
        assertEquals(AdventureRejection.INVENTORY_FULL, (extra as AdventureResolution.Rejected).reason)
    }

    @Test
    fun aDuplicateItemRejectsTheAction() {
        val running = engine()
        var state = running.newGame(scholar())
        state = accepted(running, state, "find_bandage")

        val rejected = running.resolve(state, "find_bandage_again")

        assertSame(state, rejected.state)
        assertEquals(AdventureRejection.DUPLICATE_ITEM, (rejected as AdventureResolution.Rejected).reason)
    }

    @Test
    fun midnightFiresOnceWhenTheClockCrossesIt() {
        val running = engine()
        var state = running.newGame(scholar())
        val stamp = state.worldStamp
        state = accepted(running, state, "wait_long")
        assertEquals(stamp, state.worldStamp)
        assertEquals(100, state.minutesElapsed)
        assertFalse(state.midnightFired)
        assertEquals("watching", state.npcStates["warden"])

        state = accepted(running, state, "wait_short")
        assertEquals(130, state.minutesElapsed)
        assertTrue(state.midnightFired)
        assertEquals(setOf("bells"), state.firedEventIds)
        assertEquals("lost", state.npcStates["warden"])
        assertEquals(0, clockFace(state.minutesElapsed).hour)
        assertEquals(10, clockFace(state.minutesElapsed).minute)

        val after = accepted(running, state, "look_around")
        assertEquals(135, after.minutesElapsed)
        assertTrue(after.midnightFired)
        assertEquals(setOf("bells"), after.firedEventIds)
        assertEquals("lost", after.npcStates["warden"])
    }

    @Test
    fun aRejectedActionDoesNotFireMidnight() {
        val running = engine()
        var state = running.newGame(scholar())
        state = accepted(running, state, "wait_long")

        val rejected = running.resolve(state, "dust_mark")

        assertSame(state, rejected.state)
        assertFalse(rejected.state.midnightFired)
        assertEquals(100, rejected.state.minutesElapsed)
    }

    @Test
    fun sparingTheWardenSelectsTheOtherMidnightListOnce() {
        val running = engine()
        var state = running.newGame(scholar())
        state = accepted(running, state, "warn_keeper")
        assertTrue(state.flags["keeper_spared"] == true)
        assertEquals("watching", state.npcStates["warden"])
        assertFalse(state.midnightFired)

        state = accepted(running, state, "wait_past")
        assertEquals(150, state.minutesElapsed)
        assertTrue(state.midnightFired)
        assertEquals("spared", state.npcStates["warden"])
        assertEquals(setOf("bells"), state.firedEventIds)

        val later = accepted(running, state, "look_around")
        assertEquals("spared", later.npcStates["warden"])
        assertEquals(setOf("bells"), later.firedEventIds)
    }

    @Test
    fun deductionRequiresAllFourProofsAndDoesNotSpendTime() {
        val running = engine(rolls = listOf(6, 10))
        val started = running.newGame(scholar())
        val tooSoon = running.deduce(started, "named_conclusion")
        assertSame(started, tooSoon.state)
        assertEquals(AdventureRejection.DEDUCTION_REQUIREMENTS, (tooSoon as AdventureResolution.Rejected).reason)

        var partial = running.newGame(scholar())
        listOf(
            "search_yard",
            "open_hall",
            "copy_archive",
            "ask_clerk",
            "trade_notes",
            "descend",
            "dust_mark",
        ).forEach { actionId ->
            partial = accepted(running, partial, actionId)
        }
        val missingOne = running.deduce(partial, "named_conclusion")
        assertSame(partial, missingOne.state)
        assertEquals(
            AdventureRejection.DEDUCTION_REQUIREMENTS,
            (missingOne as AdventureResolution.Rejected).reason,
        )
        partial = accepted(running, partial, "pry_cellar")
        partial = accepted(running, partial, "return_hall")
        val ready = partial
        val minutes = ready.minutesElapsed
        val deduced = running.deduce(ready, "named_conclusion") as AdventureResolution.Accepted
        assertEquals(minutes, deduced.state.minutesElapsed)
        assertEquals(ready.worldStamp, deduced.state.worldStamp)
        assertTrue("named_conclusion" in deduced.state.deductionIds)
        assertTrue(deduced.state.flags["truth_known"] == true)
        assertNull(deduced.check)

        val repeat = running.deduce(deduced.state, "named_conclusion")
        assertSame(deduced.state, repeat.state)
        assertEquals(AdventureRejection.ALREADY_DEDUCED, (repeat as AdventureResolution.Rejected).reason)

        val unknown = running.deduce(deduced.state, "other_conclusion")
        assertSame(deduced.state, unknown.state)
        assertEquals(AdventureRejection.UNKNOWN_DEDUCTION, (unknown as AdventureResolution.Rejected).reason)
    }

    @Test
    fun publishWithoutTheDeductionDoesNotChangeState() {
        val running = engine(rolls = listOf(6, 10))
        val ready = investigate(running)

        val rejected = running.resolve(ready, "publish_truth")

        assertSame(ready, rejected.state)
        assertEquals(AdventureRejection.REQUIREMENTS_NOT_MET, (rejected as AdventureResolution.Rejected).reason)
        assertNull(rejected.state.endingId)
    }

    @Test
    fun eachEndingBlocksTheNextAction() {
        val justice = ending("justice")
        val vengeance = ending("vengeance")
        val silence = ending("silence")
        val failure = ending("failure")
        val death = ending("death")

        mapOf(
            "justice" to justice,
            "vengeance" to vengeance,
            "silence" to silence,
            "failure" to failure,
            "death" to death,
        ).forEach { (endingId, finished) ->
            assertEquals(endingId, finished.state.endingId)
            val rejected = finished.engine.resolve(finished.state, "look_around")
            assertSame(finished.state, rejected.state)
            assertEquals(AdventureRejection.TERMINAL, (rejected as AdventureResolution.Rejected).reason)
            val deduced = finished.engine.deduce(finished.state, "named_conclusion")
            assertSame(finished.state, deduced.state)
            assertEquals(AdventureRejection.TERMINAL, (deduced as AdventureResolution.Rejected).reason)
        }
    }

    @Test
    fun damageLargerThanRemainingLifeStopsAtZero() {
        val running = engine(rolls = listOf(1, 1))
        var state = running.newGame(warrior())
        state = (running.resolve(state, "force_hatch") as AdventureResolution.Accepted).state
        assertEquals(9, state.character.health)

        val dead = running.resolve(state, "reckless_climb") as AdventureResolution.Accepted

        assertEquals(0, dead.state.character.health)
        assertEquals("death", dead.state.endingId)
    }

    @Test
    fun deathDuringANestedEventStopsTheResolution() {
        val running = engineFrom { adventure ->
            adventure.copy(
                events = adventure.events.map { event ->
                    if (event.id != "bell_note") {
                        event
                    } else {
                        event.copy(
                            effects = listOf(
                                Effect.AddClue(clueId = "clue_name"),
                                Effect.SetFlag(key = "door_open", value = true),
                                Effect.ChangeLife(amount = -10),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                                Effect.AddItem(itemId = "bandage"),
                                Effect.ChangeLife(amount = 4),
                                Effect.SetNpcState(npcId = "warden", state = "lost"),
                            ),
                        )
                    }
                },
                actions = adventure.actions.map { action ->
                    if (action.id != "ring_bell") {
                        action
                    } else {
                        action.copy(
                            success = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "climbed", value = true),
                                    Effect.TriggerEvent(eventId = "bell_note"),
                                    Effect.SetFlag(key = "hatch_open", value = true),
                                    Effect.AddItem(itemId = "brass_key"),
                                    Effect.AddEvidence(evidenceId = "proof_crypt"),
                                    Effect.ChangeLife(amount = 4),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val state = accepted(running, running.newGame(scholar()), "ring_bell")

        assertEquals("death", state.endingId)
        assertEquals(0, state.character.health)
        assertEquals(setOf("clue_name"), state.clueIds)
        assertTrue(state.flags["door_open"] == true)
        assertTrue(state.flags["climbed"] == true)
        assertFalse(state.flags["hatch_open"] == true)
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
        assertEquals("watching", state.npcStates["warden"])
        assertEquals(setOf("bell_note"), state.firedEventIds)
        assertTrue(state.history.any { it is AdventureLog.EndingReached })
    }

    @Test
    fun midnightDeathStopsTheActionEffects() {
        val running = engineFrom { adventure ->
            adventure.copy(
                events = adventure.events.map { event ->
                    if (event.id != "bells") {
                        event
                    } else {
                        event.copy(
                            whenFlag = null,
                            whenFlagEffects = emptyList(),
                            effects = listOf(
                                Effect.SetNpcState(npcId = "warden", state = "lost"),
                                Effect.ChangeLife(amount = -10),
                                Effect.SetFlag(key = "hatch_open", value = true),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                                Effect.ChangeLife(amount = 4),
                                Effect.SetEnding(endingId = "justice"),
                            ),
                        )
                    }
                },
                actions = adventure.actions.map { action ->
                    if (action.id != "wait_past") {
                        action
                    } else {
                        action.copy(
                            success = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "climbed", value = true),
                                    Effect.AddItem(itemId = "bandage"),
                                    Effect.ChangeLife(amount = 4),
                                    Effect.AddEvidence(evidenceId = "proof_crypt"),
                                    Effect.SetEnding(endingId = "justice"),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val state = accepted(running, running.newGame(scholar()), "wait_past")

        assertEquals("death", state.endingId)
        assertEquals(0, state.character.health)
        assertTrue(state.midnightFired)
        assertEquals("lost", state.npcStates["warden"])
        assertFalse(state.flags["climbed"] == true)
        assertFalse(state.flags["hatch_open"] == true)
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
        assertEquals(130, state.minutesElapsed)
    }

    @Test
    fun cumulativeDamageStopsAtZeroAndDropsLaterEffects() {
        val running = engineFrom(rolls = listOf(1)) { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    if (action.id != "reckless_climb") {
                        action
                    } else {
                        action.copy(
                            failure = OutcomeDefinition(
                                effects = listOf(
                                    Effect.ChangeLife(amount = -6),
                                    Effect.ChangeLife(amount = -6),
                                    Effect.ChangeLife(amount = 4),
                                    Effect.AddItem(itemId = "bandage"),
                                    Effect.AddEvidence(evidenceId = "proof_archive"),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val state = accepted(running, running.newGame(scholar()), "reckless_climb")

        assertEquals("death", state.endingId)
        assertEquals(0, state.character.health)
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
    }

    @Test
    fun effectsAfterDeathAreNotApplied() {
        val running = engineFrom(rolls = listOf(1)) { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    if (action.id != "reckless_climb") {
                        action
                    } else {
                        action.copy(
                            failure = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "climbed", value = true),
                                    Effect.ChangeLife(amount = -10),
                                    Effect.ChangeLife(amount = 4),
                                    Effect.AddItem(itemId = "bandage"),
                                    Effect.AddEvidence(evidenceId = "proof_archive"),
                                    Effect.SetFlag(key = "hatch_open", value = true),
                                    Effect.SetNpcState(npcId = "warden", state = "lost"),
                                    Effect.SetEnding(endingId = "failure"),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val state = accepted(running, running.newGame(scholar()), "reckless_climb")

        assertEquals("death", state.endingId)
        assertEquals(0, state.character.health)
        assertTrue(state.flags["climbed"] == true)
        assertFalse(state.flags["hatch_open"] == true)
        assertFalse(state.flags["gave_up"] == true)
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
        assertEquals("watching", state.npcStates["warden"])
    }

    @Test
    fun effectsAfterSetEndingAreNotApplied() {
        val running = engineFrom { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    if (action.id != "walk_away") {
                        action
                    } else {
                        action.copy(
                            success = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "gave_up", value = true),
                                    Effect.SetEnding(endingId = "failure"),
                                    Effect.ChangeLife(amount = -4),
                                    Effect.AddItem(itemId = "bandage"),
                                    Effect.AddEvidence(evidenceId = "proof_archive"),
                                    Effect.SetFlag(key = "climbed", value = true),
                                    Effect.SetEnding(endingId = "justice"),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val state = accepted(running, running.newGame(scholar()), "walk_away")

        assertEquals("failure", state.endingId)
        assertEquals(10, state.character.health)
        assertTrue(state.flags["gave_up"] == true)
        assertFalse(state.flags["climbed"] == true)
        assertTrue(state.evidenceIds.isEmpty())
        assertEquals(listOf(Character.COIN_PURSE_ID), state.character.inventory.map { it.id })
    }

    @Test
    fun lifeCannotChangeAfterAnEnding() {
        val deadly = engineFrom(rolls = listOf(1)) { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    when (action.id) {
                        "reckless_climb" -> action.copy(
                            failure = OutcomeDefinition(
                                effects = listOf(
                                    Effect.ChangeLife(amount = -10),
                                    Effect.ChangeLife(amount = 4),
                                ),
                            ),
                        )
                        "look_around" -> action.copy(
                            success = OutcomeDefinition(effects = listOf(Effect.ChangeLife(amount = 4))),
                        )
                        else -> action
                    }
                },
            )
        }
        val dead = accepted(deadly, deadly.newGame(scholar()), "reckless_climb")
        assertEquals("death", dead.endingId)
        assertEquals(0, dead.character.health)
        val healed = deadly.resolve(dead, "look_around")
        assertSame(dead, healed.state)
        assertEquals(AdventureRejection.TERMINAL, (healed as AdventureResolution.Rejected).reason)
        assertEquals(0, healed.state.character.health)

        val closed = engineFrom { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    when (action.id) {
                        "walk_away" -> action.copy(
                            success = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "gave_up", value = true),
                                    Effect.SetEnding(endingId = "failure"),
                                    Effect.ChangeLife(amount = -4),
                                ),
                            ),
                        )
                        "look_around" -> action.copy(
                            success = OutcomeDefinition(effects = listOf(Effect.ChangeLife(amount = -1))),
                        )
                        else -> action
                    }
                },
            )
        }
        val failed = accepted(closed, closed.newGame(scholar()), "walk_away")
        assertEquals("failure", failed.endingId)
        assertEquals(10, failed.character.health)
        val hurt = closed.resolve(failed, "look_around")
        assertSame(failed, hurt.state)
        assertEquals(AdventureRejection.TERMINAL, (hurt as AdventureResolution.Rejected).reason)
        assertEquals(10, hurt.state.character.health)
    }

    @Test
    fun appliedEffectsStopAtTheFirstEnding() {
        val running = engineFrom { adventure ->
            adventure.copy(
                actions = adventure.actions.map { action ->
                    if (action.id != "walk_away") {
                        action
                    } else {
                        action.copy(
                            success = OutcomeDefinition(
                                effects = listOf(
                                    Effect.SetFlag(key = "gave_up", value = true),
                                    Effect.SetEnding(endingId = "failure"),
                                    Effect.ChangeLife(amount = -4),
                                ),
                            ),
                        )
                    }
                },
            )
        }
        val resolution = running.resolve(running.newGame(scholar()), "walk_away") as AdventureResolution.Accepted

        assertEquals(
            listOf(
                Effect.SetFlag(key = "gave_up", value = true),
                Effect.SetEnding(endingId = "failure"),
            ),
            resolution.appliedEffects,
        )
    }

    @Test
    fun appliedEffectsStopWhenLifeReachesZero() {
        val running = engine(rolls = listOf(1))
        val resolution = running.resolve(running.newGame(scholar()), "reckless_climb") as AdventureResolution.Accepted

        assertEquals(listOf(Effect.ChangeLife(amount = -10)), resolution.appliedEffects)
        assertEquals("death", resolution.state.endingId)
    }

    @Test
    fun resolutionKeepsANarrativeSummary() {
        val running = engine()
        assertEquals("", running.newGame(scholar()).narrativeSummary)

        val started = running.newGame(scholar()).copy(narrativeSummary = "The yard is quiet.")
        val resolved = running.resolve(started, "look_around") as AdventureResolution.Accepted

        assertEquals("The yard is quiet.", resolved.state.narrativeSummary)
        assertEquals(5, resolved.state.minutesElapsed)
    }

    @Test
    fun deathSetsTheEndingAtZeroAndDoesNotGoBelow() {
        val running = engine(rolls = listOf(1))
        val state = running.newGame(scholar())

        val resolved = running.resolve(state, "reckless_climb") as AdventureResolution.Accepted

        assertFalse(resolved.success)
        assertEquals(3, resolved.check?.total)
        assertEquals(20, resolved.check?.difficulty)
        assertEquals(0, resolved.state.character.health)
        assertEquals("death", resolved.state.endingId)
    }

    private fun investigate(running: com.dungeonmasterai.domain.adventure.engine.AdventureEngine) =
        listOf(
            "search_yard",
            "open_hall",
            "copy_archive",
            "ask_clerk",
            "trade_notes",
            "descend",
            "dust_mark",
            "pry_cellar",
            "return_hall",
        ).fold(running.newGame(scholar())) { state, actionId ->
            accepted(running, state, actionId)
        }

    private fun ending(id: String): Finished {
        return when (id) {
            "failure" -> {
                val running = engine()
                Finished(running, accepted(running, running.newGame(scholar()), "walk_away"))
            }
            "death" -> {
                val running = engine(rolls = listOf(1))
                val state = running.resolve(running.newGame(scholar()), "reckless_climb").state
                Finished(running, state)
            }
            else -> {
                val running = engine(rolls = listOf(6, 10))
                var state = investigate(running)
                state = (running.deduce(state, "named_conclusion") as AdventureResolution.Accepted).state
                val action = when (id) {
                    "justice" -> "publish_truth"
                    "vengeance" -> "strike_culprit"
                    "silence" -> "bury_truth"
                    else -> error(id)
                }
                Finished(running, accepted(running, state, action))
            }
        }
    }

    private fun engineFrom(
        rolls: List<Int> = emptyList(),
        edit: (Adventure) -> Adventure,
    ): AdventureEngine = AdventureEngine(edit(FixtureWatch.adventure()), ScriptedDie(rolls))

    private data class Finished(
        val engine: com.dungeonmasterai.domain.adventure.engine.AdventureEngine,
        val state: com.dungeonmasterai.domain.adventure.state.AdventureState,
    )
}
