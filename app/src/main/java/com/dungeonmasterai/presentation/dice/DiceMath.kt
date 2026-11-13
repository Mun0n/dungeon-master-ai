package com.dungeonmasterai.presentation.dice

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val magnitude = length()
        require(magnitude > 0f) { "Cannot normalize a zero vector." }
        return this * (1f / magnitude)
    }
}

internal data class Vec2(val x: Float, val y: Float)

internal data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    fun dot(other: Quat): Float = x * other.x + y * other.y + z * other.z + w * other.w

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Quat {
        val magnitude = length()
        require(magnitude > 0f) { "Cannot normalize a zero quaternion." }
        return Quat(x / magnitude, y / magnitude, z / magnitude, w / magnitude)
    }

    fun conjugate(): Quat = Quat(-x, -y, -z, w)

    operator fun unaryMinus(): Quat = Quat(-x, -y, -z, -w)

    operator fun times(other: Quat): Quat = Quat(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z,
    )

    fun rotate(vector: Vec3): Vec3 {
        val rotated = this * Quat(vector.x, vector.y, vector.z, 0f) * conjugate()
        return Vec3(rotated.x, rotated.y, rotated.z)
    }

    companion object {
        val IDENTITY: Quat = Quat(0f, 0f, 0f, 1f)

        fun fromAxisAngle(axis: Vec3, radians: Float): Quat {
            val normal = axis.normalized()
            val half = radians * 0.5f
            val scale = sin(half)
            return Quat(normal.x * scale, normal.y * scale, normal.z * scale, cos(half)).normalized()
        }
    }
}

internal fun slerp(start: Quat, end: Quat, t: Float): Quat {
    var target = end
    var dot = start.dot(target)
    if (dot < 0f) {
        target = -target
        dot = -dot
    }
    if (dot > 0.9995f) {
        return Quat(
            start.x + (target.x - start.x) * t,
            start.y + (target.y - start.y) * t,
            start.z + (target.z - start.z) * t,
            start.w + (target.w - start.w) * t,
        ).normalized()
    }
    val theta = acos(dot.coerceIn(-1f, 1f))
    val scale = sin(theta)
    val startWeight = sin((1f - t) * theta) / scale
    val endWeight = sin(t * theta) / scale
    return Quat(
        start.x * startWeight + target.x * endWeight,
        start.y * startWeight + target.y * endWeight,
        start.z * startWeight + target.z * endWeight,
        start.w * startWeight + target.w * endWeight,
    ).normalized()
}

internal data class Basis(val x: Vec3, val y: Vec3, val z: Vec3)

internal fun basis(normal: Vec3, upHint: Vec3): Basis {
    val z = normal.normalized()
    val x = upHint.cross(z).normalized()
    val y = z.cross(x).normalized()
    return Basis(x, y, z)
}

internal fun rotationBetween(from: Basis, to: Basis): Quat {
    val columnX = to.x * from.x.x + to.y * from.y.x + to.z * from.z.x
    val columnY = to.x * from.x.y + to.y * from.y.y + to.z * from.z.y
    val columnZ = to.x * from.x.z + to.y * from.y.z + to.z * from.z.z
    return quaternion(columnX, columnY, columnZ)
}

internal fun quaternion(columnX: Vec3, columnY: Vec3, columnZ: Vec3): Quat {
    val trace = columnX.x + columnY.y + columnZ.z
    val raw = if (trace > 0f) {
        val scale = sqrt(trace + 1f) * 2f
        Quat(
            (columnY.z - columnZ.y) / scale,
            (columnZ.x - columnX.z) / scale,
            (columnX.y - columnY.x) / scale,
            0.25f * scale,
        )
    } else if (columnX.x > columnY.y && columnX.x > columnZ.z) {
        val scale = sqrt(1f + columnX.x - columnY.y - columnZ.z) * 2f
        Quat(
            0.25f * scale,
            (columnX.y + columnY.x) / scale,
            (columnZ.x + columnX.z) / scale,
            (columnY.z - columnZ.y) / scale,
        )
    } else if (columnY.y > columnZ.z) {
        val scale = sqrt(1f + columnY.y - columnX.x - columnZ.z) * 2f
        Quat(
            (columnX.y + columnY.x) / scale,
            0.25f * scale,
            (columnY.z + columnZ.y) / scale,
            (columnZ.x - columnX.z) / scale,
        )
    } else {
        val scale = sqrt(1f + columnZ.z - columnX.x - columnY.y) * 2f
        Quat(
            (columnZ.x + columnX.z) / scale,
            (columnY.z + columnZ.y) / scale,
            0.25f * scale,
            (columnX.y - columnY.x) / scale,
        )
    }
    return raw.normalized()
}

internal fun poseMatrix(pose: DiePose): FloatArray {
    val rotation = pose.rotation.normalized()
    val x = rotation.x
    val y = rotation.y
    val z = rotation.z
    val w = rotation.w
    val x2 = x + x
    val y2 = y + y
    val z2 = z + z
    val xx = x * x2
    val xy = x * y2
    val xz = x * z2
    val yy = y * y2
    val yz = y * z2
    val zz = z * z2
    val wx = w * x2
    val wy = w * y2
    val wz = w * z2
    val sx = pose.scale.x
    val sy = pose.scale.y
    val sz = pose.scale.z
    return floatArrayOf(
        (1f - (yy + zz)) * sx,
        (xy + wz) * sx,
        (xz - wy) * sx,
        0f,
        (xy - wz) * sy,
        (1f - (xx + zz)) * sy,
        (yz + wx) * sy,
        0f,
        (xz + wy) * sz,
        (yz - wx) * sz,
        (1f - (xx + yy)) * sz,
        0f,
        pose.position.x,
        pose.position.y,
        pose.position.z,
        1f,
    )
}

internal fun angleBetween(start: Quat, end: Quat): Float {
    val dot = kotlin.math.abs(start.dot(end)).coerceIn(0f, 1f)
    return acos(dot) * 2f
}
