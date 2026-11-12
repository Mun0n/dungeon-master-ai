package com.dungeonmasterai.domain.adventure

import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.ScriptedDie
import org.junit.Assert.assertTrue

object FixtureWatch {
    fun text(): String {
        val resource = checkNotNull(FixtureWatch::class.java.classLoader).getResource("adventures/fixture_watch.json")
        checkNotNull(resource) { "Missing adventures/fixture_watch.json" }
        return resource.readText()
    }

    fun adventure(): Adventure = loadAdventure(text())
}

fun accepted(engine: AdventureEngine, state: AdventureState, actionId: String): AdventureState {
    val resolution = engine.resolve(state, actionId)
    assertTrue(resolution.toString(), resolution is AdventureResolution.Accepted)
    return (resolution as AdventureResolution.Accepted).state
}

fun scholar(name: String = "Ada"): Character = Character.create(name, CharacterClass.SCHOLAR)

fun warrior(name: String = "Bram"): Character = Character.create(name, CharacterClass.WARRIOR)

fun engine(rolls: List<Int> = emptyList()): AdventureEngine =
    AdventureEngine(FixtureWatch.adventure(), ScriptedDie(rolls))
