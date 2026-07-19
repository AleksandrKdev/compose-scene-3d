package dev.composescene3d.core

/** Features that a backend actually implements, not merely features supported by its GPU API. */
data class RendererCapabilities(
    val primitiveGeometry: Boolean = false,
    val customGeometry: Boolean = false,
    val physicallyBasedRendering: Boolean = false,
    val bloom: Boolean = false,
    val skeletalAnimation: Boolean = false,
    val picking: Boolean = false,
)

sealed interface SceneCommand {
    data class Create(val node: SceneNode) : SceneCommand
    data class Update(val previous: SceneNode, val node: SceneNode) : SceneCommand
    data class Remove(val key: NodeKey) : SceneCommand
}

interface SceneRenderer {
    val capabilities: RendererCapabilities

    fun apply(commands: List<SceneCommand>)
    fun close()
}

class SceneController(private val renderer: SceneRenderer) {
    private var current = SceneDescription.Empty
    private var closed = false

    fun submit(scene: SceneDescription) {
        check(!closed) { "SceneController is closed" }
        val commands = reconcile(current, scene)
        if (commands.isNotEmpty()) renderer.apply(commands)
        current = scene
    }

    fun close() {
        if (closed) return
        if (current.nodes.isNotEmpty()) {
            renderer.apply(current.nodes.asReversed().map { SceneCommand.Remove(it.key) })
        }
        current = SceneDescription.Empty
        closed = true
        renderer.close()
    }
}

fun reconcile(previous: SceneDescription, next: SceneDescription): List<SceneCommand> {
    val before = previous.nodes.associateBy(SceneNode::key)
    val after = next.nodes.associateBy(SceneNode::key)
    val commands = mutableListOf<SceneCommand>()

    previous.nodes.asReversed().forEach { node ->
        if (node.key !in after) commands += SceneCommand.Remove(node.key)
    }
    next.nodes.forEach { node ->
        val old = before[node.key]
        when {
            old == null -> commands += SceneCommand.Create(node)
            old != node -> commands += SceneCommand.Update(old, node)
        }
    }
    return commands
}
