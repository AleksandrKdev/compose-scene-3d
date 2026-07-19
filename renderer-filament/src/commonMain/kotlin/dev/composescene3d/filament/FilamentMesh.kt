package dev.composescene3d.filament

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import dev.composescene3d.core.Geometry3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.Transform
import io.github.erkko68.filament.Box
import io.github.erkko68.filament.Entity
import io.github.erkko68.filament.IndexBuffer
import io.github.erkko68.filament.RenderableManager
import io.github.erkko68.filament.SurfaceOrientation
import io.github.erkko68.filament.VertexBuffer
import io.github.erkko68.filament.VertexBuffer.AttributeType
import io.github.erkko68.filament.VertexBuffer.VertexAttribute
import io.github.erkko68.filament.compose.FilamentSceneScope
import io.github.erkko68.filament.compose.LocalFilamentEngine
import io.github.erkko68.filament.compose.LocalFilamentScene
import io.github.erkko68.filament.toBytes
import kotlin.math.max
import kotlin.math.min

internal val LocalComposeScene3DParent = compositionLocalOf<Entity?> { null }

private data class MeshHandles(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val boundingBox: Box,
)

@Composable
internal fun FilamentSceneScope.FilamentMesh(renderer: FilamentRenderer, node: MeshNode) {
    val engine = LocalFilamentEngine.current
    val scene = LocalFilamentScene.current
    val parent = LocalComposeScene3DParent.current
    val material = rememberSceneMaterial(renderer, node.material)
    val handles = remember(node.geometry) { uploadGeometry(node.geometry, engine) }

    DisposableEffect(handles) {
        onDispose {
            engine.destroyVertexBuffer(handles.vertexBuffer)
            engine.destroyIndexBuffer(handles.indexBuffer)
        }
    }

    val entity = remember(handles, material) {
        engine.getEntityManager().create().also { entity ->
            RenderableManager.Builder(1)
                .geometry(
                    0,
                    RenderableManager.PrimitiveType.TRIANGLES,
                    handles.vertexBuffer,
                    handles.indexBuffer,
                )
                .material(0, material)
                .boundingBox(handles.boundingBox)
                .castShadows(true)
                .receiveShadows(true)
                .build(engine, entity)
        }
    }

    DisposableEffect(entity) {
        scene.addEntity(entity)
        renderer.registerEntities(node.key, listOf(entity))
        onDispose {
            scene.removeEntity(entity)
            engine.getRenderableManager().destroy(entity)
            engine.getTransformManager().destroy(entity)
            engine.getEntityManager().destroy(entity)
        }
    }

    DisposableEffect(entity, node.transform) {
        val transforms = engine.getTransformManager()
        if (!transforms.hasComponent(entity)) transforms.create(entity)
        transforms.setTransform(transforms.getInstance(entity), node.transform.toMatrix())
        onDispose { }
    }

    DisposableEffect(entity, parent) {
        val transforms = engine.getTransformManager()
        if (!transforms.hasComponent(entity)) transforms.create(entity)
        transforms.setParent(
            transforms.getInstance(entity),
            if (parent == null) 0 else transforms.getInstance(parent),
        )
        onDispose { }
    }
}

private fun uploadGeometry(
    geometry: Geometry3D,
    engine: io.github.erkko68.filament.Engine,
): MeshHandles {
    val uvs = geometry.uvs
    val tangents = FloatArray(geometry.vertexCount * 4)
    val orientationBuilder = SurfaceOrientation.Builder()
        .vertexCount(geometry.vertexCount)
        .normals(geometry.normals)
    if (uvs != null) {
        orientationBuilder
            .positions(geometry.positions)
            .uvs(uvs)
            .triangleCount(geometry.triangleCount)
            .triangles32(geometry.indices)
    }
    val orientation = orientationBuilder.build()
    orientation.getQuatsAsFloat(tangents, geometry.vertexCount)
    orientation.destroy()

    val bufferCount = if (uvs == null) 2 else 3
    val vertexBuilder = VertexBuffer.Builder()
        .vertexCount(geometry.vertexCount)
        .bufferCount(bufferCount)
        .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3)
        .attribute(VertexAttribute.TANGENTS, 1, AttributeType.FLOAT4)
    if (uvs != null) {
        vertexBuilder.attribute(VertexAttribute.UV0, 2, AttributeType.FLOAT2)
    }
    val vertexBuffer = vertexBuilder.build(engine)
    vertexBuffer.setBufferAt(engine, 0, geometry.positions.toBytes())
    vertexBuffer.setBufferAt(engine, 1, tangents.toBytes())
    uvs?.let { vertexBuffer.setBufferAt(engine, 2, it.toBytes()) }

    val indexBuffer = IndexBuffer.Builder()
        .indexCount(geometry.indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.UINT)
        .build(engine)
    indexBuffer.setBuffer(engine, geometry.indices.toBytes())

    return MeshHandles(vertexBuffer, indexBuffer, geometry.boundingBox())
}

private fun Geometry3D.boundingBox(): Box {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    for (index in positions.indices step 3) {
        minX = min(minX, positions[index])
        minY = min(minY, positions[index + 1])
        minZ = min(minZ, positions[index + 2])
        maxX = max(maxX, positions[index])
        maxY = max(maxY, positions[index + 1])
        maxZ = max(maxZ, positions[index + 2])
    }
    return Box(
        (minX + maxX) / 2f,
        (minY + maxY) / 2f,
        (minZ + maxZ) / 2f,
        (maxX - minX) / 2f,
        (maxY - minY) / 2f,
        (maxZ - minZ) / 2f,
    )
}

private fun Transform.toMatrix(): FloatArray {
    val x = rotation.x
    val y = rotation.y
    val z = rotation.z
    val w = rotation.w
    val m00 = (1f - 2f * (y * y + z * z)) * scale.x
    val m01 = (2f * (x * y + w * z)) * scale.x
    val m02 = (2f * (x * z - w * y)) * scale.x
    val m10 = (2f * (x * y - w * z)) * scale.y
    val m11 = (1f - 2f * (x * x + z * z)) * scale.y
    val m12 = (2f * (y * z + w * x)) * scale.y
    val m20 = (2f * (x * z + w * y)) * scale.z
    val m21 = (2f * (y * z - w * x)) * scale.z
    val m22 = (1f - 2f * (x * x + y * y)) * scale.z
    return floatArrayOf(
        m00, m01, m02, 0f,
        m10, m11, m12, 0f,
        m20, m21, m22, 0f,
        translation.x, translation.y, translation.z, 1f,
    )
}
