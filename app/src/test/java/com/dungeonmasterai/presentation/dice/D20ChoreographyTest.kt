package com.dungeonmasterai.presentation.dice

import com.dungeonmasterai.domain.dice.Difficulty
import com.dungeonmasterai.domain.dice.resolveCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class D20ChoreographyTest {
    @Test
    fun oppositeFacesSumToTwentyOne() {
        D20Model.faces.forEach { face ->
            val opposite = D20Model.faces.minBy { it.normal.dot(face.normal) }
            assertEquals(21, face.value + opposite.value)
            assertTrue(face.normal.dot(opposite.normal) < -0.95f)
        }
    }

    @Test
    fun numbersReadUprightWhenTheFacePointsAtTheCamera() {
        for (value in 1..20) {
            val face = D20Model.face(value)
            val samples = D20Model.mesh.vertices.filter { vertex ->
                face.numberCorners.any { corner ->
                    abs(vertex.uv.x - corner.x) < 0.0001f && abs(vertex.uv.y - corner.y) < 0.0001f
                }
            }
            assertEquals("Face $value", 3, samples.size)
            val right = basis(face.normal, face.up).x
            val origin = samples[0]
            val across = samples[1].uv.x - origin.uv.x
            val down = samples[1].uv.y - origin.uv.y
            val acrossNext = samples[2].uv.x - origin.uv.x
            val downNext = samples[2].uv.y - origin.uv.y
            val edge = samples[1].position - origin.position
            val edgeNext = samples[2].position - origin.position
            val det = across * downNext - acrossNext * down
            assertTrue("Face $value has a flat number", abs(det) > 0.000001f)
            val alongU = (edge * downNext - edgeNext * down) * (1f / det)
            val alongV = (edgeNext * across - edge * acrossNext) * (1f / det)
            assertTrue("Face $value leans ${alongU.dot(right)}", alongU.dot(right) / alongU.length() > 0.98f)
            assertTrue("Face $value is upside down ${alongV.dot(face.up)}", alongV.dot(face.up) / alongV.length() < -0.98f)
            val anchor = D20Model.numberAnchor(value)
            val column = (value - 1) % D20Model.ATLAS_COLUMNS
            val row = (value - 1) / D20Model.ATLAS_COLUMNS
            val left = column.toFloat() / D20Model.ATLAS_COLUMNS
            val top = row.toFloat() / D20Model.ATLAS_ROWS
            face.numberCorners.forEach { corner ->
                val paintedU = anchor.x + (corner.x - anchor.x) * 1.18f
                val paintedV = anchor.y + (corner.y - anchor.y) * 1.18f
                assertTrue(paintedU > left && paintedU < left + 1f / D20Model.ATLAS_COLUMNS)
                assertTrue(paintedV > top && paintedV < top + 1f / D20Model.ATLAS_ROWS)
            }
        }
    }

    @Test
    fun everyFaceCanBeTurnedTowardTheCamera() {
        for (value in 1..20) {
            val pose = D20Choreography.poseAt(1f, value)
            val face = D20Model.face(value)
            val shown = pose.rotation.rotate(face.normal)
            val upright = pose.rotation.rotate(face.up)
            assertTrue("Face $value normal ${shown.dot(D20Model.towardCamera)}", shown.dot(D20Model.towardCamera) > 0.98f)
            val cameraUp = uprightOn(D20Model.towardCamera)
            assertTrue("Face $value up ${upright.dot(cameraUp)}", upright.dot(cameraUp) > 0.98f)
        }
    }

    @Test
    fun theFinalTurnDoesNotJump() {
        for (value in 1..20) {
            val atBlend = D20Choreography.orientation(D20Choreography.BLEND_START, D20Model.restRotation(value))
            val justAfter = D20Choreography.orientation(D20Choreography.BLEND_START + 0.01f, D20Model.restRotation(value))
            val step = angleBetween(atBlend, justAfter)
            assertTrue("Face $value stepped $step", step < 0.35f)
        }
    }

    @Test
    fun anIdentityPoseKeepsTheAxes() {
        val matrix = poseMatrix(DiePose(Vec3(0f, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)))
        assertEquals(1f, matrix[0], 0.0001f)
        assertEquals(1f, matrix[5], 0.0001f)
        assertEquals(1f, matrix[10], 0.0001f)
        assertEquals(1f, matrix[15], 0.0001f)
        assertTrue(abs(matrix[1]) < 0.0001f)
    }

    @Test
    fun aNaturalFaceDoesNotChangeTheVerdict() {
        val failure = resolveCheck(1, attribute = 5, difficulty = Difficulty.EASY)
        val success = resolveCheck(20, attribute = 1, difficulty = Difficulty.VERY_HARD)
        assertEquals(false, failure.success)
        assertEquals(true, success.success)
        assertEquals(DiceCue.NaturalOne, naturalAccent(failure.roll))
        assertEquals(DiceCue.NaturalTwenty, naturalAccent(success.roll))
        assertEquals(null, naturalAccent(12))
    }

    private fun uprightOn(normal: Vec3): Vec3 {
        val hint = Vec3(0f, 1f, 0f)
        return (hint - normal * hint.dot(normal)).normalized()
    }
}
