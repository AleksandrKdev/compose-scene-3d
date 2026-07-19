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

data class GroupNode(
    override val key: NodeKey,
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
        val duplicateKeys = nodes.groupingBy(SceneNode::key).eachCount().filterValues { it > 1 }.keys
        require(duplicateKeys.isEmpty()) { "Duplicate scene node keys: $duplicateKeys" }
    }

    companion object {
        val Empty = SceneDescription(emptyList())
    }
}
