package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DimensionLabelsTest {
    @Test
    fun formatsUnitsPrefixesPrecisionAndTolerance() {
        assertEquals("⌀12,5 +0,1/-0,05 mm", formatDimensionValue(
            12.5,
            DimensionTextFormat(
                decimals = 2, unit = "mm", prefix = "⌀", decimalSeparator = ',',
                tolerance = DimensionTolerance(0.1, -0.05),
            ),
        ))
        assertEquals("90°", formatDimensionValue(
            90.0, DimensionTextFormat(unit = "°", spaceBeforeUnit = false),
        ))
    }

    @Test
    fun placesCollidingLabelsDeterministically() {
        val anchor = ScreenPosition3D(100f, 100f, 2f, true)
        val result = layoutScreenLabels(
            listOf(
                ScreenLabel3D("low", anchor, 80f, 20f),
                ScreenLabel3D("high", anchor.copy(depth = 1f), 80f, 20f, priority = 1),
            ),
            viewportWidth = 200, viewportHeight = 200,
        )
        assertEquals(90f, result[1].y)
        assertTrue(result.all(PositionedScreenLabel3D::visible))
        assertTrue(result[0].y != result[1].y)
    }

    @Test
    fun hidesInvisibleOrUnplaceableLabels() {
        val hidden = layoutScreenLabels(
            listOf(ScreenLabel3D("hidden", ScreenPosition3D(5f, 5f, 1f, false), 20f, 10f)),
            100, 100,
        ).single()
        assertFalse(hidden.visible)
    }
}
