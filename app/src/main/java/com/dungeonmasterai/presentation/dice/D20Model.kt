package com.dungeonmasterai.presentation.dice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal data class D20Face(
    val value: Int,
    val normal: Vec3,
    val up: Vec3,
    val numberCorners: List<Vec2>,
)

internal data class D20Vertex(
    val position: Vec3,
    val orientation: Quat,
    val uv: Vec2,
)

internal data class D20Mesh(
    val vertices: List<D20Vertex>,
    val indices: ShortArray,
)

/**
 * One icosahedron. Opposite faces sum to 21.
 * The mesh is built once and shared by the renderer and the pose tests.
 */
internal object D20Model {
    const val ATLAS_COLUMNS: Int = 5
    const val ATLAS_ROWS: Int = 4
    val camera: Vec3 = Vec3(0f, 0.42f, 3.6f)
    val towardCamera: Vec3 = camera.normalized()

    val faces: List<D20Face>
    val mesh: D20Mesh

    init {
        val built = build()
        faces = built.first
        mesh = built.second
        check(faces.size == 20) { "A d20 has 20 faces, found ${faces.size}." }
        check(faces.map { it.value }.toSet().size == 20) { "Face values must be 1 through 20." }
    }

    fun face(value: Int): D20Face {
        require(value in 1..20) { "Die face must be 1..20, was $value." }
        return faces.first { it.value == value }
    }

    fun restRotation(value: Int): Quat {
        val face = face(value)
        val from = basis(face.normal, face.up)
        val to = basis(towardCamera, upright(towardCamera))
        return rotationBetween(from, to)
    }

    fun numberAnchor(value: Int): Vec2 {
        val corners = face(value).numberCorners
        return Vec2(
            corners.sumOf { it.x.toDouble() }.toFloat() / corners.size,
            corners.sumOf { it.y.toDouble() }.toFloat() / corners.size,
        )
    }

    private fun upright(normal: Vec3): Vec3 {
        val hint = if (kotlin.math.abs(normal.y) > 0.92f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
        return (hint - normal * hint.dot(normal)).normalized()
    }

    private fun build(): Pair<List<D20Face>, D20Mesh> {
        val phi = (1f + sqrt(5f)) / 2f
        val raw = listOf(
            Vec3(0f, 1f, phi),
            Vec3(0f, -1f, phi),
            Vec3(0f, 1f, -phi),
            Vec3(0f, -1f, -phi),
            Vec3(1f, phi, 0f),
            Vec3(-1f, phi, 0f),
            Vec3(1f, -phi, 0f),
            Vec3(-1f, -phi, 0f),
            Vec3(phi, 0f, 1f),
            Vec3(-phi, 0f, 1f),
            Vec3(phi, 0f, -1f),
            Vec3(-phi, 0f, -1f),
        )
        val radius = raw.first().length()
        val vertices = raw.map { it * (1f / radius) }
        val unnumbered = mutableListOf<Triangle>()
        for (first in vertices.indices) {
            for (second in first + 1 until vertices.size) {
                for (third in second + 1 until vertices.size) {
                    if (!edge(raw[first], raw[second]) ||
                        !edge(raw[second], raw[third]) ||
                        !edge(raw[third], raw[first])
                    ) {
                        continue
                    }
                    val a = vertices[first]
                    val b = vertices[second]
                    val c = vertices[third]
                    val wound = (b - a).cross(c - a)
                    val centroid = (a + b + c) * (1f / 3f)
                    val (left, middle, right) = if (wound.dot(centroid) > 0f) {
                        Triple(a, b, c)
                    } else {
                        Triple(a, c, b)
                    }
                    val normal = (middle - left).cross(right - left).normalized()
                    unnumbered += Triangle(left, middle, right, normal)
                }
            }
        }
        check(unnumbered.size == 20) { "Icosahedron construction found ${unnumbered.size} faces." }
        val numbered = numberFaces(unnumbered)
        val published = numbered.map { face ->
            D20Face(
                value = face.value,
                normal = face.triangle.normal,
                up = upright(face.triangle.normal),
                numberCorners = orientedUvs(face.value, inset(face.triangle)),
            )
        }
        return published to meshOf(numbered, published)
    }

    private fun edge(start: Vec3, end: Vec3): Boolean {
        val distance = (start - end).length()
        return kotlin.math.abs(distance - 2f) < 0.02f
    }

    private fun numberFaces(triangles: List<Triangle>): List<NumberedFace> {
        val remaining = triangles.indices.toMutableSet()
        val numbered = MutableList<NumberedFace?>(triangles.size) { null }
        var low = 1
        while (remaining.isNotEmpty()) {
            val index = remaining.minOrNull() ?: break
            val opposite = remaining.minBy { triangles[it].normal.dot(triangles[index].normal) }
            check(opposite != index) { "A face cannot be opposite itself." }
            check(triangles[index].normal.dot(triangles[opposite].normal) < -0.9f) {
                "Opposite faces are not aligned."
            }
            numbered[index] = NumberedFace(triangles[index], low)
            numbered[opposite] = NumberedFace(triangles[opposite], 21 - low)
            remaining.remove(index)
            remaining.remove(opposite)
            low += 1
        }
        return numbered.map { checkNotNull(it) }
    }

    private fun meshOf(faces: List<NumberedFace>, published: List<D20Face>): D20Mesh {
        val vertices = mutableListOf<D20Vertex>()
        val indices = mutableListOf<Short>()
        fun add(position: Vec3, normal: Vec3, uv: Vec2) {
            val up = upright(normal)
            val frame = basis(normal, up)
            vertices += D20Vertex(position, quaternion(frame.x, frame.y, frame.z), uv)
        }
        fun triangle(a: Vec3, b: Vec3, c: Vec3, normal: Vec3, uvs: List<Vec2>) {
            val start = vertices.size
            add(a, normal, uvs[0])
            add(b, normal, uvs[1])
            add(c, normal, uvs[2])
            indices += start.toShort()
            indices += (start + 1).toShort()
            indices += (start + 2).toShort()
        }
        faces.forEach { face ->
            val inset = inset(face.triangle)
            val glyph = published.first { it.value == face.value }.numberCorners
            triangle(inset.a, inset.b, inset.c, face.triangle.normal, glyph)
            val outer = listOf(face.triangle.a, face.triangle.b, face.triangle.c)
            val pulled = listOf(inset.a, inset.b, inset.c)
            for (index in 0 until 3) {
                val next = (index + 1) % 3
                side(
                    outerA = outer[index],
                    outerB = outer[next],
                    innerA = pulled[index],
                    innerB = pulled[next],
                    add = ::triangle,
                )
            }
        }
        return D20Mesh(vertices, indices.toShortArray())
    }

    internal fun faceCorners(value: Int): List<Vec2> = face(value).numberCorners

    /**
     * Places the number so it reads upright when the face points at the camera.
     * Texture V grows downward, matching the atlas. Screen-right grows U, and
     * screen-up shrinks V. A fixed triangle left every glyph mirrored.
     */
    private fun orientedUvs(value: Int, triangle: Triangle): List<Vec2> {
        val frame = basis(triangle.normal, upright(triangle.normal))
        val center = (triangle.a + triangle.b + triangle.c) * (1f / 3f)
        val plane = listOf(triangle.a, triangle.b, triangle.c).map { point ->
            val offset = point - center
            offset.dot(frame.x) to offset.dot(frame.y)
        }
        val reach = plane.maxOf { (right, up) -> max(abs(right), abs(up)) }.coerceAtLeast(0.0001f)
        val anchor = cellAnchor(value)
        val scale = NUMBER_RADIUS / reach
        return plane.map { (right, up) ->
            Vec2(anchor.x + right * scale, anchor.y - up * scale)
        }
    }

    private fun cellAnchor(value: Int): Vec2 {
        val index = value - 1
        val column = index % ATLAS_COLUMNS
        val row = index / ATLAS_COLUMNS
        return Vec2(
            (column + 0.5f) / ATLAS_COLUMNS,
            (row + 0.44f) / ATLAS_ROWS,
        )
    }

    private fun inset(triangle: Triangle): Triangle {
        val center = (triangle.a + triangle.b + triangle.c) * (1f / 3f)
        fun pull(point: Vec3): Vec3 = center + (point - center) * 0.82f
        return triangle.copy(a = pull(triangle.a), b = pull(triangle.b), c = pull(triangle.c))
    }

    private fun side(
        outerA: Vec3,
        outerB: Vec3,
        innerA: Vec3,
        innerB: Vec3,
        add: (Vec3, Vec3, Vec3, Vec3, List<Vec2>) -> Unit,
    ) {
        val edge = listOf(EDGE_UV, EDGE_UV, EDGE_UV)
        val normal = (outerB - outerA).cross(innerA - outerA)
        val midpoint = (outerA + outerB + innerA + innerB) * 0.25f
        if (normal.dot(midpoint) >= 0f) {
            val outward = normal.normalized()
            add(outerA, outerB, innerB, outward, edge)
            add(outerA, innerB, innerA, outward, edge)
        } else {
            val outward = (-normal).normalized()
            add(outerA, innerB, outerB, outward, edge)
            add(outerA, innerA, innerB, outward, edge)
        }
    }

    private data class Triangle(val a: Vec3, val b: Vec3, val c: Vec3, val normal: Vec3)

    private data class NumberedFace(val triangle: Triangle, val value: Int)
}

private const val NUMBER_RADIUS: Float = 0.062f

private val EDGE_UV: Vec2 = Vec2(0.002f, 0.002f)
