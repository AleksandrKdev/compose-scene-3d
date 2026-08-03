package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.sqrt

class CameraTest {
    @Test
    fun rejectsInvalidPerspectiveClippingPlanes() {
        assertFailsWith<IllegalArgumentException> {
            CameraProjection.Perspective(near = 10.0, far = 1.0)
        }
    }

    @Test
    fun rejectsNonPositiveOrthographicSize() {
        assertFailsWith<IllegalArgumentException> {
            CameraProjection.Orthographic(verticalSize = 0.0)
        }
    }

    @Test
    fun focusesPerspectiveCameraOnBoundingSphere() {
        val focused = CameraDescription(eye = Vec3(0f, 0f, 10f)).focusedOn(
            CameraFocus3D(center = Vec3(3f, 2f, 1f), radius = 2f),
        )
        assertEquals(Vec3(3f, 2f, 1f), focused.target)
        assertTrue(distance(focused.eye, focused.target) > 2f)
        assertEquals(3f, focused.eye.x, 0.0001f)
        assertEquals(2f, focused.eye.y, 0.0001f)
    }

    @Test
    fun focusesOrthographicCameraByChangingVerticalSize() {
        val focused = CameraDescription(
            projection = CameraProjection.Orthographic(verticalSize = 20.0),
        ).focusedOn(CameraFocus3D(Vec3.Zero, radius = 2f, padding = 1.25f))
        assertEquals(5.0, (focused.projection as CameraProjection.Orthographic).verticalSize)
    }
}

private fun distance(a: Vec3, b: Vec3): Float {
    val x = a.x - b.x
    val y = a.y - b.y
    val z = a.z - b.z
    return sqrt(x * x + y * y + z * z)
}
