package com.dungeonmasterai.domain.adventure.load

import com.dungeonmasterai.domain.adventure.model.Adventure
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AdventureLoadException(
    message: String,
) : IllegalArgumentException(message)

internal val adventureJson: Json = Json {
    ignoreUnknownKeys = false
    isLenient = false
    explicitNulls = false
    classDiscriminator = "type"
}

fun loadAdventure(text: String): Adventure {
    val adventure = try {
        adventureJson.decodeFromString(Adventure.serializer(), text)
    } catch (error: SerializationException) {
        throw AdventureLoadException(error.message ?: "Adventure JSON could not be decoded.")
    }
    val errors = validateAdventure(adventure)
    if (errors.isNotEmpty()) {
        throw AdventureLoadException(errors.joinToString("\n"))
    }
    return adventure
}
