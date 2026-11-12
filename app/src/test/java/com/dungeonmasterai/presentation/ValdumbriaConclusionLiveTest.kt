package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.BackendCall
import com.dungeonmasterai.data.HttpAdventureBackend
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
import com.dungeonmasterai.domain.adventure.state.AdventureLog
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One recorded Groq run for the authored conclusion.
 * Skipped unless ADVENTURE_LIVE_LLM=1. It does not read or print an API key.
 */
class ValdumbriaConclusionLiveTest {
    @Test
    fun threeSpanishConclusionsAgainstGroq() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_LIVE_LLM") == "1")
        val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
        val report = StringBuilder()
        val file = File("build/reports/valdumbria-conclusion.txt")
        try {
            val health = health(base)
            report.appendLine("health $health")
            val clean = proofBeforeDeduction()
            val adventure = valdumbriaDocument()
            val sentences = listOf(
                "incomplete" to INCOMPLETE,
                "false" to FALSE,
                "correct" to CORRECT,
            )
            sentences.forEach { (label, sentence) ->
                val attempts = 3
                var settled = false
                repeat(attempts) { attempt ->
                    if (settled) return@repeat
                    // The configured Groq model allows about eight thousand tokens per minute.
                    delay(75_000)
                    val backend = ConclusionRecordingBackend(
                        HttpAdventureBackend(
                            baseUrl = base,
                            allowCleartext = true,
                            connectTimeoutMs = 5_000,
                            readTimeoutMs = 90_000,
                        ),
                    )
                    val store = MemoryAdventureGameStore()
                    store.commit(
                        state = clean,
                        deathEndingId = adventure.deathEndingId,
                        midnightEventId = adventure.clock.midnightEventId,
                        expectedRevision = null,
                        pendingNarration = null,
                        sceneProse = emptyMap(),
                        recentInteractions = emptyList(),
                    )
                    val session = AdventureSession(
                        AdventureEngine(adventure, ScriptedDie(emptyList())),
                        store,
                        backend,
                    )
                    session.restore()
                    val shown = session.submitText(sentence)
                    val state = shown.record?.state
                    val call = backend.lastCall
                    val claims = (call as? BackendCall.Ready)?.body?.claims
                        ?.joinToString { "${it.claimId}=${it.optionId}" }
                    val failure = (call as? BackendCall.Failed)?.code
                    report.appendLine("attempt ${attempt + 1} sentence $label: $sentence")
                    report.appendLine("status ${backend.interpretation?.status} claims $claims error $failure")
                    report.appendLine(
                        "truth=${state?.flags?.get("truth_known")} clock=${state?.minutesElapsed} " +
                            "stamp=${state?.worldStamp} deductions=${state?.deductionIds} " +
                            "accepted=${state?.history?.count { it is AdventureLog.DeductionAccepted }} " +
                            "notice=${shown.notice}",
                    )
                    if (failure != null && attempt < attempts - 1) {
                        report.appendLine("unusable reply is not a classification")
                        return@repeat
                    }
                    val answers = (call as? BackendCall.Ready)?.body?.claims
                        ?.associate { it.claimId to it.optionId }
                        .orEmpty()
                    val verdict = if (answers.isEmpty()) {
                        null
                    } else {
                        classifyConclusion(
                            adventure.deductions.single { it.id == "name_the_crime" },
                            answers,
                        )
                    }
                    report.appendLine("verdict $verdict")
                    settled = true
                    if (label == "correct") {
                        assertEquals(ConclusionVerdict.ACCEPTED, verdict)
                        assertEquals(true, state?.flags?.get("truth_known"))
                        assertEquals(setOf("name_the_crime"), state?.deductionIds)
                        assertEquals(260, state?.minutesElapsed)
                        assertEquals(clean.worldStamp, state?.worldStamp)
                        assertEquals(1, state?.history?.count { it is AdventureLog.DeductionAccepted })
                    } else if (label == "false") {
                        assertEquals(null, failure)
                        assertEquals(ConclusionVerdict.FALSE_ACCUSATION, verdict)
                        assertEquals(AdventureSession.CONCLUSION_REJECTED, shown.notice)
                        assertEquals(clean, state)
                    } else {
                        assertEquals(null, failure)
                        assertEquals(ConclusionVerdict.PARTIAL, verdict)
                        assertEquals(AdventureSession.CONCLUSION_REJECTED, shown.notice)
                        assertEquals(clean, state)
                    }
                }
            }
        } finally {
            file.parentFile?.mkdirs()
            file.writeText(report.toString())
        }
        assertTrue(file.isFile)
    }

    private fun health(base: String): String {
        val connection = URI(base.trimEnd('/') + "/health").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}

private class ConclusionRecordingBackend(
    private val delegate: AdventureBackend,
) : AdventureBackend {
    var interpretation: InterpretationResponse? = null
    var lastCall: BackendCall<InterpretationResponse>? = null

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> {
        val call = delegate.interpret(request)
        lastCall = call
        interpretation = (call as? BackendCall.Ready)?.body
        return call
    }

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> =
        delegate.narrate(request)

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> =
        delegate.scene(request)
}

private const val CORRECT: String =
    "Padre Aldren es quien mata ahora en el pueblo. Durante la epidemia encerró a personas sanas con los enfermos, hizo manipular los registros para ocultar aquel crimen, y los asesinatos recientes sirven para que ese encierro no salga a la luz."

private const val INCOMPLETE: String =
    "Padre Aldren encerró a personas sanas con los enfermos. No sé qué pasó con los registros ni por qué siguen los asesinatos."

private const val FALSE: String =
    "Don Beltrán es el asesino. La criatura del cementerio hizo el resto. Los registros del archivo están bien."
