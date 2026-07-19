package dev.composescene3d.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
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
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Browser renderer with no Filament dependency. The first implementation deliberately uses the
 * portable Compose canvas, while retaining indexed triangles and a depth-sorted render list. This
 * gives Wasm users a working backend today and leaves the public API independent of a future
 * WebGL2 implementation.
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
    var surface = modifier
        .background(backgroundColor.toColor())
        .onSizeChanged { viewportHeight = it.height.coerceAtLeast(1) }
    if (orbitEnabled) {
        surface = surface.sceneCameraGestures(cameraState, { viewportHeight }, zoomSpeed = zoomSpeed)
    }
    Canvas(surface) {
        val triangles = buildRenderList(renderer.nodes.values, cameraState, size.width, size.height)
        triangles.sortedByDescending(ProjectedTriangle::depth).forEach { triangle ->
            drawTriangle(triangle)
        }
    }
}

private data class MeshData(
    val positions: List<Vec3>,
    val indices: List<Int>,
    val normals: List<Vec3>,
    val material: Material3D,
)

private data class ProjectedTriangle(
    val a: Offset,
    val b: Offset,
    val c: Offset,
    val depth: Float,
    val color: Color,
)

private fun buildRenderList(
    nodes: Collection<SceneNode>,
    camera: SceneCameraState,
    width: Float,
    height: Float,
): List<ProjectedTriangle> = buildList {
    fun append(node: SceneNode, parents: List<Transform>) {
        val transforms = listOf(node.transform) + parents
        if (node is GroupNode) {
            node.children.forEach { append(it, transforms) }
            return
        }
        val mesh = node.toMesh() ?: return
        val world = mesh.positions.map { point -> transforms.fold(point) { p, t -> t.apply(p) } }
        val transformedNormals = mesh.normals.map { normal ->
            transforms.fold(normal) { n, t -> t.rotation.rotate(n) }.normalized()
        }
        mesh.indices.chunked(3).forEach { index ->
            val p0 = camera.project(world[index[0]], width, height) ?: return@forEach
            val p1 = camera.project(world[index[1]], width, height) ?: return@forEach
            val p2 = camera.project(world[index[2]], width, height) ?: return@forEach
            val normal = (transformedNormals[index[0]] + transformedNormals[index[1]] +
                transformedNormals[index[2]]).normalized()
            val light = (0.2f + 0.8f * normal.dot(Vec3(0.35f, 0.8f, 0.45f).normalized()))
                .coerceIn(0.15f, 1f)
            add(ProjectedTriangle(p0.screen, p1.screen, p2.screen,
                (p0.depth + p1.depth + p2.depth) / 3f, mesh.material.color(light)))
        }
    }
    nodes.forEach { append(it, emptyList()) }
}

private data class Projection(val screen: Offset, val depth: Float)

private fun SceneCameraState.project(point: Vec3, width: Float, height: Float): Projection? {
    val forward = (target - eye).normalized()
    val right = forward.cross(up).normalized()
    val cameraUp = right.cross(forward)
    val relative = point - eye
    val x = relative.dot(right)
    val y = relative.dot(cameraUp)
    val z = relative.dot(forward)
    if (z <= 0.01f) return null
    val aspect = (width / height).coerceAtLeast(0.01f)
    val scale = when (val value = projection) {
        is CameraProjection.Perspective ->
            (1.0 / tan(value.verticalFovDegrees * PI / 360.0)).toFloat()
        is CameraProjection.Orthographic -> 2f * z / value.verticalSize.toFloat()
    }
    return Projection(
        Offset((x * scale / (z * aspect) + 1f) * width / 2f, (1f - y * scale / z) * height / 2f),
        z,
    )
}

private fun DrawScope.drawTriangle(triangle: ProjectedTriangle) {
    drawPath(Path().apply {
        moveTo(triangle.a.x, triangle.a.y)
        lineTo(triangle.b.x, triangle.b.y)
        lineTo(triangle.c.x, triangle.c.y)
        close()
    }, triangle.color)
}

private fun SceneNode.toMesh(): MeshData? = when (this) {
    is BoxNode -> boxMesh(size, UnlitMaterial(Color3D(color.x, color.y, color.z)))
    is MeshNode -> MeshData(
        geometry.positions.toVec3List(), geometry.indices.toList(), geometry.normals.toVec3List(), material,
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
    return MeshData(p, i, n, material)
}

private fun planeMesh(width: Float, depth: Float, material: Material3D) = MeshData(
    listOf(Vec3(-width/2,0f,-depth/2), Vec3(width/2,0f,-depth/2),
        Vec3(width/2,0f,depth/2), Vec3(-width/2,0f,depth/2)),
    listOf(0,2,1,0,3,2), List(4) { Vec3(0f,1f,0f) }, material,
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
    return MeshData(p, i, p.map { it.normalized() }, material)
}

private fun cylinderMesh(radius: Float, height: Float, segments: Int, material: Material3D): MeshData {
    val p = mutableListOf<Vec3>(); val n = mutableListOf<Vec3>()
    for (y in listOf(-height/2, height/2)) for (s in 0..segments) {
        val a = 2f * PI.toFloat() * s / segments
        p += Vec3(cos(a)*radius, y, sin(a)*radius); n += Vec3(cos(a),0f,sin(a))
    }
    val i = mutableListOf<Int>()
    for (s in 0 until segments) { val a=s; val b=s+segments+1; i += listOf(a,b,a+1,a+1,b,b+1) }
    return MeshData(p, i, n, material)
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
