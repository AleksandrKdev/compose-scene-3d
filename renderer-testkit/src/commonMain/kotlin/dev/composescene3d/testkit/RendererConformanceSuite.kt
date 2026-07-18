package dev.composescene3d.testkit

import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Backend-neutral retained renderer contract.
 *
 * A backend supplies a factory and a test-only snapshot of its retained scene. The same assertions
 * can then be applied to native, JVM and future Web renderers without exposing backend handles in
 * the production API.
 */
class RendererConformanceSuite<R : SceneRenderer>(
    private val createRenderer: () -> R,
    private val retainedNodes: (R) -> List<SceneNode>,
    private val expectedCapabilities: RendererCapabilities,
) {
    fun createUpdateAndRemoveRetainStableIdentity() {
        val renderer = createRenderer()
        val first = BoxNode(NodeKey("box"))
        val moved = first.copy(transform = Transform(translation = Vec3(1f, 2f, 3f)))

        renderer.apply(listOf(SceneCommand.Create(first)))
        assertEquals(listOf(first), retainedNodes(renderer))

        renderer.apply(listOf(SceneCommand.Update(first, moved)))
        assertEquals(listOf(moved), retainedNodes(renderer))

        renderer.apply(listOf(SceneCommand.Remove(first.key)))
        assertEquals(emptyList(), retainedNodes(renderer))
        renderer.close()
    }

    fun invalidCommandSequencesAreRejected() {
        val existing = BoxNode(NodeKey("existing"))

        createRenderer().useForTest { renderer ->
            renderer.apply(listOf(SceneCommand.Create(existing)))
            assertFailsWith<IllegalStateException> {
                renderer.apply(listOf(SceneCommand.Create(existing)))
            }
        }
        createRenderer().useForTest { renderer ->
            assertFailsWith<IllegalStateException> {
                renderer.apply(listOf(SceneCommand.Update(existing, existing)))
            }
            assertFailsWith<IllegalStateException> {
                renderer.apply(listOf(SceneCommand.Remove(existing.key)))
            }
        }
    }

    fun closeIsIdempotentClearsStateAndRejectsCommands() {
        val renderer = createRenderer()
        val box = BoxNode(NodeKey("box"))
        renderer.apply(listOf(SceneCommand.Create(box)))

        renderer.close()
        renderer.close()

        assertEquals(emptyList(), retainedNodes(renderer))
        assertFailsWith<IllegalStateException> {
            renderer.apply(listOf(SceneCommand.Create(box)))
        }
    }

    fun capabilitiesMatchBackendDeclaration() {
        createRenderer().useForTest { renderer ->
            assertEquals(expectedCapabilities, renderer.capabilities)
        }
    }

    private inline fun R.useForTest(block: (R) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}
