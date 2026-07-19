@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.composescene3d.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import dev.composescene3d.compose.SceneCameraState
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.compose.sceneCameraGestures
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.DirectionalLightNode
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.Quaternion
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.ShadowMap3D
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.Transform
import dev.composescene3d.core.TransparentMaterial
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.assetKey
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.js.unsafeCast
import kotlin.js.JsArray
import kotlinx.browser.document
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLTexture
import org.khronos.webgl.set
import org.khronos.webgl.get
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement

/**
 * Browser renderer with no Filament dependency. It uses WebGL2 while retaining the browser canvas
 * behind a transparent Compose input surface. This
 * keeps the scene API backend-neutral and reuses the common camera gesture implementation.
 */
class WebRenderer(
    private val onModelError: (ModelSource, Throwable) -> Unit = { _, _ -> },
    private val onTextureError: (TextureSource, Throwable) -> Unit = { _, _ -> },
) : SceneRenderer {
    internal val nodes = mutableStateMapOf<NodeKey, SceneNode>()

    override val capabilities = RendererCapabilities(
        primitiveGeometry = true,
        customGeometry = true,
        shadows = true,
        physicallyBasedRendering = true,
    )

    override fun apply(commands: List<SceneCommand>) {
        commands.forEach { command ->
            when (command) {
                is SceneCommand.Create -> nodes[command.node.key] = command.node
                is SceneCommand.Update -> nodes[command.node.key] = command.node
                is SceneCommand.Remove -> nodes.remove(command.key)
            }
        }
    }

    override fun close() = nodes.clear()

    internal fun modelError(source: ModelSource, error: Throwable) = onModelError(source, error)
    internal fun textureError(source: TextureSource, error: Throwable) = onTextureError(source, error)
}

@Composable
fun WebViewport(
    renderer: WebRenderer,
    modifier: Modifier = Modifier.fillMaxSize(),
    backgroundColor: Color3D = Color3D(0.04f, 0.05f, 0.07f),
    cameraState: SceneCameraState = rememberSceneCameraState(),
    orbitEnabled: Boolean = true,
    zoomSpeed: Float = 0.12f,
) {
    var viewportHeight by remember { mutableIntStateOf(1) }
    var textureVersion by remember { mutableIntStateOf(0) }
    val gpuSurface = remember(renderer) { WebGlSurface(renderer) { textureVersion++ } }
    DisposableEffect(gpuSurface) { onDispose(gpuSurface::close) }
    var surface = modifier
        .onSizeChanged { viewportHeight = it.height.coerceAtLeast(1) }
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            gpuSurface.place(
                position.x.toInt(), position.y.toInt(),
                coordinates.size.width, coordinates.size.height,
            )
        }
    if (orbitEnabled) {
        surface = surface.sceneCameraGestures(cameraState, { viewportHeight }, zoomSpeed = zoomSpeed)
    }
    Canvas(surface) {
        textureVersion
        gpuSurface.render(renderer.nodes.values, cameraState, backgroundColor)
    }
}

private data class MeshData(
    val positions: List<Vec3>,
    val indices: List<Int>,
    val normals: List<Vec3>,
    val uvs: FloatArray?,
    val material: Material3D,
)

private fun SceneNode.toMesh(): MeshData? = when (this) {
    is BoxNode -> boxMesh(size, PbrMaterial(baseColor = Color3D(color.x, color.y, color.z)))
    is MeshNode -> MeshData(
        geometry.positions.toVec3List(), geometry.indices.toList(), geometry.normals.toVec3List(),
        geometry.uvs, material,
    )
    is PlaneNode -> planeMesh(width, depth, material)
    is SphereNode -> sphereMesh(radius, rings, segments, material)
    is CylinderNode -> cylinderMesh(radius, height, segments, material)
    else -> null
}

private fun boxMesh(size: Vec3, material: Material3D): MeshData {
    val x = size.x / 2f; val y = size.y / 2f; val z = size.z / 2f
    val p = mutableListOf<Vec3>(); val n = mutableListOf<Vec3>(); val i = mutableListOf<Int>()
    fun face(normal: Vec3, a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
        val first = p.size
        p += listOf(a, b, c, d); n += List(4) { normal }
        i += listOf(first, first + 1, first + 2, first, first + 2, first + 3)
    }
    face(Vec3(0f,0f,1f), Vec3(-x,-y,z), Vec3(x,-y,z), Vec3(x,y,z), Vec3(-x,y,z))
    face(Vec3(0f,0f,-1f), Vec3(x,-y,-z), Vec3(-x,-y,-z), Vec3(-x,y,-z), Vec3(x,y,-z))
    face(Vec3(0f,1f,0f), Vec3(-x,y,z), Vec3(x,y,z), Vec3(x,y,-z), Vec3(-x,y,-z))
    face(Vec3(0f,-1f,0f), Vec3(-x,-y,-z), Vec3(x,-y,-z), Vec3(x,-y,z), Vec3(-x,-y,z))
    face(Vec3(1f,0f,0f), Vec3(x,-y,z), Vec3(x,-y,-z), Vec3(x,y,-z), Vec3(x,y,z))
    face(Vec3(-1f,0f,0f), Vec3(-x,-y,-z), Vec3(-x,-y,z), Vec3(-x,y,z), Vec3(-x,y,-z))
    return MeshData(p, i, n, null, material)
}

private fun planeMesh(width: Float, depth: Float, material: Material3D) = MeshData(
    listOf(Vec3(-width/2,0f,-depth/2), Vec3(width/2,0f,-depth/2),
        Vec3(width/2,0f,depth/2), Vec3(-width/2,0f,depth/2)),
    listOf(0,2,1,0,3,2), List(4) { Vec3(0f,1f,0f) },
    floatArrayOf(0f,0f, 1f,0f, 1f,1f, 0f,1f), material,
)

private fun sphereMesh(radius: Float, rings: Int, segments: Int, material: Material3D): MeshData {
    val p = mutableListOf<Vec3>()
    for (ring in 0..rings) {
        val phi = PI.toFloat() * ring / rings
        for (segment in 0..segments) {
            val theta = 2f * PI.toFloat() * segment / segments
            p += Vec3(sin(phi)*cos(theta), cos(phi), sin(phi)*sin(theta)) * radius
        }
    }
    val i = mutableListOf<Int>()
    for (ring in 0 until rings) for (segment in 0 until segments) {
        val a = ring * (segments + 1) + segment; val b = a + segments + 1
        i += listOf(a,b,a+1, a+1,b,b+1)
    }
    val uvs = FloatArray(p.size * 2)
    for (ring in 0..rings) for (segment in 0..segments) {
        val index = ring * (segments + 1) + segment
        uvs[index*2] = segment.toFloat()/segments; uvs[index*2+1] = 1f-ring.toFloat()/rings
    }
    return MeshData(p, i, p.map { it.normalized() }, uvs, material)
}

private fun cylinderMesh(radius: Float, height: Float, segments: Int, material: Material3D): MeshData {
    val p = mutableListOf<Vec3>(); val n = mutableListOf<Vec3>()
    for (y in listOf(-height/2, height/2)) for (s in 0..segments) {
        val a = 2f * PI.toFloat() * s / segments
        p += Vec3(cos(a)*radius, y, sin(a)*radius); n += Vec3(cos(a),0f,sin(a))
    }
    val i = mutableListOf<Int>()
    for (s in 0 until segments) { val a=s; val b=s+segments+1; i += listOf(a,b,a+1,a+1,b,b+1) }
    val uvs = FloatArray(p.size * 2)
    for (row in 0..1) for (s in 0..segments) {
        val index = row * (segments + 1) + s
        uvs[index*2] = s.toFloat()/segments; uvs[index*2+1] = row.toFloat()
    }
    return MeshData(p, i, n, uvs, material)
}

private fun FloatArray.toVec3List() = asList().chunked(3).map { Vec3(it[0], it[1], it[2]) }
private fun Transform.apply(point: Vec3) = rotation.rotate(point * scale) + translation
private fun Quaternion.rotate(v: Vec3): Vec3 {
    val q = Vec3(x,y,z); val uv = q.cross(v); val uuv = q.cross(uv)
    return v + uv * (2f*w) + uuv * 2f
}
private fun Material3D.color(): Color = when (this) {
    is PbrMaterial -> baseColor.toColor()
    is UnlitMaterial -> color.toColor()
    is EmissiveMaterial -> color.toColor(intensity)
    is TransparentMaterial -> color.toColor()
    is TexturedMaterial -> Color.White
}
private fun Material3D.metallic() = when (this) {
    is PbrMaterial -> metallic
    is TransparentMaterial -> metallic
    is TexturedMaterial -> metallic
    else -> 0f
}
private fun Material3D.roughness() = when (this) {
    is PbrMaterial -> roughness
    is TransparentMaterial -> roughness
    is TexturedMaterial -> roughness
    else -> 1f
}
private fun Material3D.reflectance() = when (this) {
    is PbrMaterial -> reflectance
    is TransparentMaterial -> reflectance
    else -> 0.5f
}
private fun Material3D.shadingModel() = when (this) {
    is UnlitMaterial, is EmissiveMaterial -> 1
    else -> 0
}
private fun Material3D.normalScale() = (this as? TexturedMaterial)?.normalScale ?: 1f
private fun Material3D.emissiveColor() = when (this) {
    is TexturedMaterial -> emissiveColor
    is EmissiveMaterial -> color
    else -> Color3D.Black
}
private fun Material3D.emissiveIntensity() = when (this) {
    is TexturedMaterial -> if (emissiveTexture != null) emissiveIntensity else 0f
    is EmissiveMaterial -> intensity
    else -> 0f
}
private fun Material3D.ambientOcclusionStrength() =
    (this as? TexturedMaterial)?.ambientOcclusionStrength ?: 1f
private fun Color3D.toColor(multiplier: Float = 1f) = Color(
    (red*multiplier).coerceIn(0f,1f), (green*multiplier).coerceIn(0f,1f),
    (blue*multiplier).coerceIn(0f,1f), alpha.coerceIn(0f,1f),
)
private operator fun Vec3.plus(v: Vec3)=Vec3(x+v.x,y+v.y,z+v.z)
private operator fun Vec3.minus(v: Vec3)=Vec3(x-v.x,y-v.y,z-v.z)
private operator fun Vec3.times(v: Vec3)=Vec3(x*v.x,y*v.y,z*v.z)
private operator fun Vec3.times(v: Float)=Vec3(x*v,y*v,z*v)
private fun Vec3.dot(v: Vec3)=x*v.x+y*v.y+z*v.z
private fun Vec3.cross(v: Vec3)=Vec3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
private fun Vec3.normalized(): Vec3 { val l=sqrt(dot(this)); return if(l==0f) Vec3.Zero else this*(1f/l) }

private class WebGlSurface(
    private val renderer: WebRenderer,
    private val invalidate: () -> Unit,
) {
    private val canvas = document.createElement("canvas").unsafeCast<HTMLCanvasElement>()
    private val gl: WebGLRenderingContext
    private val program: WebGLProgram
    private val shadowProgram: WebGLProgram
    private var shadowTarget: WebShadowTarget? = null
    private var shadowTargetSize = 0
    private var spotShadowTarget: WebShadowTarget? = null
    private var spotShadowTargetSize = 0
    private val meshBuffers = mutableListOf<WebMeshBuffers>()
    private val worldPositionAttribute: Int
    private val normalAttribute: Int
    private val colorAttribute: Int
    private val uvAttribute: Int
    private val useTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val textureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useNormalTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val normalTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useMetallicRoughnessTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val metallicRoughnessTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useEmissiveTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val emissiveTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useAmbientOcclusionTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val ambientOcclusionTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraPositionUniform: org.khronos.webgl.WebGLUniformLocation?
    private val lightDirectionUniform: org.khronos.webgl.WebGLUniformLocation?
    private val lightColorUniform: org.khronos.webgl.WebGLUniformLocation?
    private val lightIntensityUniform: org.khronos.webgl.WebGLUniformLocation?
    private val metallicUniform: org.khronos.webgl.WebGLUniformLocation?
    private val roughnessUniform: org.khronos.webgl.WebGLUniformLocation?
    private val reflectanceUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadingModelUniform: org.khronos.webgl.WebGLUniformLocation?
    private val normalScaleUniform: org.khronos.webgl.WebGLUniformLocation?
    private val emissiveColorUniform: org.khronos.webgl.WebGLUniformLocation?
    private val emissiveIntensityUniform: org.khronos.webgl.WebGLUniformLocation?
    private val ambientOcclusionStrengthUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraRightUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraUpUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraForwardUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraAspectUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraScaleUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraNearUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraFarUniform: org.khronos.webgl.WebGLUniformLocation?
    private val cameraPerspectiveUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useShadowUniform: org.khronos.webgl.WebGLUniformLocation?
    private val receiveShadowUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowBiasUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowCenterUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowRightUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowUpUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowForwardUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowExtentUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowDepthUniform: org.khronos.webgl.WebGLUniformLocation?
    private val useSpotShadowUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowBiasUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowPositionUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowRightUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowUpUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowForwardUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowTanHalfAngleUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowNearUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowFarUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotShadowLightIndexUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowWorldPositionAttribute: Int
    private val shadowPassCenterUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassRightUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassUpUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassForwardUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassExtentUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassDepthUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassPerspectiveUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassNearUniform: org.khronos.webgl.WebGLUniformLocation?
    private val shadowPassFarUniform: org.khronos.webgl.WebGLUniformLocation?
    private val pointLightCountUniform: org.khronos.webgl.WebGLUniformLocation?
    private val pointLightPositionUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val pointLightColorUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val pointLightIntensityUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val pointLightFalloffUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightCountUniform: org.khronos.webgl.WebGLUniformLocation?
    private val spotLightPositionUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightDirectionUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightColorUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightIntensityUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightFalloffUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightInnerCosUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val spotLightOuterCosUniforms: List<org.khronos.webgl.WebGLUniformLocation?>
    private val textureCache = mutableMapOf<String, WebGLTexture>()
    private val loadingTextures = mutableSetOf<String>()
    private val failedTextures = mutableSetOf<String>()
    private val modelCache = mutableMapOf<String, List<MeshData>>()
    private val loadingModels = mutableSetOf<String>()
    private val failedModels = mutableSetOf<String>()
    private var modelRevision = 0
    private var cachedSceneNodes: List<SceneNode>? = null
    private var cachedModelRevision = -1
    private var cachedBatches = emptyList<GpuMesh>()
    private var width = 1
    private var height = 1

    init {
        canvas.style.cssText = "position:fixed;pointer-events:none;z-index:2147483647;"
        document.body?.appendChild(canvas)
        gl = requireNotNull(webGl2Context(canvas)) {
            "ComposeScene3D Web requires WebGL2"
        }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER)
        worldPositionAttribute = gl.getAttribLocation(program, "aWorldPosition")
        normalAttribute = gl.getAttribLocation(program, "aNormal")
        colorAttribute = gl.getAttribLocation(program, "aColor")
        uvAttribute = gl.getAttribLocation(program, "aUv")
        useTextureUniform = gl.getUniformLocation(program, "uUseTexture")
        textureUniform = gl.getUniformLocation(program, "uTexture")
        useNormalTextureUniform = gl.getUniformLocation(program, "uUseNormalTexture")
        normalTextureUniform = gl.getUniformLocation(program, "uNormalTexture")
        useMetallicRoughnessTextureUniform = gl.getUniformLocation(program, "uUseMetallicRoughnessTexture")
        metallicRoughnessTextureUniform = gl.getUniformLocation(program, "uMetallicRoughnessTexture")
        useEmissiveTextureUniform = gl.getUniformLocation(program, "uUseEmissiveTexture")
        emissiveTextureUniform = gl.getUniformLocation(program, "uEmissiveTexture")
        useAmbientOcclusionTextureUniform = gl.getUniformLocation(program, "uUseAmbientOcclusionTexture")
        ambientOcclusionTextureUniform = gl.getUniformLocation(program, "uAmbientOcclusionTexture")
        cameraPositionUniform = gl.getUniformLocation(program, "uCameraPosition")
        lightDirectionUniform = gl.getUniformLocation(program, "uLightDirection")
        lightColorUniform = gl.getUniformLocation(program, "uLightColor")
        lightIntensityUniform = gl.getUniformLocation(program, "uLightIntensity")
        metallicUniform = gl.getUniformLocation(program, "uMetallic")
        roughnessUniform = gl.getUniformLocation(program, "uRoughness")
        reflectanceUniform = gl.getUniformLocation(program, "uReflectance")
        shadingModelUniform = gl.getUniformLocation(program, "uShadingModel")
        normalScaleUniform = gl.getUniformLocation(program, "uNormalScale")
        emissiveColorUniform = gl.getUniformLocation(program, "uEmissiveColor")
        emissiveIntensityUniform = gl.getUniformLocation(program, "uEmissiveIntensity")
        ambientOcclusionStrengthUniform = gl.getUniformLocation(program, "uAmbientOcclusionStrength")
        cameraRightUniform = gl.getUniformLocation(program, "uCameraRight")
        cameraUpUniform = gl.getUniformLocation(program, "uCameraUp")
        cameraForwardUniform = gl.getUniformLocation(program, "uCameraForward")
        cameraAspectUniform = gl.getUniformLocation(program, "uCameraAspect")
        cameraScaleUniform = gl.getUniformLocation(program, "uCameraScale")
        cameraNearUniform = gl.getUniformLocation(program, "uCameraNear")
        cameraFarUniform = gl.getUniformLocation(program, "uCameraFar")
        cameraPerspectiveUniform = gl.getUniformLocation(program, "uCameraPerspective")
        useShadowUniform = gl.getUniformLocation(program, "uUseShadow")
        receiveShadowUniform = gl.getUniformLocation(program, "uReceiveShadow")
        shadowTextureUniform = gl.getUniformLocation(program, "uShadowTexture")
        shadowBiasUniform = gl.getUniformLocation(program, "uShadowBias")
        shadowCenterUniform = gl.getUniformLocation(program, "uShadowCenter")
        shadowRightUniform = gl.getUniformLocation(program, "uShadowRight")
        shadowUpUniform = gl.getUniformLocation(program, "uShadowUp")
        shadowForwardUniform = gl.getUniformLocation(program, "uShadowForward")
        shadowExtentUniform = gl.getUniformLocation(program, "uShadowExtent")
        shadowDepthUniform = gl.getUniformLocation(program, "uShadowDepth")
        useSpotShadowUniform = gl.getUniformLocation(program, "uUseSpotShadow")
        spotShadowTextureUniform = gl.getUniformLocation(program, "uSpotShadowTexture")
        spotShadowBiasUniform = gl.getUniformLocation(program, "uSpotShadowBias")
        spotShadowPositionUniform = gl.getUniformLocation(program, "uSpotShadowPosition")
        spotShadowRightUniform = gl.getUniformLocation(program, "uSpotShadowRight")
        spotShadowUpUniform = gl.getUniformLocation(program, "uSpotShadowUp")
        spotShadowForwardUniform = gl.getUniformLocation(program, "uSpotShadowForward")
        spotShadowTanHalfAngleUniform = gl.getUniformLocation(program, "uSpotShadowTanHalfAngle")
        spotShadowNearUniform = gl.getUniformLocation(program, "uSpotShadowNear")
        spotShadowFarUniform = gl.getUniformLocation(program, "uSpotShadowFar")
        spotShadowLightIndexUniform = gl.getUniformLocation(program, "uSpotShadowLightIndex")
        shadowWorldPositionAttribute = gl.getAttribLocation(shadowProgram, "aWorldPosition")
        shadowPassCenterUniform = gl.getUniformLocation(shadowProgram, "uShadowCenter")
        shadowPassRightUniform = gl.getUniformLocation(shadowProgram, "uShadowRight")
        shadowPassUpUniform = gl.getUniformLocation(shadowProgram, "uShadowUp")
        shadowPassForwardUniform = gl.getUniformLocation(shadowProgram, "uShadowForward")
        shadowPassExtentUniform = gl.getUniformLocation(shadowProgram, "uShadowExtent")
        shadowPassDepthUniform = gl.getUniformLocation(shadowProgram, "uShadowDepth")
        shadowPassPerspectiveUniform = gl.getUniformLocation(shadowProgram, "uShadowPerspective")
        shadowPassNearUniform = gl.getUniformLocation(shadowProgram, "uShadowNear")
        shadowPassFarUniform = gl.getUniformLocation(shadowProgram, "uShadowFar")
        pointLightCountUniform = gl.getUniformLocation(program, "uPointLightCount")
        pointLightPositionUniforms = lightUniforms("uPointLightPositions")
        pointLightColorUniforms = lightUniforms("uPointLightColors")
        pointLightIntensityUniforms = lightUniforms("uPointLightIntensities")
        pointLightFalloffUniforms = lightUniforms("uPointLightFalloffs")
        spotLightCountUniform = gl.getUniformLocation(program, "uSpotLightCount")
        spotLightPositionUniforms = lightUniforms("uSpotLightPositions")
        spotLightDirectionUniforms = lightUniforms("uSpotLightDirections")
        spotLightColorUniforms = lightUniforms("uSpotLightColors")
        spotLightIntensityUniforms = lightUniforms("uSpotLightIntensities")
        spotLightFalloffUniforms = lightUniforms("uSpotLightFalloffs")
        spotLightInnerCosUniforms = lightUniforms("uSpotLightInnerCos")
        spotLightOuterCosUniforms = lightUniforms("uSpotLightOuterCos")
        gl.enable(WebGLRenderingContext.DEPTH_TEST)
        gl.depthFunc(WebGLRenderingContext.LEQUAL)
    }

    fun place(x: Int, y: Int, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        val density = browserPixelRatio().coerceAtLeast(1.0)
        canvas.style.left = "${x / density}px"
        canvas.style.top = "${y / density}px"
        canvas.style.width = "${this.width / density}px"
        canvas.style.height = "${this.height / density}px"
        if (canvas.width != this.width || canvas.height != this.height) {
            canvas.width = this.width
            canvas.height = this.height
        }
    }

    fun render(nodes: Collection<SceneNode>, camera: SceneCameraState, background: Color3D) {
        val sceneNodes = nodes.toList()
        val batches = if (cachedSceneNodes == sceneNodes && cachedModelRevision == modelRevision) {
            cachedBatches
        } else {
            buildGpuBatches(sceneNodes, resolveModel = { node -> resolveModel(node) }).also {
                cachedSceneNodes = sceneNodes
                cachedModelRevision = modelRevision
                cachedBatches = it
            }
        }
        val preparedMeshes = prepareMeshes(batches)
        val lights = nodes.webLights()
        val directionalProjection = lights.directional.shadow?.let { shadow ->
            fittedDirectionalShadowProjection(batches, camera.target, shadow.mapSize)
        } ?: directionalShadowProjection(camera.target)
        lights.directional.shadow?.let { shadow ->
            renderShadowMap(preparedMeshes, directionalProjection, shadow, spot = false)
        }
        val shadowSpot = lights.spots.firstOrNull { it.shadow != null }
        shadowSpot?.shadow?.let { shadow ->
            renderShadowMap(preparedMeshes, spotShadowProjection(shadowSpot), shadow, spot = true)
        }
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(background.red, background.green, background.blue, background.alpha)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT or WebGLRenderingContext.DEPTH_BUFFER_BIT)
        if (batches.isEmpty()) return

        gl.useProgram(program)
        uploadCamera(camera)
        val light = lights.directional
        val shadowEnabled = light.shadow != null
        gl.uniform3f(cameraPositionUniform, camera.eye.x, camera.eye.y, camera.eye.z)
        gl.uniform3f(lightDirectionUniform, -0.3f, 1f, 0.5f)
        gl.uniform3f(lightColorUniform, light.color.x, light.color.y, light.color.z)
        gl.uniform1f(lightIntensityUniform, light.intensity)
        gl.uniform1i(useShadowUniform, if (shadowEnabled) 1 else 0)
        gl.uniform1f(shadowBiasUniform, light.shadow?.let {
            it.constantBias + it.normalBias * 0.0005f
        } ?: 0f)
        uploadShadowBasis(program = false, directionalProjection)
        if (shadowEnabled) {
            gl.activeTexture(WebGLRenderingContext.TEXTURE0 + SHADOW_TEXTURE_UNIT)
            gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, requireNotNull(shadowTarget).texture)
            gl.uniform1i(shadowTextureUniform, SHADOW_TEXTURE_UNIT)
        }
        val spotShadowEnabled = shadowSpot != null
        gl.uniform1i(useSpotShadowUniform, if (spotShadowEnabled) 1 else 0)
        if (shadowSpot != null) {
            val projection = spotShadowProjection(shadowSpot)
            gl.uniform1f(spotShadowBiasUniform,
                requireNotNull(shadowSpot.shadow).constantBias + shadowSpot.shadow.normalBias * 0.0005f)
            gl.uniform3f(spotShadowPositionUniform,
                projection.center.x, projection.center.y, projection.center.z)
            gl.uniform3f(spotShadowRightUniform,
                projection.right.x, projection.right.y, projection.right.z)
            gl.uniform3f(spotShadowUpUniform, projection.up.x, projection.up.y, projection.up.z)
            gl.uniform3f(spotShadowForwardUniform,
                projection.forward.x, projection.forward.y, projection.forward.z)
            gl.uniform1f(spotShadowTanHalfAngleUniform, projection.extent)
            gl.uniform1f(spotShadowNearUniform, projection.near)
            gl.uniform1f(spotShadowFarUniform, projection.depth)
            gl.uniform1i(spotShadowLightIndexUniform, lights.spots.indexOf(shadowSpot))
            gl.activeTexture(WebGLRenderingContext.TEXTURE0 + SPOT_SHADOW_TEXTURE_UNIT)
            gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, requireNotNull(spotShadowTarget).texture)
            gl.uniform1i(spotShadowTextureUniform, SPOT_SHADOW_TEXTURE_UNIT)
        }
        uploadPointLights(lights.points)
        uploadSpotLights(lights.spots)
        preparedMeshes.forEach { prepared ->
            val mesh = prepared.mesh
            gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, prepared.buffers.vertex)
            gl.enableVertexAttribArray(worldPositionAttribute)
            gl.vertexAttribPointer(worldPositionAttribute, 3, WebGLRenderingContext.FLOAT, false, 64, 16)
            gl.enableVertexAttribArray(normalAttribute)
            gl.vertexAttribPointer(normalAttribute, 3, WebGLRenderingContext.FLOAT, false, 64, 28)
            gl.enableVertexAttribArray(colorAttribute)
            gl.vertexAttribPointer(colorAttribute, 4, WebGLRenderingContext.FLOAT, false, 64, 40)
            gl.enableVertexAttribArray(uvAttribute)
            gl.vertexAttribPointer(uvAttribute, 2, WebGLRenderingContext.FLOAT, false, 64, 56)
            gl.uniform1f(metallicUniform, mesh.metallic)
            gl.uniform1f(roughnessUniform, mesh.roughness)
            gl.uniform1f(reflectanceUniform, mesh.reflectance)
            gl.uniform1i(shadingModelUniform, mesh.shadingModel)
            gl.uniform1f(normalScaleUniform, mesh.normalScale)
            gl.uniform3f(emissiveColorUniform,
                mesh.emissiveColor.red, mesh.emissiveColor.green, mesh.emissiveColor.blue)
            gl.uniform1f(emissiveIntensityUniform, mesh.emissiveIntensity)
            gl.uniform1f(ambientOcclusionStrengthUniform, mesh.ambientOcclusionStrength)
            gl.uniform1i(receiveShadowUniform, if (mesh.receiveShadows) 1 else 0)
            bindTexture(mesh.baseColorTexture, 0, useTextureUniform, textureUniform)
            bindTexture(mesh.normalTexture, 1, useNormalTextureUniform, normalTextureUniform)
            bindTexture(mesh.metallicRoughnessTexture, 2,
                useMetallicRoughnessTextureUniform, metallicRoughnessTextureUniform)
            bindTexture(mesh.emissiveTexture, 3, useEmissiveTextureUniform, emissiveTextureUniform)
            bindTexture(mesh.ambientOcclusionTexture, 4,
                useAmbientOcclusionTextureUniform, ambientOcclusionTextureUniform)
            gl.bindBuffer(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER, prepared.buffers.index)
            gl.drawElements(WebGLRenderingContext.TRIANGLES, mesh.indices.size,
                WebGLRenderingContext.UNSIGNED_INT, 0)
        }
    }

    fun close() {
        meshBuffers.forEach { buffers ->
            gl.deleteBuffer(buffers.vertex)
            gl.deleteBuffer(buffers.index)
        }
        cachedSceneNodes = null
        cachedBatches = emptyList()
        gl.deleteProgram(program)
        gl.deleteProgram(shadowProgram)
        shadowTarget?.let { deleteShadowTarget(gl, it) }
        spotShadowTarget?.let { deleteShadowTarget(gl, it) }
        textureCache.values.forEach(gl::deleteTexture)
        canvas.remove()
    }

    private fun renderShadowMap(
        batches: List<PreparedGpuMesh>,
        projection: ShadowBasis,
        shadow: ShadowMap3D,
        spot: Boolean,
    ) {
        val target = ensureShadowTarget(shadow.mapSize, spot)
        gl.bindFramebuffer(WebGLRenderingContext.FRAMEBUFFER, target.framebuffer)
        val targetSize = if (spot) spotShadowTargetSize else shadowTargetSize
        gl.viewport(0, 0, targetSize, targetSize)
        gl.clear(WebGLRenderingContext.DEPTH_BUFFER_BIT)
        gl.useProgram(shadowProgram)
        uploadShadowBasis(program = true, projection)
        gl.enable(WebGLRenderingContext.POLYGON_OFFSET_FILL)
        gl.polygonOffset(SHADOW_POLYGON_OFFSET_FACTOR, SHADOW_POLYGON_OFFSET_UNITS)
        batches.filter { it.mesh.castShadows }.forEach { prepared ->
            val mesh = prepared.mesh
            gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, prepared.buffers.vertex)
            gl.enableVertexAttribArray(shadowWorldPositionAttribute)
            gl.vertexAttribPointer(shadowWorldPositionAttribute, 3,
                WebGLRenderingContext.FLOAT, false, 64, 16)
            gl.bindBuffer(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER, prepared.buffers.index)
            gl.drawElements(WebGLRenderingContext.TRIANGLES, mesh.indices.size,
                WebGLRenderingContext.UNSIGNED_INT, 0)
        }
        gl.disable(WebGLRenderingContext.POLYGON_OFFSET_FILL)
        gl.bindFramebuffer(WebGLRenderingContext.FRAMEBUFFER, null)
    }

    private fun prepareMeshes(meshes: List<GpuMesh>): List<PreparedGpuMesh> {
        while (meshBuffers.size < meshes.size) {
            meshBuffers += WebMeshBuffers(
                vertex = requireNotNull(gl.createBuffer()),
                index = requireNotNull(gl.createBuffer()),
            )
        }
        while (meshBuffers.size > meshes.size) {
            val buffers = meshBuffers.removeAt(meshBuffers.lastIndex)
            gl.deleteBuffer(buffers.vertex)
            gl.deleteBuffer(buffers.index)
        }
        return meshes.mapIndexed { index, mesh ->
            val buffers = meshBuffers[index]
            val vertexHash = mesh.vertices.contentHashCode()
            if (buffers.vertexHash != vertexHash) {
                gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffers.vertex)
                gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER,
                    mesh.vertices.toTypedArray(), WebGLRenderingContext.STATIC_DRAW)
                buffers.vertexHash = vertexHash
            }
            val indexHash = mesh.indices.contentHashCode()
            if (buffers.indexHash != indexHash) {
                gl.bindBuffer(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER, buffers.index)
                gl.bufferData(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER,
                    mesh.indices.toTypedArray(), WebGLRenderingContext.STATIC_DRAW)
                buffers.indexHash = indexHash
            }
            PreparedGpuMesh(mesh, buffers)
        }
    }

    private fun uploadCamera(camera: SceneCameraState) {
        val forward = (camera.target - camera.eye).normalized()
        val right = forward.cross(camera.up).normalized()
        val cameraUp = right.cross(forward)
        val aspect = (width.toFloat() / height.toFloat()).coerceAtLeast(0.01f)
        gl.uniform3f(cameraRightUniform, right.x, right.y, right.z)
        gl.uniform3f(cameraUpUniform, cameraUp.x, cameraUp.y, cameraUp.z)
        gl.uniform3f(cameraForwardUniform, forward.x, forward.y, forward.z)
        gl.uniform1f(cameraAspectUniform, aspect)
        when (val projection = camera.projection) {
            is CameraProjection.Perspective -> {
                gl.uniform1i(cameraPerspectiveUniform, 1)
                gl.uniform1f(cameraScaleUniform,
                    (1.0 / tan(projection.verticalFovDegrees * PI / 360.0)).toFloat())
                gl.uniform1f(cameraNearUniform, projection.near.toFloat())
                gl.uniform1f(cameraFarUniform, projection.far.toFloat())
            }
            is CameraProjection.Orthographic -> {
                gl.uniform1i(cameraPerspectiveUniform, 0)
                gl.uniform1f(cameraScaleUniform, 2f / projection.verticalSize.toFloat())
                gl.uniform1f(cameraNearUniform, projection.near.toFloat())
                gl.uniform1f(cameraFarUniform, projection.far.toFloat())
            }
        }
    }

    private fun ensureShadowTarget(requestedSize: Int, spot: Boolean): WebShadowTarget {
        val size = requestedSize.coerceAtMost(MAX_WEB_SHADOW_MAP_SIZE)
        if (spot) {
            if (spotShadowTarget == null || spotShadowTargetSize != size) {
                spotShadowTarget?.let { deleteShadowTarget(gl, it) }
                spotShadowTarget = createShadowTarget(gl, size)
                spotShadowTargetSize = size
            }
            return requireNotNull(spotShadowTarget)
        }
        if (shadowTarget == null || shadowTargetSize != size) {
            shadowTarget?.let { deleteShadowTarget(gl, it) }
            shadowTarget = createShadowTarget(gl, size)
            shadowTargetSize = size
        }
        return requireNotNull(shadowTarget)
    }

    private fun uploadShadowBasis(program: Boolean, basis: ShadowBasis) {
        val centerUniform = if (program) shadowPassCenterUniform else shadowCenterUniform
        val rightUniform = if (program) shadowPassRightUniform else shadowRightUniform
        val upUniform = if (program) shadowPassUpUniform else shadowUpUniform
        val forwardUniform = if (program) shadowPassForwardUniform else shadowForwardUniform
        val extentUniform = if (program) shadowPassExtentUniform else shadowExtentUniform
        val depthUniform = if (program) shadowPassDepthUniform else shadowDepthUniform
        gl.uniform3f(centerUniform, basis.center.x, basis.center.y, basis.center.z)
        gl.uniform3f(rightUniform, basis.right.x, basis.right.y, basis.right.z)
        gl.uniform3f(upUniform, basis.up.x, basis.up.y, basis.up.z)
        gl.uniform3f(forwardUniform, basis.forward.x, basis.forward.y, basis.forward.z)
        gl.uniform1f(extentUniform, basis.extent)
        gl.uniform1f(depthUniform, basis.depth)
        if (program) {
            gl.uniform1i(shadowPassPerspectiveUniform, if (basis.perspective) 1 else 0)
            gl.uniform1f(shadowPassNearUniform, basis.near)
            gl.uniform1f(shadowPassFarUniform, basis.depth)
        }
    }

    private fun bindTexture(
        source: TextureSource?,
        unit: Int,
        useUniform: org.khronos.webgl.WebGLUniformLocation?,
        samplerUniform: org.khronos.webgl.WebGLUniformLocation?,
    ) {
        if (source == null) {
            gl.uniform1i(useUniform, 0)
            return
        }
        val key = source.assetKey().value
        val texture = textureCache[key]
        if (texture == null) {
            if (key in failedTextures) {
                gl.uniform1i(useUniform, 0)
                return
            }
            requestTexture(key, source)
            gl.uniform1i(useUniform, 0)
            return
        }
        gl.activeTexture(WebGLRenderingContext.TEXTURE0 + unit)
        gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture)
        gl.uniform1i(samplerUniform, unit)
        gl.uniform1i(useUniform, 1)
    }

    private fun requestTexture(key: String, source: TextureSource) {
        if (!loadingTextures.add(key)) return
        val url = when (source) {
            is TextureSource.Url -> source.value
            is TextureSource.Resource -> source.path
            is TextureSource.Bytes -> bytesUrl(source.value.toTypedArray())
        }
        loadImage(url, onLoad = { image ->
            val texture = requireNotNull(gl.createTexture())
            gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture)
            // TextureSource UVs follow glTF's top-left origin. Browser image uploads already map
            // the first decoded row to v=0, so flipping here corrupts atlased model textures.
            gl.pixelStorei(WebGLRenderingContext.UNPACK_FLIP_Y_WEBGL, 0)
            gl.texImage2D(WebGLRenderingContext.TEXTURE_2D, 0, WebGLRenderingContext.RGBA,
                WebGLRenderingContext.RGBA, WebGLRenderingContext.UNSIGNED_BYTE, image)
            gl.generateMipmap(WebGLRenderingContext.TEXTURE_2D)
            gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,
                WebGLRenderingContext.TEXTURE_MIN_FILTER, WebGLRenderingContext.LINEAR_MIPMAP_LINEAR)
            textureCache[key] = texture
            loadingTextures.remove(key)
            if (source is TextureSource.Bytes) revokeObjectUrl(url)
            invalidate()
        }, onError = { message ->
            loadingTextures.remove(key)
            failedTextures += key
            if (source is TextureSource.Bytes) revokeObjectUrl(url)
            renderer.textureError(source, IllegalStateException(message))
        })
    }

    private fun resolveModel(node: ModelNode): List<MeshData>? {
        val key = node.source.assetKey().value
        modelCache[key]?.let { return it }
        if (key in failedModels) return null
        if (loadingModels.add(key)) {
            val accept: (Uint8Array) -> Unit = { bytes ->
                runCatching { parseGlb(bytes, key) }
                    .onSuccess {
                        modelCache[key] = it
                        modelRevision++
                    }
                    .onFailure {
                        failedModels += key
                        renderer.modelError(node.source, it)
                    }
                loadingModels.remove(key)
                invalidate()
            }
            when (val source = node.source) {
                is ModelSource.Bytes -> accept(source.value.toTypedArray())
                is ModelSource.Resource -> requestModelUrl(source.path, source, accept, key)
                is ModelSource.Url -> requestModelUrl(source.value, source, accept, key)
            }
        }
        return modelCache[key]
    }

    private fun requestModelUrl(
        url: String,
        source: ModelSource,
        accept: (Uint8Array) -> Unit,
        key: String,
    ) {
        val reject: (String) -> Unit = { message ->
            loadingModels.remove(key)
            failedModels += key
            renderer.modelError(source, IllegalStateException(message))
        }
        if (url.substringBefore('?').lowercase().endsWith(".gltf")) {
            loadGltfAsGlb(url, accept, reject)
        } else {
            fetchBytes(url, accept, reject)
        }
    }

    private fun lightUniforms(name: String) =
        List(MAX_WEB_LIGHTS) { index -> gl.getUniformLocation(program, "$name[$index]") }

    private fun uploadPointLights(lights: List<WebPointLight>) {
        gl.uniform1i(pointLightCountUniform, lights.size)
        lights.forEachIndexed { index, light ->
            gl.uniform3f(pointLightPositionUniforms[index],
                light.position.x, light.position.y, light.position.z)
            gl.uniform3f(pointLightColorUniforms[index], light.color.x, light.color.y, light.color.z)
            gl.uniform1f(pointLightIntensityUniforms[index], light.intensity)
            gl.uniform1f(pointLightFalloffUniforms[index], light.falloff)
        }
    }

    private fun uploadSpotLights(lights: List<WebSpotLight>) {
        gl.uniform1i(spotLightCountUniform, lights.size)
        lights.forEachIndexed { index, light ->
            gl.uniform3f(spotLightPositionUniforms[index],
                light.position.x, light.position.y, light.position.z)
            gl.uniform3f(spotLightDirectionUniforms[index],
                light.direction.x, light.direction.y, light.direction.z)
            gl.uniform3f(spotLightColorUniforms[index], light.color.x, light.color.y, light.color.z)
            gl.uniform1f(spotLightIntensityUniforms[index], light.intensity)
            gl.uniform1f(spotLightFalloffUniforms[index], light.falloff)
            gl.uniform1f(spotLightInnerCosUniforms[index], light.innerCos)
            gl.uniform1f(spotLightOuterCosUniforms[index], light.outerCos)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): WebGLProgram {
        val vertex = compileShader(WebGLRenderingContext.VERTEX_SHADER, vertexSource)
        val fragment = compileShader(WebGLRenderingContext.FRAGMENT_SHADER, fragmentSource)
        val result = requireNotNull(gl.createProgram())
        gl.attachShader(result, vertex)
        gl.attachShader(result, fragment)
        gl.linkProgram(result)
        check(gl.getProgramParameter(result, WebGLRenderingContext.LINK_STATUS) == true.toJsBoolean()) {
            "WebGL program link failed: ${gl.getProgramInfoLog(result)}"
        }
        gl.deleteShader(vertex)
        gl.deleteShader(fragment)
        return result
    }

    private fun compileShader(type: Int, source: String): WebGLShader {
        val shader = requireNotNull(gl.createShader(type))
        gl.shaderSource(shader, source)
        gl.compileShader(shader)
        check(gl.getShaderParameter(shader, WebGLRenderingContext.COMPILE_STATUS) == true.toJsBoolean()) {
            "WebGL shader compile failed: ${gl.getShaderInfoLog(shader)}"
        }
        return shader
    }
}

private data class GpuMesh(
    val vertices: FloatArray,
    val indices: IntArray,
    val baseColorTexture: TextureSource?,
    val normalTexture: TextureSource?,
    val metallicRoughnessTexture: TextureSource?,
    val emissiveTexture: TextureSource?,
    val ambientOcclusionTexture: TextureSource?,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float,
    val shadingModel: Int,
    val normalScale: Float,
    val emissiveColor: Color3D,
    val emissiveIntensity: Float,
    val ambientOcclusionStrength: Float,
    val castShadows: Boolean,
    val receiveShadows: Boolean,
)

private data class WebMeshBuffers(
    val vertex: WebGLBuffer,
    val index: WebGLBuffer,
    var vertexHash: Int? = null,
    var indexHash: Int? = null,
)

private data class PreparedGpuMesh(
    val mesh: GpuMesh,
    val buffers: WebMeshBuffers,
)

private data class WebDirectionalLight(
    val color: Vec3,
    val intensity: Float,
    val shadow: ShadowMap3D? = null,
)
private data class WebPointLight(
    val position: Vec3, val color: Vec3, val intensity: Float, val falloff: Float,
)
private data class WebSpotLight(
    val position: Vec3,
    val direction: Vec3,
    val color: Vec3,
    val intensity: Float,
    val falloff: Float,
    val innerCos: Float,
    val outerCos: Float,
    val outerConeRadians: Float,
    val shadow: ShadowMap3D? = null,
)
private data class WebLights(
    var directional: WebDirectionalLight = WebDirectionalLight(Vec3.One, 2f),
    val points: MutableList<WebPointLight> = mutableListOf(),
    val spots: MutableList<WebSpotLight> = mutableListOf(),
)
private data class ShadowBasis(
    val center: Vec3,
    val right: Vec3,
    val up: Vec3,
    val forward: Vec3,
    val extent: Float = 8f,
    val depth: Float = 12f,
    val near: Float = 0.1f,
    val perspective: Boolean = false,
)

private fun directionalShadowProjection(center: Vec3): ShadowBasis {
    val forward = Vec3(0.3f, -1f, -0.5f).normalized()
    val right = forward.cross(Vec3(0f, 1f, 0f)).normalized()
    return ShadowBasis(center, right, right.cross(forward).normalized(), forward)
}

private fun fittedDirectionalShadowProjection(
    batches: List<GpuMesh>,
    fallbackCenter: Vec3,
    mapSize: Int,
): ShadowBasis {
    val basis = directionalShadowProjection(fallbackCenter)
    var minRight = Float.POSITIVE_INFINITY
    var maxRight = Float.NEGATIVE_INFINITY
    var minUp = Float.POSITIVE_INFINITY
    var maxUp = Float.NEGATIVE_INFINITY
    var minForward = Float.POSITIVE_INFINITY
    var maxForward = Float.NEGATIVE_INFINITY
    var vertexCount = 0
    batches.forEach { mesh ->
        var offset = WORLD_POSITION_FLOAT_OFFSET
        while (offset + 2 < mesh.vertices.size) {
            val point = Vec3(mesh.vertices[offset], mesh.vertices[offset + 1], mesh.vertices[offset + 2])
            val right = point.dot(basis.right)
            val up = point.dot(basis.up)
            val forward = point.dot(basis.forward)
            minRight = minOf(minRight, right)
            maxRight = maxOf(maxRight, right)
            minUp = minOf(minUp, up)
            maxUp = maxOf(maxUp, up)
            minForward = minOf(minForward, forward)
            maxForward = maxOf(maxForward, forward)
            vertexCount++
            offset += VERTEX_STRIDE_FLOATS
        }
    }
    if (vertexCount == 0) return basis

    val halfWidth = (maxRight - minRight) * 0.5f + DIRECTIONAL_SHADOW_PADDING
    val halfHeight = (maxUp - minUp) * 0.5f + DIRECTIONAL_SHADOW_PADDING
    val extent = maxOf(halfWidth, halfHeight, MIN_DIRECTIONAL_SHADOW_EXTENT)
    val depth = maxOf(
        (maxForward - minForward) * 0.5f + DIRECTIONAL_SHADOW_DEPTH_PADDING,
        MIN_DIRECTIONAL_SHADOW_DEPTH,
    )
    val texelSize = 2f * extent / mapSize.coerceAtMost(MAX_WEB_SHADOW_MAP_SIZE)
    val centerRight = snapToStep((minRight + maxRight) * 0.5f, texelSize)
    val centerUp = snapToStep((minUp + maxUp) * 0.5f, texelSize)
    val centerForward = (minForward + maxForward) * 0.5f
    val center = basis.right * centerRight + basis.up * centerUp + basis.forward * centerForward
    return basis.copy(center = center, extent = extent, depth = depth)
}

private fun snapToStep(value: Float, step: Float): Float =
    kotlin.math.floor(value / step + 0.5f) * step

private fun spotShadowProjection(light: WebSpotLight): ShadowBasis {
    val referenceUp = if (kotlin.math.abs(light.direction.y) > 0.98f) {
        Vec3(1f, 0f, 0f)
    } else {
        Vec3(0f, 1f, 0f)
    }
    val right = light.direction.cross(referenceUp).normalized()
    return ShadowBasis(
        center = light.position,
        right = right,
        up = right.cross(light.direction).normalized(),
        forward = light.direction,
        extent = tan(light.outerConeRadians),
        depth = light.falloff,
        near = 0.1f,
        perspective = true,
    )
}

private fun Collection<SceneNode>.webLights(): WebLights {
    val result = WebLights()
    var foundDirectional = false
    fun visit(nodes: Collection<SceneNode>, parents: List<Transform>) {
        nodes.forEach { node ->
            val transforms = listOf(node.transform) + parents
            when (node) {
                is GroupNode -> visit(node.children, transforms)
                is DirectionalLightNode -> if (!foundDirectional) {
                    result.directional = WebDirectionalLight(
                        node.color, node.intensity / 100_000f, node.shadow)
                    foundDirectional = true
                }
                is PointLightNode -> if (result.points.size < MAX_WEB_LIGHTS) {
                    val position = transforms.fold(Vec3.Zero) { value, transform ->
                        transform.apply(value)
                    }
                    result.points += WebPointLight(
                        position, Vec3(node.color.red, node.color.green, node.color.blue),
                        node.intensity / 100_000f, node.falloff,
                    )
                }
                is SpotLightNode -> if (result.spots.size < MAX_WEB_LIGHTS) {
                    val position = transforms.fold(Vec3.Zero) { value, transform ->
                        transform.apply(value)
                    }
                    val direction = transforms.fold(node.direction) { value, transform ->
                        transform.rotation.rotate(value)
                    }.normalized()
                    result.spots += WebSpotLight(
                        position, direction,
                        Vec3(node.color.red, node.color.green, node.color.blue),
                        node.intensity / 100_000f, node.falloff,
                        cos(node.innerConeRadians), cos(node.outerConeRadians),
                        node.outerConeRadians, node.shadow,
                    )
                }
                else -> Unit
            }
        }
    }
    visit(this, emptyList())
    return result
}

private fun buildGpuBatches(
    nodes: Collection<SceneNode>,
    resolveModel: (ModelNode) -> List<MeshData>?,
): List<GpuMesh> = buildList {
    fun appendMesh(
        mesh: MeshData,
        transforms: List<Transform>,
        castShadows: Boolean,
        receiveShadows: Boolean,
    ) {
        val vertices = mutableListOf<Float>()
        mesh.positions.forEachIndexed { index, position ->
            val world = transforms.fold(position) { point, transform -> transform.apply(point) }
            val normal = transforms.fold(mesh.normals[index]) { value, transform ->
                transform.rotation.rotate(value)
            }.normalized()
            val color = mesh.material.color()
            val uv = mesh.uvs
            vertices += listOf(0f, 0f, 0f, 0f,
                world.x, world.y, world.z,
                normal.x, normal.y, normal.z,
                color.red, color.green, color.blue, color.alpha,
                uv?.get(index*2) ?: 0f, uv?.get(index*2+1) ?: 0f)
        }
        val textured = mesh.material as? TexturedMaterial
        add(GpuMesh(
            vertices = vertices.toFloatArray(),
            indices = mesh.indices.toIntArray(),
            baseColorTexture = textured?.baseColorTexture,
            normalTexture = textured?.normalTexture,
            metallicRoughnessTexture = textured?.metallicRoughnessTexture,
            emissiveTexture = textured?.emissiveTexture,
            ambientOcclusionTexture = textured?.ambientOcclusionTexture,
            metallic = mesh.material.metallic(),
            roughness = mesh.material.roughness(),
            reflectance = mesh.material.reflectance(),
            shadingModel = mesh.material.shadingModel(),
            normalScale = mesh.material.normalScale(),
            emissiveColor = mesh.material.emissiveColor(),
            emissiveIntensity = mesh.material.emissiveIntensity(),
            ambientOcclusionStrength = mesh.material.ambientOcclusionStrength(),
            castShadows = castShadows,
            receiveShadows = receiveShadows,
        ))
    }
    fun append(node: SceneNode, parents: List<Transform>) {
        val transforms = listOf(node.transform) + parents
        if (node is GroupNode) {
            node.children.forEach { append(it, transforms) }
            return
        }
        if (node is ModelNode) {
            if (!node.visible) return
            resolveModel(node)?.forEach {
                appendMesh(it, transforms, node.castShadows, node.receiveShadows)
            }
            return
        }
        val mesh = node.toMesh() ?: return
        appendMesh(mesh, transforms, node.castShadows(), node.receiveShadows())
    }
    nodes.forEach { append(it, emptyList()) }
}

private fun SceneNode.castShadows() = when (this) {
    is BoxNode -> castShadows
    is SphereNode -> castShadows
    is PlaneNode -> castShadows
    is CylinderNode -> castShadows
    is MeshNode -> castShadows
    is ModelNode -> castShadows
    else -> false
}

private fun SceneNode.receiveShadows() = when (this) {
    is BoxNode -> receiveShadows
    is SphereNode -> receiveShadows
    is PlaneNode -> receiveShadows
    is CylinderNode -> receiveShadows
    is MeshNode -> receiveShadows
    is ModelNode -> receiveShadows
    else -> false
}

internal fun perspectiveClipDepth(z: Float, near: Float, far: Float): Float {
    require(near > 0f && far > near)
    return ((far + near) * z - 2f * far * near) / (far - near)
}

private fun FloatArray.toTypedArray() = Float32Array(size).also { result ->
    forEachIndexed { index, value -> result[index] = value }
}
private fun IntArray.toTypedArray() = Uint32Array(size).also { result ->
    forEachIndexed { index, value -> result[index] = value }
}
private fun ByteArray.toTypedArray() = Uint8Array(size).also { result ->
    forEachIndexed { index, value -> result[index] = value }
}

private external interface JsGlbPrimitive : JsAny {
    val positions: Float32Array
    val normals: Float32Array
    val uvs: Float32Array?
    val indices: Uint32Array
    val color: Float32Array
    val texture: Uint8Array?
    val normalTexture: Uint8Array?
    val metallicRoughnessTexture: Uint8Array?
    val emissiveTexture: Uint8Array?
    val ambientOcclusionTexture: Uint8Array?
    val metallic: Float
    val roughness: Float
    val normalScale: Float
    val emissiveColor: Float32Array
    val ambientOcclusionStrength: Float
}

private fun parseGlb(bytes: Uint8Array, cacheKey: String): List<MeshData> {
    val parsed = parseGlbData(bytes)
    return List(parsed.length) { primitiveIndex ->
        val primitive = requireNotNull(parsed[primitiveIndex])
        fun texture(data: Uint8Array?, channel: String) = data?.let {
            TextureSource.Bytes(
                value = ByteArray(it.length) { index -> it[index] },
                cacheKey = "$cacheKey:image:$primitiveIndex:$channel",
            )
        }
        val baseColorTexture = texture(primitive.texture, "baseColor")
        val color = primitive.color
        val emissive = primitive.emissiveColor
        MeshData(
            positions = FloatArray(primitive.positions.length) { primitive.positions[it] }.toVec3List(),
            indices = List(primitive.indices.length) { primitive.indices[it] },
            normals = FloatArray(primitive.normals.length) { primitive.normals[it] }.toVec3List(),
            uvs = primitive.uvs?.let { values -> FloatArray(values.length) { values[it] } },
            material = baseColorTexture?.let {
                TexturedMaterial(
                    baseColorTexture = it,
                    metallic = primitive.metallic,
                    roughness = primitive.roughness,
                    normalTexture = texture(primitive.normalTexture, "normal"),
                    metallicRoughnessTexture = texture(
                        primitive.metallicRoughnessTexture, "metallicRoughness"),
                    emissiveTexture = texture(primitive.emissiveTexture, "emissive"),
                    ambientOcclusionTexture = texture(
                        primitive.ambientOcclusionTexture, "ambientOcclusion"),
                    normalScale = primitive.normalScale,
                    emissiveColor = Color3D(emissive[0], emissive[1], emissive[2]),
                    ambientOcclusionStrength = primitive.ambientOcclusionStrength,
                )
            } ?: PbrMaterial(
                baseColor = Color3D(color[0], color[1], color[2], color[3]),
                metallic = primitive.metallic,
                roughness = primitive.roughness,
            ),
        )
    }
}

@JsFun("""(bytes) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getUint32(0, true) !== 0x46546c67 || view.getUint32(4, true) !== 2) throw new Error('Expected GLB 2.0');
  let offset = 12, json = null, bin = null;
  while (offset < view.byteLength) {
    const length = view.getUint32(offset, true), type = view.getUint32(offset + 4, true); offset += 8;
    if (type === 0x4e4f534a) json = JSON.parse(new TextDecoder().decode(bytes.subarray(offset, offset + length)));
    if (type === 0x004e4942) bin = bytes.subarray(offset, offset + length);
    offset += length;
  }
  if (!json || !bin) throw new Error('GLB must contain JSON and BIN chunks');
  const components = { 5120:1, 5121:1, 5122:2, 5123:2, 5125:4, 5126:4 };
  const counts = { SCALAR:1, VEC2:2, VEC3:3, VEC4:4, MAT4:16 };
  const readComponent = (dv, p, type) => type===5120?dv.getInt8(p):type===5121?dv.getUint8(p):type===5122?dv.getInt16(p,true):type===5123?dv.getUint16(p,true):type===5125?dv.getUint32(p,true):dv.getFloat32(p,true);
  const accessor = (index, integer) => {
    const a=json.accessors[index], bv=json.bufferViews[a.bufferView], n=counts[a.type], bytesPer=components[a.componentType];
    const stride=bv.byteStride || n*bytesPer, start=(bv.byteOffset||0)+(a.byteOffset||0), out=integer?new Uint32Array(a.count*n):new Float32Array(a.count*n);
    const dv=new DataView(bin.buffer, bin.byteOffset, bin.byteLength);
    for(let i=0;i<a.count;i++) for(let c=0;c<n;c++) out[i*n+c]=readComponent(dv,start+i*stride+c*bytesPer,a.componentType);
    return out;
  };
  const identity=()=>new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]);
  const mul=(a,b)=>{const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;};
  const local=(n)=>{if(n.matrix)return new Float32Array(n.matrix);const t=n.translation||[0,0,0],s=n.scale||[1,1,1],q=n.rotation||[0,0,0,1],x=q[0],y=q[1],z=q[2],w=q[3],m=identity();m[0]=(1-2*y*y-2*z*z)*s[0];m[1]=(2*x*y+2*w*z)*s[0];m[2]=(2*x*z-2*w*y)*s[0];m[4]=(2*x*y-2*w*z)*s[1];m[5]=(1-2*x*x-2*z*z)*s[1];m[6]=(2*y*z+2*w*x)*s[1];m[8]=(2*x*z+2*w*y)*s[2];m[9]=(2*y*z-2*w*x)*s[2];m[10]=(1-2*x*x-2*y*y)*s[2];m[12]=t[0];m[13]=t[1];m[14]=t[2];return m;};
  const imageBytes=(textureIndex)=>{if(textureIndex==null)return null;const tex=json.textures[textureIndex],img=json.images[tex.source];if(img.bufferView==null)return null;const bv=json.bufferViews[img.bufferView],start=bv.byteOffset||0;return bin.slice(start,start+bv.byteLength);};
  const output=[];
  const visit=(nodeIndex,parent)=>{const node=json.nodes[nodeIndex],world=mul(parent,local(node));if(node.mesh!=null){for(const p of json.meshes[node.mesh].primitives){if(p.mode!=null&&p.mode!==4)continue;const pos=accessor(p.attributes.POSITION,false),nor=p.attributes.NORMAL!=null?accessor(p.attributes.NORMAL,false):new Float32Array(pos.length),uv=p.attributes.TEXCOORD_0!=null?accessor(p.attributes.TEXCOORD_0,false):null,idx=p.indices!=null?accessor(p.indices,true):new Uint32Array(pos.length/3);if(p.indices==null)for(let i=0;i<idx.length;i++)idx[i]=i;for(let i=0;i<pos.length;i+=3){const x=pos[i],y=pos[i+1],z=pos[i+2];pos[i]=world[0]*x+world[4]*y+world[8]*z+world[12];pos[i+1]=world[1]*x+world[5]*y+world[9]*z+world[13];pos[i+2]=world[2]*x+world[6]*y+world[10]*z+world[14];const nx=nor[i],ny=nor[i+1],nz=nor[i+2],tx=world[0]*nx+world[4]*ny+world[8]*nz,ty=world[1]*nx+world[5]*ny+world[9]*nz,tz=world[2]*nx+world[6]*ny+world[10]*nz,l=Math.hypot(tx,ty,tz)||1;nor[i]=tx/l;nor[i+1]=ty/l;nor[i+2]=tz/l;}const mat=p.material!=null?json.materials[p.material]:null,pbr=mat&&mat.pbrMetallicRoughness,color=new Float32Array((pbr&&pbr.baseColorFactor)||[0.7,0.7,0.7,1]),texture=imageBytes(pbr&&pbr.baseColorTexture&&pbr.baseColorTexture.index),normalTexture=imageBytes(mat&&mat.normalTexture&&mat.normalTexture.index),metallicRoughnessTexture=imageBytes(pbr&&pbr.metallicRoughnessTexture&&pbr.metallicRoughnessTexture.index),emissiveTexture=imageBytes(mat&&mat.emissiveTexture&&mat.emissiveTexture.index),ambientOcclusionTexture=imageBytes(mat&&mat.occlusionTexture&&mat.occlusionTexture.index),metallic=pbr&&pbr.metallicFactor!=null?pbr.metallicFactor:1,roughness=pbr&&pbr.roughnessFactor!=null?pbr.roughnessFactor:1,normalScale=mat&&mat.normalTexture&&mat.normalTexture.scale!=null?mat.normalTexture.scale:1,emissiveColor=new Float32Array((mat&&mat.emissiveFactor)||[0,0,0]),ambientOcclusionStrength=mat&&mat.occlusionTexture&&mat.occlusionTexture.strength!=null?mat.occlusionTexture.strength:1;output.push({positions:pos,normals:nor,uvs:uv,indices:idx,color,texture,normalTexture,metallicRoughnessTexture,emissiveTexture,ambientOcclusionTexture,metallic,roughness,normalScale,emissiveColor,ambientOcclusionStrength});}}for(const child of node.children||[])visit(child,world);};
  const scene=json.scenes[(json.scene||0)],roots=scene?scene.nodes:json.nodes.map((_,i)=>i);for(const root of roots)visit(root,identity());return output;
}""")
private external fun parseGlbData(bytes: Uint8Array): JsArray<JsGlbPrimitive>

private external interface WebShadowTarget : JsAny {
    val framebuffer: WebGLFramebuffer
    val texture: WebGLTexture
}

@JsFun("""(gl, size) => {
  const texture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.DEPTH_COMPONENT24, size, size, 0, gl.DEPTH_COMPONENT, gl.UNSIGNED_INT, null);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  const framebuffer = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, texture, 0);
  gl.drawBuffers([gl.NONE]); gl.readBuffer(gl.NONE);
  if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('Unable to create Web shadow framebuffer');
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  return { framebuffer, texture };
}""")
private external fun createShadowTarget(gl: WebGLRenderingContext, size: Int): WebShadowTarget

@JsFun("(gl, target) => { gl.deleteFramebuffer(target.framebuffer); gl.deleteTexture(target.texture); }")
private external fun deleteShadowTarget(gl: WebGLRenderingContext, target: WebShadowTarget)

@JsFun("(canvas) => canvas.getContext('webgl2')")
private external fun webGl2Context(canvas: HTMLCanvasElement): WebGLRenderingContext?

@JsFun("() => window.devicePixelRatio || 1")
private external fun browserPixelRatio(): Double

@JsFun("(url, onLoad, onError) => { const image = new Image(); image.crossOrigin = 'anonymous'; image.onload = () => onLoad(image); image.onerror = () => onError('Unable to decode texture: ' + url); image.src = url; }")
private external fun loadImage(
    url: String,
    onLoad: (HTMLImageElement) -> Unit,
    onError: (String) -> Unit,
)

@JsFun("""(bytes) => {
  const png = bytes.length > 8 && bytes[0] === 0x89 && bytes[1] === 0x50;
  const jpeg = bytes.length > 2 && bytes[0] === 0xff && bytes[1] === 0xd8;
  const first = new TextDecoder().decode(bytes.subarray(0, Math.min(bytes.length, 256))).trimStart();
  const type = png ? 'image/png' : jpeg ? 'image/jpeg' : first.startsWith('<svg') || first.startsWith('<?xml') ? 'image/svg+xml' : 'application/octet-stream';
  return URL.createObjectURL(new Blob([bytes], { type }));
}""")
private external fun bytesUrl(bytes: Uint8Array): String

@JsFun("(url) => URL.revokeObjectURL(url)")
private external fun revokeObjectUrl(url: String)

@JsFun("(url, onLoad, onError) => fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url); return r.arrayBuffer(); }).then(b => onLoad(new Uint8Array(b))).catch(e => onError(String(e)))")
private external fun fetchBytes(
    url: String,
    onLoad: (Uint8Array) -> Unit,
    onError: (String) -> Unit,
)

@JsFun("""(url, onLoad, onError) => {
  const align4 = n => (n + 3) & ~3;
  const documentUrl = new URL(url, document.baseURI).href;
  const resolve = uri => new URL(uri, documentUrl).href;
  fetch(documentUrl).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + documentUrl); return r.json(); }).then(async json => {
    if (!json.asset || !String(json.asset.version).startsWith('2')) throw new Error('Expected glTF 2.0');
    if (!json.buffers || json.buffers.length !== 1 || !json.buffers[0].uri) throw new Error('External .gltf currently requires exactly one URI buffer');
    const response = await fetch(resolve(json.buffers[0].uri));
    if (!response.ok) throw new Error('Unable to load glTF buffer: HTTP ' + response.status);
    let bin = new Uint8Array(await response.arrayBuffer());
    delete json.buffers[0].uri;
    for (const image of json.images || []) {
      if (!image.uri) continue;
      const imageResponse = await fetch(resolve(image.uri));
      if (!imageResponse.ok) throw new Error('Unable to load glTF image: HTTP ' + imageResponse.status);
      const imageBytes = new Uint8Array(await imageResponse.arrayBuffer()), start = align4(bin.length), joined = new Uint8Array(start + imageBytes.length);
      joined.set(bin); joined.set(imageBytes, start); bin = joined;
      json.bufferViews = json.bufferViews || [];
      image.bufferView = json.bufferViews.length;
      json.bufferViews.push({ buffer: 0, byteOffset: start, byteLength: imageBytes.length });
      delete image.uri;
    }
    json.buffers[0].byteLength = bin.length;
    const encoded = new TextEncoder().encode(JSON.stringify(json)), jsonLength = align4(encoded.length), binLength = align4(bin.length), total = 12 + 8 + jsonLength + 8 + binLength;
    const glb = new Uint8Array(total), view = new DataView(glb.buffer);
    view.setUint32(0, 0x46546c67, true); view.setUint32(4, 2, true); view.setUint32(8, total, true);
    view.setUint32(12, jsonLength, true); view.setUint32(16, 0x4e4f534a, true); glb.fill(0x20, 20, 20 + jsonLength); glb.set(encoded, 20);
    const binHeader = 20 + jsonLength; view.setUint32(binHeader, binLength, true); view.setUint32(binHeader + 4, 0x004e4942, true); glb.set(bin, binHeader + 8);
    onLoad(glb);
  }).catch(e => onError(String(e)));
}""")
private external fun loadGltfAsGlb(
    url: String,
    onLoad: (Uint8Array) -> Unit,
    onError: (String) -> Unit,
)

private const val MAX_WEB_LIGHTS = 4
private const val MAX_WEB_SHADOW_MAP_SIZE = 2048
private const val VERTEX_STRIDE_FLOATS = 16
private const val WORLD_POSITION_FLOAT_OFFSET = 4
private const val DIRECTIONAL_SHADOW_PADDING = 0.5f
private const val DIRECTIONAL_SHADOW_DEPTH_PADDING = 1f
private const val MIN_DIRECTIONAL_SHADOW_EXTENT = 1f
private const val MIN_DIRECTIONAL_SHADOW_DEPTH = 2f
private const val SHADOW_POLYGON_OFFSET_FACTOR = 2f
private const val SHADOW_POLYGON_OFFSET_UNITS = 4f
private const val SHADOW_TEXTURE_UNIT = 5
private const val SPOT_SHADOW_TEXTURE_UNIT = 6

private const val SHADOW_VERTEX_SHADER = """#version 300 es
precision highp float;
in vec3 aWorldPosition;
uniform vec3 uShadowCenter;
uniform vec3 uShadowRight;
uniform vec3 uShadowUp;
uniform vec3 uShadowForward;
uniform float uShadowExtent;
uniform float uShadowDepth;
uniform bool uShadowPerspective;
uniform float uShadowNear;
uniform float uShadowFar;
void main() {
    vec3 relative = aWorldPosition - uShadowCenter;
    float forward = dot(relative, uShadowForward);
    if (uShadowPerspective) {
        float a = (uShadowFar + uShadowNear) / (uShadowFar - uShadowNear);
        float b = -2.0 * uShadowFar * uShadowNear / (uShadowFar - uShadowNear);
        gl_Position = vec4(
            dot(relative, uShadowRight) / uShadowExtent,
            dot(relative, uShadowUp) / uShadowExtent,
            a * forward + b,
            forward
        );
        return;
    }
    gl_Position = vec4(
        dot(relative, uShadowRight) / uShadowExtent,
        dot(relative, uShadowUp) / uShadowExtent,
        forward / uShadowDepth,
        1.0
    );
}
"""

private const val SHADOW_FRAGMENT_SHADER = """#version 300 es
precision highp float;
void main() {}
"""

private const val VERTEX_SHADER = """#version 300 es
precision highp float;
in vec3 aWorldPosition;
in vec3 aNormal;
in vec4 aColor;
in vec2 aUv;
uniform vec3 uCameraPosition;
uniform vec3 uCameraRight;
uniform vec3 uCameraUp;
uniform vec3 uCameraForward;
uniform float uCameraAspect;
uniform float uCameraScale;
uniform float uCameraNear;
uniform float uCameraFar;
uniform bool uCameraPerspective;
out vec3 vWorldPosition;
out vec3 vNormal;
out vec4 vColor;
out vec2 vUv;
void main() {
    vec3 relative = aWorldPosition - uCameraPosition;
    float x = dot(relative, uCameraRight);
    float y = dot(relative, uCameraUp);
    float z = dot(relative, uCameraForward);
    if (uCameraPerspective) {
        float a = (uCameraFar + uCameraNear) / (uCameraFar - uCameraNear);
        float b = -2.0 * uCameraFar * uCameraNear / (uCameraFar - uCameraNear);
        gl_Position = vec4(x * uCameraScale / uCameraAspect,
            y * uCameraScale, a * z + b, z);
    } else {
        float depth = ((z - uCameraNear) / (uCameraFar - uCameraNear)) * 2.0 - 1.0;
        gl_Position = vec4(x * uCameraScale / uCameraAspect, y * uCameraScale, depth, 1.0);
    }
    vWorldPosition = aWorldPosition;
    vNormal = aNormal;
    vColor = aColor;
    vUv = aUv;
}
"""

private const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec3 vWorldPosition;
in vec3 vNormal;
in vec4 vColor;
in vec2 vUv;
uniform sampler2D uTexture;
uniform bool uUseTexture;
uniform sampler2D uNormalTexture;
uniform bool uUseNormalTexture;
uniform sampler2D uMetallicRoughnessTexture;
uniform bool uUseMetallicRoughnessTexture;
uniform sampler2D uEmissiveTexture;
uniform bool uUseEmissiveTexture;
uniform sampler2D uAmbientOcclusionTexture;
uniform bool uUseAmbientOcclusionTexture;
uniform vec3 uCameraPosition;
uniform vec3 uLightDirection;
uniform vec3 uLightColor;
uniform float uLightIntensity;
uniform float uMetallic;
uniform float uRoughness;
uniform float uReflectance;
uniform int uShadingModel;
uniform float uNormalScale;
uniform vec3 uEmissiveColor;
uniform float uEmissiveIntensity;
uniform float uAmbientOcclusionStrength;
uniform int uPointLightCount;
uniform vec3 uPointLightPositions[4];
uniform vec3 uPointLightColors[4];
uniform float uPointLightIntensities[4];
uniform float uPointLightFalloffs[4];
uniform int uSpotLightCount;
uniform vec3 uSpotLightPositions[4];
uniform vec3 uSpotLightDirections[4];
uniform vec3 uSpotLightColors[4];
uniform float uSpotLightIntensities[4];
uniform float uSpotLightFalloffs[4];
uniform float uSpotLightInnerCos[4];
uniform float uSpotLightOuterCos[4];
uniform sampler2D uShadowTexture;
uniform bool uUseShadow;
uniform bool uReceiveShadow;
uniform float uShadowBias;
uniform vec3 uShadowCenter;
uniform vec3 uShadowRight;
uniform vec3 uShadowUp;
uniform vec3 uShadowForward;
uniform float uShadowExtent;
uniform float uShadowDepth;
uniform sampler2D uSpotShadowTexture;
uniform bool uUseSpotShadow;
uniform float uSpotShadowBias;
uniform int uSpotShadowLightIndex;
uniform vec3 uSpotShadowPosition;
uniform vec3 uSpotShadowRight;
uniform vec3 uSpotShadowUp;
uniform vec3 uSpotShadowForward;
uniform float uSpotShadowTanHalfAngle;
uniform float uSpotShadowNear;
uniform float uSpotShadowFar;
out vec4 outColor;

const float PI = 3.14159265359;

float distributionGgx(float nDotH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float denominator = nDotH * nDotH * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denominator * denominator, 0.0001);
}

float geometrySchlickGgx(float nDotV, float roughness) {
    float r = roughness + 1.0;
    float k = r * r / 8.0;
    return nDotV / max(nDotV * (1.0 - k) + k, 0.0001);
}

vec3 fresnelSchlick(float cosTheta, vec3 f0) {
    return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
}

mat3 cotangentFrame(vec3 normal, vec3 position, vec2 uv) {
    vec3 dp1 = dFdx(position);
    vec3 dp2 = dFdy(position);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 dp2Perp = cross(dp2, normal);
    vec3 dp1Perp = cross(normal, dp1);
    vec3 tangent = dp2Perp * duv1.x + dp1Perp * duv2.x;
    vec3 bitangent = dp2Perp * duv1.y + dp1Perp * duv2.y;
    float scale = inversesqrt(max(max(dot(tangent, tangent), dot(bitangent, bitangent)), 0.0001));
    return mat3(tangent * scale, bitangent * scale, normal);
}

vec3 evaluatePbr(
    vec3 n, vec3 v, vec3 l, vec3 baseColor, float metallic, float roughness,
    float reflectance, vec3 radiance
) {
    vec3 h = normalize(v + l);
    float nDotL = max(dot(n, l), 0.0);
    float nDotV = max(dot(n, v), 0.0001);
    float nDotH = max(dot(n, h), 0.0);
    float hDotV = max(dot(h, v), 0.0);
    vec3 f0 = mix(vec3(0.16 * reflectance * reflectance), baseColor, metallic);
    vec3 f = fresnelSchlick(hDotV, f0);
    float d = distributionGgx(nDotH, roughness);
    float g = geometrySchlickGgx(nDotV, roughness) * geometrySchlickGgx(nDotL, roughness);
    vec3 specular = d * g * f / max(4.0 * nDotV * nDotL, 0.0001);
    vec3 diffuse = (1.0 - f) * (1.0 - metallic) * baseColor / PI;
    return (diffuse + specular) * radiance * nDotL;
}

float directionalShadow(vec3 worldPosition) {
    if (!uUseShadow || !uReceiveShadow) return 1.0;
    vec3 relative = worldPosition - uShadowCenter;
    vec3 coordinate = vec3(
        dot(relative, uShadowRight) / (2.0 * uShadowExtent) + 0.5,
        dot(relative, uShadowUp) / (2.0 * uShadowExtent) + 0.5,
        dot(relative, uShadowForward) / (2.0 * uShadowDepth) + 0.5
    );
    if (any(lessThan(coordinate, vec3(0.0))) || any(greaterThan(coordinate, vec3(1.0)))) return 1.0;
    float visibility = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(uShadowTexture, 0));
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        float closest = texture(uShadowTexture, coordinate.xy + vec2(float(x), float(y)) * texel).r;
        visibility += coordinate.z - uShadowBias <= closest ? 1.0 : 0.0;
    }
    return mix(0.28, 1.0, visibility / 9.0);
}

float spotShadow(vec3 worldPosition) {
    if (!uUseSpotShadow || !uReceiveShadow) return 1.0;
    vec3 relative = worldPosition - uSpotShadowPosition;
    float depth = dot(relative, uSpotShadowForward);
    if (depth <= uSpotShadowNear || depth >= uSpotShadowFar) return 1.0;
    float a = (uSpotShadowFar + uSpotShadowNear) / (uSpotShadowFar - uSpotShadowNear);
    float b = -2.0 * uSpotShadowFar * uSpotShadowNear /
        (uSpotShadowFar - uSpotShadowNear);
    vec3 coordinate = vec3(
        dot(relative, uSpotShadowRight) / (depth * uSpotShadowTanHalfAngle) * 0.5 + 0.5,
        dot(relative, uSpotShadowUp) / (depth * uSpotShadowTanHalfAngle) * 0.5 + 0.5,
        (a + b / depth) * 0.5 + 0.5
    );
    if (any(lessThan(coordinate, vec3(0.0))) || any(greaterThan(coordinate, vec3(1.0)))) return 1.0;
    float visibility = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(uSpotShadowTexture, 0));
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        float closest = texture(uSpotShadowTexture,
            coordinate.xy + vec2(float(x), float(y)) * texel).r;
        visibility += coordinate.z - uSpotShadowBias <= closest ? 1.0 : 0.0;
    }
    return mix(0.28, 1.0, visibility / 9.0);
}

void main() {
    vec4 base = uUseTexture ? texture(uTexture, vUv) * vColor : vColor;
    if (uUseTexture) base.rgb = pow(base.rgb, vec3(2.2));
    if (uShadingModel == 1) {
        outColor = uUseTexture ? vec4(pow(base.rgb, vec3(1.0 / 2.2)), base.a) : base;
        return;
    }

    vec3 n = normalize(vNormal);
    if (uUseNormalTexture) {
        vec3 mappedNormal = texture(uNormalTexture, vUv).xyz * 2.0 - 1.0;
        mappedNormal.xy *= uNormalScale;
        n = normalize(cotangentFrame(n, vWorldPosition, vUv) * mappedNormal);
    }
    vec3 v = normalize(uCameraPosition - vWorldPosition);
    vec3 l = normalize(uLightDirection);
    vec4 metallicRoughness = uUseMetallicRoughnessTexture
        ? texture(uMetallicRoughnessTexture, vUv) : vec4(1.0);
    float roughness = clamp(uRoughness * metallicRoughness.g, 0.045, 1.0);
    float metallic = clamp(uMetallic * metallicRoughness.b, 0.0, 1.0);
    vec3 direct = evaluatePbr(n, v, l, base.rgb, metallic, roughness, uReflectance,
        uLightColor * uLightIntensity) * directionalShadow(vWorldPosition);
    for (int i = 0; i < 4; i++) {
        if (i >= uPointLightCount) break;
        vec3 offset = uPointLightPositions[i] - vWorldPosition;
        float distanceToLight = length(offset);
        float range = clamp(1.0 - distanceToLight / uPointLightFalloffs[i], 0.0, 1.0);
        float attenuation = range * range * 20.0 / max(distanceToLight * distanceToLight, 0.25);
        direct += evaluatePbr(n, v, normalize(offset), base.rgb, metallic, roughness,
            uReflectance, uPointLightColors[i] * uPointLightIntensities[i] * attenuation);
    }
    for (int i = 0; i < 4; i++) {
        if (i >= uSpotLightCount) break;
        vec3 offset = uSpotLightPositions[i] - vWorldPosition;
        float distanceToLight = length(offset);
        vec3 toLight = normalize(offset);
        float coneCos = dot(normalize(uSpotLightDirections[i]), -toLight);
        float cone = smoothstep(uSpotLightOuterCos[i], uSpotLightInnerCos[i], coneCos);
        float range = clamp(1.0 - distanceToLight / uSpotLightFalloffs[i], 0.0, 1.0);
        float attenuation = cone * range * range * 20.0 /
            max(distanceToLight * distanceToLight, 0.25);
        float visibility = i == uSpotShadowLightIndex ? spotShadow(vWorldPosition) : 1.0;
        direct += evaluatePbr(n, v, toLight, base.rgb, metallic, roughness,
            uReflectance, uSpotLightColors[i] * uSpotLightIntensities[i] * attenuation) * visibility;
    }
    float aoSample = uUseAmbientOcclusionTexture ? texture(uAmbientOcclusionTexture, vUv).r : 1.0;
    float ao = mix(1.0, aoSample, uAmbientOcclusionStrength);
    vec3 ambient = base.rgb * (0.06 + 0.04 * (1.0 - roughness)) * ao;
    vec3 emissive = uEmissiveColor * uEmissiveIntensity;
    if (uUseEmissiveTexture) {
        emissive *= pow(texture(uEmissiveTexture, vUv).rgb, vec3(2.2));
    } else if (uEmissiveIntensity == 0.0) {
        emissive = vec3(0.0);
    }
    vec3 lit = direct + ambient + emissive;
    vec3 mapped = lit / (lit + vec3(1.0));
    mapped = pow(mapped, vec3(1.0 / 2.2));
    outColor = vec4(mapped, base.a);
}
"""
