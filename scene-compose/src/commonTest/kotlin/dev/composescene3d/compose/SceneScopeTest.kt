package dev.composescene3d.compose

import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.Geometry3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.TransparentMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SceneScopeTest {
    @Test
    fun buildsNewPrimitivesWithBackendNeutralMaterials() {
        val metal = PbrMaterial(baseColor = Color3D(1f, 0.5f, 0f), metallic = 1f)
        val scene = SceneScope().apply {
            sphere(key = "sphere", material = metal)
            plane(key = "plane", width = 4f, depth = 3f)
            cylinder(key = "cylinder", height = 2f)
        }.build()

        assertEquals(3, scene.nodes.size)
        assertEquals(metal, assertIs<SphereNode>(scene.nodes[0]).material)
        assertEquals(4f, assertIs<PlaneNode>(scene.nodes[1]).width)
        assertEquals(2f, assertIs<CylinderNode>(scene.nodes[2]).height)
    }

    @Test
    fun buildsPortableMaterialsAndLocalLights() {
        val scene = SceneScope().apply {
            sphere("emitter", material = EmissiveMaterial(Color3D.Cyan, intensity = 4f))
            pointLight("fill", intensity = 2_000f, color = Color3D.Blue)
            spotLight("spot", intensity = 5_000f, color = Color3D.rgb(255, 180, 120))
        }.build()

        assertIs<EmissiveMaterial>(assertIs<SphereNode>(scene.nodes[0]).material)
        assertIs<PointLightNode>(scene.nodes[1])
        assertIs<SpotLightNode>(scene.nodes[2])
    }

    @Test
    fun attachesTextureSourcesWithoutBackendTypes() {
        val textured = TexturedMaterial(
            baseColorTexture = TextureSource.Resource("files/checker.png"),
            roughness = 0.8f,
        )
        val scene = SceneScope().apply {
            plane("textured-plane", material = textured)
        }.build()

        assertEquals(textured, assertIs<PlaneNode>(scene.nodes.single()).material)
    }

    @Test
    fun attachesTransparentMaterialWithoutBackendTypes() {
        val glass = TransparentMaterial(Color3D(0.2f, 0.7f, 1f, alpha = 0.35f))
        val scene = SceneScope().apply {
            sphere("glass", material = glass)
        }.build()

        assertEquals(glass, assertIs<SphereNode>(scene.nodes.single()).material)
    }

    @Test
    fun buildsNestedTransformGroups() {
        val scene = SceneScope().apply {
            group("vehicle") {
                box("body")
                group("wheels") {
                    cylinder("front-wheel")
                    cylinder("rear-wheel")
                }
            }
        }.build()

        val vehicle = assertIs<GroupNode>(scene.nodes.single())
        assertIs<dev.composescene3d.core.BoxNode>(vehicle.children[0])
        val wheels = assertIs<GroupNode>(vehicle.children[1])
        assertEquals(listOf("front-wheel", "rear-wheel"), wheels.children.map { it.key.value })
    }

    @Test
    fun buildsCustomIndexedMesh() {
        val geometry = Geometry3D(
            positions = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            uvs = floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f),
        )

        val scene = SceneScope().apply { mesh("triangle", geometry) }.build()

        assertEquals(geometry, assertIs<MeshNode>(scene.nodes.single()).geometry)
    }
}
