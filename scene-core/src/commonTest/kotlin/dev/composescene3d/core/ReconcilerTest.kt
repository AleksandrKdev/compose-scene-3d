package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReconcilerTest {
    @Test
    fun controllerExposesOptionalModelPartProvider() {
        val expected = listOf(ModelPart3D(ModelPartKey("gear"), "Gear", renderable = true))
        val renderer = object : SceneRenderer, ModelPartProvider {
            var listener: ((NodeKey, List<ModelPart3D>) -> Unit)? = null
            override val capabilities = RendererCapabilities()
            override fun apply(commands: List<SceneCommand>) = Unit
            override fun close() = Unit
            override fun modelParts(nodeKey: NodeKey) =
                if (nodeKey == NodeKey("model")) expected else emptyList()
            override fun observeModelParts(listener: (NodeKey, List<ModelPart3D>) -> Unit): SceneSubscription {
                this.listener = listener
                return SceneSubscription { this.listener = null }
            }
        }

        val controller = SceneController(renderer)

        assertEquals(expected, controller.modelParts(NodeKey("model")))
        assertTrue(controller.modelParts(NodeKey("missing")).isEmpty())
        var observed = emptyList<ModelPart3D>()
        val subscription = controller.observeModelParts { _, parts -> observed = parts }
        renderer.listener?.invoke(NodeKey("model"), expected)
        assertEquals(expected, observed)
        subscription.dispose()
        assertEquals(null, renderer.listener)
    }

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
            listOf(GroupNode(NodeKey("parent"), emptyList()), GroupNode(NodeKey("child"), emptyList()))
        )
        val next = SceneDescription(listOf(GroupNode(NodeKey("replacement"), emptyList())))

        assertEquals(
            listOf(
                SceneCommand.Remove(NodeKey("child")),
                SceneCommand.Remove(NodeKey("parent")),
                SceneCommand.Create(GroupNode(NodeKey("replacement"), emptyList())),
            ),
            reconcile(old, next),
        )
    }

    @Test
    fun rejectsDuplicateKeys() {
        assertFailsWith<IllegalArgumentException> {
            SceneDescription(
                listOf(GroupNode(NodeKey("same"), emptyList()), GroupNode(NodeKey("same"), emptyList()))
            )
        }
    }

    @Test
    fun rejectsDuplicateKeysAcrossNestedGroups() {
        assertFailsWith<IllegalArgumentException> {
            SceneDescription(
                listOf(
                    GroupNode(
                        NodeKey("root"),
                        listOf(GroupNode(NodeKey("nested"), listOf(BoxNode(NodeKey("root"))))),
                    )
                )
            )
        }
    }

    @Test
    fun flattensSceneTreeInPreOrder() {
        val leaf = SphereNode(NodeKey("leaf"))
        val nested = GroupNode(NodeKey("nested"), listOf(leaf))
        val root = GroupNode(NodeKey("root"), listOf(nested))

        assertEquals(listOf(root, nested, leaf), listOf(root).flattenSceneNodes())
    }
}
