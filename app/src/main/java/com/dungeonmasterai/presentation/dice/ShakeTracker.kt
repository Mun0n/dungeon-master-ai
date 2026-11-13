package com.dungeonmasterai.presentation.dice

/**
 * Turns accelerometer samples into one intentional shake.
 * Gravity is removed with a low-pass. A slow tilt does not qualify.
 * The tracker does not read a gyroscope.
 */
internal class ShakeTracker {
    private var gravity: Vec3? = null
    private var armedAt: Long = 0L
    private var quiet: Boolean = false
    private var fired: Boolean = false

    fun arm(nowMs: Long) {
        gravity = null
        armedAt = nowMs
        quiet = false
        fired = false
    }

    fun sample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (fired) return false
        val acceleration = Vec3(x, y, z)
        val current = gravity
        if (current == null) {
            gravity = acceleration
            return false
        }
        gravity = current * (1f - GRAVITY_WEIGHT) + acceleration * GRAVITY_WEIGHT
        val linear = (acceleration - gravity!!).length()
        if (nowMs - armedAt < ARM_MS) return false
        if (linear < QUIET) quiet = true
        if (quiet && linear >= SHAKE) {
            fired = true
            return true
        }
        return false
    }

    private companion object {
        const val GRAVITY_WEIGHT: Float = 0.12f
        const val ARM_MS: Long = 400L
        const val QUIET: Float = 4f
        const val SHAKE: Float = 14f
    }
}
