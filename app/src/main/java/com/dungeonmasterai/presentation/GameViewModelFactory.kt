package com.dungeonmasterai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dungeonmasterai.data.Narrator
import com.dungeonmasterai.data.SavedGameStore
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.RandomDie

class GameViewModelFactory(
    private val store: SavedGameStore,
    private val die: Die = RandomDie(),
    private val narrator: Narrator? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(GameViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(die, store, narrator) as T
    }
}
