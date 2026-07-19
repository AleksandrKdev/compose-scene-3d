package dev.composescene3d.filament

import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import dev.composescene3d.testkit.RendererConformanceSuite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilamentRendererTest {
    private val conformance = RendererConformanceSuite(
        createRenderer = ::FilamentRenderer,
        retainedNodes = { it.nodes.toList() },
        expectedCapabilities = RendererCapabilities(
            primitiveGeometry = true,
            customGeometry = true,
            physicallyBasedRendering = true,
            skeletalAnimation = true,
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
