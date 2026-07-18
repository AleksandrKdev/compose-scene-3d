package dev.composescene3d.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.composescene3d.compose.Scene3D
import dev.composescene3d.compose.rememberSceneController
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.filament.FilamentRenderer
import dev.composescene3d.filament.FilamentViewport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Sample() }
    }
}

@androidx.compose.runtime.Composable
private fun Sample() {
    val renderer = remember { FilamentRenderer() }
    val controller = rememberSceneController(renderer)
    val camera = rememberSceneCameraState(
        CameraDescription(eye = Vec3(0f, 2f, 5f), target = Vec3(0f, 0.5f, 0f))
    )
    val resources = LocalContext.current.resources
    val duckBytes = remember {
        resources.openRawResource(R.raw.duck).use { it.readBytes() }
    }
    var moved by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        FilamentViewport(
            renderer = renderer,
            cameraState = camera,
            onNodePicked = { selected = it?.value },
        )
        Scene3D(controller) {
            box(
                key = "demo-box",
                color = Vec3(0.15f, 0.55f, 0.95f),
                transform = Transform(translation = Vec3(if (moved) 1f else -1f, 0f, 0f)),
            )
            model(
                key = "duck",
                source = ModelSource.Bytes(duckBytes, cacheKey = "sample-duck"),
                transform = Transform(
                    translation = Vec3(0f, -1f, 0f),
                    scale = Vec3.One,
                ),
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
        Button(
            onClick = { moved = !moved },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) {
            Text("Move retained box")
        }
        Text(
            text = "Drag: orbit · Scroll/pinch: zoom · Selected: ${selected ?: "none"}",
            modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
        )
    }
}
