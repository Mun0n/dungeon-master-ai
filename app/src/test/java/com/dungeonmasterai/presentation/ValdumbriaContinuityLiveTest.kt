package com.dungeonmasterai.presentation

import com.dungeonmasterai.data.AdventureBackend
import com.dungeonmasterai.data.AdventureCommit
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
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.domain.adventure.model.Adventure
import com.dungeonmasterai.domain.adventure.narration.containsPlayerTechnicalToken
import com.dungeonmasterai.domain.adventure.scholar
import com.dungeonmasterai.domain.adventure.state.AdventureResolution
import com.dungeonmasterai.domain.adventure.state.AdventureState
import com.dungeonmasterai.domain.dice.ScriptedDie
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One short live sequence: a passed check, finding the archive, and then entering it.
 * Skipped unless ADVENTURE_LIVE_LLM=1. It does not read or print an API key.
 */
class ValdumbriaContinuityLiveTest {
    @Test
    fun shortContinuitySequence() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_LIVE_LLM") == "1")
        val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
        val report = File("build/reports/milestone-11/continuity-short.txt")
        val lines = mutableListOf<String>()
        val watched = ContinuityWatch(
            HttpAdventureBackend(
                baseUrl = base,
                allowCleartext = true,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 60_000,
            ),
        )
        val session = AdventureSession(
            AdventureEngine(
                loadAdventure(File("src/main/assets/adventures/valdumbria.json").readText()),
                ScriptedDie(listOf(12)),
            ),
            MemoryAdventureGameStore(),
            watched,
        )
        try {
            lines += health(base)
            session.restore()
            val opened = session.start(scholar())
            assertNull(opened.notice)
            repeat(3) { assertNull(session.continuePrologue().notice) }
            listOf(
                "leave_the_cemetery",
                "look_over_the_square",
                "follow_elias_directions",
                "enter_the_archive",
            ).forEach { actionId ->
                val started = System.nanoTime()
                val shown = session.choose(actionId)
                val ms = (System.nanoTime() - started) / 1_000_000
                val text = shown.narration.orEmpty()
                val sent = watched.last
                lines += "$actionId ${ms}ms place=${sent?.locationName} " +
                    "hora=${sent?.hora} life=${sent?.lifeAfter} " +
                    "check=${sent?.checkSuccess} failure=${sent?.failure} text=$text"
                assertNull(shown.notice)
                assertFalse(text.contains("Elia sigue viva", ignoreCase = true))
                assertFalse(text.contains("Elia is alive", ignoreCase = true))
                assertFalse(containsPlayerTechnicalToken(text))
                assertTrue(sent?.text.orEmpty().isNotBlank())
                if (actionId != "enter_the_archive") delay(6_000)
            }
            watched.sent.forEach { sent ->
                assertFalse(recitesConfirmedNumbers(sent.text))
                assertFalse(treatsALaterHourAsNow(sent.text))
            }
            val looked = watched.sent.last { it.actionId == "look_over_the_square" }
            org.junit.Assert.assertEquals("Plaza", looked.locationName)
            org.junit.Assert.assertEquals("22:15", looked.hora)
            org.junit.Assert.assertEquals(10, looked.lifeAfter)
            org.junit.Assert.assertEquals(true, looked.checkSuccess)
            org.junit.Assert.assertEquals(12, looked.roll)
            org.junit.Assert.assertEquals(8, looked.difficulty)
            assertTrue(describesTheSuccessfulLook(looked.text))
            val found = watched.sent.last { it.actionId == "follow_elias_directions" }
            org.junit.Assert.assertEquals("Plaza", found.locationName)
            org.junit.Assert.assertEquals("22:20", found.hora)
            assertTrue(found.text.contains("archivo", ignoreCase = true))
            assertFalse(claimsToEnterTheArchive(found.text))
            val entered = watched.sent.last { it.actionId == "enter_the_archive" }
            assertEqualsPlace(entered)
            assertTrue(claimsToEnterTheArchive(entered.text))
        } finally {
            report.parentFile?.mkdirs()
            report.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    @Test
    fun preparedStatesStayOutsideThenMoveInside() {
        val engine = AdventureEngine(valdumbria(), ScriptedDie(emptyList()))
        val found = engine.resolve(
            plazaThatCanFindTheArchive(engine),
            "follow_elias_directions",
        ) as AdventureResolution.Accepted
        assertEquals("plaza", found.state.locationId)
        assertTrue("archive" in found.state.discoveredLocationIds)
        assertEquals(25, found.state.minutesElapsed)

        val entered = engine.resolve(
            plazaThatCanEnterTheArchive(engine),
            "enter_the_archive",
        ) as AdventureResolution.Accepted
        assertEquals("archive", entered.state.locationId)
        assertEquals(30, entered.state.minutesElapsed)
    }

    /**
     * Two narrations only: finding the archive, then the confirmed move inside.
     * A transient provider failure is recorded and is not called again.
     */
    @Test
    fun archiveBoundaryNarrations() = runBlocking {
        assumeTrue(System.getenv("ADVENTURE_LIVE_LLM") == "1")
        val base = System.getenv("ADVENTURE_BACKEND_URL") ?: "http://127.0.0.1:8000"
        val report = File("build/reports/milestone-11/archive-boundary.txt")
        val lines = mutableListOf<String>()
        val adventure = valdumbria()
        try {
            lines += health(base)
            val found = narrateOnce(adventure, base, plazaThatCanFindTheArchive(AdventureEngine(adventure, ScriptedDie(emptyList()))), "follow_elias_directions")
            lines += found.line
            if (isTransient(found.failure)) {
                lines += "transient ${found.failure}; not retried"
                fail("Transient provider failure ${found.failure}. Not retried.")
            }
            assertNull(found.failure)
            assertEquals("Plaza", found.locationName)
            assertTrue(found.hecho.orEmpty().contains("das con el archivo"))
            assertTrue(found.modelText.isNotBlank())
            assertFalse(describesArchiveInterior(found.modelText))
            assertFalse(claimsToEnterTheArchive(found.modelText))

            val entered = narrateOnce(
                adventure,
                base,
                plazaThatCanEnterTheArchive(AdventureEngine(adventure, ScriptedDie(emptyList()))),
                "enter_the_archive",
            )
            lines += entered.line
            if (isTransient(entered.failure)) {
                lines += "transient ${entered.failure}; not retried"
                fail("Transient provider failure ${entered.failure}. Not retried.")
            }
            assertNull(entered.failure)
            assertEquals("Archivo", entered.locationName)
            assertTrue(entered.hecho.orEmpty().contains("Entras en el archivo"))
            assertTrue(entered.modelText.isNotBlank())
            assertTrue(claimsToEnterTheArchive(entered.modelText) || describesArchiveInterior(entered.modelText))
        } finally {
            report.parentFile?.mkdirs()
            report.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

private fun assertEqualsPlace(sent: SentNarration) {
    org.junit.Assert.assertEquals("Archivo", sent.locationName)
    org.junit.Assert.assertEquals("22:30", sent.hora)
    org.junit.Assert.assertTrue(sent.hecho.orEmpty().contains("Entras en el archivo"))
}

private fun treatsALaterHourAsNow(text: String): Boolean {
    val lower = text.lowercase()
    if ("medianoche" !in lower) return false
    return listOf("es medianoche", "a punto de", "están sonando", "estan sonando", "suenan ahora").any { it in lower }
}

private fun recitesConfirmedNumbers(text: String): Boolean {
    val lower = text.lowercase()
    return listOf(
        "22:05",
        "22:10",
        "22:20",
        "22:30",
        "puntos de vida",
        "supera la prueba",
        "superas la prueba",
        "superas una prueba",
        "no supera la prueba",
        "dificultad",
    ).any { it in lower }
}

private fun describesTheSuccessfulLook(text: String): Boolean {
    val lower = text.lowercase()
    return listOf("campanario", "torre", "sombras", "distingu").any { it in lower }
}

private fun claimsToEnterTheArchive(text: String): Boolean {
    val lower = text.lowercase()
    if (!Regex("""\barchivo\b""").containsMatchIn(lower)) return false
    if (Regex("""llegas al archivo|llegas a el archivo|dentro del archivo|entre los estantes""").containsMatchIn(lower)) {
        return true
    }
    return Regex(
        """\b(entras|entrar|adentras|adentrar|cruzas|cruzar|umbral|traspasas|traspasar|franqueas|franquear)\b""",
    ).containsMatchIn(lower)
}

private data class SentNarration(
    val actionId: String?,
    val locationName: String,
    val hora: String?,
    val hecho: String?,
    val lifeAfter: Int,
    val checkSuccess: Boolean?,
    val roll: Int?,
    val difficulty: Int?,
    val failure: String?,
    val text: String,
)

private class ContinuityWatch(private val inner: AdventureBackend) : AdventureBackend {
    val sent = mutableListOf<SentNarration>()
    val last: SentNarration? get() = sent.lastOrNull()

    override suspend fun interpret(request: InterpretationRequest): BackendCall<InterpretationResponse> =
        inner.interpret(request)

    override suspend fun narrate(request: NarrationRequest): BackendCall<NarrationResponse> {
        val call = inner.narrate(request)
        val check = request.resolvedAction.check
        sent += SentNarration(
            actionId = request.resolvedAction.actionId,
            locationName = request.authorizedContext.locationName,
            hora = request.hora,
            hecho = request.hecho,
            lifeAfter = request.lifeAfter,
            checkSuccess = check?.success,
            roll = check?.roll,
            difficulty = check?.difficulty,
            failure = (call as? BackendCall.Failed)?.code,
            text = (call as? BackendCall.Ready)?.body?.text.orEmpty(),
        )
        return call
    }

    override suspend fun scene(request: SceneRequest): BackendCall<SceneResponse> = inner.scene(request)
}

private fun valdumbria(): Adventure =
    loadAdventure(File("src/main/assets/adventures/valdumbria.json").readText())

private fun plazaThatCanFindTheArchive(engine: AdventureEngine): AdventureState {
    val started = engine.newGame(scholar())
    return started.copy(
        flags = started.flags + ("letter_read" to true),
        minutesElapsed = 15,
    )
}

private fun plazaThatCanEnterTheArchive(engine: AdventureEngine): AdventureState {
    val started = engine.newGame(scholar())
    return started.copy(
        flags = started.flags + ("letter_read" to true),
        discoveredLocationIds = started.discoveredLocationIds + "archive",
        minutesElapsed = 20,
    )
}

private fun describesArchiveInterior(text: String): Boolean {
    val lower = text.lowercase()
    return listOf(
        "estante",
        "anaquel",
        "pasillo",
        "dentro del archivo",
        "en el interior",
        "polvo",
        "lámpara",
        "lampara",
        "pergamino",
        "entre los libros",
        "sala del archivo",
    ).any { it in lower }
}

private fun isTransient(code: String?): Boolean =
    code == "PROVIDER_ERROR" || code == "PROVIDER_TIMEOUT" || code?.contains("429") == true

private data class BoundaryNarration(
    val line: String,
    val failure: String?,
    val locationName: String?,
    val hecho: String?,
    val modelText: String,
)

private suspend fun narrateOnce(
    adventure: Adventure,
    base: String,
    state: AdventureState,
    actionId: String,
): BoundaryNarration {
    val engine = AdventureEngine(adventure, ScriptedDie(emptyList()))
    val store = MemoryAdventureGameStore()
    val saved = store.commit(
        state = state,
        deathEndingId = engine.adventure.deathEndingId,
        midnightEventId = engine.adventure.clock.midnightEventId,
        expectedRevision = null,
        pendingNarration = null,
        sceneProse = emptyMap(),
        recentInteractions = emptyList(),
    )
    check(saved is AdventureCommit.Saved)
    val watched = ContinuityWatch(
        HttpAdventureBackend(
            baseUrl = base,
            allowCleartext = true,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 60_000,
        ),
    )
    val session = AdventureSession(engine, store, watched)
    session.restore()
    val started = System.nanoTime()
    val shown = session.choose(actionId)
    val ms = (System.nanoTime() - started) / 1_000_000
    val sent = watched.last
    val modelText = sent?.text.orEmpty()
    return BoundaryNarration(
        line = "$actionId ${ms}ms place=${sent?.locationName} hora=${sent?.hora} " +
            "failure=${sent?.failure} notice=${shown.notice} model=$modelText",
        failure = sent?.failure,
        locationName = sent?.locationName,
        hecho = sent?.hecho,
        modelText = modelText,
    )
}

private fun health(base: String): String {
    val connection = URI("$base/health").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
}
