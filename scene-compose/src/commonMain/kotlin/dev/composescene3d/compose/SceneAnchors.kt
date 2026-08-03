package dev.composescene3d.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.ModelPartAnchor3D
import dev.composescene3d.core.SceneController
import dev.composescene3d.core.ScreenPosition3D
import dev.composescene3d.core.projectWorldToScreen

/** Snapshot of the backend-neutral camera represented by this mutable Compose state. */
fun SceneCameraState.description(): CameraDescription = CameraDescription(
    eye = eye,
    target = target,
    up = up,
    projection = projection,
)

/**
 * Tracks an imported model-part anchor once per rendered frame. The value is null until the model
 * part exists; use [ScreenPosition3D.visible] to hide labels outside the camera frustum.
 */
@Composable
fun rememberModelPartScreenPosition(
    controller: SceneController,
    anchor: ModelPartAnchor3D,
    cameraState: SceneCameraState,
    viewportWidth: Int,
    viewportHeight: Int,
): State<ScreenPosition3D?> = produceState<ScreenPosition3D?>(
    initialValue = null,
    controller,
    anchor,
    cameraState,
    viewportWidth,
    viewportHeight,
) {
    if (viewportWidth <= 0 || viewportHeight <= 0) {
        value = null
        return@produceState
    }
    while (true) {
        withFrameNanos { }
        value = controller.modelPartWorldPosition(anchor)?.let { worldPosition ->
            projectWorldToScreen(
                worldPosition = worldPosition,
                camera = cameraState.description(),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
    }
}
