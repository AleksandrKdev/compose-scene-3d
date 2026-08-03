package dev.composescene3d.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Projection parameters independent of viewport aspect ratio. */
sealed interface CameraProjection {
    /** Perspective projection whose field of view is measured vertically in degrees. */
    data class Perspective(
        val verticalFovDegrees: Double = 45.0,
        val near: Double = 0.1,
        val far: Double = 100.0,
    ) : CameraProjection {
        init {
            require(verticalFovDegrees in 1.0..179.0) { "Vertical FOV must be between 1 and 179 degrees" }
            require(near > 0.0 && far > near) { "Perspective clipping planes must satisfy 0 < near < far" }
        }
    }

    /** Orthographic projection whose [verticalSize] is expressed in scene units. */
    data class Orthographic(
        val verticalSize: Double = 10.0,
        val near: Double = -100.0,
        val far: Double = 100.0,
    ) : CameraProjection {
        init {
            require(verticalSize > 0.0) { "Orthographic vertical size must be positive" }
            require(far > near) { "Orthographic far plane must be greater than near plane" }
        }
    }
}

/** Right-handed look-at camera shared by every renderer backend. */
data class CameraDescription(
    val eye: Vec3 = Vec3(0f, 1f, 10f),
    val target: Vec3 = Vec3.Zero,
    val up: Vec3 = Vec3(0f, 1f, 0f),
    val projection: CameraProjection = CameraProjection.Perspective(),
)

/** A point or bounding sphere that should fill the camera view. */
data class CameraFocus3D(
    val center: Vec3,
    val radius: Float = 0f,
    val padding: Float = 1.25f,
) {
    init {
        require(radius >= 0f && radius.isFinite()) { "Focus radius must be finite and non-negative" }
        require(padding >= 1f && padding.isFinite()) { "Focus padding must be finite and at least 1" }
    }
}

/** Returns a camera aimed at [focus] while preserving the current viewing direction and up axis. */
fun CameraDescription.focusedOn(focus: CameraFocus3D): CameraDescription {
    val offset = eye - target
    val currentDistance = offset.length().coerceAtLeast(0.001f)
    val direction = offset * (1f / currentDistance)
    val distance = when (val currentProjection = projection) {
        is CameraProjection.Perspective -> if (focus.radius == 0f) currentDistance else {
            val halfFov = currentProjection.verticalFovDegrees * PI / 360.0
            (focus.radius * focus.padding / sin(halfFov)).toFloat()
        }
        is CameraProjection.Orthographic -> currentDistance
    }
    val nextProjection = when (val currentProjection = projection) {
        is CameraProjection.Perspective -> currentProjection
        is CameraProjection.Orthographic -> if (focus.radius == 0f) currentProjection else {
            currentProjection.copy(verticalSize = (focus.radius * 2f * focus.padding).toDouble())
        }
    }
    return copy(eye = focus.center + direction * distance, target = focus.center, projection = nextProjection)
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.length() = sqrt(x * x + y * y + z * z)
