package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        assertFailsWith<IllegalArgumentException> { HighlightMaterial(intensity = 0f) }
        assertFailsWith<IllegalArgumentException> { ModelPartOutline(width = 0f) }
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
        assertFailsWith<IllegalArgumentException> {
            ClippingPlane3D(Vec3.Zero)
        }
        assertFailsWith<IllegalArgumentException> {
            ClippedPbrMaterial(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ClippedPbrMaterial(List(4) { ClippingPlane3D(Vec3(0f, 1f, 0f)) })
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
        assertFailsWith<IllegalArgumentException> {
            LineNode(NodeKey("line"), Vec3.Zero, Vec3.Zero)
        }
        assertFailsWith<IllegalArgumentException> {
            ArrowNode(NodeKey("arrow"), Vec3.Zero, Vec3(0f, 0f, 0.1f), headLength = 0.1f)
        }
    }

    @Test
    fun buildsLineAndArrowGeometryAlongAnyAxis() {
        val line = LineNode(NodeKey("line"), Vec3(-1f, 2f, 0f), Vec3(2f, 2f, 0f))
        val arrow = ArrowNode(
            NodeKey("arrow"), Vec3.Zero, Vec3(0f, -2f, 0f), headLength = 0.4f,
        )

        assertTrue(line.geometry().triangleCount > 0)
        assertTrue(arrow.geometry().triangleCount > line.geometry().triangleCount)
        assertTrue(arrow.geometry().positions.all(Float::isFinite))
    }
}
