package dev.composescene3d.compose

import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.Vec3
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneCameraGestureControllerTest {
    @Test
    fun orbitPreservesDistanceToTarget() {
        val camera = SceneCameraState(CameraDescription(eye = Vec3(0f, 2f, 5f)))
        val before = distance(camera.eye, camera.target)

        SceneCameraGestureController(camera).orbit(deltaX = 30f, deltaY = -12f)

        assertEquals(before, distance(camera.eye, camera.target), absoluteTolerance = 0.0001f)
    }

    @Test
    fun panMovesEyeAndTargetByTheSameVector() {
        val camera = SceneCameraState(CameraDescription(eye = Vec3(0f, 0f, 5f)))
        val before = camera.eye - camera.target

        SceneCameraGestureController(camera).pan(deltaX = 50f, deltaY = 20f, viewportHeight = 500)

        assertEquals(before, camera.eye - camera.target)
        assertTrue(camera.target.x < 0f)
        assertTrue(camera.target.y > 0f)
    }

    @Test
    fun spreadingTwoPointersZoomsIn() {
        val camera = SceneCameraState(CameraDescription(eye = Vec3(0f, 0f, 5f)))

        SceneCameraGestureController(camera).zoom(scale = 2f)

        assertTrue(distance(camera.eye, camera.target) < 5f)
    }
}

private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private fun distance(a: Vec3, b: Vec3): Float {
    val d = a - b
    return sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
}
