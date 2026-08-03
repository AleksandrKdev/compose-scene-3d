package dev.composescene3d.filament

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import dev.composescene3d.compose.SceneCameraState
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.compose.sceneCameraGestures
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.ClippedPbrMaterial
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.ArrowNode
import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.DirectionalLightNode
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.HighlightMaterial
import dev.composescene3d.core.HatchMaterial
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelAssetKey
import dev.composescene3d.core.ModelPart3D
import dev.composescene3d.core.ModelPartKey
import dev.composescene3d.core.ModelPartOverride
import dev.composescene3d.core.ModelPartOutline
import dev.composescene3d.core.ModelPartProvider
import dev.composescene3d.core.ModelPartAnchor3D
import dev.composescene3d.core.ModelPartAnchorProvider
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.LineNode
import dev.composescene3d.core.LinearDimensionNode
import dev.composescene3d.core.RadialDimensionNode
import dev.composescene3d.core.AngularDimensionNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.ScenePickResult
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.SectionedMeshNode
import dev.composescene3d.core.SceneSubscription
import dev.composescene3d.core.ShadowMap3D
import dev.composescene3d.core.ShadowTechnique3D
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.Transform
import dev.composescene3d.core.TransparentMaterial
import dev.composescene3d.core.OpacityMaterial
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.EnvironmentMap
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.assetKey
import dev.composescene3d.core.geometry
import dev.composescene3d.core.withOpacity
import dev.composescene3d.core.section
import dev.composescene3d.core.Vec3
import dev.composescene3d.filament.resources.Res
import io.github.erkko68.filament.compose.FilamentSceneView
import io.github.erkko68.filament.compose.FilamentSceneScope
import io.github.erkko68.filament.compose.rememberFilamentViewState
import io.github.erkko68.filament.compose.rememberFilamentEngine
import io.github.erkko68.filament.compose.pickOnTap
import io.github.erkko68.filament.compose.scene.Color
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.DirectionalLight
import io.github.erkko68.filament.compose.scene.PointLight
import io.github.erkko68.filament.compose.scene.Position
import io.github.erkko68.filament.compose.scene.Scale
import io.github.erkko68.filament.compose.scene.GltfInstance
import io.github.erkko68.filament.compose.scene.GltfInstanceScope
import io.github.erkko68.filament.compose.scene.Group
import io.github.erkko68.filament.compose.scene.rememberGltfAsset
import io.github.erkko68.filament.compose.scene.SkyboxSource
import io.github.erkko68.filament.compose.scene.rememberSkyboxState
import io.github.erkko68.filament.compose.scene.rememberCameraState
import io.github.erkko68.filament.compose.scene.rememberIndirectLightState
import io.github.erkko68.filament.compose.scene.SphericalHarmonics
import io.github.erkko68.filament.compose.scene.Projection
import io.github.erkko68.filament.compose.scene.primitives.Cube
import io.github.erkko68.filament.compose.scene.primitives.Cylinder
import io.github.erkko68.filament.compose.scene.primitives.Plane
import io.github.erkko68.filament.compose.scene.primitives.Sphere
import io.github.erkko68.filament.compose.scene.rememberColorMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberEmissiveMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberMaterial
import io.github.erkko68.filament.compose.scene.rememberMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberUnlitColorMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberTexture
import io.github.erkko68.filament.compose.scene.SpotCone
import io.github.erkko68.filament.compose.scene.SpotLight
import io.github.erkko68.filament.compose.scene.ShadowConfig
import io.github.erkko68.filament.compose.scene.Shadows
import io.github.erkko68.filament.utils.Quaternion
import io.github.erkko68.filament.utils.KTX1Loader
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.MaterialInstance
import io.github.erkko68.filament.Texture
import io.github.erkko68.filament.TextureSampler
import io.github.erkko68.filament.Renderer
import io.github.erkko68.filament.gltfio.FilamentInstance
import io.github.erkko68.filament.utils.TextureLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

fun interface ModelByteLoader {
    suspend fun load(source: ModelSource): ByteArray
}

fun interface TextureByteLoader {
    suspend fun load(source: TextureSource): ByteArray
}

private val bytesOnlyModelLoader = ModelByteLoader { source ->
    when (source) {
        is ModelSource.Bytes -> source.value
        is ModelSource.Resource -> error(
            "Resource model '${source.path}' needs an application-provided ModelByteLoader"
        )
        is ModelSource.Url -> error(
            "URL model '${source.value}' needs an application-provided ModelByteLoader"
        )
    }
}

private val bytesOnlyTextureLoader = TextureByteLoader { source ->
    when (source) {
        is TextureSource.Bytes -> source.value
        is TextureSource.Resource -> error(
            "Resource texture '${source.path}' needs an application-provided TextureByteLoader"
        )
        is TextureSource.Url -> error(
            "URL texture '${source.value}' needs an application-provided TextureByteLoader"
        )
    }
}

/**
 * Retained adapter between ComposeScene3D commands and Filament KMP.
 *
 * Filament types are deliberately absent from the constructor and public state. The adapter keeps
 * stable nodes by [NodeKey]; the Filament composables below are also wrapped in Compose [key] so an
 * update changes parameters without replacing an unchanged native entity.
 */
class FilamentRenderer(
    internal val modelByteLoader: ModelByteLoader = bytesOnlyModelLoader,
    internal val onModelError: (ModelAssetKey, Throwable) -> Unit = { _, _ -> },
) : SceneRenderer, ModelPartProvider, ModelPartAnchorProvider {
    internal var textureByteLoader: TextureByteLoader = bytesOnlyTextureLoader
        private set
    internal var onTextureError: (TextureSource, Throwable) -> Unit = { _, _ -> }
        private set

    constructor(
        textureByteLoader: TextureByteLoader,
        modelByteLoader: ModelByteLoader = bytesOnlyModelLoader,
        onModelError: (ModelAssetKey, Throwable) -> Unit = { _, _ -> },
        onTextureError: (TextureSource, Throwable) -> Unit = { _, _ -> },
    ) : this(modelByteLoader, onModelError) {
        this.textureByteLoader = textureByteLoader
        this.onTextureError = onTextureError
    }
    override val capabilities = RendererCapabilities(
        primitiveGeometry = true,
        customGeometry = true,
        shadows = true,
        physicallyBasedRendering = true,
        skeletalAnimation = true,
        clippingPlanes = true,
        sectionHatching = true,
    )

    private val retainedNodes = mutableStateMapOf<NodeKey, SceneNode>()
    private val entityToNode = mutableMapOf<Int, NodeKey>()
    private val nodeToEntities = mutableMapOf<NodeKey, Set<Int>>()
    private val partsByNode = mutableMapOf<NodeKey, List<ModelPart3D>>()
    private val entityToPart = mutableMapOf<Int, ModelPartKey>()
    private val modelPartBindings = mutableMapOf<NodeKey, List<ModelPartBinding>>()
    private val modelPartEngines = mutableMapOf<NodeKey, Engine>()
    private val modelPartListeners = mutableSetOf<(NodeKey, List<ModelPart3D>) -> Unit>()
    private var closed = false

    internal val nodes: Collection<SceneNode> get() = retainedNodes.values

    override fun apply(commands: List<SceneCommand>) {
        check(!closed) { "FilamentRenderer is closed" }
        commands.forEach { command ->
            when (command) {
                is SceneCommand.Create -> {
                    check(retainedNodes.put(command.node.key, command.node) == null) {
                        "Node already exists: ${command.node.key.value}"
                    }
                }
                is SceneCommand.Update -> {
                    check(retainedNodes.containsKey(command.node.key)) {
                        "Cannot update missing node: ${command.node.key.value}"
                    }
                    unregisterRemovedDescendants(command.previous, command.node)
                    val previousModel = command.previous as? ModelNode
                    val nextModel = command.node as? ModelNode
                    if (previousModel != null && nextModel != null &&
                        (!nextModel.visible || previousModel.source != nextModel.source)
                    ) {
                        unregisterEntities(nextModel.key)
                    }
                    retainedNodes[command.node.key] = command.node
                }
                is SceneCommand.Remove -> {
                    val removed = retainedNodes.remove(command.key)
                    check(removed != null) {
                        "Cannot remove missing node: ${command.key.value}"
                    }
                    unregisterTree(removed)
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        retainedNodes.clear()
        entityToNode.clear()
        nodeToEntities.clear()
        partsByNode.clear()
        entityToPart.clear()
        modelPartBindings.clear()
        modelPartEngines.clear()
        modelPartListeners.clear()
        closed = true
    }

    internal fun registerEntities(key: NodeKey, entities: Collection<Int>) {
        unregisterEntities(key)
        val stableEntities = entities.toSet()
        nodeToEntities[key] = stableEntities
        stableEntities.forEach { entityToNode[it] = key }
    }

    internal fun registerEntity(key: NodeKey, entity: Int) {
        entityToNode[entity] = key
        nodeToEntities[key] = nodeToEntities[key].orEmpty() + entity
    }

    internal fun unregisterEntity(key: NodeKey, entity: Int) {
        entityToNode.remove(entity)
        val remaining = nodeToEntities[key].orEmpty() - entity
        if (remaining.isEmpty()) nodeToEntities.remove(key) else nodeToEntities[key] = remaining
    }

    internal fun resolveEntity(entity: Int): NodeKey? = entityToNode[entity]

    internal fun resolvePick(entity: Int): ScenePickResult? = entityToNode[entity]?.let { nodeKey ->
        ScenePickResult(nodeKey = nodeKey, modelPartKey = entityToPart[entity])
    }

    internal fun registerModelPartEntity(entity: Int, partKey: ModelPartKey) {
        entityToPart[entity] = partKey
    }

    override fun modelParts(nodeKey: NodeKey): List<ModelPart3D> = partsByNode[nodeKey].orEmpty()

    override fun observeModelParts(
        listener: (NodeKey, List<ModelPart3D>) -> Unit,
    ): SceneSubscription {
        modelPartListeners += listener
        return SceneSubscription { modelPartListeners -= listener }
    }

    internal fun registerModelParts(
        key: NodeKey,
        instance: FilamentInstance,
        engine: Engine,
    ) {
        partsByNode.remove(key)
        val parts = describeModelParts(instance, engine)
        val result = parts.map { part ->
            registerModelPartEntity(part.entity, part.key)
            ModelPart3D(
                key = part.key,
                name = part.name,
                parentKey = part.parentKey,
                childKeys = parts.filter { it.parentKey == part.key }.map(NativeModelPart::key),
                renderable = part.originalMaterials.isNotEmpty(),
            )
        }
        modelPartBindings[key] = parts.map { part ->
            ModelPartBinding(
                entity = part.entity,
                key = part.key,
                parentKey = part.parentKey,
                originalTransform = part.originalTransform,
                originalMaterials = part.originalMaterials,
            )
        }
        modelPartEngines[key] = engine
        partsByNode[key] = result
        modelPartListeners.toList().forEach { it(key, result) }
    }

    internal fun applyModelPartOutlines(
        instance: FilamentInstance,
        overrides: Map<ModelPartKey, ModelPartOverride>,
        materials: Map<ModelPartOutline, MaterialInstance>,
        engine: Engine,
    ) {
        val parts = describeModelParts(instance, engine)
        val parentByKey = parts.associate { it.key to it.parentKey }
        val transforms = engine.getTransformManager()
        val renderables = engine.getRenderableManager()
        parts.forEach { part ->
            val outline = resolveModelPartOutline(part.key, parentByKey, overrides)
            val visible = outline != null && isModelPartVisible(part.key, parentByKey, overrides)
            if (renderables.hasComponent(part.entity)) {
                val renderable = renderables.getInstance(part.entity)
                renderables.setLayerMask(renderable, 0xff, if (visible) 0xff else 0x00)
                materials[outline]?.let { material ->
                    repeat(renderables.getPrimitiveCount(renderable)) { primitive ->
                        renderables.setMaterialInstanceAt(renderable, primitive, material)
                    }
                }
                renderables.setCastShadows(renderable, false)
                renderables.setReceiveShadows(renderable, false)
                renderables.setPriority(renderable, 1)
            }
            if (transforms.hasComponent(part.entity)) {
                val offset = overrides[part.key]?.transformOffset ?: Transform()
                transforms.setTransform(
                    transforms.getInstance(part.entity),
                    multiplyMatrices(part.originalTransform, offset.toFilamentMatrix()),
                )
            }
        }
    }

    private fun unregisterEntities(key: NodeKey) {
        modelPartBindings.remove(key)
        modelPartEngines.remove(key)
        nodeToEntities.remove(key)?.forEach { entity ->
            if (entityToNode[entity] == key) entityToNode.remove(entity)
            entityToPart.remove(entity)
        }
        if (partsByNode.remove(key) != null) {
            modelPartListeners.toList().forEach { it(key, emptyList()) }
        }
    }

    override fun modelPartWorldPosition(anchor: ModelPartAnchor3D): Vec3? {
        val binding = modelPartBindings[anchor.nodeKey]?.firstOrNull { it.key == anchor.partKey }
            ?: return null
        val engine = modelPartEngines[anchor.nodeKey] ?: return null
        val transforms = engine.getTransformManager()
        if (!transforms.hasComponent(binding.entity)) return null
        val matrix = transforms.getWorldTransform(transforms.getInstance(binding.entity), FloatArray(16))
        val point = anchor.localPosition
        return Vec3(
            matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12],
            matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13],
            matrix[2] * point.x + matrix[6] * point.y + matrix[10] * point.z + matrix[14],
        )
    }

    internal fun applyModelPartOverrides(
        key: NodeKey,
        overrides: Map<ModelPartKey, ModelPartOverride>,
        materials: Map<Material3D, MaterialInstance>,
        engine: Engine,
    ) {
        val bindings = modelPartBindings[key].orEmpty()
        val parentByKey = bindings.associate { it.key to it.parentKey }
        val transforms = engine.getTransformManager()
        val renderables = engine.getRenderableManager()
        bindings.forEach { binding ->
            val visible = isModelPartVisible(binding.key, parentByKey, overrides)
            if (renderables.hasComponent(binding.entity)) {
                val renderable = renderables.getInstance(binding.entity)
                renderables.setLayerMask(renderable, 0xff, if (visible) 0xff else 0x00)
                val overrideMaterial = resolveModelPartMaterial(binding.key, parentByKey, overrides)
                    ?.let(materials::get)
                binding.originalMaterials.forEachIndexed { primitive, originalMaterial ->
                    when {
                        overrideMaterial != null -> renderables.setMaterialInstanceAt(
                            renderable, primitive, overrideMaterial,
                        )
                        originalMaterial != null -> renderables.setMaterialInstanceAt(
                            renderable, primitive, originalMaterial,
                        )
                        else -> renderables.clearMaterialInstanceAt(renderable, primitive)
                    }
                }
            }
            if (transforms.hasComponent(binding.entity)) {
                val offset = overrides[binding.key]?.transformOffset ?: Transform()
                transforms.setTransform(
                    transforms.getInstance(binding.entity),
                    multiplyMatrices(binding.originalTransform, offset.toFilamentMatrix()),
                )
            }
        }
    }

    private fun unregisterTree(node: SceneNode) {
        unregisterEntities(node.key)
        if (node is GroupNode) node.children.forEach(::unregisterTree)
    }

    private fun unregisterRemovedDescendants(previous: SceneNode, next: SceneNode) {
        val nextKeys = next.descendantKeys()
        previous.descendants().filter { it.key !in nextKeys }.forEach { unregisterEntities(it.key) }
    }
}

private data class ModelPartBinding(
    val entity: Int,
    val key: ModelPartKey,
    val parentKey: ModelPartKey?,
    val originalTransform: FloatArray,
    val originalMaterials: List<MaterialInstance?>,
)

private data class NativeModelPart(
    val entity: Int,
    val key: ModelPartKey,
    val name: String,
    val parentKey: ModelPartKey?,
    val originalTransform: FloatArray,
    val originalMaterials: List<MaterialInstance?>,
)

private fun describeModelParts(instance: FilamentInstance, engine: Engine): List<NativeModelPart> {
    val asset = instance.getAsset()
    val entities = instance.getEntities().toList()
    val entitySet = entities.toSet()
    val transforms = engine.getTransformManager()
    val renderables = engine.getRenderableManager()
    val parents = entities.associateWith { entity ->
        transforms.getParent(transforms.getInstance(entity)).takeIf(entitySet::contains)
    }
    val names = entities.associateWith { entity ->
        asset.getName(entity).orEmpty().takeIf(String::isNotBlank) ?: "part"
    }
    val keys = mutableMapOf<Int, ModelPartKey>()
    fun keyFor(entity: Int): ModelPartKey = keys.getOrPut(entity) {
        val parent = parents[entity]
        val name = requireNotNull(names[entity]).replace("/", "_")
        val siblings = entities.filter { parents[it] == parent && names[it] == names[entity] }
        val occurrence = siblings.indexOf(entity)
        val segment = if (occurrence == 0) name else "$name#${occurrence + 1}"
        ModelPartKey(parent?.let { "${keyFor(it).value}/$segment" } ?: segment)
    }
    return entities.filter(transforms::hasComponent).map { entity ->
        val renderable = if (renderables.hasComponent(entity)) renderables.getInstance(entity) else null
        NativeModelPart(
            entity = entity,
            key = keyFor(entity),
            name = requireNotNull(names[entity]),
            parentKey = parents[entity]?.let(::keyFor),
            originalTransform = transforms.getTransform(transforms.getInstance(entity), FloatArray(16)),
            originalMaterials = if (renderable == null) emptyList() else {
                List(renderables.getPrimitiveCount(renderable)) { primitive ->
                    renderables.getMaterialInstanceAt(renderable, primitive)
                }
            },
        )
    }
}

internal fun isModelPartVisible(
    key: ModelPartKey,
    parentByKey: Map<ModelPartKey, ModelPartKey?>,
    overrides: Map<ModelPartKey, ModelPartOverride>,
): Boolean {
    var current: ModelPartKey? = key
    while (current != null) {
        if (overrides[current]?.visible == false) return false
        current = parentByKey[current]
    }
    return true
}

internal fun resolveModelPartMaterial(
    key: ModelPartKey,
    parentByKey: Map<ModelPartKey, ModelPartKey?>,
    overrides: Map<ModelPartKey, ModelPartOverride>,
): Material3D? {
    var current: ModelPartKey? = key
    while (current != null) {
        overrides[current]?.material?.let { return it }
        current = parentByKey[current]
    }
    return null
}

internal fun resolveModelPartOutline(
    key: ModelPartKey,
    parentByKey: Map<ModelPartKey, ModelPartKey?>,
    overrides: Map<ModelPartKey, ModelPartOverride>,
): ModelPartOutline? {
    var current: ModelPartKey? = key
    while (current != null) {
        overrides[current]?.outline?.let { return it }
        current = parentByKey[current]
    }
    return null
}

internal fun multiplyMatrices(left: FloatArray, right: FloatArray): FloatArray {
    require(left.size == 16 && right.size == 16)
    return FloatArray(16) { index ->
        val column = index / 4
        val row = index % 4
        (0..3).sumOf { k -> (left[k * 4 + row] * right[column * 4 + k]).toDouble() }.toFloat()
    }
}

private fun Transform.toFilamentMatrix(): FloatArray {
    val (x, y, z, w) = rotation
    return floatArrayOf(
        (1f - 2f * (y * y + z * z)) * scale.x,
        (2f * (x * y + w * z)) * scale.x,
        (2f * (x * z - w * y)) * scale.x,
        0f,
        (2f * (x * y - w * z)) * scale.y,
        (1f - 2f * (x * x + z * z)) * scale.y,
        (2f * (y * z + w * x)) * scale.y,
        0f,
        (2f * (x * z + w * y)) * scale.z,
        (2f * (y * z - w * x)) * scale.z,
        (1f - 2f * (x * x + y * y)) * scale.z,
        0f,
        translation.x, translation.y, translation.z, 1f,
    )
}

private fun SceneNode.descendants(): List<SceneNode> = buildList {
    fun append(node: SceneNode) {
        add(node)
        if (node is GroupNode) node.children.forEach(::append)
    }
    append(this@descendants)
}

private fun SceneNode.descendantKeys(): Set<NodeKey> = descendants().mapTo(mutableSetOf()) { it.key }

@Composable
fun FilamentViewport(
    renderer: FilamentRenderer,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Vec3 = Vec3(0.04f, 0.05f, 0.07f),
    cameraState: SceneCameraState = rememberSceneCameraState(),
    orbitEnabled: Boolean = true,
    zoomSpeed: Float = 0.12f,
    pickingEnabled: Boolean = true,
    shadows: ShadowTechnique3D? = ShadowTechnique3D.Pcf,
    onNodePicked: (NodeKey?) -> Unit = {},
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, null, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, shadows, onNodePicked, null,
)

/**
 * Displays a scene and reports both its selected node and, for imported glTF/GLB models, the
 * selected model part. [onPicked] receives null when Filament does not resolve a scene entity.
 */
@Composable
fun FilamentViewport(
    renderer: FilamentRenderer,
    onPicked: (ScenePickResult?) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Vec3 = Vec3(0.04f, 0.05f, 0.07f),
    cameraState: SceneCameraState = rememberSceneCameraState(),
    orbitEnabled: Boolean = true,
    zoomSpeed: Float = 0.12f,
    pickingEnabled: Boolean = true,
    shadows: ShadowTechnique3D? = ShadowTechnique3D.Pcf,
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, null, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, shadows, {}, onPicked,
)

@Composable
fun FilamentViewport(
    renderer: FilamentRenderer,
    environment: EnvironmentMap,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Vec3 = Vec3(0.04f, 0.05f, 0.07f),
    cameraState: SceneCameraState = rememberSceneCameraState(),
    orbitEnabled: Boolean = true,
    zoomSpeed: Float = 0.12f,
    pickingEnabled: Boolean = true,
    shadows: ShadowTechnique3D? = ShadowTechnique3D.Pcf,
    onNodePicked: (NodeKey?) -> Unit = {},
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, environment, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, shadows, onNodePicked, null,
)

/** Environment-map variant that reports an imported model's selected part. */
@Composable
fun FilamentViewport(
    renderer: FilamentRenderer,
    environment: EnvironmentMap,
    onPicked: (ScenePickResult?) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Vec3 = Vec3(0.04f, 0.05f, 0.07f),
    cameraState: SceneCameraState = rememberSceneCameraState(),
    orbitEnabled: Boolean = true,
    zoomSpeed: Float = 0.12f,
    pickingEnabled: Boolean = true,
    shadows: ShadowTechnique3D? = ShadowTechnique3D.Pcf,
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, environment, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, shadows, {}, onPicked,
)

@Composable
private fun FilamentViewportContent(
    renderer: FilamentRenderer,
    modifier: Modifier,
    backgroundColor: Vec3,
    environment: EnvironmentMap?,
    cameraState: SceneCameraState,
    orbitEnabled: Boolean,
    zoomSpeed: Float,
    pickingEnabled: Boolean,
    shadows: ShadowTechnique3D?,
    onNodePicked: (NodeKey?) -> Unit,
    onPicked: ((ScenePickResult?) -> Unit)?,
) {
    val engine = rememberFilamentEngine()
    val viewState = rememberFilamentViewState()
    val filamentCameraState = rememberCameraState(
        eye = Position(cameraState.eye.x, cameraState.eye.y, cameraState.eye.z),
        target = Position(cameraState.target.x, cameraState.target.y, cameraState.target.z),
        up = Direction(cameraState.up.x, cameraState.up.y, cameraState.up.z),
        projection = cameraState.projection.toFilamentProjection(),
    )
    val viewportHeight = remember { mutableIntStateOf(0) }
    val callbackScope = rememberCoroutineScope()
    val skyboxState = rememberSkyboxState(
        SkyboxSource.Color(
            Color(backgroundColor.x, backgroundColor.y, backgroundColor.z)
        )
    )
    val environmentState = rememberEnvironmentState(renderer, engine, environment)

    SideEffect {
        skyboxState.source = environmentState.skybox?.let(SkyboxSource::Cubemap)
            ?: SkyboxSource.Color(Color(backgroundColor.x, backgroundColor.y, backgroundColor.z))
        skyboxState.intensity = environment?.skyboxIntensity ?: 1f
    }
    val filamentRenderer = viewState.renderer

    LaunchedEffect(cameraState) {
        snapshotFlow {
            CameraSyncSnapshot(cameraState.eye, cameraState.target, cameraState.up, cameraState.projection)
        }.collectLatest { snapshot ->
            filamentCameraState.eye = Position(snapshot.eye.x, snapshot.eye.y, snapshot.eye.z)
            filamentCameraState.target = Position(snapshot.target.x, snapshot.target.y, snapshot.target.z)
            filamentCameraState.up = Direction(snapshot.up.x, snapshot.up.y, snapshot.up.z)
            filamentCameraState.projection = snapshot.projection.toFilamentProjection()
        }
    }
    LaunchedEffect(filamentCameraState) {
        snapshotFlow {
            Triple(filamentCameraState.eye, filamentCameraState.target, filamentCameraState.up)
        }.collectLatest { (eye, target, up) ->
            cameraState.eye = Vec3(eye.x, eye.y, eye.z)
            cameraState.target = Vec3(target.x, target.y, target.z)
            cameraState.up = Vec3(up.x, up.y, up.z)
        }
    }

    // Desktop uses an offscreen swap chain followed by asynchronous GPU -> CPU readback. Filament
    // defaults to clear=false/discard=true, which leaves background pixels undefined when no
    // environment has drawn them yet and can appear as rapid flashing. Always begin with a stable,
    // opaque buffer; the skybox then draws the same visible background on every platform.
    DisposableEffect(filamentRenderer, backgroundColor) {
        filamentRenderer?.clearOptions = Renderer.ClearOptions().apply {
            clearColor = doubleArrayOf(
                backgroundColor.x.toDouble(),
                backgroundColor.y.toDouble(),
                backgroundColor.z.toDouble(),
                1.0,
            )
            clear = true
            discard = false
        }
        onDispose { }
    }

    var surfaceModifier = Modifier.fillMaxSize().onSizeChanged {
        viewportHeight.intValue = it.height
    }
    if (orbitEnabled) {
        surfaceModifier = surfaceModifier.sceneCameraGestures(
            cameraState = cameraState,
            viewportHeight = { viewportHeight.intValue },
            zoomSpeed = zoomSpeed,
        )
    }

    var containerModifier = modifier
    if (pickingEnabled) {
        // Picking lives on the parent, as in Filament KMP's own sample. Keeping its tap detector
        // off the render-surface modifier prevents it from competing with two-finger pinch events.
        containerModifier = containerModifier.pickOnTap(viewState) { result ->
            val picked = renderer.resolvePick(result.renderable)
            callbackScope.launch {
                onNodePicked(picked?.nodeKey)
                onPicked?.invoke(picked)
            }
        }
    }

    Box(containerModifier) {
        FilamentSceneView(
            modifier = surfaceModifier,
            engine = engine,
            viewState = viewState,
            skyboxState = skyboxState,
            indirectLightState = environmentState.indirectLight,
            cameraState = filamentCameraState,
            shadows = shadows.toFilamentShadows(),
        ) {
            FilamentNodes(renderer, renderer.nodes)
        }
    }
}

private val LocalSceneOpacity = compositionLocalOf { 1f }

@Composable
private fun FilamentSceneScope.FilamentNodes(
    renderer: FilamentRenderer,
    nodes: Collection<SceneNode>,
) {
    modelsByAssetKey(nodes).forEach { (assetKey, models) ->
        key("asset:${assetKey.value}") { FilamentModels(renderer, assetKey, models) }
    }
    nodes.filterNot { it is ModelNode }.forEach { node ->
        key(node.key.value) {
            when (node) {
                is BoxNode -> FilamentBox(renderer, node)
                is SphereNode -> FilamentSphere(renderer, node)
                is PlaneNode -> FilamentPlane(renderer, node)
                is CylinderNode -> FilamentCylinder(renderer, node)
                is LineNode -> FilamentMesh(
                    renderer,
                    MeshNode(
                        node.key, node.geometry(), node.material, node.transform,
                        node.castShadows, node.receiveShadows,
                    ),
                )
                is ArrowNode -> FilamentMesh(
                    renderer,
                    MeshNode(
                        node.key, node.geometry(), node.material, node.transform,
                        node.castShadows, node.receiveShadows,
                    ),
                )
                is LinearDimensionNode -> node.geometry().forEach { geometry ->
                    FilamentMesh(
                        renderer,
                        MeshNode(
                            node.key, geometry, node.material, node.transform,
                            node.castShadows, node.receiveShadows,
                        ),
                    )
                }
                is RadialDimensionNode -> node.geometry().forEach { geometry ->
                    FilamentMesh(renderer, MeshNode(node.key, geometry, node.material, node.transform, node.castShadows, node.receiveShadows))
                }
                is AngularDimensionNode -> node.geometry().forEach { geometry ->
                    FilamentMesh(renderer, MeshNode(node.key, geometry, node.material, node.transform, node.castShadows, node.receiveShadows))
                }
                is SectionedMeshNode -> FilamentSectionedMesh(renderer, node)
                is MeshNode -> FilamentMesh(renderer, node)
                is DirectionalLightNode -> FilamentLight(node)
                is PointLightNode -> FilamentLight(node)
                is SpotLightNode -> FilamentLight(node)
                is GroupNode -> {
                    if (node.visible) {
                        val groupEntity = remember(node.key) { mutableStateOf<Int?>(null) }
                        Group(
                            position = node.transform.translation.toFilamentPosition(),
                            rotation = node.transform.rotation.toFilamentQuaternion(),
                            scale = node.transform.scale.toFilamentScale(),
                            onCreate = { groupEntity.value = it },
                        ) {
                            CompositionLocalProvider(
                                LocalComposeScene3DParent provides groupEntity.value,
                                LocalSceneOpacity provides LocalSceneOpacity.current * node.opacity,
                            ) {
                                FilamentNodes(renderer, node.children)
                            }
                        }
                    }
                }
                is ModelNode -> error("Model nodes are rendered in shared asset groups")
            }
        }
    }
}

@Composable
private fun FilamentSceneScope.FilamentSectionedMesh(
    renderer: FilamentRenderer,
    node: SectionedMeshNode,
) {
    val section = remember(node.geometry, node.plane) { node.geometry.section(node.plane) }
    section.surface?.let { geometry ->
        FilamentMesh(
            renderer,
            MeshNode(
                node.key, geometry, node.material, node.transform,
                node.castShadows, node.receiveShadows,
            ),
        )
    }
    section.cap?.let { geometry ->
        FilamentMesh(
            renderer,
            MeshNode(
                node.key, geometry, node.capMaterial, node.transform,
                node.castShadows, node.receiveShadows,
            ),
        )
    }
}

private data class EnvironmentState(
    val skybox: Texture?,
    val indirectLight: io.github.erkko68.filament.compose.scene.IndirectLightState?,
)

@Composable
private fun rememberEnvironmentState(
    renderer: FilamentRenderer,
    engine: Engine,
    environment: EnvironmentMap?,
): EnvironmentState {
    if (environment == null) return EnvironmentState(null, null)

    val reflectionsBytes = rememberTextureBytes(renderer, environment.reflections)
    val skyboxBytes = environment.skybox?.let { rememberTextureBytes(renderer, it) }
    val reflections = rememberKtxCubemap(renderer, engine, environment.reflections, reflectionsBytes)
    val skybox = environment.skybox?.let {
        rememberKtxCubemap(renderer, engine, it, skyboxBytes)
    }
    val coefficients = remember(reflectionsBytes) {
        reflectionsBytes?.let(KTX1Loader::getSphericalHarmonics)
    }
    val indirectLight = rememberIndirectLightState()
    val rotation = remember(environment.rotationYRadians) {
        yRotationMatrix(environment.rotationYRadians)
    }

    SideEffect {
        indirectLight.reflections = reflections
        indirectLight.irradianceSh = coefficients?.let { SphericalHarmonics(3, it) }
        indirectLight.intensity = environment.intensity
        indirectLight.rotation = rotation
    }
    return EnvironmentState(skybox, indirectLight)
}

@Composable
private fun rememberTextureBytes(
    renderer: FilamentRenderer,
    source: TextureSource,
): ByteArray? {
    val bytes by produceState<ByteArray?>(null, source.assetKey()) {
        value = try {
            renderer.textureByteLoader.load(source)
        } catch (error: Throwable) {
            renderer.onTextureError(source, error)
            null
        }
    }
    return bytes
}

@Composable
private fun rememberKtxCubemap(
    renderer: FilamentRenderer,
    engine: Engine,
    source: TextureSource,
    bytes: ByteArray?,
): Texture? {
    val texture = remember(engine, bytes) {
        bytes?.let {
            KTX1Loader.createTexture(engine, it, KTX1Loader.Options().apply { srgb = false })
        }
    }
    LaunchedEffect(bytes, texture) {
        if (bytes != null && texture == null) {
            renderer.onTextureError(source, IllegalArgumentException("Invalid KTX1 cubemap"))
        }
    }
    DisposableEffect(engine, texture) {
        onDispose { texture?.let(engine::destroyTexture) }
    }
    return texture
}

private fun yRotationMatrix(radians: Float): FloatArray {
    val cosine = cos(radians)
    val sine = sin(radians)
    return floatArrayOf(
        cosine, 0f, -sine,
        0f, 1f, 0f,
        sine, 0f, cosine,
    )
}

private fun ShadowTechnique3D?.toFilamentShadows(): Shadows? = when (this) {
    null -> null
    ShadowTechnique3D.Pcf -> Shadows.Pcf
    ShadowTechnique3D.Pcfd -> Shadows.Pcfd
    is ShadowTechnique3D.Vsm -> Shadows.Vsm(
        highPrecision = highPrecision,
        lightBleedReduction = lightBleedReduction,
    )
    is ShadowTechnique3D.Dpcf -> Shadows.Dpcf(penumbraScale = penumbraScale)
    is ShadowTechnique3D.Pcss -> Shadows.Pcss(penumbraScale = penumbraScale)
}

private fun ShadowMap3D.toFilamentShadowConfig() = ShadowConfig(
    mapSize = mapSize,
    constantBias = constantBias,
    normalBias = normalBias,
    shadowFar = shadowFar,
    cascades = cascades,
    contactShadows = contactShadows,
    contactShadowDistance = contactShadowDistance,
    contactShadowSteps = contactShadowSteps,
    bulbRadius = bulbRadius,
)

internal fun modelsByAssetKey(nodes: Collection<SceneNode>): Map<ModelAssetKey, List<ModelNode>> =
    nodes.filterIsInstance<ModelNode>().groupBy { it.source.assetKey() }

@Composable
private fun FilamentSceneScope.FilamentModels(
    renderer: FilamentRenderer,
    assetKey: ModelAssetKey,
    models: List<ModelNode>,
) {
    val source = models.first().source
    val asset = rememberGltfAsset(
        key = assetKey,
        onError = { renderer.onModelError(assetKey, it) },
    ) {
        renderer.modelByteLoader.load(source)
    }

    models.filter(ModelNode::visible).forEach { model ->
        key(model.key.value) {
            val overrideMaterials = model.partOverrides.values
                .mapNotNull(ModelPartOverride::material)
                .distinct()
                .associateWith { material ->
                    key(material) { rememberSceneMaterial(renderer, material) }
                }
            val outlineMaterials = model.partOverrides.values
                .mapNotNull(ModelPartOverride::outline)
                .distinct()
                .mapNotNull { outline ->
                    key(outline) { rememberOutlineMaterial(outline) }?.let { outline to it }
                }
                .toMap()
            GltfInstance(
                asset = asset,
                position = Position(
                    model.transform.translation.x,
                    model.transform.translation.y,
                    model.transform.translation.z,
                ),
                rotation = Quaternion(
                    model.transform.rotation.x,
                    model.transform.rotation.y,
                    model.transform.rotation.z,
                    model.transform.rotation.w,
                ),
                scale = Scale(
                    model.transform.scale.x,
                    model.transform.scale.y,
                    model.transform.scale.z,
                ),
                onCreate = {
                    renderer.registerEntities(model.key, instance.getEntities().toList())
                    renderer.registerModelParts(model.key, instance, engine)
                    renderer.applyModelPartOverrides(
                        model.key, model.partOverrides, overrideMaterials, engine,
                    )
                    applyShadows(model.castShadows, model.receiveShadows)
                },
                onUpdate = {
                    renderer.applyModelPartOverrides(
                        model.key, model.partOverrides, overrideMaterials, engine,
                    )
                    applyShadows(model.castShadows, model.receiveShadows)
                },
            )
            if (outlineMaterials.isNotEmpty()) {
                key("outline", model.partOverrides) {
                    GltfInstance(
                        asset = asset,
                        position = model.transform.translation.toFilamentPosition(),
                        rotation = model.transform.rotation.toFilamentQuaternion(),
                        scale = model.transform.scale.toFilamentScale(),
                        onCreate = {
                            renderer.applyModelPartOutlines(
                                instance, model.partOverrides, outlineMaterials, engine,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun GltfInstanceScope.applyShadows(cast: Boolean, receive: Boolean) {
    val renderables = engine.getRenderableManager()
    instance.getEntities().forEach { entity ->
        if (!renderables.hasComponent(entity)) return@forEach
        val renderable = renderables.getInstance(entity)
        renderables.setCastShadows(renderable, cast)
        renderables.setReceiveShadows(renderable, receive)
    }
}

@Composable
private fun FilamentSceneScope.FilamentBox(renderer: FilamentRenderer, node: BoxNode) {
    val material = rememberSceneMaterial(
        renderer,
        PbrMaterial(baseColor = Color3D(node.color.x, node.color.y, node.color.z))
    )
    Cube(
        material = material,
        position = Position(
            node.transform.translation.x,
            node.transform.translation.y,
            node.transform.translation.z,
        ),
        rotation = node.transform.rotation.toFilamentQuaternion(),
        scale = Scale(
            node.transform.scale.x * node.size.x,
            node.transform.scale.y * node.size.y,
            node.transform.scale.z * node.size.z,
        ),
        size = 1f,
        castShadows = node.castShadows,
        receiveShadows = node.receiveShadows,
        onCreate = { rendererEntity ->
            // The primitive owns one renderable entity.
            // Registration is replaced, not appended, if Compose recreates this node.
            // This keeps picking deterministic across native resource recreation.
            renderer.registerEntities(node.key, listOf(rendererEntity))
        },
    )
}

@Composable
private fun FilamentSceneScope.FilamentSphere(renderer: FilamentRenderer, node: SphereNode) {
    Sphere(
        material = rememberSceneMaterial(renderer, node.material),
        position = node.transform.translation.toFilamentPosition(),
        rotation = node.transform.rotation.toFilamentQuaternion(),
        scale = node.transform.scale.toFilamentScale(),
        radius = node.radius,
        rings = node.rings,
        segments = node.segments,
        castShadows = node.castShadows,
        receiveShadows = node.receiveShadows,
        onCreate = { renderer.registerEntities(node.key, listOf(it)) },
    )
}

@Composable
private fun FilamentSceneScope.FilamentPlane(renderer: FilamentRenderer, node: PlaneNode) {
    Plane(
        material = rememberSceneMaterial(renderer, node.material),
        position = node.transform.translation.toFilamentPosition(),
        rotation = node.transform.rotation.toFilamentQuaternion(),
        scale = node.transform.scale.toFilamentScale(),
        width = node.width,
        depth = node.depth,
        doubleSided = node.doubleSided,
        castShadows = node.castShadows,
        receiveShadows = node.receiveShadows,
        onCreate = { renderer.registerEntities(node.key, listOf(it)) },
    )
}

@Composable
private fun FilamentSceneScope.FilamentCylinder(renderer: FilamentRenderer, node: CylinderNode) {
    Cylinder(
        material = rememberSceneMaterial(renderer, node.material),
        position = node.transform.translation.toFilamentPosition(),
        rotation = node.transform.rotation.toFilamentQuaternion(),
        scale = node.transform.scale.toFilamentScale(),
        radius = node.radius,
        height = node.height,
        segments = node.segments,
        castShadows = node.castShadows,
        receiveShadows = node.receiveShadows,
        onCreate = { renderer.registerEntities(node.key, listOf(it)) },
    )
}

@Composable
internal fun rememberSceneMaterial(renderer: FilamentRenderer, material: Material3D): MaterialInstance {
    val effective = material.withOpacity(LocalSceneOpacity.current)
    return when (effective) {
    is PbrMaterial -> rememberColorMaterialInstance(
        color = effective.baseColor.toFilamentColor(),
        metallic = effective.metallic,
        roughness = effective.roughness,
        reflectance = effective.reflectance,
    )
    is UnlitMaterial -> rememberUnlitColorMaterialInstance(effective.color.toFilamentColor())
    is EmissiveMaterial -> rememberEmissiveMaterialInstance(
        color = effective.color.toFilamentColor(),
        intensity = effective.intensity,
    )
    is HighlightMaterial -> rememberEmissiveMaterialInstance(
        color = effective.color.toFilamentColor(),
        intensity = effective.intensity,
    )
    is HatchMaterial -> rememberHatchMaterial(effective)
    is ClippedPbrMaterial -> rememberClippedPbrMaterial(effective)
    is TexturedMaterial -> {
        rememberPbrTexturedMaterial(renderer, effective)
    }
    is TransparentMaterial -> rememberTransparentMaterial(effective)
    is OpacityMaterial -> rememberOpacityMaterial(renderer, effective)
    }
}

@Composable
private fun rememberOpacityMaterial(renderer: FilamentRenderer, faded: OpacityMaterial): MaterialInstance {
    val base = faded.material
    if (base !is TexturedMaterial) {
        val color = when (base) {
            is PbrMaterial -> base.baseColor
            is UnlitMaterial -> base.color
            is EmissiveMaterial -> base.color
            is HighlightMaterial -> base.color
            is TransparentMaterial -> base.color
            else -> Color3D.White
        }
        return rememberTransparentMaterial(TransparentMaterial(
            color.copy(alpha = color.alpha * faded.opacity),
            metallic = (base as? PbrMaterial)?.metallic ?: 0f,
            roughness = (base as? PbrMaterial)?.roughness ?: 0.5f,
        ))
    }
    val albedo = rememberSceneTexture(renderer, base.baseColorTexture, TextureLoader.TextureType.COLOR)
    val compiled = rememberMaterial(key = "compose-scene-3d-transparent-textured-pbr-v1.72") {
        Res.readBytes("files/materials/transparent_textured_pbr.filamat")
    }
    if (albedo == null || compiled == null) return rememberTransparentMaterial(
        TransparentMaterial(Color3D(0.7f, 0.7f, 0.7f, faded.opacity)),
    )
    val sampler = remember { TextureSampler() }
    return rememberMaterialInstance(compiled, albedo, faded) {
        setParameter("albedo", albedo, sampler)
        setParameter("opacity", faded.opacity)
        setParameter("metallicFactor", base.metallic)
        setParameter("roughnessFactor", base.roughness)
    }
}

@Composable
private fun rememberHatchMaterial(material: HatchMaterial): MaterialInstance {
    val compiled = rememberMaterial(key = "compose-scene-3d-hatch-v1.72") {
        Res.readBytes("files/materials/hatch.filamat")
    }
    if (compiled == null) {
        return rememberUnlitColorMaterialInstance(material.backgroundColor.toFilamentColor())
    }
    val background = material.backgroundColor.toLinearSrgb()
    val line = material.lineColor.toLinearSrgb()
    return rememberMaterialInstance(compiled, material) {
        setParameter(
            "backgroundColor", background.red, background.green, background.blue, background.alpha,
        )
        setParameter("lineColor", line.red, line.green, line.blue, line.alpha)
        setParameter("spacing", material.spacing)
        setParameter("lineWidth", material.lineWidth)
        setParameter("angleRadians", material.angleRadians)
    }
}

@Composable
private fun rememberClippedPbrMaterial(material: ClippedPbrMaterial): MaterialInstance {
    val compiled = rememberMaterial(key = "compose-scene-3d-clipped-pbr-v1.72") {
        Res.readBytes("files/materials/clipped_pbr.filamat")
    }
    if (compiled == null) {
        return rememberColorMaterialInstance(
            color = material.baseColor.toFilamentColor(),
            metallic = material.metallic,
            roughness = material.roughness,
            reflectance = material.reflectance,
        )
    }
    val linear = material.baseColor.toLinearSrgb()
    return rememberMaterialInstance(compiled, material) {
        setParameter("baseColor", linear.red, linear.green, linear.blue, linear.alpha)
        setParameter("metallic", material.metallic)
        setParameter("roughness", material.roughness)
        setParameter("reflectance", material.reflectance)
        setParameter("planeCount", material.planes.size.toFloat())
        val equations = material.planes.map { plane ->
            val length = kotlin.math.sqrt(
                plane.normal.x * plane.normal.x +
                    plane.normal.y * plane.normal.y +
                    plane.normal.z * plane.normal.z,
            )
            val sign = if (plane.keepPositive) 1f else -1f
            floatArrayOf(
                sign * plane.normal.x / length,
                sign * plane.normal.y / length,
                sign * plane.normal.z / length,
                sign * plane.offset / length,
            )
        }
        repeat(3) { index ->
            val equation = equations.getOrNull(index) ?: floatArrayOf(0f, 1f, 0f, 0f)
            setParameter(
                "plane$index", equation[0], equation[1], equation[2], equation[3],
            )
        }
    }
}

@Composable
private fun rememberOutlineMaterial(outline: ModelPartOutline): MaterialInstance? {
    val compiled = rememberMaterial(key = "compose-scene-3d-outline-v1.72") {
        Res.readBytes("files/materials/outline.filamat")
    } ?: return null
    val linear = outline.color.toLinearSrgb()
    return rememberMaterialInstance(compiled, outline) {
        setParameter("color", linear.red, linear.green, linear.blue, linear.alpha)
        setParameter("width", outline.width)
    }
}

@Composable
private fun rememberPbrTexturedMaterial(
    renderer: FilamentRenderer,
    material: TexturedMaterial,
): io.github.erkko68.filament.MaterialInstance {
    val albedo = rememberSceneTexture(renderer, material.baseColorTexture, TextureLoader.TextureType.COLOR)
    val normal = rememberSceneTexture(renderer, material.normalTexture, TextureLoader.TextureType.NORMAL)
    val metallicRoughness = rememberSceneTexture(
        renderer,
        material.metallicRoughnessTexture,
        TextureLoader.TextureType.DATA,
    )
    val emissive = rememberSceneTexture(renderer, material.emissiveTexture, TextureLoader.TextureType.COLOR)
    val ambientOcclusion = rememberSceneTexture(
        renderer,
        material.ambientOcclusionTexture,
        TextureLoader.TextureType.DATA,
    )
    val compiled = rememberMaterial(key = "compose-scene-3d-textured-pbr-v1.72") {
        Res.readBytes("files/materials/textured_pbr.filamat")
    }
    if (albedo == null || compiled == null) {
        return rememberColorMaterialInstance(
            color = Color(0.7f, 0.7f, 0.7f),
            metallic = material.metallic,
            roughness = material.roughness,
        )
    }

    val sampler = remember {
        TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.REPEAT,
        )
    }
    val emissiveFactor = material.emissiveColor.toLinearSrgb()
    return rememberMaterialInstance(
        compiled,
        albedo,
        normal,
        metallicRoughness,
        emissive,
        ambientOcclusion,
        material,
    ) {
        setParameter("albedo", albedo, sampler)
        normal?.let { setParameter("normalMap", it, sampler) }
        metallicRoughness?.let { setParameter("metallicRoughnessMap", it, sampler) }
        emissive?.let { setParameter("emissiveMap", it, sampler) }
        ambientOcclusion?.let { setParameter("aoMap", it, sampler) }
        setParameter("metallicFactor", material.metallic)
        setParameter("roughnessFactor", material.roughness)
        setParameter("normalScale", material.normalScale)
        setParameter(
            "emissiveFactor",
            emissiveFactor.red,
            emissiveFactor.green,
            emissiveFactor.blue,
        )
        setParameter("emissiveIntensity", material.emissiveIntensity)
        setParameter("aoStrength", material.ambientOcclusionStrength)
        setParameter("hasNormalMap", if (normal == null) 0f else 1f)
        setParameter("hasMetallicRoughnessMap", if (metallicRoughness == null) 0f else 1f)
        setParameter("hasEmissiveMap", if (emissive == null) 0f else 1f)
        setParameter("hasAoMap", if (ambientOcclusion == null) 0f else 1f)
    }
}

@Composable
private fun rememberSceneTexture(
    renderer: FilamentRenderer,
    source: TextureSource?,
    type: TextureLoader.TextureType,
): Texture? {
    if (source == null) return null
    return rememberTexture(
        type = type,
        key = source.assetKey(),
        onError = { renderer.onTextureError(source, it) },
    ) {
        renderer.textureByteLoader.load(source)
    }
}

@Composable
private fun rememberTransparentMaterial(material: TransparentMaterial): io.github.erkko68.filament.MaterialInstance {
    val compiled = rememberMaterial(key = "compose-scene-3d-transparent-lit-v1.72") {
        Res.readBytes("files/materials/transparent_lit.filamat")
    }
    if (compiled == null) {
        return rememberColorMaterialInstance(
            color = material.color.toFilamentColor(),
            metallic = material.metallic,
            roughness = material.roughness,
            reflectance = material.reflectance,
        )
    }

    val linear = material.color.toLinearSrgb()
    return rememberMaterialInstance(
        compiled,
        linear,
        material.metallic,
        material.roughness,
        material.reflectance,
    ) {
        setParameter(
            "baseColor",
            linear.red * linear.alpha,
            linear.green * linear.alpha,
            linear.blue * linear.alpha,
            linear.alpha,
        )
        setParameter("metallic", material.metallic)
        setParameter("roughness", material.roughness)
        setParameter("reflectance", material.reflectance)
    }
}

private fun Color3D.toFilamentColor(): Color {
    val linear = toLinearSrgb()
    return Color(linear.red, linear.green, linear.blue)
}

private fun Vec3.toFilamentPosition() = Position(x, y, z)

private fun Vec3.toFilamentScale() = Scale(x, y, z)

private fun dev.composescene3d.core.Quaternion.toFilamentQuaternion() =
    Quaternion(x, y, z, w)

private data class CameraSyncSnapshot(
    val eye: Vec3,
    val target: Vec3,
    val up: Vec3,
    val projection: CameraProjection,
)

private fun CameraProjection.toFilamentProjection(): Projection = when (this) {
    is CameraProjection.Perspective -> Projection.Perspective(
        fovDegrees = verticalFovDegrees,
        near = near,
        far = far,
    )
    is CameraProjection.Orthographic -> Projection.Orthographic(
        left = -verticalSize / 2.0,
        right = verticalSize / 2.0,
        bottom = -verticalSize / 2.0,
        top = verticalSize / 2.0,
        near = near,
        far = far,
    )
}

@Composable
private fun FilamentSceneScope.FilamentLight(node: DirectionalLightNode) {
    DirectionalLight(
        direction = Direction(0.3f, -1f, -0.5f),
        color = Color(node.color.x, node.color.y, node.color.z),
        intensity = node.intensity,
        shadow = node.shadow?.toFilamentShadowConfig(),
    )
}

@Composable
private fun FilamentSceneScope.FilamentLight(node: PointLightNode) {
    PointLight(
        position = node.transform.translation.toFilamentPosition(),
        color = node.color.toFilamentColor(),
        intensity = node.intensity,
        falloff = node.falloff,
    )
}

@Composable
private fun FilamentSceneScope.FilamentLight(node: SpotLightNode) {
    SpotLight(
        position = node.transform.translation.toFilamentPosition(),
        direction = Direction(node.direction.x, node.direction.y, node.direction.z),
        color = node.color.toFilamentColor(),
        intensity = node.intensity,
        falloff = node.falloff,
        cone = SpotCone(node.innerConeRadians, node.outerConeRadians),
        shadow = node.shadow?.toFilamentShadowConfig(),
    )
}
