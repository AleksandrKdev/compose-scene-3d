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

    @Test
    fun transformsSceneAnchorFromLocalToWorldSpace() {
        val anchor = SceneAnchor3D(
            localPosition = Vec3(1f, 0f, 0f),
            transform = Transform(
                translation = Vec3(3f, 4f, 0f),
                rotation = Quaternion(0f, 0f, 0.70710677f, 0.70710677f),
                scale = Vec3(2f, 2f, 2f),
            ),
        )

        assertEquals(3f, anchor.worldPosition.x, 0.0001f)
        assertEquals(6f, anchor.worldPosition.y, 0.0001f)
        assertEquals(0f, anchor.worldPosition.z, 0.0001f)
    }

    @Test
    fun exposesDimensionAsSceneAnchor() {
        val dimension = LinearDimensionNode(
            NodeKey("width"), Vec3(-1f, 0f, 0f), Vec3(1f, 0f, 0f),
            offset = Vec3(0f, 0.5f, 0f), transform = Transform(translation = Vec3(2f, 0f, 0f)),
        )

        assertEquals(Vec3(2f, 0.5f, 0f), dimension.sceneAnchor().worldPosition)
    }
}
