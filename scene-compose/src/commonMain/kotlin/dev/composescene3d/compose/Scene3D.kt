package dev.composescene3d.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import dev.composescene3d.core.DirectionalLightNode
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.CylinderNode
import dev.composescene3d.core.LineNode
import dev.composescene3d.core.LinearDimensionNode
import dev.composescene3d.core.RadialDimensionNode
import dev.composescene3d.core.AngularDimensionNode
import dev.composescene3d.core.ArrowNode
import dev.composescene3d.core.Color3D
import dev.composescene3d.core.Material3D
import dev.composescene3d.core.Geometry3D
import dev.composescene3d.core.MeshNode
import dev.composescene3d.core.SectionedMeshNode
import dev.composescene3d.core.ClippingPlane3D
import dev.composescene3d.core.HatchMaterial
import dev.composescene3d.core.ModelNode
import dev.composescene3d.core.ModelPartKey
import dev.composescene3d.core.ModelPartOverride
import dev.composescene3d.core.ModelSource
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.PbrMaterial
import dev.composescene3d.core.UnlitMaterial
import dev.composescene3d.core.PlaneNode
import dev.composescene3d.core.PointLightNode
import dev.composescene3d.core.SceneController
import dev.composescene3d.core.SceneDescription
import dev.composescene3d.core.GroupNode
import dev.composescene3d.core.SceneNode
import dev.composescene3d.core.SceneRenderer
import dev.composescene3d.core.SphereNode
import dev.composescene3d.core.SpotLightNode
import dev.composescene3d.core.ShadowMap3D
import dev.composescene3d.core.Transform
import dev.composescene3d.core.Vec3

class SceneScope internal constructor() {
    private val nodes = mutableListOf<SceneNode>()

    fun model(
        key: String,
        source: ModelSource,
        transform: Transform = Transform(),
        visible: Boolean = true,
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
        partOverrides: Map<ModelPartKey, ModelPartOverride> = emptyMap(),
    ) {
        nodes += ModelNode(
            NodeKey(key), source, transform, visible, castShadows, receiveShadows, partOverrides,
        )
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
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += BoxNode(NodeKey(key), size, color, transform, castShadows, receiveShadows)
    }

    fun sphere(
        key: String,
        radius: Float = 0.5f,
        rings: Int = 16,
        segments: Int = 32,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += SphereNode(
            NodeKey(key), radius, rings, segments, material, transform, castShadows, receiveShadows,
        )
    }

    fun plane(
        key: String,
        width: Float = 1f,
        depth: Float = 1f,
        doubleSided: Boolean = true,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += PlaneNode(
            NodeKey(key), width, depth, doubleSided, material, transform, castShadows, receiveShadows,
        )
    }

    fun cylinder(
        key: String,
        radius: Float = 0.5f,
        height: Float = 1f,
        segments: Int = 32,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += CylinderNode(
            NodeKey(key), radius, height, segments, material, transform, castShadows, receiveShadows,
        )
    }

    fun line(
        key: String,
        start: Vec3,
        end: Vec3,
        radius: Float = 0.01f,
        segments: Int = 12,
        material: Material3D = UnlitMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = false,
        receiveShadows: Boolean = false,
    ) {
        nodes += LineNode(
            NodeKey(key), start, end, radius, segments, material, transform,
            castShadows, receiveShadows,
        )
    }

    fun arrow(
        key: String,
        start: Vec3,
        end: Vec3,
        shaftRadius: Float = 0.01f,
        headRadius: Float = 0.035f,
        headLength: Float = 0.12f,
        segments: Int = 16,
        material: Material3D = UnlitMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = false,
        receiveShadows: Boolean = false,
    ) {
        nodes += ArrowNode(
            NodeKey(key), start, end, shaftRadius, headRadius, headLength, segments,
            material, transform, castShadows, receiveShadows,
        )
    }

    fun linearDimension(
        key: String,
        start: Vec3,
        end: Vec3,
        offset: Vec3,
        radius: Float = 0.008f,
        arrowHeadRadius: Float = 0.028f,
        arrowHeadLength: Float = 0.09f,
        extensionGap: Float = 0.025f,
        extensionOvershoot: Float = 0.04f,
        segments: Int = 12,
        material: Material3D = UnlitMaterial(Color3D.Yellow),
        transform: Transform = Transform(),
        castShadows: Boolean = false,
        receiveShadows: Boolean = false,
    ) {
        nodes += LinearDimensionNode(
            NodeKey(key), start, end, offset, radius, arrowHeadRadius, arrowHeadLength,
            extensionGap, extensionOvershoot, segments, material, transform,
            castShadows, receiveShadows,
        )
    }

    fun radialDimension(
        key: String, center: Vec3, edge: Vec3, labelOffset: Float = 0.25f,
        radius: Float = 0.008f, arrowHeadRadius: Float = 0.028f,
        arrowHeadLength: Float = 0.09f, segments: Int = 12,
        material: Material3D = UnlitMaterial(Color3D.Yellow), transform: Transform = Transform(),
        castShadows: Boolean = false, receiveShadows: Boolean = false,
    ) {
        nodes += RadialDimensionNode(NodeKey(key), center, edge, labelOffset, radius, arrowHeadRadius, arrowHeadLength, segments, material, transform, castShadows, receiveShadows)
    }

    fun angularDimension(
        key: String, center: Vec3, startDirection: Vec3, endDirection: Vec3, arcRadius: Float,
        radius: Float = 0.008f, arrowHeadRadius: Float = 0.028f,
        arrowHeadLength: Float = 0.09f, arcSegments: Int = 24, radialOvershoot: Float = 0.04f,
        material: Material3D = UnlitMaterial(Color3D.Yellow), transform: Transform = Transform(),
        castShadows: Boolean = false, receiveShadows: Boolean = false,
    ) {
        nodes += AngularDimensionNode(NodeKey(key), center, startDirection, endDirection, arcRadius, radius, arrowHeadRadius, arrowHeadLength, arcSegments, radialOvershoot, material, transform, castShadows, receiveShadows)
    }

    fun mesh(
        key: String,
        geometry: Geometry3D,
        material: Material3D = PbrMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += MeshNode(NodeKey(key), geometry, material, transform, castShadows, receiveShadows)
    }

    fun sectionedMesh(
        key: String,
        geometry: Geometry3D,
        plane: ClippingPlane3D,
        material: Material3D = PbrMaterial(),
        capMaterial: Material3D = HatchMaterial(),
        transform: Transform = Transform(),
        castShadows: Boolean = true,
        receiveShadows: Boolean = true,
    ) {
        nodes += SectionedMeshNode(
            NodeKey(key), geometry, plane, material, capMaterial, transform,
            castShadows, receiveShadows,
        )
    }

    fun directionalLight(
        key: String,
        intensity: Float,
        color: Vec3 = Vec3.One,
        transform: Transform = Transform(),
        shadow: ShadowMap3D? = null,
    ) {
        nodes += DirectionalLightNode(NodeKey(key), intensity, color, transform, shadow)
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
        shadow: ShadowMap3D? = null,
    ) {
        nodes += SpotLightNode(
            NodeKey(key), intensity, direction, color, falloff,
            innerConeRadians, outerConeRadians, transform, shadow,
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
