package dev.composescene3d.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExplodedViewTest {
    private val shaft = ModelPartKey("assembly/shaft")

    @Test
    fun interpolatesTranslationAndPreservesExistingOverrideFields() {
        val outline = ModelPartOutline(Color3D.Cyan)
        val existing = ModelPartOverride(
            visible = false,
            transformOffset = Transform(
                translation = Vec3(1f, 0f, 0f),
                rotation = Quaternion(0f, 0f, 1f, 0f),
                scale = Vec3(2f, 2f, 2f),
            ),
            outline = outline,
        )
        val result = ExplodedView3D(
            listOf(ExplodedPart3D(shaft, Vec3(0f, 4f, 2f))),
        ).overrides(0.5f, mapOf(shaft to existing)).getValue(shaft)

        assertEquals(Vec3(1f, 2f, 1f), result.transformOffset.translation)
        assertEquals(existing.transformOffset.rotation, result.transformOffset.rotation)
        assertEquals(existing.transformOffset.scale, result.transformOffset.scale)
        assertEquals(false, result.visible)
        assertEquals(outline, result.outline)
    }

    @Test
    fun zeroProgressReturnsBaseWithoutIdentityEntries() {
        val base = mapOf(shaft to ModelPartOverride(visible = false))
        val view = ExplodedView3D(listOf(ExplodedPart3D(ModelPartKey("cover"), Vec3.One)))

        assertEquals(base, view.overrides(0f, base))
    }

    @Test
    fun validatesDefinitionAndProgress() {
        assertFailsWith<IllegalArgumentException> {
            ExplodedView3D(listOf(ExplodedPart3D(shaft, Vec3.One), ExplodedPart3D(shaft, Vec3.One)))
        }
        val view = ExplodedView3D(emptyList())
        assertFailsWith<IllegalArgumentException> { view.overrides(-0.1f) }
        assertFailsWith<IllegalArgumentException> { view.overrides(1.1f) }
    }
}
