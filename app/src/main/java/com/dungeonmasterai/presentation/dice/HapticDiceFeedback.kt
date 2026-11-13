package com.dungeonmasterai.presentation.dice

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

internal class HapticDiceFeedback(context: Context) : DiceCueSink {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val resolver = context.contentResolver

    override fun onCue(cue: DiceCue) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        @Suppress("DEPRECATION")
        val hapticsEnabled = Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        if (!hapticsEnabled) return
        val (millis, amplitude) = when (cue) {
            DiceCue.Appear -> 12 to 40
            DiceCue.ShakeAccepted -> 18 to 70
            DiceCue.Spin -> 14 to 48
            DiceCue.Impact -> 26 to 110
            DiceCue.Bounce -> 12 to 55
            DiceCue.Settle -> 20 to 80
            DiceCue.NaturalOne -> 36 to 150
            DiceCue.NaturalTwenty -> 32 to 130
        }
        val strength = if (vibrator.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(millis.toLong(), strength))
    }
}
