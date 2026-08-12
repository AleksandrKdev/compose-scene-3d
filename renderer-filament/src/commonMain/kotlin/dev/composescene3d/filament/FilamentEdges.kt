package dev.composescene3d.filament

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import dev.composescene3d.core.EdgeGeometry3D
import dev.composescene3d.core.EdgeNode
import dev.composescene3d.core.Transform
import io.github.erkko68.filament.Box
import io.github.erkko68.filament.IndexBuffer
import io.github.erkko68.filament.RenderableManager
import io.github.erkko68.filament.VertexBuffer
import io.github.erkko68.filament.VertexBuffer.AttributeType
import io.github.erkko68.filament.VertexBuffer.VertexAttribute
import io.github.erkko68.filament.compose.FilamentSceneScope
import io.github.erkko68.filament.compose.LocalFilamentEngine
import io.github.erkko68.filament.compose.LocalFilamentScene
import io.github.erkko68.filament.toBytes
import kotlin.math.max
import kotlin.math.min

private data class EdgeHandles(
    val vertices: VertexBuffer,
    val indices: IndexBuffer,
    val bounds: Box,
)

@Composable
internal fun FilamentSceneScope.FilamentEdges(renderer: FilamentRenderer, node: EdgeNode) {
    val engine = LocalFilamentEngine.current
    val scene = LocalFilamentScene.current
    val parent = LocalComposeScene3DParent.current
    val material = rememberSceneMaterial(renderer, node.material)
    val handles = remember(node.geometry) {
        val tangents = FloatArray(node.geometry.vertexCount * 4) { index ->
            if (index % 4 == 3) 1f else 0f
        }
        val vertices = VertexBuffer.Builder()
            .vertexCount(node.geometry.vertexCount)
            .bufferCount(2)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3)
            .attribute(VertexAttribute.TANGENTS, 1, AttributeType.FLOAT4)
            .build(engine)
        vertices.setBufferAt(engine, 0, node.geometry.positions.toBytes())
        vertices.setBufferAt(engine, 1, tangents.toBytes())
        val indices = IndexBuffer.Builder()
            .indexCount(node.geometry.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indices.setBuffer(engine, node.geometry.indices.toBytes())
        EdgeHandles(vertices, indices, node.geometry.edgeBounds())
    }

    DisposableEffect(handles) {
        onDispose {
            engine.destroyVertexBuffer(handles.vertices)
            engine.destroyIndexBuffer(handles.indices)
        }
    }

    val entity = remember(handles, material) {
        engine.getEntityManager().create().also { entity ->
            RenderableManager.Builder(1)
                .geometry(0, RenderableManager.PrimitiveType.LINES, handles.vertices, handles.indices)
                .material(0, material)
                .boundingBox(handles.bounds)
                .castShadows(false)
                .receiveShadows(false)
                .build(engine, entity)
        }
    }

    DisposableEffect(entity) {
        scene.addEntity(entity)
        renderer.registerEntity(node.key, entity)
        onDispose {
            renderer.unregisterEntity(node.key, entity)
            scene.removeEntity(entity)
            engine.getRenderableManager().destroy(entity)
            engine.getTransformManager().destroy(entity)
            engine.getEntityManager().destroy(entity)
        }
    }
    DisposableEffect(entity, node.transform, parent) {
        val transforms = engine.getTransformManager()
        if (!transforms.hasComponent(entity)) transforms.create(entity)
        transforms.setTransform(transforms.getInstance(entity), node.transform.edgeMatrix())
        transforms.setParent(
            transforms.getInstance(entity),
            if (parent == null) 0 else transforms.getInstance(parent),
        )
        onDispose { }
    }
}

private fun EdgeGeometry3D.edgeBounds(): Box {
    var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
    for (index in positions.indices step 3) {
        minX = min(minX, positions[index]); minY = min(minY, positions[index + 1]); minZ = min(minZ, positions[index + 2])
        maxX = max(maxX, positions[index]); maxY = max(maxY, positions[index + 1]); maxZ = max(maxZ, positions[index + 2])
    }
    return Box((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, (maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f)
}

private fun Transform.edgeMatrix(): FloatArray {
    val x = rotation.x; val y = rotation.y; val z = rotation.z; val w = rotation.w
    return floatArrayOf(
        (1f - 2f * (y * y + z * z)) * scale.x, (2f * (x * y + w * z)) * scale.x, (2f * (x * z - w * y)) * scale.x, 0f,
        (2f * (x * y - w * z)) * scale.y, (1f - 2f * (x * x + z * z)) * scale.y, (2f * (y * z + w * x)) * scale.y, 0f,
        (2f * (x * z + w * y)) * scale.z, (2f * (y * z - w * x)) * scale.z, (1f - 2f * (x * x + y * y)) * scale.z, 0f,
        translation.x, translation.y, translation.z, 1f,
    )
}
