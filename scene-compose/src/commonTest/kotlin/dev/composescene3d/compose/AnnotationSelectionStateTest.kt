package dev.composescene3d.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnotationSelectionStateTest {
    @Test
    fun selectsTogglesAndClearsAnnotation() {
        val state = AnnotationSelectionState()
        state.select("shaft")
        assertTrue(state.isSelected("shaft"))
        assertFalse(state.isSelected("bearing"))
        state.toggle("shaft")
        assertNull(state.selectedKey)
        state.toggle("bearing")
        assertEquals("bearing", state.selectedKey)
        state.clear()
        assertNull(state.selectedKey)
    }
}
