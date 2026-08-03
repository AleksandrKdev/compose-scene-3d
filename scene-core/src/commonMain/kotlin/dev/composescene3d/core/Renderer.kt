package dev.composescene3d.core

/** Features that a backend actually implements, not merely features supported by its GPU API. */
data class RendererCapabilities(
    val primitiveGeometry: Boolean = false,
    val customGeometry: Boolean = false,
    val shadows: Boolean = false,
    val physicallyBasedRendering: Boolean = false,
    val bloom: Boolean = false,
    val skeletalAnimation: Boolean = false,
    val picking: Boolean = false,
    val clippingPlanes: Boolean = false,
    val sectionHatching: Boolean = false,
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

/** Optional renderer service for inspecting nodes inside imported model instances. */
interface ModelPartProvider {
    fun modelParts(nodeKey: NodeKey): List<ModelPart3D>
    fun observeModelParts(
        listener: (nodeKey: NodeKey, parts: List<ModelPart3D>) -> Unit,
    ): SceneSubscription
}

/** Optional renderer service that resolves local model-part anchors into world coordinates. */
interface ModelPartAnchorProvider {
    fun modelPartWorldPosition(anchor: ModelPartAnchor3D): Vec3?
}

fun interface SceneSubscription {
    fun dispose()
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

    /** Returns the currently loaded hierarchy for [nodeKey], or an empty list while it is loading. */
    fun modelParts(nodeKey: NodeKey): List<ModelPart3D> =
        (renderer as? ModelPartProvider)?.modelParts(nodeKey).orEmpty()

    /** Observes model hierarchies as asynchronous model instances are created or removed. */
    fun observeModelParts(
        listener: (nodeKey: NodeKey, parts: List<ModelPart3D>) -> Unit,
    ): SceneSubscription = (renderer as? ModelPartProvider)?.observeModelParts(listener)
        ?: SceneSubscription { }

    /** Returns null while the model is loading or when the model/part is no longer present. */
    fun modelPartWorldPosition(anchor: ModelPartAnchor3D): Vec3? =
        (renderer as? ModelPartAnchorProvider)?.modelPartWorldPosition(anchor)

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
