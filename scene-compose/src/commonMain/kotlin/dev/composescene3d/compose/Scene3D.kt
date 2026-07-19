package dev.composescene3d.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import dev.composescene3d.core.DirectionalLightNode
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.SceneController
import dev.composescene3d.core.SceneDescription
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3

class SceneScope internal constructor() {
    private val nodes = mutableListOf<SceneNode>()

    fun model(
        key: String,
        source: ModelSource,
        transform: Transform = Transform(),
        visible: Boolean = true,
    ) {
        nodes += ModelNode(NodeKey(key), source, transform, visible)
    }

    /**
     * Adds a transform group. Every node declared in [content] uses coordinates local to this
     * group and inherits its translation, rotation and scale.
     */
    fun group(
        key: String,
        transform: Transform = Transform(),
        content: SceneScope.() -> Unit,
    ) {
        val children = SceneScope().apply(content).nodes.toList()
        nodes += GroupNode(NodeKey(key), children, transform)
    }

    fun box(
        key: String,
        size: Vec3 = Vec3.One,
        color: Vec3 = Vec3(0.7f, 0.7f, 0.7f),
        transform: Transform = Transform(),
    ) {
        nodes += BoxNode(NodeKey(key), size, color, transform)
    }

    fun sphere(
        key: String,
        radius: Float = 0.5f,
        rings: Int = 16,
        segments: Int = 32,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
    ) {
        nodes += SphereNode(NodeKey(key), radius, rings, segments, material, transform)
    }

    fun plane(
        key: String,
        width: Float = 1f,
        depth: Float = 1f,
        doubleSided: Boolean = true,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
    ) {
        nodes += PlaneNode(NodeKey(key), width, depth, doubleSided, material, transform)
    }

    fun cylinder(
        key: String,
        radius: Float = 0.5f,
        height: Float = 1f,
        segments: Int = 32,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
    ) {
        nodes += CylinderNode(NodeKey(key), radius, height, segments, material, transform)
    }

    fun directionalLight(
        key: String,
        intensity: Float,
        color: Vec3 = Vec3.One,
        transform: Transform = Transform(),
    ) {
        nodes += DirectionalLightNode(NodeKey(key), intensity, color, transform)
    }

    fun pointLight(
        key: String,
        intensity: Float,
        color: Color3D = Color3D.White,
        falloff: Float = 10f,
        transform: Transform = Transform(),
    ) {
        nodes += PointLightNode(NodeKey(key), intensity, color, falloff, transform)
    }

    fun spotLight(
        key: String,
        intensity: Float,
        direction: Vec3 = Vec3(0f, -1f, 0f),
        color: Color3D = Color3D.White,
        falloff: Float = 10f,
        innerConeRadians: Float = 0.5f,
        outerConeRadians: Float = 0.6f,
        transform: Transform = Transform(),
    ) {
        nodes += SpotLightNode(
            NodeKey(key), intensity, direction, color, falloff,
            innerConeRadians, outerConeRadians, transform,
        )
    }

    internal fun build(): SceneDescription = SceneDescription(nodes.toList())
}

@Composable
fun rememberSceneController(renderer: SceneRenderer): SceneController =
    remember(renderer) { SceneController(renderer) }

@Composable
fun Scene3D(
    controller: SceneController,
    content: SceneScope.() -> Unit,
) {
    val description = SceneScope().apply(content).build()

    SideEffect {
        controller.submit(description)
    }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }
}
