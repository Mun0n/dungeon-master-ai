package com.dungeonmasterai.presentation

internal data class PublishedAdventure(
    val id: String,
    val genre: String,
    val premise: String,
)

internal data class MenuResume(
    val adventureId: String,
    val place: String,
    val clock: String,
)

internal sealed interface MenuPrimary {
    data class Continue(val detail: String) : MenuPrimary

    data object NewGame : MenuPrimary
}

internal fun publishedAdventures(): List<PublishedAdventure> = listOf(
    PublishedAdventure(
        id = VALDUMBRIA_ID,
        genre = "Terror gótico",
        premise = PlayerCopy.VALDUMBRIA_PREMISE,
    ),
)

internal fun seriesLabel(index: Int, genre: String): String {
    val mark = when (index) {
        0 -> "I"
        1 -> "II"
        2 -> "III"
        else -> (index + 1).toString()
    }
    return "Aventura $mark · $genre"
}

internal fun adventureTitle(id: String, titles: Map<String, String>): String = titles[id].orEmpty()

internal fun menuPrimary(adventureId: String, resume: MenuResume?): MenuPrimary {
    if (resume == null || resume.adventureId != adventureId) return MenuPrimary.NewGame
    val detail = if (resume.place.isBlank()) resume.clock else "${resume.place} · ${resume.clock}"
    return MenuPrimary.Continue(detail)
}

internal fun newGameErasesSave(adventureId: String, resume: MenuResume?): Boolean =
    resume?.adventureId == adventureId
