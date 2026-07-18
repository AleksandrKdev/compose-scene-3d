import dev.composescene3d.compose.SceneCameraGestureController
import dev.composescene3d.core.BoxNode
import dev.composescene3d.core.NodeKey
import dev.composescene3d.core.SceneDescription
import dev.composescene3d.filament.FilamentRenderer

/** Compile-only check that all published modules and their public APIs can be consumed externally. */
fun publishedApiSmokeTest() {
    val scene = SceneDescription(listOf(BoxNode(NodeKey("published-box"))))
    val renderer = FilamentRenderer()
    val gestures: SceneCameraGestureController? = null

    check(scene.nodes.isNotEmpty())
    check(renderer.capabilities.primitiveGeometry)
    check(gestures == null)
    renderer.close()
}
