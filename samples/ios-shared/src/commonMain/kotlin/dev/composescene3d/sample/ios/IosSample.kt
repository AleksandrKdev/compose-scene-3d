package dev.composescene3d.sample.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.composescene3d.compose.Scene3D
import dev.composescene3d.compose.SceneCameraState
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.compose.rememberSceneController
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import dev.composescene3d.filament.FilamentRenderer
import dev.composescene3d.filament.FilamentViewport
import dev.composescene3d.filament.ModelByteLoader
import dev.composescene3d.sample.ios.resources.Res

@Composable
fun IosSample(
    cameraState: SceneCameraState = rememberSceneCameraState(
        CameraDescription(eye = Vec3(0f, 2f, 5f), target = Vec3(0f, 0.5f, 0f))
    ),
) {
    val renderer = remember {
        FilamentRenderer(
            modelByteLoader = ModelByteLoader { source ->
                when (source) {
                    is ModelSource.Resource -> Res.readBytes(source.path)
                    is ModelSource.Bytes -> source.value
                    is ModelSource.Url -> error("URL loading is not enabled in the iOS sample")
                }
            }
        )
    }
    val controller = rememberSceneController(renderer)
    var selected by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        FilamentViewport(
            renderer = renderer,
            cameraState = cameraState,
            zoomSpeed = 0.12f,
            onNodePicked = { selected = it?.value },
        )
        Scene3D(controller) {
            box(
                key = "demo-box",
                color = Vec3(0.15f, 0.55f, 0.95f),
                transform = Transform(translation = Vec3(-1f, 0f, 0f)),
            )
            model(
                key = "duck",
                source = ModelSource.Resource("files/duck.glb"),
                transform = Transform(translation = Vec3(0f, -1f, 0f)),
            )
            sphere(
                key = "metal-sphere",
                material = PbrMaterial(
                    baseColor = Vec3(0.9f, 0.55f, 0.12f),
                    metallic = 1f,
                    roughness = 0.18f,
                ),
                transform = Transform(translation = Vec3(-2f, 0f, 0f)),
            )
            cylinder(
                key = "rough-cylinder",
                material = PbrMaterial(
                    baseColor = Vec3(0.25f, 0.85f, 0.4f),
                    roughness = 0.85f,
                ),
                transform = Transform(translation = Vec3(2f, 0f, 0f)),
            )
            plane(
                key = "ground",
                width = 6f,
                depth = 5f,
                material = PbrMaterial(baseColor = Vec3(0.22f, 0.24f, 0.28f), roughness = 1f),
                transform = Transform(translation = Vec3(0f, -1.05f, 0f)),
            )
            directionalLight(key = "sun", intensity = 100_000f)
        }
        Text(
            text = "Orbit: drag · Pan/zoom: two fingers · Selected: ${selected ?: "none"}",
            modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
        )
    }
}
