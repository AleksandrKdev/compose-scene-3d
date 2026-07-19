package dev.composescene3d.sample.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.composescene3d.compose.Scene3D
import dev.composescene3d.compose.rememberSceneController
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.Quaternion
import dev.composescene3d.core.ShadowMap3D
import dev.composescene3d.core.Transform
import dev.composescene3d.core.TexturedMaterial
import dev.composescene3d.core.TextureSource
import dev.composescene3d.core.Vec3
import dev.composescene3d.web.WebRenderer
import dev.composescene3d.web.WebViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        MaterialTheme {
            val renderer = remember {
                WebRenderer(
                    onModelError = { source, error ->
                        println("ComposeScene3D model $source failed: ${error.message}")
                    },
                    onTextureError = { source, error ->
                        println("ComposeScene3D texture $source failed: ${error.message}")
                    },
                )
            }
            val controller = rememberSceneController(renderer)
            DisposableEffect(Unit) { onDispose(renderer::close) }

            Box(Modifier.fillMaxSize()) {
                WebViewport(renderer)
                Scene3D(controller) {
                    directionalLight(
                        key = "sun",
                        intensity = 45_000f,
                        shadow = ShadowMap3D(
                            mapSize = 1024,
                            constantBias = 0.0035f,
                            normalBias = 1f,
                        ),
                    )
                    pointLight(
                        key = "blue-fill",
                        intensity = 240_000f,
                        color = Color3D(0.18f, 0.42f, 1f),
                        falloff = 8f,
                        transform = Transform(translation = Vec3(-3f, 2.5f, 2f)),
                    )
                    spotLight(
                        key = "warm-spot",
                        intensity = 280_000f,
                        direction = Vec3(0f, -1f, -0.35f),
                        color = Color3D(1f, 0.42f, 0.12f),
                        falloff = 10f,
                        innerConeRadians = 0.32f,
                        outerConeRadians = 0.62f,
                        transform = Transform(translation = Vec3(3f, 4f, 3f)),
                    )
                    group(
                        key = "assembly",
                        transform = Transform(rotation = Quaternion(0f, 0.2f, 0f, 0.98f)),
                    ) {
                        box(
                            key = "cube",
                            size = Vec3(2f, 2f, 2f),
                            color = Vec3(0.16f, 0.58f, 0.96f),
                            transform = Transform(translation = Vec3(-2.4f, 0f, 0f)),
                        )
                        sphere(
                            key = "sphere",
                            radius = 1.1f,
                            material = PbrMaterial(
                                baseColor = Color3D(0.96f, 0.36f, 0.14f),
                                roughness = 0.35f,
                            ),
                            transform = Transform(translation = Vec3(2.4f, 0f, 0f)),
                        )
                    }
                    model(
                        key = "duck",
                        source = ModelSource.Resource("duck.glb"),
                        transform = Transform(translation = Vec3(0f, -1f, 0f)),
                    )
                    model(
                        key = "external-gltf",
                        source = ModelSource.Resource("external-triangle.gltf"),
                        transform = Transform(
                            translation = Vec3(0f, 0.6f, -1f),
                            scale = Vec3(0.45f, 0.45f, 0.45f),
                        ),
                    )
                    plane(
                        key = "floor",
                        width = 10f,
                        depth = 10f,
                        material = TexturedMaterial(
                            baseColorTexture = TextureSource.Resource("checker.svg"),
                            normalTexture = TextureSource.Resource("normal-map.svg"),
                            metallicRoughnessTexture = TextureSource.Resource("metallic-roughness.svg"),
                            ambientOcclusionTexture = TextureSource.Resource("ambient-occlusion.svg"),
                            normalScale = 0.35f,
                            ambientOcclusionStrength = 0.65f,
                            roughness = 0.85f,
                        ),
                        castShadows = false,
                        transform = Transform(translation = Vec3(0f, -1.3f, 0f)),
                    )
                }
            }
        }
    }
}
