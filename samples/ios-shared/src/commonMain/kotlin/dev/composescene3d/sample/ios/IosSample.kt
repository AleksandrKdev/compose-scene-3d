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
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.TransparentMaterial
import dev.composescene3d.core.EnvironmentMap
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.Geometry3D
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3
import dev.composescene3d.filament.FilamentRenderer
import dev.composescene3d.filament.FilamentViewport
import dev.composescene3d.filament.ModelByteLoader
import dev.composescene3d.filament.TextureByteLoader
import dev.composescene3d.sample.ios.resources.Res

private val sampleTriangle = Geometry3D(
    positions = floatArrayOf(-0.7f, 0f, 0f, 0.7f, 0f, 0f, 0f, 1.1f, 0f),
    indices = intArrayOf(0, 1, 2),
    normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
    uvs = floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f),
)

@Composable
fun IosSample(
    cameraState: SceneCameraState = rememberSceneCameraState(
        CameraDescription(eye = Vec3(0f, 2f, 5f), target = Vec3(0f, 0.5f, 0f))
    ),
) {
    val renderer = remember {
        FilamentRenderer(
            textureByteLoader = TextureByteLoader { source ->
                when (source) {
                    is TextureSource.Resource -> Res.readBytes(source.path)
                    is TextureSource.Bytes -> source.value
                    is TextureSource.Url -> error("URL loading is not enabled in the iOS sample")
                }
            },
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
    val environment = remember {
        EnvironmentMap(
            reflections = TextureSource.Resource("files/lightroom_ibl.ktx"),
            skybox = TextureSource.Resource("files/lightroom_skybox.ktx"),
            intensity = 18_000f,
        )
    }
    val floorMaterial = remember {
        fun texture(name: String) = TextureSource.Resource("files/$name")
        TexturedMaterial(
            baseColorTexture = texture("pbr_albedo.png"),
            metallic = 0.85f,
            roughness = 0.8f,
            normalTexture = texture("pbr_normal.png"),
            metallicRoughnessTexture = texture("pbr_metallic_roughness.png"),
            emissiveTexture = texture("pbr_emissive.png"),
            ambientOcclusionTexture = texture("pbr_ao.png"),
            emissiveIntensity = 0.35f,
            ambientOcclusionStrength = 0.8f,
        )
    }
    val albedoOnlyMaterial = floorMaterial.copy(
        metallic = 0f,
        normalTexture = null,
        metallicRoughnessTexture = null,
        emissiveTexture = null,
        ambientOcclusionTexture = null,
    )
    var selected by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        FilamentViewport(
            renderer = renderer,
            environment = environment,
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
            group(key = "material-showcase") {
                sphere(
                    key = "metal-sphere",
                    material = PbrMaterial(
                        baseColor = Color3D(0.9f, 0.55f, 0.12f),
                        metallic = 1f,
                        roughness = 0.18f,
                    ),
                    transform = Transform(translation = Vec3(-2f, 0f, 0f)),
                )
                cylinder(
                    key = "rough-cylinder",
                    material = albedoOnlyMaterial,
                    transform = Transform(translation = Vec3(2f, 0f, 0f)),
                )
                sphere(
                    key = "glass-sphere",
                    radius = 0.45f,
                    material = TransparentMaterial(
                        color = Color3D(0.2f, 0.65f, 1f, alpha = 0.35f),
                        roughness = 0.12f,
                    ),
                    transform = Transform(translation = Vec3(0f, 0.8f, 0f)),
                )
                mesh(
                    key = "custom-triangle",
                    geometry = sampleTriangle,
                    material = PbrMaterial(baseColor = Color3D.Magenta, roughness = 0.35f),
                    transform = Transform(translation = Vec3(0f, 1.35f, 0f)),
                )
            }
            plane(
                key = "ground",
                width = 6f,
                depth = 5f,
                material = floorMaterial,
                transform = Transform(translation = Vec3(0f, -1.05f, 0f)),
            )
            directionalLight(key = "sun", intensity = 100_000f)
            pointLight(
                key = "warm-fill",
                intensity = 1_500f,
                color = Color3D.rgb(255, 170, 100),
                falloff = 6f,
                transform = Transform(translation = Vec3(-2f, 2f, 2f)),
            )
        }
        Text(
            text = "Orbit: drag · Pan/zoom: two fingers · Selected: ${selected ?: "none"}",
            modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
        )
    }
}
