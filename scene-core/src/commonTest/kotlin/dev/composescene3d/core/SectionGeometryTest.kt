package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SectionGeometryTest {
    private val tetrahedron = Geometry3D(
        positions = floatArrayOf(
            1f, 0f, 0f,
            -1f, -1f, -1f,
            -1f, 1f, -1f,
            -1f, 0f, 1f,
        ),
        indices = intArrayOf(0, 1, 2, 0, 3, 1, 0, 2, 3, 1, 3, 2),
        normals = floatArrayOf(
            1f, 0f, 0f,
            -1f, -1f, -1f,
            -1f, 1f, -1f,
            -1f, 0f, 1f,
        ),
    )

    @Test
    fun clipsTrianglesAndGeneratesPlanarCap() {
        val result = tetrahedron.section(ClippingPlane3D(Vec3(1f, 0f, 0f)))
        val surface = assertNotNull(result.surface)
        val cap = assertNotNull(result.cap)

        assertTrue(surface.positions.asList().chunked(3).all { it[0] >= -0.0001f })
        assertTrue(cap.positions.asList().chunked(3).all { kotlin.math.abs(it[0]) < 0.0001f })
        assertNotNull(cap.uvs)
    }

    @Test
    fun leavesCapEmptyWhenPlaneDoesNotCrossMesh() {
        val result = tetrahedron.section(ClippingPlane3D(Vec3(1f, 0f, 0f), offset = -2f))

        assertNotNull(result.surface)
        assertNull(result.cap)
    }

    @Test
    fun triangulatesConcaveCutContourWithoutFillingItsNotch() {
        val outline = listOf(
            0f to 0f, 2f to 0f, 2f to 1f,
            1f to 1f, 1f to 2f, 0f to 2f,
        )
        val cap = assertNotNull(
            prism(outline).section(ClippingPlane3D(Vec3(0f, 0f, 1f))).cap,
        )

        assertEquals(4, cap.triangleCount)
        assertEquals(3f, cap.areaInXy(), absoluteTolerance = 0.0001f)
    }

    @Test
    fun closesMultipleDisconnectedContoursIndependently() {
        val first = prism(listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f))
        val second = prism(listOf(3f to 0f, 4f to 0f, 4f to 1f, 3f to 1f))
        val cap = assertNotNull(
            merge(first, second).section(ClippingPlane3D(Vec3(0f, 0f, 1f))).cap,
        )

        assertEquals(4, cap.triangleCount)
        assertEquals(2f, cap.areaInXy(), absoluteTolerance = 0.0001f)
    }

    @Test
    fun preservesNestedContourAsASectionHole() {
        val outer = prism(listOf(-2f to -2f, 2f to -2f, 2f to 2f, -2f to 2f))
        val bore = prism(listOf(-1f to -1f, -1f to 1f, 1f to 1f, 1f to -1f))
        val cap = assertNotNull(
            merge(outer, bore).section(ClippingPlane3D(Vec3(0f, 0f, 1f))).cap,
        )

        assertEquals(12f, cap.areaInXy(), absoluteTolerance = 0.0001f)
        assertTrue(cap.positions.asList().chunked(9).none { it.containsXy(0f, 0f) })
    }

    private fun prism(outline: List<Pair<Float, Float>>): Geometry3D {
        val positions = buildList {
            listOf(-1f, 1f).forEach { z ->
                outline.forEach { (x, y) -> addAll(listOf(x, y, z)) }
            }
        }.toFloatArray()
        val vertexCount = outline.size
        val indices = buildList {
            outline.indices.forEach { index ->
                val next = (index + 1) % vertexCount
                addAll(listOf(index, next, next + vertexCount))
                addAll(listOf(index, next + vertexCount, index + vertexCount))
            }
        }.toIntArray()
        return Geometry3D(positions, indices, FloatArray(positions.size) { index ->
            if (index % 3 == 2) 1f else 0f
        })
    }

    private fun merge(first: Geometry3D, second: Geometry3D): Geometry3D = Geometry3D(
        first.positions + second.positions,
        first.indices + second.indices.map { it + first.vertexCount },
        first.normals + second.normals,
    )

    private fun Geometry3D.areaInXy(): Float = positions.asList().chunked(9).sumOf { triangle ->
        val ax = triangle[3] - triangle[0]
        val ay = triangle[4] - triangle[1]
        val bx = triangle[6] - triangle[0]
        val by = triangle[7] - triangle[1]
        (kotlin.math.abs(ax * by - ay * bx) / 2f).toDouble()
    }.toFloat()

    private fun List<Float>.containsXy(x: Float, y: Float): Boolean {
        fun cross(ax: Float, ay: Float, bx: Float, by: Float) = ax * by - ay * bx
        val signs = (0..2).map { index ->
            val next = (index + 1) % 3
            cross(
                this[next * 3] - this[index * 3], this[next * 3 + 1] - this[index * 3 + 1],
                x - this[index * 3], y - this[index * 3 + 1],
            )
        }
        return signs.all { it >= -0.0001f } || signs.all { it <= 0.0001f }
    }
}
