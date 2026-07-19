package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Geometry3DTest {
    private val positions = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    private val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)

    @Test
    fun reportsCountsAndUsesContentEquality() {
        val first = Geometry3D(positions, intArrayOf(0, 1, 2), normals)
        val copy = Geometry3D(positions.copyOf(), intArrayOf(0, 1, 2), normals.copyOf())

        assertEquals(3, first.vertexCount)
        assertEquals(1, first.triangleCount)
        assertEquals(first, copy)
        assertEquals(first.hashCode(), copy.hashCode())
    }

    @Test
    fun rejectsInvalidVertexAttributesAndIndices() {
        assertFailsWith<IllegalArgumentException> {
            Geometry3D(positions, intArrayOf(0, 1, 3), normals)
        }
        assertFailsWith<IllegalArgumentException> {
            Geometry3D(positions, intArrayOf(0, 1, 2), normals, floatArrayOf(0f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            Geometry3D(positions, intArrayOf(0, 1, 2), FloatArray(9))
        }
    }

    @Test
    fun texturedMeshRequiresUvs() {
        val geometry = Geometry3D(positions, intArrayOf(0, 1, 2), normals)

        assertFailsWith<IllegalArgumentException> {
            MeshNode(
                NodeKey("mesh"),
                geometry,
                TexturedMaterial(TextureSource.Resource("files/color.png")),
            )
        }
    }
}
