package dev.composescene3d.web

import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRendererTest {
    @Test
    fun retainsUpdatesAndRemovesNodes() {
        val renderer = WebRenderer()
        val first = BoxNode(NodeKey("box"))
        val updated = first.copy(color = dev.composescene3d.core.Vec3(1f, 0f, 0f))

        renderer.apply(listOf(SceneCommand.Create(first)))
        assertEquals(first, renderer.nodes[first.key])
        renderer.apply(listOf(SceneCommand.Update(first, updated)))
        assertEquals(updated, renderer.nodes[first.key])
        renderer.apply(listOf(SceneCommand.Remove(first.key)))
        assertTrue(renderer.nodes.isEmpty())
    }

    @Test
    fun advertisesOnlyImplementedFeatures() {
        assertEquals(
            RendererCapabilities(primitiveGeometry = true, customGeometry = true),
            WebRenderer().capabilities,
        )
    }
}
