package com.dungeonmasterai.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.dungeonmasterai.R
import java.io.IOException

/**
 * Looping music for the whole app session. The activity starts it while the
 * screen is visible and pauses it when the app leaves the foreground.
 * The rules engine does not call this.
 */
internal class BackgroundMusic(context: Context) {
    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager =
        requireNotNull(appContext.getSystemService(AudioManager::class.java))
    private var player: MediaPlayer? = null
    private var resumeAfterTransientLoss: Boolean = false

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MUSIC_ATTRIBUTES)
            .setOnAudioFocusChangeListener(::onFocusChange)
            .build()

    fun start() {
        val media = player ?: open() ?: return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return
        try {
            media.setVolume(FULL_VOLUME, FULL_VOLUME)
            if (!media.isPlaying) {
                media.start()
                Log.i(TAG, "Playing b_minor_arpeggio")
            }
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Background music could not start.", error)
            closePlayer()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun pause() {
        resumeAfterTransientLoss = false
        val media = player
        if (media != null) {
            try {
                if (media.isPlaying) media.pause()
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Background music could not pause.", error)
                closePlayer()
            }
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun open(): MediaPlayer? {
        val created = MediaPlayer()
        try {
            created.setAudioAttributes(MUSIC_ATTRIBUTES)
            appContext.resources.openRawResourceFd(R.raw.b_minor_arpeggio).use { descriptor ->
                created.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            created.isLooping = true
            created.prepare()
            player = created
            return created
        } catch (error: IOException) {
            Log.e(TAG, "Background music could not be prepared.", error)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Background music could not be prepared.", error)
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Background music could not be prepared.", error)
        }
        created.release()
        player = null
        return null
    }

    private fun closePlayer() {
        player?.release()
        player = null
    }

    private fun onFocusChange(change: Int) {
        val media = player ?: return
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    media.setVolume(FULL_VOLUME, FULL_VOLUME)
                    if (resumeAfterTransientLoss) {
                        resumeAfterTransientLoss = false
                        media.start()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeAfterTransientLoss = false
                    if (media.isPlaying) media.pause()
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeAfterTransientLoss = media.isPlaying
                    if (media.isPlaying) media.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    media.setVolume(DUCK_VOLUME, DUCK_VOLUME)
                }
            }
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Background music lost its player during a focus change.", error)
            closePlayer()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object {
        const val TAG: String = "BackgroundMusic"
        const val FULL_VOLUME: Float = 1f
        const val DUCK_VOLUME: Float = 0.2f
        val MUSIC_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
