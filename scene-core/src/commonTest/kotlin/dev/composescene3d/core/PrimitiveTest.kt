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
    fun convertsSrgbColorsToLinearSpace() {
        val linear = Color3D.rgb(255, 128, 0).toLinearSrgb()

        assertEquals(ColorSpace3D.LinearSrgb, linear.colorSpace)
        assertEquals(1f, linear.red)
        assertEquals(0f, linear.blue)
        assertEquals(0.21586f, linear.green, absoluteTolerance = 0.00001f)
    }

    @Test
    fun validatesMaterialsAndLights() {
        assertFailsWith<IllegalArgumentException> { EmissiveMaterial(intensity = -1f) }
        assertFailsWith<IllegalArgumentException> {
            PointLightNode(NodeKey("point"), intensity = 1f, falloff = 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            SpotLightNode(
                NodeKey("spot"),
                intensity = 1f,
                innerConeRadians = 0.7f,
                outerConeRadians = 0.6f,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TexturedMaterial(TextureSource.Resource("grid.png"), roughness = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            TransparentMaterial(Color3D.White, reflectance = 1.1f)
        }
    }

    @Test
    fun textureBytesUseContentEqualityAndStableAssetKeys() {
        val first = TextureSource.Bytes(byteArrayOf(1, 2, 3), cacheKey = "grid")
        val same = TextureSource.Bytes(byteArrayOf(1, 2, 3), cacheKey = "grid")

        assertEquals(first, same)
        assertEquals(TextureAssetKey("bytes:grid"), first.assetKey())
        assertEquals(
            TextureAssetKey("resource:files/grid.png"),
            TextureSource.Resource("files/grid.png").assetKey(),
        )
    }

    @Test
    fun validatesEnvironmentConfiguration() {
        val source = TextureSource.Resource("files/studio_ibl.ktx")

        assertFailsWith<IllegalArgumentException> {
            EnvironmentMap(source, intensity = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            EnvironmentMap(source, rotationYRadians = Float.NaN)
        }
        assertEquals(source, EnvironmentMap(source).reflections)
    }

    @Test
    fun validatesPrimitiveGeometry() {
        assertFailsWith<IllegalArgumentException> { SphereNode(NodeKey("sphere"), radius = 0f) }
        assertFailsWith<IllegalArgumentException> { PlaneNode(NodeKey("plane"), depth = 0f) }
        assertFailsWith<IllegalArgumentException> { CylinderNode(NodeKey("cylinder"), segments = 2) }
    }
}
