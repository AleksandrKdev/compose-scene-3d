package dev.composescene3d.filament

import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilamentRendererTest {
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
}
