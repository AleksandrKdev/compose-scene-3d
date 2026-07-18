package dev.composescene3d.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.CameraProjection
import dev.composescene3d.core.Vec3

class SceneCameraState internal constructor(initial: CameraDescription) {
    var eye: Vec3 by mutableStateOf(initial.eye)
    var target: Vec3 by mutableStateOf(initial.target)
    var up: Vec3 by mutableStateOf(initial.up)
    var projection: CameraProjection by mutableStateOf(initial.projection)

    fun reset(description: CameraDescription) {
        eye = description.eye
        target = description.target
        up = description.up
        projection = description.projection
    }

}

@Composable
fun rememberSceneCameraState(
    initial: CameraDescription = CameraDescription(),
): SceneCameraState = remember { SceneCameraState(initial) }
