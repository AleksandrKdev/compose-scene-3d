package dev.composescene3d.filament

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import dev.composescene3d.compose.SceneCameraState
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.compose.sceneCameraGestures
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.DirectionalLightNode
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelAssetKey
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.TransparentMaterial
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.EnvironmentMap
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.assetKey
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
import io.github.erkko68.filament.compose.scene.rememberTexturedMaterialInstance
import io.github.erkko68.filament.compose.scene.SpotCone
import io.github.erkko68.filament.compose.scene.SpotLight
import io.github.erkko68.filament.utils.Quaternion
import io.github.erkko68.filament.utils.KTX1Loader
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.Texture
import io.github.erkko68.filament.Renderer
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
) : SceneRenderer {
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
        physicallyBasedRendering = true,
        skeletalAnimation = true,
    )

    private val retainedNodes = mutableStateMapOf<NodeKey, SceneNode>()
    private val entityToNode = mutableMapOf<Int, NodeKey>()
    private val nodeToEntities = mutableMapOf<NodeKey, Set<Int>>()
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
        closed = true
    }

    internal fun registerEntities(key: NodeKey, entities: Collection<Int>) {
        unregisterEntities(key)
        val stableEntities = entities.toSet()
        nodeToEntities[key] = stableEntities
        stableEntities.forEach { entityToNode[it] = key }
    }

    internal fun resolveEntity(entity: Int): NodeKey? = entityToNode[entity]

    private fun unregisterEntities(key: NodeKey) {
        nodeToEntities.remove(key)?.forEach { entity ->
            if (entityToNode[entity] == key) entityToNode.remove(entity)
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
    onNodePicked: (NodeKey?) -> Unit = {},
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, null, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, onNodePicked,
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
    onNodePicked: (NodeKey?) -> Unit = {},
) = FilamentViewportContent(
    renderer, modifier, backgroundColor, environment, cameraState, orbitEnabled, zoomSpeed,
    pickingEnabled, onNodePicked,
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
    onNodePicked: (NodeKey?) -> Unit,
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
            val key = renderer.resolveEntity(result.renderable)
            callbackScope.launch { onNodePicked(key) }
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
        ) {
            FilamentNodes(renderer, renderer.nodes)
        }
    }
}

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
                is DirectionalLightNode -> FilamentLight(node)
                is PointLightNode -> FilamentLight(node)
                is SpotLightNode -> FilamentLight(node)
                is GroupNode -> Group(
                    position = node.transform.translation.toFilamentPosition(),
                    rotation = node.transform.rotation.toFilamentQuaternion(),
                    scale = node.transform.scale.toFilamentScale(),
                ) {
                    FilamentNodes(renderer, node.children)
                }
                is ModelNode -> error("Model nodes are rendered in shared asset groups")
            }
        }
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
                },
            )
        }
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
        onCreate = { renderer.registerEntities(node.key, listOf(it)) },
    )
}

@Composable
private fun rememberSceneMaterial(renderer: FilamentRenderer, material: Material3D) = when (material) {
    is PbrMaterial -> rememberColorMaterialInstance(
        color = material.baseColor.toFilamentColor(),
        metallic = material.metallic,
        roughness = material.roughness,
        reflectance = material.reflectance,
    )
    is UnlitMaterial -> rememberUnlitColorMaterialInstance(material.color.toFilamentColor())
    is EmissiveMaterial -> rememberEmissiveMaterialInstance(
        color = material.color.toFilamentColor(),
        intensity = material.intensity,
    )
    is TexturedMaterial -> {
        val texture = rememberTexture(
            key = material.baseColorTexture.assetKey(),
            onError = { renderer.onTextureError(material.baseColorTexture, it) },
        ) {
            renderer.textureByteLoader.load(material.baseColorTexture)
        }
        if (texture == null) {
            rememberColorMaterialInstance(
                color = Color(0.7f, 0.7f, 0.7f),
                metallic = material.metallic,
                roughness = material.roughness,
            )
        } else {
            rememberTexturedMaterialInstance(
                texture = texture,
                metallic = material.metallic,
                roughness = material.roughness,
            )
        }
    }
    is TransparentMaterial -> rememberTransparentMaterial(material)
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
    )
}
