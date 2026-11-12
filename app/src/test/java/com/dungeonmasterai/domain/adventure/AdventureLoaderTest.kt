package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.load.AdventureLoadException
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.load.validateAdventure
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.CheckDefinition
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.model.OutcomeDefinition
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureLoaderTest {
    @Test
    fun fixtureDecodesSchemaVersionOne() {
        val adventure = FixtureWatch.adventure()

        assertEquals(AdventureRules.SCHEMA_VERSION, adventure.schemaVersion)
        assertEquals("fixture_watch", adventure.id)
        assertEquals("yard_contract", adventure.sceneContracts.single().id)
        assertEquals(22, adventure.clock.startHour)
        assertEquals(0, adventure.clock.startMinute)
    }

    @Test
    fun sceneContractsAreNotRequiredToResolveAnAction() {
        val adventure = FixtureWatch.adventure().copy(sceneContracts = emptyList())
        val running = com.dungeonmasterai.domain.adventure.engine.AdventureEngine(
            adventure,
            com.dungeonmasterai.domain.dice.ScriptedDie(emptyList()),
        )
        val state = running.newGame(scholar())

        val resolution = running.resolve(state, "search_yard")

        assertTrue(resolution is AdventureResolution.Accepted)
        assertTrue("brass_key" in resolution.state.character.inventory.map { it.id })
    }

    @Test
    fun twoSceneContractsForOneLocationFailValidation() {
        val adventure = FixtureWatch.adventure()
        val extra = adventure.sceneContracts.first().copy(id = "yard_again")
        val broken = adventure.copy(sceneContracts = adventure.sceneContracts + extra)

        val errors = com.dungeonmasterai.domain.adventure.load.validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { it.contains("more than one scene contract") })
    }

    @Test
    fun localProseLongerThanTheNarrationLimitFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                if (action.id != "look_around") {
                    action
                } else {
                    action.copy(success = action.success.copy(localProse = "x".repeat(1_201)))
                }
            },
        )

        val errors = com.dungeonmasterai.domain.adventure.load.validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { it.contains("prose") })
    }

    @Test
    fun unknownEffectTypeIsRejected() {
        val broken = FixtureWatch.text().replaceFirst("\"ADD_CLUE\"", "\"RUN_CODE\"")

        val error = org.junit.Assert.assertThrows(AdventureLoadException::class.java) {
            loadAdventure(broken)
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("RUN_CODE"))
    }

    @Test
    fun unknownJsonFieldIsRejected() {
        val broken = FixtureWatch.text().replaceFirst(
            "\"schemaVersion\": 1",
            "\"schemaVersion\": 1, \"script\": true",
        )

        val error = org.junit.Assert.assertThrows(AdventureLoadException::class.java) {
            loadAdventure(broken)
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("script"))
    }

    @Test
    fun wrongSchemaVersionIsRejected() {
        val broken = FixtureWatch.text().replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2")

        val error = org.junit.Assert.assertThrows(AdventureLoadException::class.java) {
            loadAdventure(broken)
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Schema version 2"))
    }

    @Test
    fun difficultyOutsideTheAllowedSetFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                if (action.id == "read_archive") {
                    action.copy(check = CheckDefinition(attribute = "INTELLIGENCE", difficulty = 15))
                } else {
                    action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "read_archive" in it && "15" in it })
    }

    @Test
    fun unknownAttributeFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                if (action.id == "read_archive") {
                    action.copy(check = CheckDefinition(attribute = "LUCK", difficulty = 12))
                } else {
                    action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "LUCK" in it })
    }

    @Test
    fun evidenceWithOneRouteFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.filterNot { it.id == "copy_archive" },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun duplicateIdsFailValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(locations = adventure.locations + adventure.locations.first())

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "yard" in it && "used by" in it })
    }

    @Test
    fun evidenceGrantedAfterAnEndingDoesNotCountAsARoute() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "read_archive" -> action.copy(success = OutcomeDefinition(effects = emptyList()))
                    "copy_archive" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.SetFlag(key = "gave_up", value = true),
                                Effect.SetEnding(endingId = "failure"),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun settingDeathDirectlyFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                if (action.id != "look_around") {
                    action
                } else {
                    action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(Effect.SetEnding(endingId = "death")),
                        ),
                    )
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "death" in it && "Life reaching 0" in it })
    }

    @Test
    fun evidenceAfterCumulativeDeathDoesNotCountAsARoute() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "read_archive", "copy_archive" -> action.copy(
                        success = OutcomeDefinition(effects = emptyList()),
                    )
                    "look_around" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.ChangeLife(amount = -6),
                                Effect.ChangeLife(amount = -6),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun aConditionalMidnightThatAlwaysEndsDoesNotProveTheActionEnding() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            events = adventure.events.map { event ->
                if (event.id != "bells") {
                    event
                } else {
                    event.copy(
                        effects = listOf(
                            Effect.ChangeLife(amount = -6),
                            Effect.ChangeLife(amount = -6),
                        ),
                        whenFlagEffects = listOf(Effect.ChangeLife(amount = -10)),
                    )
                }
            },
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "read_archive", "copy_archive" -> action.copy(
                        success = OutcomeDefinition(effects = emptyList()),
                    )
                    "walk_away" -> action.copy(success = OutcomeDefinition(effects = emptyList()))
                    "look_around" -> action.copy(
                        timeCostMinutes = 130,
                        success = OutcomeDefinition(
                            effects = listOf(Effect.AddEvidence(evidenceId = "proof_archive")),
                        ),
                    )
                    "wait_past" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                                Effect.SetFlag(key = "gave_up", value = true),
                                Effect.SetEnding(endingId = "failure"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "failure" in it && "not set" in it })
        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun evidenceGrantedAfterANestedDeathDoesNotCountAsARoute() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            events = adventure.events.map { event ->
                if (event.id == "bell_note") {
                    event.copy(
                        effects = listOf(
                            Effect.AddClue(clueId = "clue_name"),
                            Effect.ChangeLife(amount = -10),
                        ),
                    )
                } else {
                    event
                }
            },
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "read_archive", "copy_archive" -> action.copy(
                        success = OutcomeDefinition(effects = emptyList()),
                    )
                    "ring_bell", "ring_bell_again" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.TriggerEvent(eventId = "bell_note"),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun evidenceAfterAConditionalEventThatAlwaysEndsDoesNotCountAsARoute() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            events = adventure.events.map { event ->
                if (event.id == "bell_note") {
                    event.copy(
                        whenFlag = "door_open",
                        effects = listOf(
                            Effect.AddClue(clueId = "clue_name"),
                            Effect.ChangeLife(amount = -10),
                        ),
                        whenFlagEffects = listOf(Effect.ChangeLife(amount = -10)),
                    )
                } else {
                    event
                }
            },
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "read_archive", "copy_archive" -> action.copy(
                        success = OutcomeDefinition(effects = emptyList()),
                    )
                    "search_yard" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.AddClue(clueId = "clue_bell"),
                                Effect.AddItem(itemId = "brass_key"),
                                Effect.SetFlag(key = "door_open", value = true),
                                Effect.TriggerEvent(eventId = "bell_note"),
                                Effect.AddEvidence(evidenceId = "proof_archive"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "proof_archive" in it })
    }

    @Test
    fun anEndingAfterCertainDeathIsNotProof() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            events = adventure.events.map { event ->
                if (event.id == "bell_note") {
                    event.copy(effects = listOf(Effect.ChangeLife(amount = -10)))
                } else {
                    event
                }
            },
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "strike_culprit" -> action.copy(success = OutcomeDefinition(effects = emptyList()))
                    "search_yard" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.TriggerEvent(eventId = "bell_note"),
                                Effect.SetFlag(key = "truth_known", value = true),
                                Effect.SetFlag(key = "chose_violence", value = true),
                                Effect.SetEnding(endingId = "vengeance"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "vengeance" in it })
    }

    @Test
    fun anEndingOnTheActionThatCrossesALethalMidnightIsNotProof() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(
            events = adventure.events.map { event ->
                if (event.id == "bells") {
                    event.copy(
                        effects = listOf(Effect.ChangeLife(amount = -10)),
                        whenFlag = null,
                        whenFlagEffects = emptyList(),
                    )
                } else {
                    event
                }
            },
            actions = adventure.actions.map { action ->
                when (action.id) {
                    "walk_away" -> action.copy(success = OutcomeDefinition(effects = emptyList()))
                    "wait_past" -> action.copy(
                        success = OutcomeDefinition(
                            effects = listOf(
                                Effect.SetFlag(key = "gave_up", value = true),
                                Effect.SetEnding(endingId = "failure"),
                            ),
                        ),
                    )
                    else -> action
                }
            },
        )

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "failure" in it && "not set" in it })
    }

    @Test
    fun clockThatDoesNotStartAtTwentyTwoHundredFailsValidation() {
        val adventure = FixtureWatch.adventure()
        val broken = adventure.copy(clock = adventure.clock.copy(startHour = 21))

        val errors = validateAdventure(broken)

        assertTrue(errors.joinToString("\n"), errors.any { "Clock must start" in it })
    }
}
