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
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement

/**
 * Browser renderer with no Filament dependency. It uses WebGL2 while retaining the browser canvas
 * behind a transparent Compose input surface. This
 * keeps the scene API backend-neutral and reuses the common camera gesture implementation.
 */
class WebRenderer : SceneRenderer {
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
    val gpuSurface = remember { WebGlSurface { textureVersion++ } }
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

private class WebGlSurface(private val invalidate: () -> Unit) {
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
        canvas.style.left = "${x}px"
        canvas.style.top = "${y}px"
        canvas.style.width = "${this.width}px"
        canvas.style.height = "${this.height}px"
        if (canvas.width != this.width || canvas.height != this.height) {
            canvas.width = this.width
            canvas.height = this.height
        }
    }

    fun render(nodes: Collection<SceneNode>, camera: SceneCameraState, background: Color3D) {
        val batches = buildGpuBatches(nodes, camera, width.toFloat(), height.toFloat())
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(background.red, background.green, background.blue, background.alpha)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT or WebGLRenderingContext.DEPTH_BUFFER_BIT)
        if (batches.isEmpty()) return

        gl.useProgram(program)
        batches.forEach { mesh ->
            gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, vertexBuffer)
            gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, mesh.vertices.toTypedArray(), WebGLRenderingContext.DYNAMIC_DRAW)
            gl.enableVertexAttribArray(positionAttribute)
            gl.vertexAttribPointer(positionAttribute, 3, WebGLRenderingContext.FLOAT, false, 36, 0)
            gl.enableVertexAttribArray(colorAttribute)
            gl.vertexAttribPointer(colorAttribute, 4, WebGLRenderingContext.FLOAT, false, 36, 12)
            gl.enableVertexAttribArray(uvAttribute)
            gl.vertexAttribPointer(uvAttribute, 2, WebGLRenderingContext.FLOAT, false, 36, 28)
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
            gl.pixelStorei(WebGLRenderingContext.UNPACK_FLIP_Y_WEBGL, 1)
            gl.texImage2D(WebGLRenderingContext.TEXTURE_2D, 0, WebGLRenderingContext.RGBA,
                WebGLRenderingContext.RGBA, WebGLRenderingContext.UNSIGNED_BYTE, image)
            gl.generateMipmap(WebGLRenderingContext.TEXTURE_2D)
            gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,
                WebGLRenderingContext.TEXTURE_MIN_FILTER, WebGLRenderingContext.LINEAR_MIPMAP_LINEAR)
            textureCache[key] = texture
            loadingTextures.remove(key)
            if (source is TextureSource.Bytes) revokeObjectUrl(url)
            invalidate()
        }, onError = {
            loadingTextures.remove(key)
            if (source is TextureSource.Bytes) revokeObjectUrl(url)
        })
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
): List<GpuMesh> = buildList {
    fun append(node: SceneNode, parents: List<Transform>) {
        val transforms = listOf(node.transform) + parents
        if (node is GroupNode) {
            node.children.forEach { append(it, transforms) }
            return
        }
        val mesh = node.toMesh() ?: return
        val vertices = mutableListOf<Float>()
        mesh.positions.forEachIndexed { index, position ->
            val world = transforms.fold(position) { point, transform -> transform.apply(point) }
            val clip = camera.projectClip(world, width, height) ?: Vec3(2f, 2f, 1f)
            val normal = transforms.fold(mesh.normals[index]) { value, transform ->
                transform.rotation.rotate(value)
            }.normalized()
            val light = (0.2f + 0.8f * normal.dot(Vec3(0.35f, 0.8f, 0.45f).normalized()))
                .coerceIn(0.15f, 1f)
            val color = mesh.material.color(light)
            val uv = mesh.uvs
            vertices += listOf(clip.x, clip.y, clip.z, color.red, color.green, color.blue, color.alpha,
                uv?.get(index*2) ?: 0f, uv?.get(index*2+1) ?: 0f)
        }
        add(GpuMesh(vertices.toFloatArray(), mesh.indices.toIntArray(),
            (mesh.material as? TexturedMaterial)?.baseColorTexture))
    }
    nodes.forEach { append(it, emptyList()) }
}

private fun SceneCameraState.projectClip(point: Vec3, width: Float, height: Float): Vec3? {
    val forward = (target - eye).normalized()
    val right = forward.cross(up).normalized()
    val cameraUp = right.cross(forward)
    val relative = point - eye
    val x = relative.dot(right)
    val y = relative.dot(cameraUp)
    val z = relative.dot(forward)
    val near: Float
    val far: Float
    val scale: Float
    when (val value = projection) {
        is CameraProjection.Perspective -> {
            near = value.near.toFloat(); far = value.far.toFloat()
            scale = (1.0 / tan(value.verticalFovDegrees * PI / 360.0)).toFloat()
        }
        is CameraProjection.Orthographic -> {
            near = value.near.toFloat(); far = value.far.toFloat()
            scale = 2f * z / value.verticalSize.toFloat()
        }
    }
    if (z <= near || z >= far) return null
    val aspect = (width / height).coerceAtLeast(0.01f)
    return Vec3(x * scale / (z * aspect), y * scale / z, ((z-near)/(far-near))*2f-1f)
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

@JsFun("(canvas) => canvas.getContext('webgl2')")
private external fun webGl2Context(canvas: HTMLCanvasElement): WebGLRenderingContext?

@JsFun("(url, onLoad, onError) => { const image = new Image(); image.crossOrigin = 'anonymous'; image.onload = () => onLoad(image); image.onerror = () => onError(); image.src = url; }")
private external fun loadImage(
    url: String,
    onLoad: (HTMLImageElement) -> Unit,
    onError: () -> Unit,
)

@JsFun("(bytes) => URL.createObjectURL(new Blob([bytes], { type: 'image/png' }))")
private external fun bytesUrl(bytes: Uint8Array): String

@JsFun("(url) => URL.revokeObjectURL(url)")
private external fun revokeObjectUrl(url: String)

private const val VERTEX_SHADER = """#version 300 es
precision highp float;
in vec3 aPosition;
in vec4 aColor;
in vec2 aUv;
out vec4 vColor;
out vec2 vUv;
void main() {
    gl_Position = vec4(aPosition, 1.0);
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
