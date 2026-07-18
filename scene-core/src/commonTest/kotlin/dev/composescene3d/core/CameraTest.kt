package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
