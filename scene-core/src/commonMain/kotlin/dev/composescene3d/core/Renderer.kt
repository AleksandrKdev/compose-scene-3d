package dev.composescene3d.core

/**
 * Features that a backend actually implements, not merely features supported by its GPU API.
 * Applications can use these flags to select a portable fallback before submitting a scene.
 *
 * @property primitiveGeometry built-in box, sphere, plane and cylinder nodes are rendered.
 * @property customGeometry indexed [Geometry3D] meshes are rendered.
 * @property shadows lights and renderable nodes honor their shadow settings.
 * @property physicallyBasedRendering PBR material parameters affect the rendered result.
 * @property bloom bright surfaces can contribute to the backend's bloom post-process.
 * @property skeletalAnimation imported model animation clips can be played.
 * @property picking screen coordinates can be resolved to stable scene and model-part keys.
 * @property clippingPlanes materials can discard geometry against a clipping plane.
 * @property sectionHatching clipped custom meshes can render generated section caps.
 * @property materialOpacity declarative [Material3D] instances support fractional opacity.
 * @property automaticImportedMaterialOpacity authored imported-model materials can be faded while
 * preserving their authored texture bindings, without supplying a replacement material.
 */
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
    val materialOpacity: Boolean = false,
    val automaticImportedMaterialOpacity: Boolean = false,
)

/** An ordered retained-scene mutation consumed by a [SceneRenderer]. */
sealed interface SceneCommand {
    /** Creates all backend resources required by [node]. */
    data class Create(val node: SceneNode) : SceneCommand

    /** Updates the retained node identified by [SceneNode.key] from [previous] to [node]. */
    data class Update(val previous: SceneNode, val node: SceneNode) : SceneCommand

    /** Removes the retained node and releases resources exclusively owned by it. */
    data class Remove(val key: NodeKey) : SceneCommand
}

/**
 * Backend boundary for retained scene rendering.
 *
 * [apply] receives commands in dependency-safe order. Implementations must make [close]
 * idempotent and release every native or GPU resource they own.
 */
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

/** A listener registration whose [dispose] operation must be safe to call more than once. */
fun interface SceneSubscription {
    fun dispose()
}

/** Owns the current scene snapshot and submits only its retained diff to [renderer]. */
class SceneController(private val renderer: SceneRenderer) {
    private var current = SceneDescription.Empty
    private var closed = false

    /** Reconciles and submits [scene]. Submission after [close] is an error. */
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

    /** Removes the current scene and closes the renderer. Safe to call more than once. */
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

/**
 * Produces the deterministic retained command sequence from [previous] to [next].
 * Removals are emitted in reverse order before creates and updates.
 */
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
