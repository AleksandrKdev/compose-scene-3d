package dev.composescene3d.core

import kotlin.jvm.JvmInline

/** Stable identity of a scene node across recompositions and retained updates. */
@JvmInline
value class NodeKey(val value: String) {
    init {
        require(value.isNotBlank()) { "A scene node key cannot be blank" }
    }
}

/** Portable source of a glTF or GLB model. */
sealed interface ModelSource {
    data class Resource(val path: String) : ModelSource
    data class Url(val value: String) : ModelSource
    data class Bytes(val value: ByteArray, val cacheKey: String) : ModelSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && cacheKey == other.cacheKey && value.contentEquals(other.value)

        override fun hashCode(): Int = 31 * cacheKey.hashCode() + value.contentHashCode()
    }
}

/** Portable source of an image consumed by a material or environment map. */
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

/** Stable backend-neutral identifier of a named node inside one model instance. */
@JvmInline
value class ModelPartKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Model part key cannot be blank" }
    }
}

/**
 * A node in an imported model hierarchy.
 *
 * [key] is stable for one model asset hierarchy and can be used with [ModelPartOverride].
 * Non-renderable nodes can still own renderable descendants and receive subtree overrides.
 */
data class ModelPart3D(
    val key: ModelPartKey,
    val name: String,
    val parentKey: ModelPartKey? = null,
    val childKeys: List<ModelPartKey> = emptyList(),
    val renderable: Boolean = false,
)

/** Declarative changes applied to one imported model part after its authored local transform. */
data class ModelPartOverride(
    val visible: Boolean = true,
    val transformOffset: Transform = Transform(),
    val material: Material3D? = null,
    val outline: ModelPartOutline? = null,
)

/** Replaces an imported part material with an explicitly supplied fade-capable material. */
fun ModelPartOverride.withOpacity(material: Material3D, opacity: Float): ModelPartOverride =
    copy(material = material.withOpacity(opacity))

/** Geometry-expanded silhouette drawn around an imported model part. */
data class ModelPartOutline(
    val color: Color3D = Color3D.Yellow,
    val width: Float = 0.015f,
) {
    init {
        require(width > 0f && width.isFinite()) { "Outline width must be finite and positive" }
    }
}

/** Result of selecting a renderable scene entity. Imported models also identify the selected part. */
data class ScenePickResult(
    val nodeKey: NodeKey,
    val modelPartKey: ModelPartKey? = null,
)

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
    val visible: Boolean = true,
    val opacity: Float = 1f,
) : SceneNode {
    init { require(opacity in 0f..1f) { "Group opacity must be between 0 and 1" } }
}

data class ModelNode(
    override val key: NodeKey,
    val source: ModelSource,
    override val transform: Transform = Transform(),
    val visible: Boolean = true,
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
    val partOverrides: Map<ModelPartKey, ModelPartOverride> = emptyMap(),
) : SceneNode

/** Per-light shadow-map quality. A null value on a light disables its shadows. */
data class ShadowMap3D(
    val mapSize: Int = 1024,
    val constantBias: Float = 0.001f,
    val normalBias: Float = 1f,
    val shadowFar: Float = 0f,
    val cascades: Int = 1,
    val contactShadows: Boolean = false,
    val contactShadowDistance: Float = 0.3f,
    val contactShadowSteps: Int = 8,
    val bulbRadius: Float = 0.02f,
) {
    init {
        require(mapSize >= 8 && mapSize.isPowerOfTwo()) {
            "Shadow map size must be a power of two and at least 8"
        }
        require(constantBias >= 0f && constantBias.isFinite()) {
            "Shadow constant bias must be finite and non-negative"
        }
        require(normalBias >= 0f && normalBias.isFinite()) {
            "Shadow normal bias must be finite and non-negative"
        }
        require(shadowFar >= 0f && shadowFar.isFinite()) {
            "Shadow far distance must be finite and non-negative"
        }
        require(cascades in 1..4) { "Shadow cascades must be between 1 and 4" }
        require(contactShadowDistance >= 0f && contactShadowDistance.isFinite()) {
            "Contact shadow distance must be finite and non-negative"
        }
        require(contactShadowSteps > 0) { "Contact shadow steps must be positive" }
        require(bulbRadius >= 0f && bulbRadius.isFinite()) {
            "Shadow bulb radius must be finite and non-negative"
        }
    }
}

private fun Int.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0

/** View-wide shadow algorithm. Null at the viewport disables shadow rendering. */
sealed interface ShadowTechnique3D {
    data object Pcf : ShadowTechnique3D
    data object Pcfd : ShadowTechnique3D
    data class Vsm(
        val highPrecision: Boolean = false,
        val lightBleedReduction: Float = 0f,
    ) : ShadowTechnique3D {
        init {
            require(lightBleedReduction in 0f..1f) {
                "VSM light bleed reduction must be between 0 and 1"
            }
        }
    }
    data class Dpcf(val penumbraScale: Float = 1f) : ShadowTechnique3D {
        init { require(penumbraScale >= 0f && penumbraScale.isFinite()) }
    }
    data class Pcss(val penumbraScale: Float = 1f) : ShadowTechnique3D {
        init { require(penumbraScale >= 0f && penumbraScale.isFinite()) }
    }
}

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

/** High-contrast unlit fill intended for selected or instructional model parts. */
data class HighlightMaterial(
    val color: Color3D = Color3D.Yellow,
    val intensity: Float = 1.5f,
) : Material3D {
    init {
        require(intensity > 0f && intensity.isFinite()) {
            "Highlight intensity must be finite and positive"
        }
    }
}

/**
 * A world-space half-space used by [ClippedPbrMaterial]. Points for which
 * `dot(normal, position) >= offset` are retained unless [keepPositive] is false.
 */
data class ClippingPlane3D(
    val normal: Vec3,
    val offset: Float = 0f,
    val keepPositive: Boolean = true,
) {
    init {
        val lengthSquared = normal.x * normal.x + normal.y * normal.y + normal.z * normal.z
        require(lengthSquared > 0f && lengthSquared.isFinite()) {
            "Clipping plane normal must be finite and non-zero"
        }
        require(offset.isFinite()) { "Clipping plane offset must be finite" }
    }
}

/** Lit material that discards fragments outside up to three world-space clipping planes. */
data class ClippedPbrMaterial(
    val planes: List<ClippingPlane3D>,
    val baseColor: Color3D = Color3D(0.7f, 0.7f, 0.7f),
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val reflectance: Float = 0.5f,
) : Material3D {
    init {
        require(planes.isNotEmpty() && planes.size <= 3) {
            "Clipped material requires between one and three clipping planes"
        }
        require(metallic in 0f..1f) { "Metallic must be between 0 and 1" }
        require(roughness in 0f..1f) { "Roughness must be between 0 and 1" }
        require(reflectance in 0f..1f) { "Reflectance must be between 0 and 1" }
    }
}

/** Procedural, unlit engineering hatch pattern evaluated from mesh UV coordinates. */
data class HatchMaterial(
    val backgroundColor: Color3D = Color3D(0.95f, 0.75f, 0.25f),
    val lineColor: Color3D = Color3D(0.15f, 0.12f, 0.08f),
    val spacing: Float = 0.1f,
    val lineWidth: Float = 0.012f,
    val angleRadians: Float = 0.7853982f,
) : Material3D {
    init {
        require(spacing > 0f && spacing.isFinite()) {
            "Hatch spacing must be finite and positive"
        }
        require(lineWidth > 0f && lineWidth.isFinite() && lineWidth <= spacing / 2f) {
            "Hatch line width must be finite, positive, and no greater than half the spacing"
        }
        require(angleRadians.isFinite()) { "Hatch angle must be finite" }
    }
}

data class TexturedMaterial(
    val baseColorTexture: TextureSource,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val normalTexture: TextureSource? = null,
    val metallicRoughnessTexture: TextureSource? = null,
    val emissiveTexture: TextureSource? = null,
    val ambientOcclusionTexture: TextureSource? = null,
    val normalScale: Float = 1f,
    val emissiveColor: Color3D = Color3D.White,
    val emissiveIntensity: Float = 1f,
    val ambientOcclusionStrength: Float = 1f,
) : Material3D {
    init {
        require(metallic in 0f..1f) { "Metallic must be between 0 and 1" }
        require(roughness in 0f..1f) { "Roughness must be between 0 and 1" }
        require(normalScale >= 0f && normalScale.isFinite()) {
            "Normal scale must be finite and non-negative"
        }
        require(emissiveIntensity >= 0f && emissiveIntensity.isFinite()) {
            "Emissive intensity must be finite and non-negative"
        }
        require(ambientOcclusionStrength in 0f..1f) {
            "Ambient occlusion strength must be between 0 and 1"
        }
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

/** Multiplies a material's alpha while retaining its shading and texture inputs. */
data class OpacityMaterial(val material: Material3D, val opacity: Float) : Material3D {
    init { require(opacity in 0f..1f) { "Material opacity must be between 0 and 1" } }
}

fun Material3D.withOpacity(opacity: Float): Material3D {
    require(opacity in 0f..1f) { "Material opacity must be between 0 and 1" }
    if (opacity == 1f) return this
    return if (this is OpacityMaterial) OpacityMaterial(material, this.opacity * opacity)
    else OpacityMaterial(this, opacity)
}

data class BoxNode(
    override val key: NodeKey,
    val size: Vec3 = Vec3.One,
    val material: Material3D = PbrMaterial(),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
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
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
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
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
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
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
) : SceneNode {
    init {
        require(radius > 0f) { "Cylinder radius must be positive" }
        require(height > 0f) { "Cylinder height must be positive" }
        require(segments >= 3) { "Cylinder segments must be at least 3" }
    }
}

/** A solid, pickable 3D line whose thickness is measured in scene units. */
data class LineNode(
    override val key: NodeKey,
    val start: Vec3,
    val end: Vec3,
    val radius: Float = 0.01f,
    val segments: Int = 12,
    val material: Material3D = UnlitMaterial(),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = false,
    val receiveShadows: Boolean = false,
) : SceneNode {
    init {
        val lengthSquared = (end.x - start.x) * (end.x - start.x) +
            (end.y - start.y) * (end.y - start.y) +
            (end.z - start.z) * (end.z - start.z)
        require(lengthSquared > 0f && lengthSquared.isFinite()) {
            "Line start and end must be finite and different"
        }
        require(radius > 0f && radius.isFinite()) { "Line radius must be finite and positive" }
        require(segments >= 3) { "Line segments must be at least 3" }
    }
}

/** A solid 3D arrow composed of a shaft and a conical head. */
data class ArrowNode(
    override val key: NodeKey,
    val start: Vec3,
    val end: Vec3,
    val shaftRadius: Float = 0.01f,
    val headRadius: Float = 0.035f,
    val headLength: Float = 0.12f,
    val segments: Int = 16,
    val material: Material3D = UnlitMaterial(),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = false,
    val receiveShadows: Boolean = false,
) : SceneNode {
    init {
        val lengthSquared = (end.x - start.x) * (end.x - start.x) +
            (end.y - start.y) * (end.y - start.y) +
            (end.z - start.z) * (end.z - start.z)
        require(lengthSquared > 0f && lengthSquared.isFinite()) {
            "Arrow start and end must be finite and different"
        }
        require(shaftRadius > 0f && shaftRadius.isFinite()) {
            "Arrow shaft radius must be finite and positive"
        }
        require(headRadius > 0f && headRadius.isFinite()) {
            "Arrow head radius must be finite and positive"
        }
        require(headLength > 0f && headLength * headLength < lengthSquared) {
            "Arrow head length must be positive and shorter than the arrow"
        }
        require(segments >= 3) { "Arrow segments must be at least 3" }
    }
}

/** A linear engineering dimension with inward-facing arrows and extension lines. */
data class LinearDimensionNode(
    override val key: NodeKey,
    val start: Vec3,
    val end: Vec3,
    val offset: Vec3,
    val radius: Float = 0.008f,
    val arrowHeadRadius: Float = 0.028f,
    val arrowHeadLength: Float = 0.09f,
    val extensionGap: Float = 0.025f,
    val extensionOvershoot: Float = 0.04f,
    val segments: Int = 12,
    val material: Material3D = UnlitMaterial(Color3D.Yellow),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = false,
    val receiveShadows: Boolean = false,
) : EngineeringDimensionNode {
    /** Local-space anchor intended for a Compose text label. */
    override val labelAnchor: Vec3
        get() = Vec3(
            (start.x + end.x) / 2f + offset.x,
            (start.y + end.y) / 2f + offset.y,
            (start.z + end.z) / 2f + offset.z,
        )

    init {
        val measuredLengthSquared = start.distanceSquared(end)
        val offsetLengthSquared = offset.distanceSquared(Vec3.Zero)
        require(measuredLengthSquared > 0f && measuredLengthSquared.isFinite()) {
            "Dimension start and end must be finite and different"
        }
        require(offsetLengthSquared > 0f && offsetLengthSquared.isFinite()) {
            "Dimension offset must be finite and non-zero"
        }
        require(radius > 0f && radius.isFinite()) { "Dimension radius must be finite and positive" }
        require(arrowHeadRadius > 0f && arrowHeadRadius.isFinite()) {
            "Dimension arrow head radius must be finite and positive"
        }
        require(arrowHeadLength > 0f && 4f * arrowHeadLength * arrowHeadLength < measuredLengthSquared) {
            "Dimension arrow head length must be positive and shorter than half the dimension"
        }
        require(extensionGap >= 0f && extensionGap.isFinite()) {
            "Dimension extension gap must be finite and non-negative"
        }
        require(extensionOvershoot >= 0f && extensionOvershoot.isFinite()) {
            "Dimension extension overshoot must be finite and non-negative"
        }
        require(segments >= 3) { "Dimension segments must be at least 3" }
    }
}

/** A radius/diameter-style leader from a circle center through its measured edge. */
data class RadialDimensionNode(
    override val key: NodeKey,
    val center: Vec3,
    val edge: Vec3,
    val labelOffset: Float = 0.25f,
    val radius: Float = 0.008f,
    val arrowHeadRadius: Float = 0.028f,
    val arrowHeadLength: Float = 0.09f,
    val segments: Int = 12,
    val material: Material3D = UnlitMaterial(Color3D.Yellow),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = false,
    val receiveShadows: Boolean = false,
) : EngineeringDimensionNode {
    override val labelAnchor: Vec3 get() = edge + (edge - center).normalizedSceneVector() * labelOffset

    init {
        val measured = center.distanceSquared(edge)
        require(measured > 0f && measured.isFinite()) { "Radial dimension center and edge must be different" }
        require(labelOffset >= 0f && labelOffset.isFinite()) { "Radial label offset must be non-negative" }
        require(radius > 0f && arrowHeadRadius > 0f && arrowHeadLength > 0f) { "Radial dimension sizes must be positive" }
        require(arrowHeadLength * arrowHeadLength < measured) { "Radial arrow head must be shorter than the radius" }
        require(segments >= 3) { "Radial dimension segments must be at least 3" }
    }
}

/** An engineering angle dimension drawn as an arc between two directions. */
data class AngularDimensionNode(
    override val key: NodeKey,
    val center: Vec3,
    val startDirection: Vec3,
    val endDirection: Vec3,
    val arcRadius: Float,
    val radius: Float = 0.008f,
    val arrowHeadRadius: Float = 0.028f,
    val arrowHeadLength: Float = 0.09f,
    val arcSegments: Int = 24,
    val radialOvershoot: Float = 0.04f,
    val material: Material3D = UnlitMaterial(Color3D.Yellow),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = false,
    val receiveShadows: Boolean = false,
) : EngineeringDimensionNode {
    override val labelAnchor: Vec3 get() = center + (startDirection.normalizedSceneVector() + endDirection.normalizedSceneVector()).normalizedSceneVector() * arcRadius

    init {
        require(startDirection.distanceSquared(Vec3.Zero) > 0f && endDirection.distanceSquared(Vec3.Zero) > 0f) { "Angular dimension directions must be non-zero" }
        require(startDirection.crossSceneVector(endDirection).distanceSquared(Vec3.Zero) > 0.000001f) { "Angular dimension directions must define a plane" }
        require(arcRadius > 0f && radius > 0f && arrowHeadRadius > 0f && arrowHeadLength > 0f) { "Angular dimension sizes must be positive" }
        require(arcSegments >= 3) { "Angular dimension arc segments must be at least 3" }
        require(radialOvershoot >= 0f) { "Angular radial overshoot must be non-negative" }
    }
}

private fun Vec3.distanceSquared(other: Vec3): Float =
    (x - other.x) * (x - other.x) +
        (y - other.y) * (y - other.y) +
        (z - other.z) * (z - other.z)

private fun Vec3.normalizedSceneVector(): Vec3 {
    val length = kotlin.math.sqrt(distanceSquared(Vec3.Zero))
    return Vec3(x / length, y / length, z / length)
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.crossSceneVector(other: Vec3) = Vec3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)

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
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
) : SceneNode {
    init {
        require(material !is TexturedMaterial || geometry.uvs != null) {
            "Textured mesh geometry requires UV coordinates"
        }
    }
}

/** A custom triangle mesh clipped and closed by one plane in the mesh's local coordinates. */
data class SectionedMeshNode(
    override val key: NodeKey,
    val geometry: Geometry3D,
    val plane: ClippingPlane3D,
    val material: Material3D = PbrMaterial(),
    val capMaterial: Material3D = HatchMaterial(),
    override val transform: Transform = Transform(),
    val castShadows: Boolean = true,
    val receiveShadows: Boolean = true,
) : SceneNode

data class DirectionalLightNode(
    override val key: NodeKey,
    val intensity: Float,
    val color: Vec3 = Vec3.One,
    override val transform: Transform = Transform(),
    val shadow: ShadowMap3D? = null,
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
    val shadow: ShadowMap3D? = null,
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
