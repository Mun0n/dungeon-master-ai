package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.MemorySavedGameStore
import com.dungeonmasterai.data.Narrator
import com.dungeonmasterai.data.NarratorRequest
import com.dungeonmasterai.data.SavedGameStore
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.dice.ScriptedDie
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.PlayerIntent
import com.dungeonmasterai.domain.game.RejectionReason
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aBlankNameDoesNotStartTheScene() {
        val store = MemorySavedGameStore()
        val viewModel = GameViewModel(ScriptedDie(emptyList()), store)
        assertTrue(viewModel.uiState.value.ready)

        viewModel.start("   ", CharacterClass.WARRIOR)

        assertNull(viewModel.uiState.value.game)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun startOpensTheBridgeWithTheClassAndThePurse() {
        val viewModel = GameViewModel(ScriptedDie(emptyList()), MemorySavedGameStore())

        viewModel.start(" Ada ", CharacterClass.SCHOLAR)
        val game = viewModel.uiState.value.game!!

        assertEquals("Ada", game.character.name)
        assertEquals(CharacterClass.SCHOLAR, game.character.characterClass)
        assertEquals(5, game.character.attributes.intelligence)
        assertEquals("guardians_bridge", game.sceneId)
        assertEquals(listOf("coin_purse"), game.character.inventory.map { it.id })
    }

    @Test
    fun aSecondViewModelRestoresTheSavedGame() {
        val store = MemorySavedGameStore()
        val first = GameViewModel(ScriptedDie(emptyList()), store)
        first.start(" Ada ", CharacterClass.SCHOLAR)

        val restored = GameViewModel(ScriptedDie(emptyList()), store).uiState.value.game!!

        assertEquals("Ada", restored.character.name)
        assertEquals(CharacterClass.SCHOLAR, restored.character.characterClass)
        assertEquals(5, restored.character.attributes.intelligence)
        assertEquals(10, restored.character.health)
        assertEquals(listOf("coin_purse"), restored.character.inventory.map { it.id })
        assertEquals(Outcome.ONGOING, restored.outcome)
        assertEquals(first.uiState.value.game!!.history, restored.history)
    }

    @Test
    fun aRejectedIntentIsSavedWithoutChangingTheCharacter() {
        val store = MemorySavedGameStore()
        val viewModel = GameViewModel(ScriptedDie(emptyList()), store)
        viewModel.start("Ada", CharacterClass.WARRIOR)
        val before = viewModel.uiState.value.game!!

        viewModel.submitText("hello")
        val after = viewModel.uiState.value.game!!

        assertEquals(before.character, after.character)
        assertEquals(before.outcome, after.outcome)
        assertEquals(before.sceneId, after.sceneId)
        assertEquals(
            RejectionReason.UNRECOGNIZED,
            (after.history.last() as GameEvent.ActionRejected).reason,
        )
        assertEquals(after, store.saved)
    }

    @Test
    fun aCustomAttackUsesTheEngineDie() {
        val store = MemorySavedGameStore()
        val viewModel = GameViewModel(ScriptedDie(listOf(12)), store)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("ATTACK the guardian")
        val game = viewModel.uiState.value.game!!

        assertEquals(Outcome.CROSSED, game.outcome)
        assertEquals(9, game.character.health)
        assertEquals(game, store.saved)
    }

    @Test
    fun aSlowSaveDoesNotOverwriteANewerSnapshot() {
        val store = GatedSavedGameStore()
        val viewModel = GameViewModel(ScriptedDie(listOf(12)), store)
        assertTrue(viewModel.uiState.value.ready)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("attack")
        store.release()

        val game = viewModel.uiState.value.game!!
        assertEquals(Outcome.CROSSED, game.outcome)
        assertEquals(9, game.character.health)
        assertEquals(game, store.saved)
    }

    @Test
    fun aValidNarratorPayloadSuppliesTheIntentAndTheLine() {
        val narrator = ScriptedNarrator("""{"narration":"The purse changes hands.","intent":"PAY"}""")
        val viewModel = GameViewModel(ScriptedDie(emptyList()), MemorySavedGameStore(), narrator)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("hello")
        val game = viewModel.uiState.value.game!!

        assertEquals(Outcome.CROSSED, game.outcome)
        assertTrue(game.character.inventory.none { it.id == "coin_purse" })
        assertEquals(10, game.character.health)
        assertEquals(
            "The purse changes hands.",
            game.history.filterIsInstance<GameEvent.Narration>().last().text,
        )
    }

    @Test
    fun anInvalidNarratorPayloadFallsBackToTheParser() {
        val narrator = ScriptedNarrator("""{"narration":"You are healed.","intent":"HEAL","health":10}""")
        val viewModel = GameViewModel(ScriptedDie(listOf(12)), MemorySavedGameStore(), narrator)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("ATTACK the guardian")
        val game = viewModel.uiState.value.game!!

        assertEquals(Outcome.CROSSED, game.outcome)
        assertEquals(9, game.character.health)
        assertTrue(game.history.none { it is GameEvent.Narration && it.text == "You are healed." })
    }

    @Test
    fun aNarratorFailureFallsBackToTheParser() {
        val viewModel = GameViewModel(ScriptedDie(emptyList()), MemorySavedGameStore(), BrokenNarrator())

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("hello")
        val game = viewModel.uiState.value.game!!

        assertEquals(10, game.character.health)
        assertEquals(RejectionReason.UNRECOGNIZED, (game.history.last() as GameEvent.ActionRejected).reason)
    }

    @Test
    fun aNewerTextReplyWinsOverAnOlderOne() {
        val narrator = MappedNarrator(
            mapOf(
                "attack" to """{"narration":"Steel.","intent":"ATTACK"}""",
                "pay" to """{"narration":"Coins.","intent":"PAY"}""",
            ),
        )
        val viewModel = GameViewModel(ScriptedDie(listOf(12)), MemorySavedGameStore(), narrator)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("attack")
        viewModel.submitText("pay")
        narrator.release("attack")
        narrator.release("pay")
        val game = viewModel.uiState.value.game!!

        assertEquals(Outcome.CROSSED, game.outcome)
        assertEquals(10, game.character.health)
        assertTrue(game.character.inventory.none { it.id == "coin_purse" })
        assertEquals("Coins.", game.history.filterIsInstance<GameEvent.Narration>().last().text)
    }

    @Test
    fun aLateNarratorReplyDoesNotApplyAfterAnotherAction() {
        val narrator = GatedNarrator("""{"narration":"The purse changes hands.","intent":"PAY"}""")
        val viewModel = GameViewModel(ScriptedDie(listOf(12)), MemorySavedGameStore(), narrator)

        viewModel.start("Ada", CharacterClass.WARRIOR)
        viewModel.submitText("hello")
        viewModel.choose(PlayerIntent.Attack)
        val afterAttack = viewModel.uiState.value.game!!
        narrator.release()

        assertEquals(afterAttack, viewModel.uiState.value.game)
        assertEquals(9, afterAttack.character.health)
        assertTrue(afterAttack.character.inventory.any { it.id == "coin_purse" })
    }
}

private class ScriptedNarrator(private val payload: String) : Narrator {
    override suspend fun complete(request: NarratorRequest): String = payload
}

private class BrokenNarrator : Narrator {
    override suspend fun complete(request: NarratorRequest): String {
        throw IOException("down")
    }
}

private class MappedNarrator(
    private val payloads: Map<String, String>,
) : Narrator {
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun complete(request: NarratorRequest): String {
        val gate = CompletableDeferred<Unit>()
        gates[request.actionText] = gate
        gate.await()
        return payloads.getValue(request.actionText)
    }

    fun release(text: String) {
        gates.getValue(text).complete(Unit)
    }
}

private class GatedNarrator(private val payload: String) : Narrator {
    private val gate = CompletableDeferred<Unit>()

    override suspend fun complete(request: NarratorRequest): String {
        gate.await()
        return payload
    }

    fun release() {
        gate.complete(Unit)
    }
}

private class GatedSavedGameStore : SavedGameStore {
    private val gate = CompletableDeferred<Unit>()
    private var gated = true
    var saved: GameState? = null

    override suspend fun load(): GameState? = null

    override suspend fun save(state: GameState) {
        if (gated) {
            gated = false
            gate.await()
        }
        saved = state
    }

    fun release() {
        gate.complete(Unit)
    }
}
