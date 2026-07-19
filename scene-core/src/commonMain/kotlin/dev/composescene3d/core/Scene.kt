package dev.composescene3d.core

import kotlin.jvm.JvmInline

@JvmInline
value class NodeKey(val value: String) {
    init {
        require(value.isNotBlank()) { "A scene node key cannot be blank" }
    }
}

sealed interface ModelSource {
    data class Resource(val path: String) : ModelSource
    data class Url(val value: String) : ModelSource
    data class Bytes(val value: ByteArray, val cacheKey: String) : ModelSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && cacheKey == other.cacheKey && value.contentEquals(other.value)

        override fun hashCode(): Int = 31 * cacheKey.hashCode() + value.contentHashCode()
    }
}

sealed interface TextureSource {
    data class Resource(val path: String) : TextureSource
    data class Url(val value: String) : TextureSource
    data class Bytes(val value: ByteArray, val cacheKey: String) : TextureSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && cacheKey == other.cacheKey && value.contentEquals(other.value)

        override fun hashCode(): Int = 31 * cacheKey.hashCode() + value.contentHashCode()
    }
}

@JvmInline
value class TextureAssetKey(val value: String)

fun TextureSource.assetKey(): TextureAssetKey = when (this) {
    is TextureSource.Resource -> TextureAssetKey("resource:$path")
    is TextureSource.Url -> TextureAssetKey("url:$value")
    is TextureSource.Bytes -> TextureAssetKey("bytes:$cacheKey")
}

data class EnvironmentMap(
    val reflections: TextureSource,
    val skybox: TextureSource? = null,
    val intensity: Float = 30_000f,
    val skyboxIntensity: Float = 1f,
    val rotationYRadians: Float = 0f,
) {
    init {
        require(intensity >= 0f && intensity.isFinite()) {
            "Environment intensity must be finite and non-negative"
        }
        require(skyboxIntensity >= 0f && skyboxIntensity.isFinite()) {
            "Skybox intensity must be finite and non-negative"
        }
        require(rotationYRadians.isFinite()) { "Environment rotation must be finite" }
    }
}

@JvmInline
value class ModelAssetKey(val value: String)

fun ModelSource.assetKey(): ModelAssetKey = when (this) {
    is ModelSource.Resource -> ModelAssetKey("resource:$path")
    is ModelSource.Url -> ModelAssetKey("url:$value")
    is ModelSource.Bytes -> ModelAssetKey("bytes:$cacheKey")
}

sealed interface SceneNode {
    val key: NodeKey
    val transform: Transform
}

/**
 * A transform node whose [children] use local coordinates and inherit this group's transform.
 * Groups can be nested to build articulated objects and movable scene assemblies.
 */
data class GroupNode(
    override val key: NodeKey,
    val children: List<SceneNode>,
    override val transform: Transform = Transform(),
) : SceneNode

data class ModelNode(
    override val key: NodeKey,
    val source: ModelSource,
    override val transform: Transform = Transform(),
    val visible: Boolean = true,
) : SceneNode

sealed interface Material3D

data class PbrMaterial(
    val baseColor: Color3D = Color3D(0.7f, 0.7f, 0.7f),
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val reflectance: Float = 0.5f,
) : Material3D {
    init {
        require(metallic in 0f..1f) { "Metallic must be between 0 and 1" }
        require(roughness in 0f..1f) { "Roughness must be between 0 and 1" }
        require(reflectance in 0f..1f) { "Reflectance must be between 0 and 1" }
    }
}

data class UnlitMaterial(
    val color: Color3D = Color3D.White,
) : Material3D

data class EmissiveMaterial(
    val color: Color3D = Color3D.White,
    val intensity: Float = 1f,
) : Material3D {
    init {
        require(intensity >= 0f && intensity.isFinite()) {
            "Emissive intensity must be finite and non-negative"
        }
    }
}

data class TexturedMaterial(
    val baseColorTexture: TextureSource,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
) : Material3D {
    init {
        require(metallic in 0f..1f) { "Metallic must be between 0 and 1" }
        require(roughness in 0f..1f) { "Roughness must be between 0 and 1" }
    }
}

data class TransparentMaterial(
    val color: Color3D,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val reflectance: Float = 0.5f,
) : Material3D {
    init {
        require(metallic in 0f..1f) { "Metallic must be between 0 and 1" }
        require(roughness in 0f..1f) { "Roughness must be between 0 and 1" }
        require(reflectance in 0f..1f) { "Reflectance must be between 0 and 1" }
    }
}

data class BoxNode(
    override val key: NodeKey,
    val size: Vec3 = Vec3.One,
    val color: Vec3 = Vec3(0.7f, 0.7f, 0.7f),
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(size.x > 0f && size.y > 0f && size.z > 0f) { "Box dimensions must be positive" }
    }
}

data class SphereNode(
    override val key: NodeKey,
    val radius: Float = 0.5f,
    val rings: Int = 16,
    val segments: Int = 32,
    val material: Material3D = PbrMaterial(),
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(radius > 0f) { "Sphere radius must be positive" }
        require(rings >= 2) { "Sphere rings must be at least 2" }
        require(segments >= 3) { "Sphere segments must be at least 3" }
    }
}

data class PlaneNode(
    override val key: NodeKey,
    val width: Float = 1f,
    val depth: Float = 1f,
    val doubleSided: Boolean = true,
    val material: Material3D = PbrMaterial(),
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(width > 0f) { "Plane width must be positive" }
        require(depth > 0f) { "Plane depth must be positive" }
    }
}

data class CylinderNode(
    override val key: NodeKey,
    val radius: Float = 0.5f,
    val height: Float = 1f,
    val segments: Int = 32,
    val material: Material3D = PbrMaterial(),
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(radius > 0f) { "Cylinder radius must be positive" }
        require(height > 0f) { "Cylinder height must be positive" }
        require(segments >= 3) { "Cylinder segments must be at least 3" }
    }
}

/** Indexed triangle geometry stored in backend-neutral, non-interleaved arrays. */
class Geometry3D(
    val positions: FloatArray,
    val indices: IntArray,
    val normals: FloatArray,
    val uvs: FloatArray? = null,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    init {
        require(positions.size >= 9 && positions.size % 3 == 0) {
            "Geometry positions must contain at least three XYZ vertices"
        }
        require(normals.size == positions.size) {
            "Geometry must contain one XYZ normal per vertex"
        }
        require(indices.isNotEmpty() && indices.size % 3 == 0) {
            "Geometry indices must contain complete triangles"
        }
        require(uvs == null || uvs.size == vertexCount * 2) {
            "Geometry UVs must contain one UV pair per vertex"
        }
        require(positions.all(Float::isFinite)) { "Geometry positions must be finite" }
        require(normals.all(Float::isFinite)) { "Geometry normals must be finite" }
        require(uvs?.all(Float::isFinite) != false) { "Geometry UVs must be finite" }
        require(indices.all { it in 0 until vertexCount }) {
            "Geometry indices must reference an existing vertex"
        }
        require(normals.asList().chunked(3).all { (x, y, z) -> x != 0f || y != 0f || z != 0f }) {
            "Geometry normals cannot be zero vectors"
        }
    }

    override fun equals(other: Any?): Boolean = other is Geometry3D &&
        positions.contentEquals(other.positions) &&
        indices.contentEquals(other.indices) &&
        normals.contentEquals(other.normals) &&
        nullableContentEquals(uvs, other.uvs)

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        return 31 * result + (uvs?.contentHashCode() ?: 0)
    }
}

private fun nullableContentEquals(first: FloatArray?, second: FloatArray?): Boolean =
    first === second || (first != null && second != null && first.contentEquals(second))

data class MeshNode(
    override val key: NodeKey,
    val geometry: Geometry3D,
    val material: Material3D = PbrMaterial(),
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(material !is TexturedMaterial || geometry.uvs != null) {
            "Textured mesh geometry requires UV coordinates"
        }
    }
}

data class DirectionalLightNode(
    override val key: NodeKey,
    val intensity: Float,
    val color: Vec3 = Vec3.One,
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(intensity >= 0f) { "Light intensity cannot be negative" }
    }
}

data class PointLightNode(
    override val key: NodeKey,
    val intensity: Float,
    val color: Color3D = Color3D.White,
    val falloff: Float = 10f,
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(intensity >= 0f && intensity.isFinite()) { "Light intensity must be non-negative" }
        require(falloff > 0f && falloff.isFinite()) { "Point light falloff must be positive" }
    }
}

data class SpotLightNode(
    override val key: NodeKey,
    val intensity: Float,
    val direction: Vec3 = Vec3(0f, -1f, 0f),
    val color: Color3D = Color3D.White,
    val falloff: Float = 10f,
    val innerConeRadians: Float = 0.5f,
    val outerConeRadians: Float = 0.6f,
    override val transform: Transform = Transform(),
) : SceneNode {
    init {
        require(intensity >= 0f && intensity.isFinite()) { "Light intensity must be non-negative" }
        require(direction != Vec3.Zero) { "Spot light direction cannot be zero" }
        require(falloff > 0f && falloff.isFinite()) { "Spot light falloff must be positive" }
        require(innerConeRadians >= 0f && innerConeRadians <= outerConeRadians) {
            "Spot light inner cone must be non-negative and no larger than its outer cone"
        }
        require(outerConeRadians <= 1.5707964f) {
            "Spot light outer cone cannot exceed PI / 2"
        }
    }
}

data class SceneDescription(val nodes: List<SceneNode>) {
    init {
        val duplicateKeys = nodes.flattenSceneNodes()
            .groupingBy(SceneNode::key)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateKeys.isEmpty()) { "Duplicate scene node keys: $duplicateKeys" }
    }

    companion object {
        val Empty = SceneDescription(emptyList())
    }
}

/** Returns all nodes in deterministic pre-order, including group nodes themselves. */
fun Iterable<SceneNode>.flattenSceneNodes(): List<SceneNode> = buildList {
    fun append(node: SceneNode) {
        add(node)
        if (node is GroupNode) node.children.forEach(::append)
    }
    this@flattenSceneNodes.forEach(::append)
}
