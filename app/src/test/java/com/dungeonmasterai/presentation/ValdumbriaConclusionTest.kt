package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.ClaimAnswerBody
import com.dungeonmasterai.data.InterpretationRequest
import com.dungeonmasterai.data.InterpretationResponse
import com.dungeonmasterai.data.MemoryAdventureGameStore
import com.dungeonmasterai.data.NarrationRequest
import com.dungeonmasterai.data.NarrationResponse
import com.dungeonmasterai.data.SceneRequest
import com.dungeonmasterai.data.SceneResponse
import com.dungeonmasterai.domain.adventure.engine.AdventureEngine
import com.dungeonmasterai.domain.adventure.engine.ConclusionVerdict
import com.dungeonmasterai.domain.adventure.engine.classifyConclusion
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.ScriptedDie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValdumbriaConclusionTest {
    @Test
    fun aCorrectConclusionCallsDeduceOnceAndShowsTheAuthoredLine() = runBlocking {
        val harness = harness()
        val before = harness.session.restore()
        val shown = harness.session.submitText(CORRECT_SENTENCE)
        assertEquals(1, harness.engine.deductions)
        assertEquals(0, harness.backend.narrations)
        assertEquals(AUTHORED_LINE, shown.narration)
        assertEquals(260, shown.record?.state?.minutesElapsed)
        assertEquals(before.record?.state?.worldStamp, shown.record?.state?.worldStamp)
        assertEquals(true, shown.record?.state?.flags?.get("truth_known"))
        assertEquals(setOf("name_the_crime"), shown.record?.state?.deductionIds)
        assertTrue(
            shown.record?.state?.history?.filterIsInstance<AdventureLog.DeductionAccepted>()?.size == 1,
        )
        assertFalse(shown.narration.orEmpty().contains("_"))
        val offer = harness.backend.lastInterpret?.authorizedContext?.availableConclusions?.single()
        assertEquals("name_the_crime", offer?.id)
        assertEquals(
            listOf("recent_killings", "confinement", "records", "connection"),
            offer?.claims?.map { it.id },
        )
        assertFalse(offer?.claims.orEmpty().any { claim -> claim.prompt.contains(AUTHORED_LINE) })
        assertTrue(offer?.claims.orEmpty().all { claim -> claim.options.none { '_' in it.gloss } })
    }

    @Test
    fun aWrongOrIncompleteConclusionDoesNotChangeTheConfirmedState() = runBlocking {
        val cases = listOf(
            ConclusionVerdict.PARTIAL to answers(connection = "unknown"),
            ConclusionVerdict.PARTIAL to answers(confinement = "treated_and_released"),
            ConclusionVerdict.FALSE_ACCUSATION to answers(recent = "beltran"),
            ConclusionVerdict.FALSE_ACCUSATION to answers(recent = "creature"),
            ConclusionVerdict.FALSE_ACCUSATION to answers(recent = "mara"),
            ConclusionVerdict.FALSE_ACCUSATION to answers(recent = "tomas"),
            ConclusionVerdict.CONTRADICTION to answers(
                confinement = "never_happened",
                connection = "recent_killings_conceal_the_epidemic",
            ),
            ConclusionVerdict.CONTRADICTION to answers(
                records = "records_are_accurate",
                connection = "recent_killings_conceal_the_epidemic",
            ),
            ConclusionVerdict.FALSE_ACCUSATION to answers(
                recent = "beltran",
                confinement = "never_happened",
                connection = "recent_killings_conceal_the_epidemic",
            ),
        )
        val deduction = valdumbriaDocument().deductions.single { it.id == "name_the_crime" }
        cases.forEach { (verdict, claims) ->
            assertEquals(verdict, classifyConclusion(deduction, claims.toMap()))
            val harness = harness(claims)
            val before = harness.session.restore().record?.state
            val shown = harness.session.submitText("Una conclusión.")
            assertEquals(0, harness.engine.deductions)
            assertEquals(0, harness.backend.narrations)
            assertEquals("Esa conclusión no se sostiene.", shown.notice)
            assertEquals(before, shown.record?.state)
            assertFalse(shown.narration.orEmpty().contains(AUTHORED_LINE))
        }
    }

    @Test
    fun anIneligibleOrMalformedConclusionDoesNotCallDeduce() = runBlocking {
        val missing = proofBeforeDeduction().copy(evidenceIds = proofBeforeDeduction().evidenceIds - "evidence_elia")
        val ineligible = harness(ACCEPTED, missing)
        val before = ineligible.session.restore().record?.state
        val declined = ineligible.session.submitText(CORRECT_SENTENCE)
        assertEquals(0, ineligible.engine.deductions)
        assertEquals(before, declined.record?.state)
        assertEquals("Esa conclusión no se sostiene.", declined.notice)

        val withAction = harness(ACCEPTED, actionId = "read_elias_notes")
        val untouched = withAction.session.restore().record?.state
        val mixed = withAction.session.submitText("Quiero concluir y además leer.")
        assertEquals(0, withAction.engine.deductions)
        assertEquals(untouched, mixed.record?.state)

        val unknownClaim = ACCEPTED.map {
            if (it.first == "connection") "not_a_claim" to it.second else it
        }
        val malformed = harness(unknownClaim)
        val prior = malformed.session.restore().record?.state
        val rejected = malformed.session.submitText("Otra frase.")
        assertEquals(0, malformed.engine.deductions)
        assertEquals(prior, rejected.record?.state)

        val named = harness(emptyList(), status = "RECOGNIZED", actionId = "name_the_crime")
        val idle = named.session.restore().record?.state
        val namedResult = named.session.submitText("name_the_crime no es un botón.")
        assertEquals(0, named.engine.deductions)
        assertEquals(idle, namedResult.record?.state)
        assertEquals(false, namedResult.record?.state?.flags?.get("truth_known"))
    }

    @Test
    fun aButtonCannotGrantTheDeductionAndASecondConclusionIsIgnored() = runBlocking {
        val harness = harness()
        val chosen = harness.session.restore().let { harness.session.choose("name_the_crime") }
        assertEquals(PlayerCopy.ACTION_UNAVAILABLE, chosen.notice)
        assertEquals(false, chosen.record?.state?.flags?.get("truth_known"))
        assertEquals(0, harness.engine.deductions)

        val offline = AdventureSession(
            CountingEngine(valdumbriaDocument(), ScriptedDie(emptyList())),
            storedProof(),
            backend = null,
        )
        val quiet = offline.restore().let { offline.submitText(CORRECT_SENTENCE) }
        assertEquals(false, quiet.record?.state?.flags?.get("truth_known"))
        assertNull(quiet.record?.state?.deductionIds?.singleOrNull())

        val accepted = harness(ACCEPTED)
        accepted.session.restore()
        accepted.session.submitText(CORRECT_SENTENCE)
        assertEquals(1, accepted.engine.deductions)
        val again = accepted.session.submitText(CORRECT_SENTENCE)
        assertEquals(1, accepted.engine.deductions)
        assertEquals(260, again.record?.state?.minutesElapsed)
        assertEquals(setOf("name_the_crime"), again.record?.state?.deductionIds)
        assertEquals("Esa conclusión no se sostiene.", again.notice)
    }

    private fun harness(
        claims: List<Pair<String, String>> = ACCEPTED,
        state: AdventureState = proofBeforeDeduction(),
        status: String = "CONCLUSION",
        actionId: String? = null,
    ): Harness {
        val engine = CountingEngine(valdumbriaDocument(), ScriptedDie(emptyList()))
        val backend = ScriptedConclusionBackend(status, actionId, claims)
        val session = AdventureSession(engine, stored(state, engine.adventure), backend)
        return Harness(engine, backend, session)
    }

    private fun stored(state: AdventureState, adventure: Adventure): MemoryAdventureGameStore {
        val store = MemoryAdventureGameStore()
        kotlinx.coroutines.runBlocking {
            store.commit(
                state = state,
                deathEndingId = adventure.deathEndingId,
                midnightEventId = adventure.clock.midnightEventId,
                expectedRevision = null,
                pendingNarration = null,
                sceneProse = emptyMap(),
                recentInteractions = emptyList(),
            )
        }
        return store
    }

    private fun storedProof(): MemoryAdventureGameStore =
        stored(proofBeforeDeduction(), valdumbriaDocument())
}

private class Harness(
    val engine: CountingEngine,
    val backend: ScriptedConclusionBackend,
    val session: AdventureSession,
)

private class CountingEngine(
    adventure: Adventure,
    die: Die,
) : AdventureEngine(adventure, die) {
    var deductions: Int = 0

    override fun deduce(
        state: AdventureState,
        deductionId: String,
    ): com.dungeonmasterai.domain.adventure.state.AdventureResolution {
        deductions += 1
        return super.deduce(state, deductionId)
    }
}

private class ScriptedConclusionBackend(
    private val status: String,
    private val actionId: String?,
    private val claims: List<Pair<String, String>>,
) : AdventureBackend {
    var narrations: Int = 0
    var lastInterpret: InterpretationRequest? = null

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> {
        lastInterpret = request
        return BackendCall.Ready(
            InterpretationResponse(
                contractVersion = 1,
                requestId = request.requestId,
                stateRevision = request.stateRevision,
                sceneContractId = request.sceneContract.id,
                status = status,
                candidateActionId = actionId,
                candidateActionIds = emptyList(),
                claims = claims.map { (claimId, optionId) ->
                    ClaimAnswerBody(claimId = claimId, optionId = optionId)
                },
            ),
        )
    }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        narrations += 1
        return BackendCall.Failed(code = "ABSENT", retryable = false)
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        BackendCall.Failed(code = "ABSENT", retryable = false)
}

private const val AUTHORED_LINE: String =
    "Las cuatro pruebas coinciden. Durante la epidemia, Padre Aldren encerró a personas sanas con los enfermos e hizo alterar los registros para ocultar aquel crimen. Los asesinatos recientes, incluida la muerte de Elia, mantienen ese encierro en secreto. La lista escondida lo señala a él. Don Beltrán, Mara, Tomás y la criatura de la taberna no son la causa. Elia no puede volver. Lo que le ocurra a Inés no cambia esta conclusión."

private const val CORRECT_SENTENCE: String =
    "Padre Aldren es quien mata ahora en el pueblo."

private val ACCEPTED: List<Pair<String, String>> = listOf(
    "recent_killings" to "alden",
    "confinement" to "shut_in_with_the_sick",
    "records" to "altered_to_conceal",
    "connection" to "recent_killings_conceal_the_epidemic",
)

private fun answers(
    recent: String = "alden",
    confinement: String = "shut_in_with_the_sick",
    records: String = "altered_to_conceal",
    connection: String = "recent_killings_conceal_the_epidemic",
): List<Pair<String, String>> = listOf(
    "recent_killings" to recent,
    "confinement" to confinement,
    "records" to records,
    "connection" to connection,
)
