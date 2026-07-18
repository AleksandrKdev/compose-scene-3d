package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimitiveTest {
    @Test
    fun boxKeepsLegacyColorApi() {
        val color = Vec3(0.1f, 0.2f, 0.3f)
        assertEquals(color, BoxNode(NodeKey("box"), color = color).color)
    }

    @Test
    fun validatesPbrRanges() {
        assertFailsWith<IllegalArgumentException> { PbrMaterial(metallic = -0.1f) }
        assertFailsWith<IllegalArgumentException> { PbrMaterial(roughness = 1.1f) }
        assertFailsWith<IllegalArgumentException> { PbrMaterial(reflectance = 2f) }
    }

    @Test
    fun validatesPrimitiveGeometry() {
        assertFailsWith<IllegalArgumentException> { SphereNode(NodeKey("sphere"), radius = 0f) }
        assertFailsWith<IllegalArgumentException> { PlaneNode(NodeKey("plane"), depth = 0f) }
        assertFailsWith<IllegalArgumentException> { CylinderNode(NodeKey("cylinder"), segments = 2) }
    }
}
