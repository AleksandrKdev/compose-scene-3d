package dev.composescene3d.core

/** Portable metadata for an interactive screen-space annotation anchored to a 3D point. */
data class SceneAnnotation3D(
    val key: String,
    val anchor: SceneAnchor3D,
    val label: String,
    val contentDescription: String = label,
    val stateDescription: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
) {
    init {
        require(key.isNotBlank()) { "Annotation key cannot be blank" }
        require(label.isNotBlank()) { "Annotation label cannot be blank" }
        require(contentDescription.isNotBlank()) { "Annotation content description cannot be blank" }
    }

    fun screenLabel(position: ScreenPosition3D, width: Float, height: Float): ScreenLabel3D =
        ScreenLabel3D(key, position, width, height, priority)
}
