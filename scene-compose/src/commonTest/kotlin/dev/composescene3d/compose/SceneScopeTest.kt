package dev.composescene3d.compose

import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SceneScopeTest {
    @Test
    fun buildsNewPrimitivesWithBackendNeutralMaterials() {
        val metal = PbrMaterial(baseColor = Vec3(1f, 0.5f, 0f), metallic = 1f)
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
}
