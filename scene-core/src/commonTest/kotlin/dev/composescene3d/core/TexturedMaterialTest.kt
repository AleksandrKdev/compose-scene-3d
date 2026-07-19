package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TexturedMaterialTest {
    private val albedo = TextureSource.Resource("files/albedo.png")

    @Test
    fun retainsEveryPortablePbrTextureChannel() {
        val material = TexturedMaterial(
            baseColorTexture = albedo,
            normalTexture = TextureSource.Resource("files/normal.png"),
            metallicRoughnessTexture = TextureSource.Resource("files/mr.png"),
            emissiveTexture = TextureSource.Resource("files/emissive.png"),
            ambientOcclusionTexture = TextureSource.Resource("files/ao.png"),
            normalScale = 0.75f,
            emissiveColor = Color3D.Cyan,
            emissiveIntensity = 3f,
            ambientOcclusionStrength = 0.6f,
        )

        assertEquals("files/normal.png", (material.normalTexture as TextureSource.Resource).path)
        assertEquals(0.75f, material.normalScale)
        assertEquals(3f, material.emissiveIntensity)
        assertEquals(0.6f, material.ambientOcclusionStrength)
    }

    @Test
    fun rejectsInvalidTextureFactors() {
        assertFailsWith<IllegalArgumentException> {
            TexturedMaterial(albedo, normalScale = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            TexturedMaterial(albedo, emissiveIntensity = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            TexturedMaterial(albedo, ambientOcclusionStrength = 1.1f)
        }
    }
}
