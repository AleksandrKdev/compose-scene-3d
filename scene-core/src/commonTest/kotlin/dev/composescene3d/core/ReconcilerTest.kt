package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReconcilerTest {
    @Test
    fun createsOnlyNewNodesAndUpdatesChangedNodes() {
        val old = SceneDescription(
            listOf(ModelNode(NodeKey("model"), ModelSource.Resource("a.glb")))
        )
        val next = SceneDescription(
            listOf(
                ModelNode(NodeKey("model"), ModelSource.Resource("a.glb"), visible = false),
                DirectionalLightNode(NodeKey("sun"), intensity = 1_000f),
            )
        )

        val commands = reconcile(old, next)

        assertEquals(2, commands.size)
        assertIs<SceneCommand.Update>(commands[0])
        assertIs<SceneCommand.Create>(commands[1])
    }

    @Test
    fun removesInReverseOrderBeforeCreatingNodes() {
        val old = SceneDescription(
            listOf(GroupNode(NodeKey("parent")), GroupNode(NodeKey("child")))
        )
        val next = SceneDescription(listOf(GroupNode(NodeKey("replacement"))))

        assertEquals(
            listOf(
                SceneCommand.Remove(NodeKey("child")),
                SceneCommand.Remove(NodeKey("parent")),
                SceneCommand.Create(GroupNode(NodeKey("replacement"))),
            ),
            reconcile(old, next),
        )
    }

    @Test
    fun rejectsDuplicateKeys() {
        assertFailsWith<IllegalArgumentException> {
            SceneDescription(listOf(GroupNode(NodeKey("same")), GroupNode(NodeKey("same"))))
        }
    }
}
