package dev.composescene3d.core

import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.PI

/** A point expressed in the local coordinate system of an imported model part. */
data class ModelPartAnchor3D(
    val nodeKey: NodeKey,
    val partKey: ModelPartKey,
    val localPosition: Vec3 = Vec3.Zero,
)

/** A backend-neutral local point and the transform that places it in world space. */
data class SceneAnchor3D(
    val localPosition: Vec3,
    val transform: Transform = Transform(),
) {
    val worldPosition: Vec3 get() = transform.transformPoint(localPosition)
}

/** Common contract for scene dimensions that can host a screen-space label. */
sealed interface EngineeringDimensionNode : SceneNode {
    val labelAnchor: Vec3

    fun sceneAnchor(transform: Transform = this.transform): SceneAnchor3D =
        SceneAnchor3D(labelAnchor, transform)
}

/** Pixel coordinates relative to the top-left of a viewport. */
data class ScreenPosition3D(
    val x: Float,
    val y: Float,
    val depth: Float,
    val visible: Boolean,
)

/** Projects a world point into a viewport without exposing renderer-specific camera types. */
fun projectWorldToScreen(
    worldPosition: Vec3,
    camera: CameraDescription,
    viewportWidth: Int,
    viewportHeight: Int,
): ScreenPosition3D {
    require(viewportWidth > 0 && viewportHeight > 0) { "Viewport dimensions must be positive" }
    val forward = (camera.target - camera.eye).normalized("Camera eye and target cannot coincide")
    val right = forward.cross(camera.up).normalized("Camera up cannot be parallel to its view direction")
    val actualUp = right.cross(forward)
    val relative = worldPosition - camera.eye
    val cameraX = relative.dot(right)
    val cameraY = relative.dot(actualUp)
    val depth = relative.dot(forward)
    val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
    val (ndcX, ndcY, insideDepth) = when (val projection = camera.projection) {
        is CameraProjection.Perspective -> {
            val halfHeight = depth * tan(PI * projection.verticalFovDegrees / 360.0).toFloat()
            val validDepth = depth.toDouble() >= projection.near && depth.toDouble() <= projection.far
            Triple(cameraX / (halfHeight * aspect), cameraY / halfHeight, validDepth && depth > 0f)
        }
        is CameraProjection.Orthographic -> {
            val halfHeight = projection.verticalSize.toFloat() / 2f
            Triple(
                cameraX / (halfHeight * aspect),
                cameraY / halfHeight,
                depth.toDouble() >= projection.near && depth.toDouble() <= projection.far,
            )
        }
    }
    return ScreenPosition3D(
        x = (ndcX + 1f) * 0.5f * viewportWidth,
        y = (1f - ndcY) * 0.5f * viewportHeight,
        depth = depth,
        visible = insideDepth && ndcX in -1f..1f && ndcY in -1f..1f,
    )
}

/** Applies scale, quaternion rotation, then translation to a local point. */
fun Transform.transformPoint(point: Vec3): Vec3 {
    val scaled = Vec3(point.x * scale.x, point.y * scale.y, point.z * scale.z)
    val vector = Vec3(rotation.x, rotation.y, rotation.z)
    val rotated = scaled + vector.cross(scaled) * (2f * rotation.w) +
        vector.cross(vector.cross(scaled)) * 2f
    return rotated + translation
}

private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z
private fun Vec3.cross(other: Vec3) = Vec3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)
private fun Vec3.normalized(message: String): Vec3 {
    val length = sqrt(dot(this))
    require(length > 0f && length.isFinite()) { message }
    return Vec3(x / length, y / length, z / length)
}
