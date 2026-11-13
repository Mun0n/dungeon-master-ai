package com.dungeonmasterai.presentation.dice

internal enum class DiceCue {
    Appear,
    ShakeAccepted,
    Spin,
    Impact,
    Bounce,
    Settle,
    NaturalOne,
    NaturalTwenty,
}

internal fun interface DiceCueSink {
    fun onCue(cue: DiceCue)
}

internal object SilentDiceCues : DiceCueSink {
    override fun onCue(cue: DiceCue) = Unit
}

internal class DiceFeedback(
    private val haptics: DiceCueSink,
    private val sound: DiceCueSink = SilentDiceCues,
) : DiceCueSink {
    override fun onCue(cue: DiceCue) {
        haptics.onCue(cue)
        sound.onCue(cue)
    }
}

/**
 * A natural 1 or 20 is a presentation accent.
 * The success flag stays the one [com.dungeonmasterai.domain.dice.resolveCheck] stored.
 */
internal fun naturalAccent(roll: Int): DiceCue? = when (roll) {
    1 -> DiceCue.NaturalOne
    20 -> DiceCue.NaturalTwenty
    else -> null
}
