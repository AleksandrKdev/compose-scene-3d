package dev.composescene3d.core

sealed interface CameraProjection {
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

data class CameraDescription(
    val eye: Vec3 = Vec3(0f, 1f, 10f),
    val target: Vec3 = Vec3.Zero,
    val up: Vec3 = Vec3(0f, 1f, 0f),
    val projection: CameraProjection = CameraProjection.Perspective(),
)
