package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShadowTest {
    @Test
    fun validatesPortableShadowMapQuality() {
        val shadow = ShadowMap3D(
            mapSize = 2048,
            cascades = 3,
            contactShadows = true,
            bulbRadius = 0.05f,
        )

        assertEquals(2048, shadow.mapSize)
        assertEquals(3, shadow.cascades)
        assertEquals(shadow, DirectionalLightNode(NodeKey("sun"), 50_000f, shadow = shadow).shadow)
    }

    @Test
    fun rejectsInvalidShadowSettings() {
        assertFailsWith<IllegalArgumentException> { ShadowMap3D(mapSize = 1000) }
        assertFailsWith<IllegalArgumentException> { ShadowMap3D(cascades = 5) }
        assertFailsWith<IllegalArgumentException> { ShadowMap3D(normalBias = -1f) }
        assertFailsWith<IllegalArgumentException> {
            ShadowTechnique3D.Vsm(lightBleedReduction = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> { ShadowTechnique3D.Pcss(penumbraScale = -1f) }
    }

    @Test
    fun geometryControlsCastingAndReceivingIndependently() {
        val receiver = PlaneNode(
            NodeKey("ground"),
            castShadows = false,
            receiveShadows = true,
        )

        assertEquals(false, receiver.castShadows)
        assertEquals(true, receiver.receiveShadows)
    }
}
