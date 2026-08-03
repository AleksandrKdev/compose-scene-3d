package dev.composescene3d.compose

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.composescene3d.core.SceneAnnotation3D

class AnnotationSelectionState(initialSelectedKey: String? = null) {
    var selectedKey: String? by mutableStateOf(initialSelectedKey)
        private set

    fun select(key: String?) {
        selectedKey = key
    }

    fun toggle(key: String) {
        selectedKey = if (selectedKey == key) null else key
    }

    fun clear() {
        selectedKey = null
    }

    fun isSelected(key: String): Boolean = selectedKey == key
}

@Composable
fun rememberAnnotationSelectionState(initialSelectedKey: String? = null): AnnotationSelectionState =
    remember { AnnotationSelectionState(initialSelectedKey) }

/** Adds selection, click handling, and TalkBack/VoiceOver metadata to annotation content. */
fun Modifier.sceneAnnotationInteraction(
    annotation: SceneAnnotation3D,
    selectionState: AnnotationSelectionState,
    onClick: (SceneAnnotation3D) -> Unit = {},
): Modifier = clickable(enabled = annotation.enabled) {
    selectionState.toggle(annotation.key)
    onClick(annotation)
}.semantics {
    contentDescription = annotation.contentDescription
    annotation.stateDescription?.let { stateDescription = it }
    selected = selectionState.isSelected(annotation.key)
    role = Role.Button
    if (!annotation.enabled) disabled()
}
