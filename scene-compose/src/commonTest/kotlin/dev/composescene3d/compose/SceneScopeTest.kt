package dev.composescene3d.compose

import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
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
}
