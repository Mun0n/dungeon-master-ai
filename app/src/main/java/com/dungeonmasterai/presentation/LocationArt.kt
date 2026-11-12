package com.dungeonmasterai.presentation

import com.dungeonmasterai.R

/**
 * Provisional location stills. Replacing a file keeps the same resource name.
 * The screen shows the picture and the place name. It does not call the picture final.
 */
internal fun locationArtRes(locationId: String): Int? = when (locationId) {
    "plaza" -> R.drawable.valdumbria_plaza
    "tavern" -> R.drawable.valdumbria_tavern
    "church" -> R.drawable.valdumbria_church
    "cemetery" -> R.drawable.valdumbria_cemetery
    "archive" -> R.drawable.valdumbria_archive
    "crypt" -> R.drawable.valdumbria_crypt
    else -> null
}
