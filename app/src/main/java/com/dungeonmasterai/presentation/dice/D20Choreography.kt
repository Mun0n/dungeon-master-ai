package com.dungeonmasterai.presentation.dice

import kotlin.math.PI

internal data class DiePose(
    val position: Vec3,
    val rotation: Quat,
    val scale: Vec3,
)

/**
 * One authored tumble. The last portion turns that tumble onto the face
 * the engine already rolled. The correction starts at zero, so the pose
 * does not jump.
 */
internal object D20Choreography {
    const val TUMBLE_MS: Long = 2_200L
    const val HOLD_MS: Long = 1_800L
    const val BLEND_START: Float = 0.70f

    private val heightKeys: FloatArray = floatArrayOf(
        0f, 1.10f,
        0.30f, 0f,
        0.50f, 0.36f,
        0.64f, 0f,
        0.78f, 0.14f,
        0.90f, 0f,
        1f, 0f,
    )

    private val cueMarks: List<Pair<Float, DiceCue>> = listOf(
        0.02f to DiceCue.Spin,
        0.30f to DiceCue.Impact,
        0.64f to DiceCue.Bounce,
        0.90f to DiceCue.Bounce,
        1f to DiceCue.Settle,
    )

    fun idle(seconds: Float): DiePose {
        val turn = Quat.fromAxisAngle(Vec3(0.08f, 1f, 0f), seconds * 0.55f)
        val bob = kotlin.math.sin(seconds * 1.3f) * 0.035f
        return DiePose(Vec3(0f, bob, 0f), turn, Vec3(1f, 1f, 1f))
    }

    fun poseAt(progress: Float, face: Int): DiePose {
        val t = progress.coerceIn(0f, 1f)
        return DiePose(
            position = Vec3(0f, sample(heightKeys, t), 0f),
            rotation = orientation(t, D20Model.restRotation(face)),
            scale = squash(t),
        )
    }

    fun cuesCrossing(from: Float, to: Float): List<DiceCue> =
        cueMarks.filter { (mark, _) -> from < mark && mark <= to }.map { it.second }

    fun orientation(progress: Float, target: Quat): Quat {
        val t = progress.coerceIn(0f, 1f)
        val base = tumble(t)
        if (t <= BLEND_START) return base
        val blend = ((t - BLEND_START) / (1f - BLEND_START)).coerceIn(0f, 1f)
        val shaped = blend * blend * (3f - 2f * blend)
        var correction = target * tumble(1f).conjugate()
        if (correction.w < 0f) correction = -correction
        return (slerp(Quat.IDENTITY, correction, shaped) * base).normalized()
    }

    private fun tumble(t: Float): Quat {
        val traveled = 1f - (1f - t) * (1f - t) * (1f - t)
        val primary = Quat.fromAxisAngle(Vec3(0.22f, 0.9f, 0.18f), traveled * 6.4f * PI.toFloat())
        val secondary = Quat.fromAxisAngle(Vec3(0.95f, 0.12f, 0.28f), traveled * 3.7f * PI.toFloat())
        return (primary * secondary).normalized()
    }

    private fun squash(t: Float): Vec3 {
        val impact = pulse(t, center = 0.30f, width = 0.045f) * 0.16f
        val bounce = pulse(t, center = 0.64f, width = 0.04f) * 0.07f
        val squeeze = impact + bounce
        return Vec3(1f + squeeze * 0.45f, 1f - squeeze, 1f + squeeze * 0.45f)
    }

    private fun pulse(t: Float, center: Float, width: Float): Float {
        val distance = kotlin.math.abs(t - center) / width
        if (distance >= 1f) return 0f
        val shaped = 1f - distance
        return shaped * shaped
    }

    private fun sample(keys: FloatArray, t: Float): Float {
        var cursor = 0
        while (cursor + 3 < keys.size && keys[cursor + 2] < t) cursor += 2
        val start = keys[cursor]
        val startValue = keys[cursor + 1]
        if (cursor + 3 >= keys.size) return startValue
        val end = keys[cursor + 2]
        val endValue = keys[cursor + 3]
        val span = (end - start).coerceAtLeast(0.0001f)
        val local = ((t - start) / span).coerceIn(0f, 1f)
        val shaped = local * local * (3f - 2f * local)
        return startValue + (endValue - startValue) * shaped
    }
}
