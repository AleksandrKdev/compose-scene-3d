package dev.composescene3d.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Applies backend-neutral orbit, pan and zoom operations to this camera. */
class SceneCameraGestureController(
    private val camera: SceneCameraState,
    private val orbitSpeed: Float = 0.01f,
    private val zoomSpeed: Float = 0.12f,
    private val minimumDistance: Float = 0.1f,
    private val maximumDistance: Float = 1_000f,
) {
    fun orbit(deltaX: Float, deltaY: Float) {
        val offset = camera.eye - camera.target
        val radius = offset.length().coerceIn(minimumDistance, maximumDistance)
        var yaw = atan2(offset.x, offset.z) - deltaX * orbitSpeed
        var pitch = asin((offset.y / radius).coerceIn(-1f, 1f)) + deltaY * orbitSpeed
        val pitchLimit = (PI.toFloat() / 2f) - 0.01f
        pitch = pitch.coerceIn(-pitchLimit, pitchLimit)
        val horizontal = cos(pitch) * radius
        camera.eye = camera.target + Vec3(
            sin(yaw) * horizontal,
            sin(pitch) * radius,
            cos(yaw) * horizontal,
        )
        camera.up = Vec3(0f, 1f, 0f)
    }

    fun pan(deltaX: Float, deltaY: Float, viewportHeight: Int) {
        if (viewportHeight <= 0) return
        val forward = (camera.target - camera.eye).normalized()
        val right = forward.cross(camera.up).normalized()
        val screenUp = right.cross(forward).normalized()
        val worldUnitsPerPixel = when (val projection = camera.projection) {
            is CameraProjection.Perspective -> {
                val distance = (camera.target - camera.eye).length()
                val halfFov = projection.verticalFovDegrees * PI / 360.0
                (2.0 * distance * kotlin.math.tan(halfFov) / viewportHeight).toFloat()
            }
            is CameraProjection.Orthographic -> (projection.verticalSize / viewportHeight).toFloat()
        }
        val shift = right * (-deltaX * worldUnitsPerPixel) +
            screenUp * (deltaY * worldUnitsPerPixel)
        camera.eye += shift
        camera.target += shift
    }

    /** [scale] is the new two-finger distance divided by the previous distance. */
    fun zoom(scale: Float) {
        if (!scale.isFinite() || scale <= 0f) return
        val offset = camera.eye - camera.target
        val distance = offset.length()
        if (distance == 0f) return
        val exponent = zoomSpeed * 8.333333f
        val nextDistance = (distance * kotlin.math.exp(-ln(scale) * exponent))
            .coerceIn(minimumDistance, maximumDistance)
        camera.eye = camera.target + offset * (nextDistance / distance)
    }
}

/**
 * One pointer orbits. Two pointers pan with their centroid and zoom with their separation.
 * The implementation updates [SceneCameraState] directly and therefore works with every backend.
 */
fun Modifier.sceneCameraGestures(
    cameraState: SceneCameraState,
    viewportHeight: () -> Int,
    orbitSpeed: Float = 0.01f,
    zoomSpeed: Float = 0.12f,
): Modifier = pointerInput(cameraState, orbitSpeed, zoomSpeed) {
    val controller = SceneCameraGestureController(cameraState, orbitSpeed, zoomSpeed)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val singlePointerPans = currentEvent.buttons.isSecondaryPressed
        var previousSingle = down.position
        var previousCentroid = Offset.Zero
        var previousDistance = 0f
        var twoPointerMode = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                val first = pressed[0].position
                val second = pressed[1].position
                val centroid = (first + second) / 2f
                val distance = (first - second).getDistance()
                if (twoPointerMode) {
                    val pan = centroid - previousCentroid
                    controller.pan(pan.x, pan.y, viewportHeight())
                    if (previousDistance > 0f) controller.zoom(distance / previousDistance)
                    event.changes.forEach { it.consume() }
                } else {
                    twoPointerMode = true
                }
                previousCentroid = centroid
                previousDistance = distance
            } else if (!twoPointerMode) {
                val position = pressed.single().position
                val delta = position - previousSingle
                if (delta != Offset.Zero) {
                    if (singlePointerPans) {
                        controller.pan(delta.x, delta.y, viewportHeight())
                    } else {
                        controller.orbit(delta.x, delta.y)
                    }
                    pressed.single().consume()
                }
                previousSingle = position
            }
        }
    }
}.pointerInput(cameraState, zoomSpeed) {
    val controller = SceneCameraGestureController(cameraState, orbitSpeed, zoomSpeed)
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.firstOrNull() ?: continue
                controller.zoom(kotlin.math.exp(-change.scrollDelta.y * 0.1f))
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.length() = sqrt(x * x + y * y + z * z)
private fun Vec3.normalized(): Vec3 {
    val length = length()
    return if (length == 0f) Vec3.Zero else this * (1f / length)
}
private fun Vec3.cross(other: Vec3) = Vec3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)
