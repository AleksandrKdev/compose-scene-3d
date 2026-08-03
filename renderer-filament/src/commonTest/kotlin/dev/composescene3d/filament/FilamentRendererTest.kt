package dev.composescene3d.filament

import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.ModelPartKey
import dev.composescene3d.core.ModelPartOverride
import dev.composescene3d.core.ModelPartOutline
import dev.composescene3d.core.ScenePickResult
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import dev.composescene3d.testkit.RendererConformanceSuite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals

class FilamentRendererTest {
    private val conformance = RendererConformanceSuite(
        createRenderer = ::FilamentRenderer,
        retainedNodes = { it.nodes.toList() },
        expectedCapabilities = RendererCapabilities(
            primitiveGeometry = true,
            customGeometry = true,
            shadows = true,
            physicallyBasedRendering = true,
            skeletalAnimation = true,
            clippingPlanes = true,
        ),
    )

    @Test
    fun conformsToRetainedCreateUpdateRemoveContract() =
        conformance.createUpdateAndRemoveRetainStableIdentity()

    @Test
    fun conformsToInvalidCommandContract() =
        conformance.invalidCommandSequencesAreRejected()

    @Test
    fun conformsToLifecycleContract() =
        conformance.closeIsIdempotentClearsStateAndRejectsCommands()

    @Test
    fun conformsToCapabilityDeclaration() =
        conformance.capabilitiesMatchBackendDeclaration()

    @Test
    fun conformsToPrimitiveGeometryContract() =
        conformance.primitiveGeometryNodesAreRetained()

    @Test
    fun updateRetainsNodeIdentityAndChangesValue() {
        val renderer = FilamentRenderer()
        val first = BoxNode(NodeKey("box"))
        val moved = first.copy(transform = Transform(translation = Vec3(1f, 0f, 0f)))

        renderer.apply(listOf(SceneCommand.Create(first)))
        renderer.apply(listOf(SceneCommand.Update(first, moved)))

        assertEquals(listOf(moved), renderer.nodes.toList())
    }

    @Test
    fun closeIsIdempotentAndRejectsFurtherCommands() {
        val renderer = FilamentRenderer()
        renderer.close()
        renderer.close()

        assertFailsWith<IllegalStateException> {
            renderer.apply(listOf(SceneCommand.Create(BoxNode(NodeKey("box")))))
        }
    }

    @Test
    fun modelsWithTheSameCacheKeyShareOneAssetGroup() {
        val firstBytes = ModelSource.Bytes(byteArrayOf(1), cacheKey = "duck")
        val sameAssetBytes = ModelSource.Bytes(byteArrayOf(2), cacheKey = "duck")
        val models = listOf(
            ModelNode(NodeKey("first"), firstBytes),
            ModelNode(NodeKey("second"), sameAssetBytes),
        )

        val groups = modelsByAssetKey(models)

        assertEquals(1, groups.size)
        assertEquals(listOf("first", "second"), groups.values.single().map { it.key.value })
    }

    @Test
    fun entityMappingsAreRemovedWithTheirNode() {
        val renderer = FilamentRenderer()
        val box = BoxNode(NodeKey("box"))
        renderer.apply(listOf(SceneCommand.Create(box)))
        renderer.registerEntities(box.key, listOf(41, 42))

        assertEquals(box.key, renderer.resolveEntity(41))

        renderer.apply(listOf(SceneCommand.Remove(box.key)))

        assertEquals(null, renderer.resolveEntity(41))
        assertEquals(null, renderer.resolveEntity(42))
    }

    @Test
    fun pickingAnImportedEntityReturnsItsNodeAndPart() {
        val renderer = FilamentRenderer()
        val model = ModelNode(NodeKey("machine"), ModelSource.Bytes(byteArrayOf(1), "machine"))
        val part = ModelPartKey("assembly/shaft")
        renderer.apply(listOf(SceneCommand.Create(model)))
        renderer.registerEntities(model.key, listOf(43))
        renderer.registerModelPartEntity(43, part)

        assertEquals(ScenePickResult(model.key, part), renderer.resolvePick(43))

        renderer.apply(listOf(SceneCommand.Remove(model.key)))
        assertEquals(null, renderer.resolvePick(43))
    }

    @Test
    fun pickingPrimitiveReturnsNodeWithoutModelPart() {
        val renderer = FilamentRenderer()
        val box = BoxNode(NodeKey("box"))
        renderer.apply(listOf(SceneCommand.Create(box)))
        renderer.registerEntities(box.key, listOf(44))

        assertEquals(ScenePickResult(box.key), renderer.resolvePick(44))
    }

    @Test
    fun hiddenParentHidesItsDescendants() {
        val root = ModelPartKey("assembly")
        val shaft = ModelPartKey("assembly/shaft")
        val bearing = ModelPartKey("assembly/shaft/bearing")
        val parents = mapOf(root to null, shaft to root, bearing to shaft)

        assertEquals(
            false,
            isModelPartVisible(bearing, parents, mapOf(root to ModelPartOverride(visible = false))),
        )
        assertEquals(true, isModelPartVisible(bearing, parents, emptyMap()))
    }

    @Test
    fun closestMaterialOverrideWinsAndParentAppliesToSubtree() {
        val root = ModelPartKey("assembly")
        val shaft = ModelPartKey("assembly/shaft")
        val bearing = ModelPartKey("assembly/shaft/bearing")
        val parents = mapOf(root to null, shaft to root, bearing to shaft)
        val rootMaterial = PbrMaterial(baseColor = Color3D.Red)
        val shaftMaterial = PbrMaterial(baseColor = Color3D.Blue)

        assertEquals(
            shaftMaterial,
            resolveModelPartMaterial(
                bearing,
                parents,
                mapOf(
                    root to ModelPartOverride(material = rootMaterial),
                    shaft to ModelPartOverride(material = shaftMaterial),
                ),
            ),
        )
        assertEquals(rootMaterial, resolveModelPartMaterial(shaft, parents, mapOf(
            root to ModelPartOverride(material = rootMaterial),
        )))
        assertEquals(null, resolveModelPartMaterial(bearing, parents, emptyMap()))
    }

    @Test
    fun closestOutlineOverrideWinsAndParentAppliesToSubtree() {
        val root = ModelPartKey("assembly")
        val child = ModelPartKey("assembly/shaft")
        val leaf = ModelPartKey("assembly/shaft/bearing")
        val parents = mapOf(root to null, child to root, leaf to child)
        val outer = ModelPartOutline(Color3D.Yellow)
        val inner = ModelPartOutline(Color3D.Cyan)

        assertEquals(inner, resolveModelPartOutline(leaf, parents, mapOf(
            root to ModelPartOverride(outline = outer),
            child to ModelPartOverride(outline = inner),
        )))
        assertEquals(outer, resolveModelPartOutline(child, parents, mapOf(
            root to ModelPartOverride(outline = outer),
        )))
    }

    @Test
    fun partTransformOffsetIsAppliedAfterAuthoredTransform() {
        val authored = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 2f, 0f, 0f, 1f,
        )
        val offset = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 3f, 0f, 0f, 1f,
        )

        assertContentEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 5f, 0f, 0f, 1f),
            multiplyMatrices(authored, offset),
        )
    }

    @Test
    fun entityMappingsFollowNestedGroupUpdatesAndRemoval() {
        val renderer = FilamentRenderer()
        val firstChild = BoxNode(NodeKey("first-child"))
        val secondChild = BoxNode(NodeKey("second-child"))
        val group = GroupNode(NodeKey("group"), listOf(firstChild, secondChild))
        renderer.apply(listOf(SceneCommand.Create(group)))
        renderer.registerEntities(firstChild.key, listOf(51))
        renderer.registerEntities(secondChild.key, listOf(52))

        val updated = group.copy(children = listOf(secondChild))
        renderer.apply(listOf(SceneCommand.Update(group, updated)))

        assertEquals(null, renderer.resolveEntity(51))
        assertEquals(secondChild.key, renderer.resolveEntity(52))

        renderer.apply(listOf(SceneCommand.Remove(group.key)))
        assertEquals(null, renderer.resolveEntity(52))
    }
}
