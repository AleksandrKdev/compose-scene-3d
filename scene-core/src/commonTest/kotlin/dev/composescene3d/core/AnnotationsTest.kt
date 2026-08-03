package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnnotationsTest {
    @Test
    fun createsLayoutLabelFromAnnotationMetadata() {
        val annotation = SceneAnnotation3D(
            key = "bearing", anchor = SceneAnchor3D(Vec3(1f, 2f, 3f)),
            label = "Bearing", contentDescription = "Deep groove ball bearing", priority = 4,
        )
        val label = annotation.screenLabel(ScreenPosition3D(20f, 30f, 2f, true), 80f, 24f)
        assertEquals("bearing", label.key)
        assertEquals(4, label.priority)
    }

    @Test
    fun rejectsMissingAccessibleText() {
        assertFailsWith<IllegalArgumentException> {
            SceneAnnotation3D("bad", SceneAnchor3D(Vec3.Zero), "Label", contentDescription = "")
        }
    }
}
