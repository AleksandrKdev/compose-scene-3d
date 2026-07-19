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
import dev.composescene3d.core.EmissiveMaterial
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.Quaternion
import dev.composescene3d.core.RendererCapabilities
import dev.composescene3d.core.SceneCommand
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.SphereNode
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
        physicallyBasedRendering = false,
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
    is BoxNode -> boxMesh(size, UnlitMaterial(Color3D(color.x, color.y, color.z)))
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
    val p = listOf(Vec3(-x,-y,-z), Vec3(x,-y,-z), Vec3(x,y,-z), Vec3(-x,y,-z),
        Vec3(-x,-y,z), Vec3(x,-y,z), Vec3(x,y,z), Vec3(-x,y,z))
    val i = listOf(0,2,1,0,3,2, 4,5,6,4,6,7, 0,1,5,0,5,4,
        3,7,6,3,6,2, 1,2,6,1,6,5, 0,4,7,0,7,3)
    val n = p.map(Vec3::normalized)
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
private fun Material3D.color(light: Float): Color = when (this) {
    is PbrMaterial -> baseColor.toColor(light)
    is UnlitMaterial -> color.toColor()
    is EmissiveMaterial -> color.toColor(intensity)
    is TransparentMaterial -> color.toColor(light)
    else -> Color(0.7f*light, 0.7f*light, 0.7f*light)
}
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
    private val vertexBuffer: WebGLBuffer
    private val indexBuffer: WebGLBuffer
    private val positionAttribute: Int
    private val colorAttribute: Int
    private val uvAttribute: Int
    private val useTextureUniform: org.khronos.webgl.WebGLUniformLocation?
    private val textureCache = mutableMapOf<String, WebGLTexture>()
    private val loadingTextures = mutableSetOf<String>()
    private val failedTextures = mutableSetOf<String>()
    private val modelCache = mutableMapOf<String, List<MeshData>>()
    private val loadingModels = mutableSetOf<String>()
    private val failedModels = mutableSetOf<String>()
    private var width = 1
    private var height = 1

    init {
        canvas.style.cssText = "position:fixed;pointer-events:none;z-index:2147483647;"
        document.body?.appendChild(canvas)
        gl = requireNotNull(webGl2Context(canvas)) {
            "ComposeScene3D Web requires WebGL2"
        }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        vertexBuffer = requireNotNull(gl.createBuffer())
        indexBuffer = requireNotNull(gl.createBuffer())
        positionAttribute = gl.getAttribLocation(program, "aPosition")
        colorAttribute = gl.getAttribLocation(program, "aColor")
        uvAttribute = gl.getAttribLocation(program, "aUv")
        useTextureUniform = gl.getUniformLocation(program, "uUseTexture")
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
        val batches = buildGpuBatches(
            nodes, camera, width.toFloat(), height.toFloat(),
            resolveModel = { node -> resolveModel(node) },
        )
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(background.red, background.green, background.blue, background.alpha)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT or WebGLRenderingContext.DEPTH_BUFFER_BIT)
        if (batches.isEmpty()) return

        gl.useProgram(program)
        batches.forEach { mesh ->
            gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, vertexBuffer)
            gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, mesh.vertices.toTypedArray(), WebGLRenderingContext.DYNAMIC_DRAW)
            gl.enableVertexAttribArray(positionAttribute)
            gl.vertexAttribPointer(positionAttribute, 4, WebGLRenderingContext.FLOAT, false, 40, 0)
            gl.enableVertexAttribArray(colorAttribute)
            gl.vertexAttribPointer(colorAttribute, 4, WebGLRenderingContext.FLOAT, false, 40, 16)
            gl.enableVertexAttribArray(uvAttribute)
            gl.vertexAttribPointer(uvAttribute, 2, WebGLRenderingContext.FLOAT, false, 40, 32)
            bindTexture(mesh.texture)
            gl.bindBuffer(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER, indexBuffer)
            gl.bufferData(WebGLRenderingContext.ELEMENT_ARRAY_BUFFER, mesh.indices.toTypedArray(), WebGLRenderingContext.DYNAMIC_DRAW)
            gl.drawElements(WebGLRenderingContext.TRIANGLES, mesh.indices.size,
                WebGLRenderingContext.UNSIGNED_INT, 0)
        }
    }

    fun close() {
        gl.deleteBuffer(vertexBuffer)
        gl.deleteBuffer(indexBuffer)
        gl.deleteProgram(program)
        textureCache.values.forEach(gl::deleteTexture)
        canvas.remove()
    }

    private fun bindTexture(source: TextureSource?) {
        if (source == null) {
            gl.uniform1i(useTextureUniform, 0)
            return
        }
        val key = source.assetKey().value
        val texture = textureCache[key]
        if (texture == null) {
            if (key in failedTextures) {
                gl.uniform1i(useTextureUniform, 0)
                return
            }
            requestTexture(key, source)
            gl.uniform1i(useTextureUniform, 0)
            return
        }
        gl.activeTexture(WebGLRenderingContext.TEXTURE0)
        gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture)
        gl.uniform1i(useTextureUniform, 1)
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
                    .onSuccess { modelCache[key] = it }
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
        return null
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
    val texture: TextureSource?,
)

private fun buildGpuBatches(
    nodes: Collection<SceneNode>,
    camera: SceneCameraState,
    width: Float,
    height: Float,
    resolveModel: (ModelNode) -> List<MeshData>?,
): List<GpuMesh> = buildList {
    fun appendMesh(mesh: MeshData, transforms: List<Transform>) {
        val vertices = mutableListOf<Float>()
        mesh.positions.forEachIndexed { index, position ->
            val world = transforms.fold(position) { point, transform -> transform.apply(point) }
            val clip = camera.projectClip(world, width, height) ?: ClipPoint(2f, 2f, 1f, 1f)
            val normal = transforms.fold(mesh.normals[index]) { value, transform ->
                transform.rotation.rotate(value)
            }.normalized()
            val light = (0.2f + 0.8f * normal.dot(Vec3(0.35f, 0.8f, 0.45f).normalized()))
                .coerceIn(0.15f, 1f)
            val color = mesh.material.color(light)
            val uv = mesh.uvs
            vertices += listOf(clip.x, clip.y, clip.z, clip.w,
                color.red, color.green, color.blue, color.alpha,
                uv?.get(index*2) ?: 0f, uv?.get(index*2+1) ?: 0f)
        }
        add(GpuMesh(vertices.toFloatArray(), mesh.indices.toIntArray(),
            (mesh.material as? TexturedMaterial)?.baseColorTexture))
    }
    fun append(node: SceneNode, parents: List<Transform>) {
        val transforms = listOf(node.transform) + parents
        if (node is GroupNode) {
            node.children.forEach { append(it, transforms) }
            return
        }
        if (node is ModelNode) {
            if (!node.visible) return
            resolveModel(node)?.forEach { appendMesh(it, transforms) }
            return
        }
        val mesh = node.toMesh() ?: return
        appendMesh(mesh, transforms)
    }
    nodes.forEach { append(it, emptyList()) }
}

private data class ClipPoint(val x: Float, val y: Float, val z: Float, val w: Float)

private fun SceneCameraState.projectClip(point: Vec3, width: Float, height: Float): ClipPoint? {
    val forward = (target - eye).normalized()
    val right = forward.cross(up).normalized()
    val cameraUp = right.cross(forward)
    val relative = point - eye
    val x = relative.dot(right)
    val y = relative.dot(cameraUp)
    val z = relative.dot(forward)
    val aspect = (width / height).coerceAtLeast(0.01f)
    when (val value = projection) {
        is CameraProjection.Perspective -> {
            val near = value.near.toFloat()
            val far = value.far.toFloat()
            if (z <= near || z >= far) return null
            val depth = ((z - near) / (far - near)) * 2f - 1f
            val scale = (1.0 / tan(value.verticalFovDegrees * PI / 360.0)).toFloat()
            return ClipPoint(x * scale / aspect, y * scale, depth * z, z)
        }
        is CameraProjection.Orthographic -> {
            val near = value.near.toFloat()
            val far = value.far.toFloat()
            if (z <= near || z >= far) return null
            val depth = ((z - near) / (far - near)) * 2f - 1f
            val scale = 2f / value.verticalSize.toFloat()
            return ClipPoint(x * scale / aspect, y * scale, depth, 1f)
        }
    }
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
}

private fun parseGlb(bytes: Uint8Array, cacheKey: String): List<MeshData> {
    val parsed = parseGlbData(bytes)
    return List(parsed.length) { primitiveIndex ->
        val primitive = requireNotNull(parsed[primitiveIndex])
        val texture = primitive.texture?.let { data ->
            TextureSource.Bytes(
                value = ByteArray(data.length) { data[it] },
                cacheKey = "$cacheKey:image:$primitiveIndex",
            )
        }
        val color = primitive.color
        MeshData(
            positions = FloatArray(primitive.positions.length) { primitive.positions[it] }.toVec3List(),
            indices = List(primitive.indices.length) { primitive.indices[it] },
            normals = FloatArray(primitive.normals.length) { primitive.normals[it] }.toVec3List(),
            uvs = primitive.uvs?.let { values -> FloatArray(values.length) { values[it] } },
            material = texture?.let(::TexturedMaterial) ?: PbrMaterial(
                baseColor = Color3D(color[0], color[1], color[2], color[3]),
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
  const visit=(nodeIndex,parent)=>{const node=json.nodes[nodeIndex],world=mul(parent,local(node));if(node.mesh!=null){for(const p of json.meshes[node.mesh].primitives){if(p.mode!=null&&p.mode!==4)continue;const pos=accessor(p.attributes.POSITION,false),nor=p.attributes.NORMAL!=null?accessor(p.attributes.NORMAL,false):new Float32Array(pos.length),uv=p.attributes.TEXCOORD_0!=null?accessor(p.attributes.TEXCOORD_0,false):null,idx=p.indices!=null?accessor(p.indices,true):new Uint32Array(pos.length/3);if(p.indices==null)for(let i=0;i<idx.length;i++)idx[i]=i;for(let i=0;i<pos.length;i+=3){const x=pos[i],y=pos[i+1],z=pos[i+2];pos[i]=world[0]*x+world[4]*y+world[8]*z+world[12];pos[i+1]=world[1]*x+world[5]*y+world[9]*z+world[13];pos[i+2]=world[2]*x+world[6]*y+world[10]*z+world[14];const nx=nor[i],ny=nor[i+1],nz=nor[i+2],tx=world[0]*nx+world[4]*ny+world[8]*nz,ty=world[1]*nx+world[5]*ny+world[9]*nz,tz=world[2]*nx+world[6]*ny+world[10]*nz,l=Math.hypot(tx,ty,tz)||1;nor[i]=tx/l;nor[i+1]=ty/l;nor[i+2]=tz/l;}const mat=p.material!=null?json.materials[p.material]:null,pbr=mat&&mat.pbrMetallicRoughness,color=new Float32Array((pbr&&pbr.baseColorFactor)||[0.7,0.7,0.7,1]),texture=imageBytes(pbr&&pbr.baseColorTexture&&pbr.baseColorTexture.index);output.push({positions:pos,normals:nor,uvs:uv,indices:idx,color,texture});}}for(const child of node.children||[])visit(child,world);};
  const scene=json.scenes[(json.scene||0)],roots=scene?scene.nodes:json.nodes.map((_,i)=>i);for(const root of roots)visit(root,identity());return output;
}""")
private external fun parseGlbData(bytes: Uint8Array): JsArray<JsGlbPrimitive>

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

private const val VERTEX_SHADER = """#version 300 es
precision highp float;
in vec4 aPosition;
in vec4 aColor;
in vec2 aUv;
out vec4 vColor;
out vec2 vUv;
void main() {
    gl_Position = aPosition;
    vColor = aColor;
    vUv = aUv;
}
"""

private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;
in vec4 vColor;
in vec2 vUv;
uniform sampler2D uTexture;
uniform bool uUseTexture;
out vec4 outColor;
void main() {
    outColor = uUseTexture ? texture(uTexture, vUv) * vColor : vColor;
}
"""
