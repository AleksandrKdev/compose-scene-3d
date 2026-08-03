package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
