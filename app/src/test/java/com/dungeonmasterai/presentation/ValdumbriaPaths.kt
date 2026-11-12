package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.adventure.accepted
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File

internal fun valdumbriaDocument(): Adventure = loadAdventure(
    File("src/main/assets/adventures/valdumbria.json").readText(),
)

internal fun playValdumbria(rolls: List<Int>, steps: List<String>): Pair<AdventureEngine, AdventureState> {
    val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(rolls))
    val state = steps.fold(engine.newGame(scholar())) { current, actionId ->
        accepted(engine, current, actionId)
    }
    return engine to state
}

internal val PROOF_BEFORE_EVIDENCE: List<String> = listOf(
    "read_elias_letter",
    "speak_with_ines",
    "warn_ines",
    "study_the_dark_tower",
    "speak_with_beltran",
    "go_to_tavern",
    "sit_with_the_regulars",
    "follow_the_path_behind_the_inn",
    "ask_mara_for_linen",
    "bind_a_wound",
    "ask_mara",
    "go_to_plaza",
    "look_over_the_square",
    "note_suspicion",
    "accuse_beltran",
    "enter_the_archive",
)

internal val PROOF_EVIDENCE: List<String> = listOf(
    "read_elias_notes",
    "leave_the_archive",
    "enter_the_cemetery",
    "read_burial_list",
    "follow_the_sinking_ground",
    "enter_the_crypt",
    "trace_the_scratches",
    "return_to_the_cemetery",
    "leave_the_cemetery",
    "enter_the_church",
    "take_the_hidden_list",
)

internal fun proofBeforeDeduction(): AdventureState {
    val engine = AdventureEngine(valdumbriaDocument(), ScriptedDie(listOf(7, 1, 1)))
    val opened = PROOF_BEFORE_EVIDENCE.fold(engine.newGame(scholar())) { current, actionId ->
        accepted(engine, current, actionId)
    }
    return PROOF_EVIDENCE.fold(opened) { current, actionId ->
        accepted(engine, current, actionId)
    }
}
