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

data class SceneDescription(val nodes: List<SceneNode>) {
    init {
        val duplicateKeys = nodes.groupingBy(SceneNode::key).eachCount().filterValues { it > 1 }.keys
        require(duplicateKeys.isEmpty()) { "Duplicate scene node keys: $duplicateKeys" }
    }

    companion object {
        val Empty = SceneDescription(emptyList())
    }
}
