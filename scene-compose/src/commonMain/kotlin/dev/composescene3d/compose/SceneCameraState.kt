package dev.composescene3d.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.Vec3
import dev.composescene3d.core.CameraFocus3D
import dev.composescene3d.core.focusedOn

/** Mutable Compose state for a backend-neutral scene camera. */
class SceneCameraState internal constructor(initial: CameraDescription) {
    private var interactionVersion = 0L
    var eye: Vec3 by mutableStateOf(initial.eye)
    var target: Vec3 by mutableStateOf(initial.target)
    var up: Vec3 by mutableStateOf(initial.up)
    var projection: CameraProjection by mutableStateOf(initial.projection)

    /** Immediately applies [description] and cancels an active [animateTo] operation. */
    fun reset(description: CameraDescription) {
        interactionVersion++
        apply(description)
    }

    internal fun notifyInteraction() {
        interactionVersion++
    }

    /** Smoothly moves to [description]. Returns false when cancelled by camera interaction. */
    suspend fun animateTo(description: CameraDescription, durationMillis: Int = 600): Boolean {
        require(durationMillis >= 0) { "Camera animation duration must be non-negative" }
        if (durationMillis == 0) {
            apply(description)
            return true
        }
        val start = this.description()
        val version = interactionVersion
        var firstFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (interactionVersion != version) return false
            if (firstFrame == 0L) firstFrame = frame
            val raw = ((frame - firstFrame) / 1_000_000f / durationMillis).coerceIn(0f, 1f)
            val progress = raw * raw * (3f - 2f * raw)
            apply(interpolateCamera(start, description, progress))
            if (raw >= 1f) return true
        }
    }

    /** Smoothly frames [focus]. Returns false when cancelled by camera interaction. */
    suspend fun focusOn(focus: CameraFocus3D, durationMillis: Int = 600): Boolean =
        animateTo(description().focusedOn(focus), durationMillis)

    private fun apply(description: CameraDescription) {
        eye = description.eye
        target = description.target
        up = description.up
        projection = description.projection
    }

}

private fun interpolateCamera(from: CameraDescription, to: CameraDescription, progress: Float) =
    CameraDescription(
        eye = from.eye.lerp(to.eye, progress),
        target = from.target.lerp(to.target, progress),
        up = from.up.lerp(to.up, progress),
        projection = interpolateProjection(from.projection, to.projection, progress),
    )

private fun interpolateProjection(from: CameraProjection, to: CameraProjection, progress: Float): CameraProjection =
    when {
        from is CameraProjection.Perspective && to is CameraProjection.Perspective -> CameraProjection.Perspective(
            verticalFovDegrees = from.verticalFovDegrees + (to.verticalFovDegrees - from.verticalFovDegrees) * progress,
            near = from.near + (to.near - from.near) * progress,
            far = from.far + (to.far - from.far) * progress,
        )
        from is CameraProjection.Orthographic && to is CameraProjection.Orthographic -> CameraProjection.Orthographic(
            verticalSize = from.verticalSize + (to.verticalSize - from.verticalSize) * progress,
            near = from.near + (to.near - from.near) * progress,
            far = from.far + (to.far - from.far) * progress,
        )
        progress < 1f -> from
        else -> to
    }

private fun Vec3.lerp(other: Vec3, progress: Float) = Vec3(
    x + (other.x - x) * progress,
    y + (other.y - y) * progress,
    z + (other.z - z) * progress,
)

/** Remembers camera state initialized once from [initial]. Later changes to [initial] are ignored. */
@Composable
fun rememberSceneCameraState(
    initial: CameraDescription = CameraDescription(),
): SceneCameraState = remember { SceneCameraState(initial) }
