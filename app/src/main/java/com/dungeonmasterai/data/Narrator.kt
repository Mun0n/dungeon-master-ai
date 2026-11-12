package com.dungeonmasterai.data

data class NarratorRequest(
    val sceneId: String,
    val actionText: String,
    val characterName: String,
    val outcomeName: String,
)

interface Narrator {
    suspend fun complete(request: NarratorRequest): String
}
