package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorsTest {
    private val camera = CameraDescription(
        eye = Vec3(0f, 0f, 10f),
        target = Vec3.Zero,
        projection = CameraProjection.Perspective(verticalFovDegrees = 90.0),
    )

    @Test
    fun projectsCameraTargetToViewportCenter() {
        val result = projectWorldToScreen(Vec3.Zero, camera, 800, 600)

        assertEquals(400f, result.x, 0.001f)
        assertEquals(300f, result.y, 0.001f)
        assertEquals(10f, result.depth, 0.001f)
        assertTrue(result.visible)
    }

    @Test
    fun marksPointsBehindCameraOrOutsideViewportAsInvisible() {
        assertFalse(projectWorldToScreen(Vec3(0f, 0f, 20f), camera, 800, 600).visible)
        assertFalse(projectWorldToScreen(Vec3(100f, 0f, 0f), camera, 800, 600).visible)
    }

    @Test
    fun supportsOrthographicProjectionAndTopLeftCoordinates() {
        val result = projectWorldToScreen(
            worldPosition = Vec3(-2f, 1f, 0f),
            camera = camera.copy(projection = CameraProjection.Orthographic(verticalSize = 4.0)),
            viewportWidth = 400,
            viewportHeight = 400,
        )

        assertEquals(0f, result.x, 0.001f)
        assertEquals(100f, result.y, 0.001f)
        assertTrue(result.visible)
    }
}
