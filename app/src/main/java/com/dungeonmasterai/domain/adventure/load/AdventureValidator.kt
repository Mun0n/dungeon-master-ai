package com.dungeonmasterai.domain.adventure.load

import com.dungeonmasterai.domain.adventure.model.ActionDefinition
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.model.AdventureRules
import com.dungeonmasterai.domain.adventure.model.EFFECT_TYPE_NAMES
import com.dungeonmasterai.domain.adventure.model.Effect
import com.dungeonmasterai.domain.adventure.model.Ending
import com.dungeonmasterai.domain.adventure.model.OutcomeDefinition
import com.dungeonmasterai.domain.adventure.model.Requirement
import com.dungeonmasterai.domain.adventure.model.StoryEvent
import com.dungeonmasterai.domain.character.Attribute
import com.dungeonmasterai.domain.character.Character

fun validateAdventure(adventure: Adventure): List<String> {
    val errors = mutableListOf<String>()
    fun rejectDuplicates(values: List<String>, owner: String) {
        val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors += "$owner lists duplicates: ${duplicates.joinToString()}."
        }
    }
    if (adventure.schemaVersion != AdventureRules.SCHEMA_VERSION) {
        errors += "Schema version ${adventure.schemaVersion} is not supported. Expected ${AdventureRules.SCHEMA_VERSION}."
    }
    val ids = mutableMapOf<String, String>()
    fun id(owner: String, value: String) {
        if (!AdventureRules.ID_PATTERN.matches(value)) {
            errors += "$owner id '$value' must match ${AdventureRules.ID_PATTERN.pattern}."
            return
        }
        val previous = ids.put(value, owner)
        if (previous != null) {
            errors += "Id '$value' is used by $previous and $owner."
        }
    }
    id("adventure", adventure.id)
    if (adventure.title.isBlank()) errors += "Adventure title must not be blank."
    val language = adventure.narrativeLanguage
    if (language != null && language != AdventureRules.NARRATIVE_SPANISH) {
        errors += "Narrative language '$language' is not supported."
    }

    adventure.acts.forEach { act ->
        id("act", act.id)
        if (act.title.isBlank()) errors += "Act '${act.id}' title must not be blank."
        if (act.locationIds.isEmpty()) errors += "Act '${act.id}' has no locations."
        rejectDuplicates(act.locationIds, "Act '${act.id}' locations")
    }
    adventure.locations.forEach { location ->
        id("location", location.id)
        if (location.name.isBlank()) errors += "Location '${location.id}' name must not be blank."
    }
    adventure.npcs.forEach { npc ->
        id("npc", npc.id)
        if (npc.name.isBlank()) errors += "Npc '${npc.id}' name must not be blank."
        if (npc.states.isEmpty()) errors += "Npc '${npc.id}' has no states."
        rejectDuplicates(npc.states, "Npc '${npc.id}' states")
        npc.states.filter { it.isBlank() }.forEach { errors += "Npc '${npc.id}' has a blank state." }
    }
    adventure.items.forEach { item ->
        id("item", item.id)
        if (item.name.isBlank()) errors += "Item '${item.id}' name must not be blank."
    }
    adventure.flags.forEach { flag -> id("flag", flag.id) }
    adventure.evidences.forEach { evidence ->
        id("evidence", evidence.id)
        if (evidence.text.isBlank()) errors += "Evidence '${evidence.id}' text must not be blank."
    }
    adventure.clues.forEach { clue ->
        id("clue", clue.id)
        if (clue.text.isBlank()) errors += "Clue '${clue.id}' text must not be blank."
    }
    adventure.actions.forEach { action -> id("action", action.id) }
    adventure.events.forEach { event -> id("event", event.id) }
    adventure.endings.forEach { ending -> id("ending", ending.id) }
    adventure.deductions.forEach { deduction -> id("deduction", deduction.id) }
    adventure.sceneContracts.forEach { contract ->
        id("scene", contract.id)
        if (contract.purpose.isBlank()) errors += "Scene '${contract.id}' purpose must not be blank."
        if (contract.purpose.length > AdventureRules.PURPOSE_MAX) {
            errors += "Scene '${contract.id}' purpose is longer than ${AdventureRules.PURPOSE_MAX} characters."
        }
    }

    val locations = adventure.locations.associateBy { it.id }
    val npcs = adventure.npcs.associateBy { it.id }
    val items = adventure.items.associateBy { it.id }
    val flags = adventure.flags.associateBy { it.id }
    val evidences = adventure.evidences.associateBy { it.id }
    val clues = adventure.clues.associateBy { it.id }
    val actions = adventure.actions.associateBy { it.id }
    val events = adventure.events.associateBy { it.id }
    val endings = adventure.endings.associateBy { it.id }
    val deductions = adventure.deductions.associateBy { it.id }
    val acts = adventure.acts.associateBy { it.id }

    if (adventure.actions.isEmpty()) errors += "Adventure has no actions."
    if (adventure.locations.isEmpty()) errors += "Adventure has no locations."
    if (adventure.endings.isEmpty()) errors += "Adventure has no endings."

    if (adventure.startLocationId !in locations) {
        errors += "Start location '${adventure.startLocationId}' does not exist."
    }
    val death = endings[adventure.deathEndingId]
    if (death == null) {
        errors += "Death ending '${adventure.deathEndingId}' does not exist."
    } else if (!death.requirements.isEmpty()) {
        errors += "Death ending '${death.id}' must not add requirements. Life 0 is the only gate."
    }

    val clock = adventure.clock
    if (clock.startHour != AdventureRules.START_HOUR || clock.startMinute != AdventureRules.START_MINUTE) {
        errors += "Clock must start at ${AdventureRules.START_HOUR}:${AdventureRules.START_MINUTE.toString().padStart(2, '0')}."
    }
    if (clock.midnightEventId !in events) {
        errors += "Midnight event '${clock.midnightEventId}' does not exist."
    }

    adventure.acts.forEach { act ->
        act.locationIds.forEach { locationId ->
            val location = locations[locationId]
            if (location == null) {
                errors += "Act '${act.id}' references missing location '$locationId'."
            } else if (location.actId != act.id) {
                errors += "Location '$locationId' is listed in act '${act.id}' but declares act '${location.actId}'."
            }
        }
    }
    adventure.locations.forEach { location ->
        val act = acts[location.actId]
        if (act == null) {
            errors += "Location '${location.id}' references missing act '${location.actId}'."
        } else if (location.id !in act.locationIds) {
            errors += "Act '${act.id}' does not list location '${location.id}'."
        }
    }
    adventure.npcs.forEach { npc ->
        if (npc.initialState !in npc.states) {
            errors += "Npc '${npc.id}' initial state '${npc.initialState}' is not declared."
        }
    }

    fun requireKnown(owner: String, kind: String, value: String, known: Set<String>) {
        if (value !in known) errors += "$owner references missing $kind '$value'."
    }

    fun checkRequirement(owner: String, requirement: Requirement) {
        rejectDuplicates(requirement.flagIds, "$owner flags")
        rejectDuplicates(requirement.absentFlagIds, "$owner absent flags")
        rejectDuplicates(requirement.itemIds, "$owner items")
        rejectDuplicates(requirement.evidenceIds, "$owner evidence")
        rejectDuplicates(requirement.clueIds, "$owner clues")
        rejectDuplicates(requirement.absentClueIds, "$owner absent clues")
        rejectDuplicates(requirement.discoveredLocationIds, "$owner locations")
        rejectDuplicates(requirement.deductionIds, "$owner deductions")
        requirement.flagIds.forEach { requireKnown(owner, "flag", it, flags.keys) }
        requirement.absentFlagIds.forEach { requireKnown(owner, "flag", it, flags.keys) }
        requirement.itemIds.forEach { requireKnown(owner, "item", it, items.keys) }
        requirement.evidenceIds.forEach { requireKnown(owner, "evidence", it, evidences.keys) }
        requirement.clueIds.forEach { requireKnown(owner, "clue", it, clues.keys) }
        requirement.absentClueIds.forEach { requireKnown(owner, "clue", it, clues.keys) }
        val both = requirement.clueIds.intersect(requirement.absentClueIds.toSet())
        if (both.isNotEmpty()) {
            errors += "$owner requires clues to be present and absent: ${both.joinToString()}."
        }
        requirement.discoveredLocationIds.forEach { requireKnown(owner, "location", it, locations.keys) }
        requirement.deductionIds.forEach { requireKnown(owner, "deduction", it, deductions.keys) }
        requirement.relationships.forEach { required ->
            requireKnown(owner, "npc", required.npcId, npcs.keys)
        }
        requirement.npcStates.forEach { required ->
            val npc = npcs[required.npcId]
            if (npc == null) {
                errors += "$owner references missing npc '${required.npcId}'."
            } else if (required.state !in npc.states) {
                errors += "$owner uses undeclared state '${required.state}' for npc '${npc.id}'."
            }
        }
        val minutes = requirement.atLeastMinutes
        if (minutes != null && minutes < 0) errors += "$owner has a negative minute minimum."
        if (requirement.beforeMidnight == true && requirement.afterMidnight == true) {
            errors += "$owner requires both before and after midnight."
        }
    }

    fun checkEffects(owner: String, effects: List<Effect>, allowTrigger: Boolean) {
        effects.forEach { effect ->
            when (effect) {
                is Effect.SetFlag -> requireKnown(owner, "flag", effect.key, flags.keys)
                is Effect.ChangeLife -> if (effect.amount == 0) {
                    errors += "$owner changes life by 0."
                }
                is Effect.AddItem -> requireKnown(owner, "item", effect.itemId, items.keys)
                is Effect.RemoveItem -> requireKnown(owner, "item", effect.itemId, items.keys)
                is Effect.DiscoverLocation -> requireKnown(owner, "location", effect.locationId, locations.keys)
                is Effect.MoveTo -> requireKnown(owner, "location", effect.locationId, locations.keys)
                is Effect.SetRelationship -> requireKnown(owner, "npc", effect.npcId, npcs.keys)
                is Effect.AddEvidence -> requireKnown(owner, "evidence", effect.evidenceId, evidences.keys)
                is Effect.AddClue -> requireKnown(owner, "clue", effect.clueId, clues.keys)
                is Effect.SetNpcState -> {
                    val npc = npcs[effect.npcId]
                    if (npc == null) {
                        errors += "$owner references missing npc '${effect.npcId}'."
                    } else if (effect.state !in npc.states) {
                        errors += "$owner uses undeclared state '${effect.state}' for npc '${effect.npcId}'."
                    }
                }
                is Effect.RecordAttempt -> requireKnown(owner, "action", effect.actionId, actions.keys)
                is Effect.TriggerEvent -> {
                    if (!allowTrigger) {
                        errors += "$owner triggers an event. Events cannot trigger events."
                    } else if (effect.eventId !in events) {
                        errors += "$owner references missing event '${effect.eventId}'."
                    } else if (effect.eventId == clock.midnightEventId) {
                        errors += "$owner triggers midnight. Only the clock may fire '${clock.midnightEventId}'."
                    }
                }
                is Effect.SetEnding -> {
                    requireKnown(owner, "ending", effect.endingId, endings.keys)
                    if (effect.endingId == adventure.deathEndingId) {
                        errors += "$owner sets the death ending directly. Life reaching 0 is the only way to reach it."
                    }
                }
            }
        }
    }

    val evidenceRoutes = mutableMapOf<String, MutableSet<String>>()
    val clueRoutes = mutableMapOf<String, MutableSet<String>>()
    val preparedEndings = mutableSetOf<String>()
    fun grant(routes: MutableMap<String, MutableSet<String>>, id: String, route: String) {
        routes.getOrPut(id) { mutableSetOf() }.add(route)
    }

    adventure.deductions.forEach { deduction ->
        if (deduction.evidenceIds.isEmpty()) errors += "Deduction '${deduction.id}' requires no evidence."
        rejectDuplicates(deduction.evidenceIds, "Deduction '${deduction.id}' evidence")
        deduction.evidenceIds.forEach { requireKnown("Deduction '${deduction.id}'", "evidence", it, evidences.keys) }
        requireKnown("Deduction '${deduction.id}'", "flag", deduction.flag, flags.keys)
        if (deduction.statement.length > AdventureRules.LOCAL_PROSE_MAX) {
            errors += "Deduction '${deduction.id}' statement is longer than ${AdventureRules.LOCAL_PROSE_MAX} characters."
        }
        if (deduction.localProse.length > AdventureRules.LOCAL_PROSE_MAX) {
            errors += "Deduction '${deduction.id}' prose is longer than ${AdventureRules.LOCAL_PROSE_MAX} characters."
        }
        if (deduction.statement.isNotEmpty() && leaksIdentifier(deduction.statement)) {
            errors += "Deduction '${deduction.id}' statement contains a technical identifier."
        }
        if (deduction.localProse.isNotEmpty() && leaksIdentifier(deduction.localProse)) {
            errors += "Deduction '${deduction.id}' prose contains a technical identifier."
        }
        rejectDuplicates(deduction.claims.map { it.id }, "Deduction '${deduction.id}' claims")
        val claimsById = deduction.claims.associateBy { it.id }
        deduction.claims.forEach { claim ->
            id("claim", claim.id)
            if (claim.prompt.isBlank() || claim.prompt.length > AdventureRules.PURPOSE_MAX) {
                errors += "Claim '${claim.id}' prompt is outside 1..${AdventureRules.PURPOSE_MAX}."
            } else if (leaksIdentifier(claim.prompt)) {
                errors += "Claim '${claim.id}' prompt contains a technical identifier."
            }
            if (claim.options.size < 2) {
                errors += "Claim '${claim.id}' needs at least two options."
            }
            rejectDuplicates(claim.options.map { it.id }, "Claim '${claim.id}' options")
            claim.options.forEach { option ->
                if (!AdventureRules.ID_PATTERN.matches(option.id)) {
                    errors += "Claim '${claim.id}' option '${option.id}' must match ${AdventureRules.ID_PATTERN.pattern}."
                }
                if (option.gloss.isBlank() || option.gloss.length > AdventureRules.PURPOSE_MAX) {
                    errors += "Claim '${claim.id}' option '${option.id}' gloss is outside 1..${AdventureRules.PURPOSE_MAX}."
                } else if (leaksIdentifier(option.gloss)) {
                    errors += "Claim '${claim.id}' option '${option.id}' gloss contains a technical identifier."
                }
            }
            val optionIds = claim.options.map { it.id }.toSet()
            if (claim.accept.isEmpty()) errors += "Claim '${claim.id}' has no accepted value."
            rejectDuplicates(claim.accept, "Claim '${claim.id}' accepted values")
            claim.accept.forEach { value ->
                if (value !in optionIds) errors += "Claim '${claim.id}' accepts unknown option '$value'."
            }
            rejectDuplicates(claim.falseValues, "Claim '${claim.id}' false values")
            claim.falseValues.forEach { value ->
                if (value !in optionIds) {
                    errors += "Claim '${claim.id}' false value '$value' is not an option."
                } else if (value in claim.accept) {
                    errors += "Claim '${claim.id}' lists '$value' as both accepted and false."
                }
            }
        }
        deduction.contradictions.forEach { pair ->
            val left = claimsById[pair.leftClaimId]
            val right = claimsById[pair.rightClaimId]
            if (left == null || right == null) {
                errors += "Deduction '${deduction.id}' contradiction names a missing claim."
            } else {
                if (pair.leftValue !in left.options.map { it.id }) {
                    errors += "Deduction '${deduction.id}' contradiction uses unknown option '${pair.leftValue}'."
                }
                if (pair.rightValue !in right.options.map { it.id }) {
                    errors += "Deduction '${deduction.id}' contradiction uses unknown option '${pair.rightValue}'."
                }
            }
        }
    }

    adventure.actions.forEach { action ->
        val owner = "Action '${action.id}'"
        requireKnown(owner, "location", action.locationId, locations.keys)
        if (action.timeCostMinutes < 0) errors += "$owner has a negative time cost."
        if (action.repeatable) {
            if (action.check != null) errors += "$owner is repeatable and cannot have a check."
            if (action.failure != null) errors += "$owner is repeatable and cannot have a failure outcome."
            if (action.retry != null) errors += "$owner is repeatable and cannot have a retry."
            if (action.success.effects.singleOrNull() !is Effect.MoveTo) {
                errors += "$owner is repeatable and must succeed with one MOVE_TO."
            }
        }
        checkRequirement(owner, action.availability)
        val check = action.check
        if (check == null) {
            if (action.failure != null) errors += "$owner has a failure outcome without a check."
        } else {
            if (check.attribute !in Attribute.entries.map { it.name }) {
                errors += "$owner uses unknown attribute '${check.attribute}'."
            }
            if (check.difficulty !in AdventureRules.DIFFICULTY_THRESHOLDS) {
                errors += "$owner uses difficulty ${check.difficulty}. Allowed values are ${AdventureRules.DIFFICULTY_THRESHOLDS}."
            }
            if (action.failure == null) errors += "$owner has a check without a failure outcome."
        }
        action.retry?.let { checkRequirement("$owner retry", it.reopenWhen) }
        listOf("success" to action.success, "failure" to action.failure).forEach { (label, outcome) ->
            if (outcome == null) return@forEach
            if (outcome.localProse.length > AdventureRules.LOCAL_PROSE_MAX) {
                errors += "$owner $label prose is longer than ${AdventureRules.LOCAL_PROSE_MAX} characters."
            }
            checkEffects("$owner $label", outcome.effects, allowTrigger = true)
            if (recordsOwnAttempt(adventure, action, outcome)) {
                errors += "$owner records its own attempt. The engine records the current action."
            }
            errors += prepareEnding(
                adventure,
                action,
                outcome,
                endings,
                deductions.mapValues { it.value.flag },
                preparedEndings,
            )
            runnableEffects(adventure, action, outcome).forEach { effect ->
                when (effect) {
                    is Effect.AddEvidence -> grant(evidenceRoutes, effect.evidenceId, action.id)
                    is Effect.AddClue -> grant(clueRoutes, effect.clueId, action.id)
                    else -> Unit
                }
            }
        }
            checkRemoval(owner, action, adventure, errors)
    }
    adventure.endings.filter { it.id != adventure.deathEndingId }.forEach { ending ->
        if (ending.id !in preparedEndings) {
            errors += "Ending '${ending.id}' is not set by an action that establishes its requirements."
        }
    }

    adventure.events.forEach { event ->
        val owner = "Event '${event.id}'"
        checkEffects(owner, event.effects, allowTrigger = false)
        if (event.whenFlag == null) {
            if (event.whenFlagEffects.isNotEmpty()) {
                errors += "$owner has alternate effects without a flag."
            }
        } else if (event.whenFlag !in flags) {
            errors += "$owner references missing flag '${event.whenFlag}'."
        }
        checkEffects("$owner alternate", event.whenFlagEffects, allowTrigger = false)
        (appliedPrefix(event.effects) + appliedPrefix(event.whenFlagEffects)).forEach { effect ->
            when (effect) {
                is Effect.AddEvidence -> grant(evidenceRoutes, effect.evidenceId, event.id)
                is Effect.AddClue -> grant(clueRoutes, effect.clueId, event.id)
                else -> Unit
            }
        }
    }

    rejectDuplicates(adventure.prologue.map { it.id }, "Prologue")
    adventure.prologue.forEachIndexed { index, beat ->
        val owner = "Prologue '${beat.id}'"
        val flag = flags[beat.id]
        if (flag == null) {
            errors += "$owner is not a flag."
        } else if (flag.defaultValue) {
            errors += "$owner must default to false."
        }
        requireKnown(owner, "location", beat.locationId, locations.keys)
        val prose = beat.prose.trim()
        if (prose.isEmpty() || prose.length > AdventureRules.LOCAL_PROSE_MAX) {
            errors += "$owner prose must be 1..${AdventureRules.LOCAL_PROSE_MAX} characters."
        } else if (leaksIdentifier(beat.prose)) {
            errors += "$owner prose contains an identifier."
        }
        val last = index == adventure.prologue.lastIndex
        if (!last && beat.effects.isNotEmpty()) {
            errors += "$owner must not grant effects before the last passage."
        }
        beat.effects.forEach { effect ->
            val allowed = effect is Effect.SetFlag ||
                effect is Effect.AddClue ||
                effect is Effect.DiscoverLocation ||
                effect is Effect.MoveTo
            if (!allowed) errors += "$owner uses an effect the prologue cannot apply."
        }
        checkEffects(owner, beat.effects, allowTrigger = false)
        beat.effects.filterIsInstance<Effect.AddClue>().forEach { effect ->
            grant(clueRoutes, effect.clueId, beat.id)
        }
    }

    val mandatoryEvidence = adventure.deductions.flatMap { it.evidenceIds }.toSet()
    adventure.evidences.forEach { evidence ->
        val routes = evidenceRoutes[evidence.id].orEmpty().size
        val needed = if (evidence.id in mandatoryEvidence) 2 else 1
        if (routes < needed) {
            errors += "Evidence '${evidence.id}' has $routes route(s). It needs $needed."
        }
    }
    adventure.clues.forEach { clue ->
        if (clueRoutes[clue.id].orEmpty().isEmpty()) {
            errors += "Clue '${clue.id}' is never granted."
        }
    }

    val writtenFlags = mutableSetOf<String>()
    adventure.flags.filter { it.defaultValue }.forEach { writtenFlags += it.id }
    adventure.deductions.forEach { writtenFlags += it.flag }
    everyEffect(adventure).filterIsInstance<Effect.SetFlag>().forEach { effect ->
        if (effect.value) writtenFlags += effect.key
    }
    val readFlags = mutableSetOf<String>()
    everyRequirement(adventure).forEach { requirement ->
        readFlags += requirement.flagIds
        readFlags += requirement.absentFlagIds
    }
    adventure.events.mapNotNull { it.whenFlag }.forEach { readFlags += it }
    readFlags.forEach { flag ->
        if (flag !in writtenFlags) errors += "Flag '$flag' is required but nothing sets it."
    }

    val grantedItems = everyEffect(adventure).filterIsInstance<Effect.AddItem>().map { it.itemId }.toSet()
    everyRequirement(adventure).flatMap { it.itemIds }.toSet().forEach { itemId ->
        if (itemId !in grantedItems) errors += "Item '$itemId' is required but never granted."
    }

    val discovered = mutableSetOf(adventure.startLocationId)
    everyEffect(adventure).forEach { effect ->
        when (effect) {
            is Effect.DiscoverLocation -> discovered += effect.locationId
            is Effect.MoveTo -> discovered += effect.locationId
            else -> Unit
        }
    }
    adventure.locations.forEach { location ->
        if (location.id !in discovered) errors += "Location '${location.id}' can never be discovered."
    }

    val negativeLife = everyEffect(adventure).filterIsInstance<Effect.ChangeLife>().any { it.amount < 0 }
    if (!negativeLife) errors += "No effect reduces life, so the death ending cannot be reached."

    adventure.sceneContracts.forEach { contract ->
        if (contract.locationId !in locations) {
            errors += "Scene '${contract.id}' references missing location '${contract.locationId}'."
        }
        contract.allowedActionIds.forEach { actionId ->
            val action = actions[actionId]
            if (action == null) {
                errors += "Scene '${contract.id}' references missing action '$actionId'."
            } else if (action.locationId != contract.locationId) {
                errors += "Scene '${contract.id}' lists action '$actionId' from location '${action.locationId}'."
            }
        }
        contract.presentNpcIds.forEach { npcId ->
            if (npcId !in npcs) errors += "Scene '${contract.id}' references missing npc '$npcId'."
        }
        contract.revealableInformation.forEach { line ->
            if (line.isBlank() || line.length > AdventureRules.PURPOSE_MAX) {
                errors += "Scene '${contract.id}' has a revealable line outside 1..${AdventureRules.PURPOSE_MAX}."
            }
        }
        contract.forbiddenInformation.forEach { line ->
            if (line.length < AdventureRules.FORBIDDEN_MIN || line.length > AdventureRules.PURPOSE_MAX) {
                errors += "Scene '${contract.id}' has a forbidden line outside " +
                    "${AdventureRules.FORBIDDEN_MIN}..${AdventureRules.PURPOSE_MAX}."
            }
        }
    }
    adventure.sceneContracts.groupBy { it.locationId }.forEach { (locationId, contracts) ->
        if (contracts.size > 1) {
            errors += "Location '$locationId' has more than one scene contract."
        }
    }

    return errors
}

private fun prepareEnding(
    adventure: Adventure,
    action: ActionDefinition,
    outcome: OutcomeDefinition,
    endings: Map<String, Ending>,
    deductionFlags: Map<String, String>,
    preparedEndings: MutableSet<String>,
): List<String> {
    val errors = mutableListOf<String>()
    val flags = mutableSetOf<String>()
    val forcedOff = action.availability.absentFlagIds.toMutableSet()
    flags += action.availability.flagIds
    action.availability.deductionIds.mapNotNull { deductionFlags[it] }.forEach { flags += it }
    val evidence = action.availability.evidenceIds.toMutableSet()
    val items = action.availability.itemIds.toMutableSet()
    val clues = action.availability.clueIds.toMutableSet()
    val heldDeductions = action.availability.deductionIds.toMutableSet()
    val discovered = (action.availability.discoveredLocationIds + adventure.startLocationId).toMutableSet()
    val relationships = adventure.npcs.associate { it.id to 0 }.toMutableMap()
    action.availability.relationships.forEach { required ->
        val current = relationships[required.npcId] ?: 0
        if (required.atLeast > current) relationships[required.npcId] = required.atLeast
    }
    val npcStates = mutableMapOf<String, String>()
    action.availability.npcStates.forEach { npcStates[it.npcId] = it.state }
    var minutes = action.availability.atLeastMinutes ?: 0
    if (action.availability.afterMidnight == true) {
        minutes = maxOf(minutes, AdventureRules.MIDNIGHT_MINUTES)
    }
    minutes += action.timeCostMinutes
    var ended = false
    var health = Character.MAX_HEALTH
    fun absorb(effect: Effect, fromEvent: Boolean) {
        when (effect) {
            is Effect.SetFlag -> if (effect.value) {
                flags += effect.key
                forcedOff -= effect.key
            } else {
                flags -= effect.key
                forcedOff += effect.key
            }
            is Effect.AddEvidence -> evidence += effect.evidenceId
            is Effect.AddItem -> items += effect.itemId
            is Effect.RemoveItem -> items -= effect.itemId
            is Effect.AddClue -> clues += effect.clueId
            is Effect.DiscoverLocation -> discovered += effect.locationId
            is Effect.MoveTo -> discovered += effect.locationId
            is Effect.SetRelationship -> relationships[effect.npcId] = effect.value
            is Effect.SetNpcState -> npcStates[effect.npcId] = effect.state
            is Effect.ChangeLife -> {
                health = (health.toLong() + effect.amount.toLong())
                    .coerceIn(0L, Character.MAX_HEALTH.toLong())
                    .toInt()
                if (health == 0) ended = true
            }
            is Effect.TriggerEvent -> {
                if (fromEvent) return
                val event = adventure.events.find { it.id == effect.eventId } ?: return
                if (event.whenFlag != null) {
                    val defaultStops = stopsFrom(event.effects, health)
                    val alternateStops = stopsFrom(event.whenFlagEffects, health)
                    if (defaultStops && alternateStops) {
                        ended = true
                    } else if (defaultStops) {
                        health = healthAfter(event.whenFlagEffects, health)
                    } else if (alternateStops) {
                        health = healthAfter(event.effects, health)
                    } else {
                        health = maxOf(
                            healthAfter(event.effects, health),
                            healthAfter(event.whenFlagEffects, health),
                        )
                    }
                    return
                }
                for (inner in event.effects) {
                    absorb(inner, fromEvent = true)
                    if (ended) break
                }
            }
            is Effect.SetEnding -> {
                if (ended) return
                val ending = endings[effect.endingId] ?: return@absorb
                val requirement = ending.requirements
                val missing = mutableListOf<String>()
                if (!flags.containsAll(requirement.flagIds)) missing += "flags"
                if (requirement.absentFlagIds.any { it in flags || it !in forcedOff }) missing += "absent flags"
                if (!evidence.containsAll(requirement.evidenceIds)) missing += "evidence"
                if (!items.containsAll(requirement.itemIds)) missing += "items"
                if (!clues.containsAll(requirement.clueIds)) missing += "clues"
                if (!heldDeductions.containsAll(requirement.deductionIds)) missing += "deductions"
                if (!discovered.containsAll(requirement.discoveredLocationIds)) missing += "locations"
                requirement.relationships.forEach { required ->
                    val current = relationships[required.npcId] ?: 0
                    if (current < required.atLeast) missing += "relationship ${required.npcId}"
                }
                requirement.npcStates.forEach { required ->
                    if (npcStates[required.npcId] != required.state) missing += "npc ${required.npcId}"
                }
                val requiredMinutes = requirement.atLeastMinutes
                if (requiredMinutes != null && minutes < requiredMinutes) missing += "minutes"
                if (requirement.beforeMidnight == true &&
                    (action.availability.beforeMidnight != true || action.timeCostMinutes != 0)
                ) {
                    missing += "before midnight"
                }
                if (requirement.afterMidnight == true && minutes < AdventureRules.MIDNIGHT_MINUTES) {
                    missing += "after midnight"
                }
                if (missing.isEmpty()) {
                    preparedEndings += ending.id
                } else {
                    errors += "Action '${action.id}' sets ending '${ending.id}' without establishing ${missing.joinToString()}."
                }
                ended = true
            }
            else -> Unit
        }
    }
    val earliest = action.availability.atLeastMinutes ?: 0
    val crossesForSure = earliest < AdventureRules.MIDNIGHT_MINUTES &&
        earliest + action.timeCostMinutes >= AdventureRules.MIDNIGHT_MINUTES
    if (crossesForSure) {
        val midnight = adventure.events.find { it.id == adventure.clock.midnightEventId }
        if (midnight != null && midnight.whenFlag == null) {
            for (inner in midnight.effects) {
                absorb(inner, fromEvent = true)
                if (ended) break
            }
        } else if (midnight != null && stopsFrom(midnight.effects, health) && stopsFrom(midnight.whenFlagEffects, health)) {
            ended = true
        }
    }
    for (effect in outcome.effects) {
        if (ended) break
        absorb(effect, fromEvent = false)
        if (ended) break
    }
    return errors
}

private fun checkRemoval(
    owner: String,
    action: ActionDefinition,
    adventure: Adventure,
    errors: MutableList<String>,
) {
    listOf(action.success, action.failure).filterNotNull().forEach { outcome ->
        val held = action.availability.itemIds.toMutableSet()
        runnableEffects(adventure, action, outcome).forEach { effect ->
            when (effect) {
                is Effect.AddItem -> held += effect.itemId
                is Effect.RemoveItem -> {
                    if (effect.itemId !in held) {
                        errors += "$owner removes '${effect.itemId}' without granting or requiring it first."
                    }
                    held -= effect.itemId
                }
                else -> Unit
            }
        }
    }
}

private fun everyEffect(adventure: Adventure): List<Effect> = buildList {
    adventure.actions.forEach { action ->
        addAll(runnableEffects(adventure, action, action.success))
        action.failure?.let { addAll(runnableEffects(adventure, action, it)) }
    }
    adventure.events.forEach { event ->
        addAll(appliedPrefix(event.effects))
        addAll(appliedPrefix(event.whenFlagEffects))
    }
    adventure.prologue.forEach { beat -> addAll(beat.effects) }
}

private data class Simulation(
    val effects: List<Effect>,
    val stopped: Boolean,
    val health: Int,
)

private fun runnableEffects(
    adventure: Adventure,
    action: ActionDefinition,
    outcome: OutcomeDefinition,
): List<Effect> {
    if (midnightBlocks(adventure, action)) return emptyList()
    return appliedPrefix(outcome.effects, adventure.events.associateBy { it.id })
}

private fun midnightBlocks(adventure: Adventure, action: ActionDefinition): Boolean {
    val earliest = action.availability.atLeastMinutes ?: 0
    val crosses = earliest < AdventureRules.MIDNIGHT_MINUTES &&
        earliest + action.timeCostMinutes >= AdventureRules.MIDNIGHT_MINUTES
    if (!crosses) return false
    val midnight = adventure.events.find { it.id == adventure.clock.midnightEventId } ?: return false
    return stopsFrom(midnight, Character.MAX_HEALTH)
}

private fun stopsFrom(event: StoryEvent, health: Int): Boolean {
    if (event.whenFlag == null) return stopsFrom(event.effects, health)
    return stopsFrom(event.effects, health) && stopsFrom(event.whenFlagEffects, health)
}

private fun stopsFrom(effects: List<Effect>, health: Int): Boolean =
    simulate(effects, emptyMap(), health).stopped

private fun healthAfter(effects: List<Effect>, health: Int): Int =
    simulate(effects, emptyMap(), health).health

private fun appliedPrefix(
    effects: List<Effect>,
    events: Map<String, StoryEvent> = emptyMap(),
): List<Effect> = simulate(effects, events, Character.MAX_HEALTH).effects

private fun simulate(
    effects: List<Effect>,
    events: Map<String, StoryEvent>,
    startingHealth: Int,
): Simulation {
    var health = startingHealth
    val kept = mutableListOf<Effect>()
    for (effect in effects) {
        kept += effect
        if (effect is Effect.ChangeLife) {
            health = (health.toLong() + effect.amount.toLong())
                .coerceIn(0L, Character.MAX_HEALTH.toLong())
                .toInt()
        }
        if (effect is Effect.SetEnding || health == 0) {
            return Simulation(kept, stopped = true, health = health)
        }
        if (effect is Effect.TriggerEvent) {
            val event = events[effect.eventId] ?: continue
            if (stopsFrom(event, health)) {
                return Simulation(kept, stopped = true, health = health)
            }
            health = healthAfterEvent(event, health)
        }
    }
    return Simulation(kept, stopped = false, health = health)
}

private fun healthAfterEvent(event: StoryEvent, health: Int): Int {
    if (event.whenFlag == null) return healthAfter(event.effects, health)
    val defaultStops = stopsFrom(event.effects, health)
    val alternateStops = stopsFrom(event.whenFlagEffects, health)
    val defaultHealth = healthAfter(event.effects, health)
    val alternateHealth = healthAfter(event.whenFlagEffects, health)
    return when {
        defaultStops -> alternateHealth
        alternateStops -> defaultHealth
        else -> maxOf(defaultHealth, alternateHealth)
    }
}

private fun recordsOwnAttempt(
    adventure: Adventure,
    action: ActionDefinition,
    outcome: OutcomeDefinition,
): Boolean {
    val events = adventure.events.associateBy { it.id }
    return runnableEffects(adventure, action, outcome).any { effect ->
        when (effect) {
            is Effect.RecordAttempt -> effect.actionId == action.id
            is Effect.TriggerEvent -> {
                val event = events[effect.eventId] ?: return@any false
                val inner = appliedPrefix(event.effects) + appliedPrefix(event.whenFlagEffects)
                inner.any { it is Effect.RecordAttempt && it.actionId == action.id }
            }
            else -> false
        }
    }
}

private fun leaksIdentifier(text: String): Boolean =
    '_' in text || EFFECT_TYPE_NAMES.any { name -> name in text }

private fun everyRequirement(adventure: Adventure): List<Requirement> = buildList {
    adventure.actions.forEach { action ->
        add(action.availability)
        action.retry?.let { add(it.reopenWhen) }
    }
    adventure.endings.forEach { add(it.requirements) }
}
