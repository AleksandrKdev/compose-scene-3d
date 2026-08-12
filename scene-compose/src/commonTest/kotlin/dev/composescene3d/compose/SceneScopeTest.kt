package dev.composescene3d.compose

import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.ArrowNode
import dev.composescene3d.core.LineNode
import dev.composescene3d.core.LinearDimensionNode
import dev.composescene3d.core.RadialDimensionNode
import dev.composescene3d.core.AngularDimensionNode
import dev.composescene3d.core.SectionedMeshNode
import dev.composescene3d.core.ClippingPlane3D
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.Geometry3D
import dev.composescene3d.core.EdgeGeometry3D
import dev.composescene3d.core.EdgeNode
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelPartKey
import dev.composescene3d.core.ModelPartOverride
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.ShadowMap3D
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.TransparentMaterial
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.HatchMaterial
import dev.composescene3d.core.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SceneScopeTest {
    @Test
    fun attachesImportedModelPartOverrides() {
        val part = ModelPartKey("assembly/cover")
        val override = ModelPartOverride(visible = false)
        val scene = SceneScope().apply {
            model(
                key = "assembly",
                source = ModelSource.Bytes(byteArrayOf(1), "assembly"),
                partOverrides = mapOf(part to override),
            )
        }.build()

        assertEquals(mapOf(part to override), assertIs<ModelNode>(scene.nodes.single()).partOverrides)
    }

    @Test
    fun buildsNewPrimitivesWithBackendNeutralMaterials() {
        val metal = PbrMaterial(baseColor = Color3D(1f, 0.5f, 0f), metallic = 1f)
        val scene = SceneScope().apply {
            box(key = "box", material = metal)
            sphere(key = "sphere", material = metal)
            plane(key = "plane", width = 4f, depth = 3f)
            cylinder(key = "cylinder", height = 2f)
        }.build()

        assertEquals(4, scene.nodes.size)
        assertEquals(metal, assertIs<BoxNode>(scene.nodes[0]).material)
        assertEquals(metal, assertIs<SphereNode>(scene.nodes[1]).material)
        assertEquals(4f, assertIs<PlaneNode>(scene.nodes[2]).width)
        assertEquals(2f, assertIs<CylinderNode>(scene.nodes[3]).height)
    }

    @Test
    fun buildsLinesAndArrowsForInstructionalOverlays() {
        val annotation = UnlitMaterial(Color3D.Cyan)
        val scene = SceneScope().apply {
            line("leader", Vec3.Zero, Vec3(1f, 1f, 0f), material = annotation)
            arrow("force", Vec3.Zero, Vec3(0f, 1f, 0f), headLength = 0.2f)
        }.build()

        assertEquals(annotation, assertIs<LineNode>(scene.nodes[0]).material)
        assertEquals(0.2f, assertIs<ArrowNode>(scene.nodes[1]).headLength)
    }

    @Test
    fun buildsLinearEngineeringDimension() {
        val scene = SceneScope().apply {
            linearDimension(
                "width", Vec3(-1f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0.5f, 0f),
            )
        }.build()

        val dimension = assertIs<LinearDimensionNode>(scene.nodes.single())
        assertEquals(Vec3(0f, 0.5f, 0f), dimension.labelAnchor)
    }

    @Test
    fun buildsRadialAndAngularEngineeringDimensions() {
        val scene = SceneScope().apply {
            radialDimension("radius", Vec3.Zero, Vec3(1f, 0f, 0f))
            angularDimension("angle", Vec3.Zero, Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), 0.8f)
        }.build()
        assertIs<RadialDimensionNode>(scene.nodes[0])
        assertIs<AngularDimensionNode>(scene.nodes[1])
    }

    @Test
    fun buildsHiddenSceneSubtree() {
        val scene = SceneScope().apply {
            group("internals", visible = false, opacity = 0.4f) { box("bearing") }
        }.build()
        val group = assertIs<GroupNode>(scene.nodes.single())
        assertFalse(group.visible)
        assertEquals(0.4f, group.opacity)
        assertEquals(1, group.children.size)
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
            normalTexture = TextureSource.Resource("files/checker-normal.png"),
            metallicRoughnessTexture = TextureSource.Resource("files/checker-mr.png"),
            emissiveTexture = TextureSource.Resource("files/checker-emissive.png"),
            ambientOcclusionTexture = TextureSource.Resource("files/checker-ao.png"),
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

    @Test
    fun buildsNonVolumetricIndexedEdges() {
        val geometry = EdgeGeometry3D(
            positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f),
            indices = intArrayOf(0, 1),
        )

        val scene = SceneScope().apply { edges("edge", geometry) }.build()

        assertEquals(geometry, assertIs<EdgeNode>(scene.nodes.single()).geometry)
    }

    @Test
    fun buildsClosedSectionMeshWithSeparateCapMaterial() {
        val geometry = Geometry3D(
            positions = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        )
        val cap = UnlitMaterial(Color3D.Yellow)
        val scene = SceneScope().apply {
            sectionedMesh(
                "section", geometry, ClippingPlane3D(Vec3(1f, 0f, 0f)), capMaterial = cap,
            )
        }.build()

        assertEquals(cap, assertIs<SectionedMeshNode>(scene.nodes.single()).capMaterial)
    }

    @Test
    fun usesProceduralHatchingForSectionCapsByDefault() {
        val geometry = Geometry3D(
            positions = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        )
        val scene = SceneScope().apply {
            sectionedMesh("hatched", geometry, ClippingPlane3D(Vec3(1f, 0f, 0f)))
        }.build()

        assertIs<HatchMaterial>(assertIs<SectionedMeshNode>(scene.nodes.single()).capMaterial)
    }

    @Test
    fun buildsPortableShadowControls() {
        val shadow = ShadowMap3D(mapSize = 2048, cascades = 2)
        val scene = SceneScope().apply {
            plane("receiver", castShadows = false, receiveShadows = true)
            directionalLight("sun", intensity = 50_000f, shadow = shadow)
        }.build()

        assertEquals(false, assertIs<PlaneNode>(scene.nodes[0]).castShadows)
        assertEquals(shadow, assertIs<dev.composescene3d.core.DirectionalLightNode>(scene.nodes[1]).shadow)
    }
}
