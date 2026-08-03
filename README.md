# ComposeScene3D

An experimental retained-mode 3D scene API for Kotlin and Compose Multiplatform.

The project deliberately separates the public scene model from the renderer. Compose describes
the desired scene, the reconciler emits a small set of backend commands, and GPU resources remain
owned by a renderer implementation rather than by recomposition.

## Status

Early architecture prototype with working Filament primitive and GLB rendering on Android,
Desktop and iOS/Metal, plus an independent WebGL2 renderer for Web/Wasm. A stable public
release is not available yet.

Current release coordinates: `io.github.aleksandrkdev:*:0.1.0-alpha02`.

Source repository: [AleksandrKdev/compose-scene-3d](https://github.com/AleksandrKdev/compose-scene-3d).

## Modules

- `scene-core`: immutable scene descriptions, retained scene reconciliation and renderer commands.
- `scene-compose`: a Compose DSL that produces scene descriptions without running a frame loop
  through Compose state.
- `renderer-filament`: a retained adapter over Filament KMP; Filament types stay private to the
  backend implementation. Models with the same cache key share one imported GPU asset while
  retaining independent instances and transforms. It provides orbit/pan/zoom interaction and maps
  Filament picking results back to stable `NodeKey` values. Box, sphere, plane and cylinder
  primitives use backend-neutral PBR material parameters.
- `renderer-web`: an independent WebGL2 Wasm renderer for primitives, indexed custom meshes,
  nested transforms and the shared orbit/pan/zoom camera. It uses GPU vertex/index buffers and a
  depth buffer. Base-color textures load asynchronously from resources, URLs or encoded bytes and
  are cached by `TextureAssetKey`. It loads binary (`.glb`) and JSON (`.gltf`) glTF 2.0 models;
  its PBR shader supports directional, point and spot lighting plus directional and spot PCF shadow
  maps. Camera projection runs in the vertex shader. Per-mesh WebGL buffers persist across renders,
  are shared by all active passes and skip uploads while world-space geometry is unchanged. CPU
  mesh batches are likewise reused until the scene or asynchronously loaded model set changes.
  Imported glTF materials honor base-color factors, `OPAQUE`/`MASK`/`BLEND`, `alphaCutoff` and
  `doubleSided`.
- `renderer-testkit`: an internal backend-neutral conformance harness for retained commands,
  lifecycle behavior and capability declarations. New renderers must pass the same contract.
- `samples/android-app`, `samples/desktop-app`, `samples/ios-app` and `samples/web-app`:
  interactive platform samples.

## Design principles

- Public API lives in `commonMain`.
- Stable node keys define identity; list position does not.
- Reconciliation changes retained nodes in place where possible.
- Backend capabilities are explicit instead of pretending every GPU API is identical.
- Renderer handles and native resources never leak into application state.
- Animation and per-frame transforms belong to the renderer/frame loop, not recomposition.

## Imported model parts

Android and iOS expose the named node hierarchy of each loaded GLB/glTF instance without leaking
Filament entities into common code. Keys are stable paths; duplicate sibling names receive a
numeric suffix. Query an already loaded model or subscribe while it loads:

```kotlin
val subscription = controller.observeModelParts { modelKey, parts ->
    if (modelKey == NodeKey("gearbox")) {
        parts.forEach { part ->
            println("${part.key.value}: ${part.name}, renderable=${part.renderable}")
        }
    }
}

val currentParts = controller.modelParts(NodeKey("gearbox"))
subscription.dispose()
```

The callback receives an empty list when the model instance is removed. A part or an entire
subtree can be hidden, and a local transform offset can be applied without losing the transform
authored in the model file:

```kotlin
Scene3D(controller) {
    model(
        key = "gearbox",
        source = ModelSource.Resource("files/gearbox.glb"),
        partOverrides = mapOf(
            ModelPartKey("Gearbox/Shaft") to ModelPartOverride(
                transformOffset = Transform(translation = Vec3(0.4f, 0f, 0f)),
            ),
            ModelPartKey("Gearbox/Cover") to ModelPartOverride(visible = false),
            ModelPartKey("Gearbox/Gear") to ModelPartOverride(
                material = HighlightMaterial(Color3D.rgb(255, 140, 40)),
                outline = ModelPartOutline(color = Color3D.Cyan, width = 0.015f),
            ),
        ),
    )
}
```

Overrides are declarative and work on Android and iOS. Hiding a non-renderable hierarchy node
hides its renderable descendants as well. A material assigned to a hierarchy node is inherited by
its descendants, while a more specific child override wins. Remove the override to restore every
original glTF primitive material. `HighlightMaterial` provides a high-contrast, lighting-independent
selection fill on Android and iOS.

`ModelPartOutline` adds a true geometry-expanded silhouette: a second lightweight glTF instance
draws only expanded back faces of the selected subtree, while the original surface remains visible.
The width is specified in scene units and should be tuned to the model scale.

Define an exploded assembly once and animate only its progress. Existing visibility, material,
outline, rotation and scale overrides are preserved when translations are merged:

```kotlin
val explodedView = remember {
    ExplodedView3D(
        listOf(
            ExplodedPart3D(ModelPartKey("Gearbox/Cover"), Vec3(0f, 0.8f, 0f)),
            ExplodedPart3D(ModelPartKey("Gearbox/Shaft"), Vec3(1.2f, 0f, 0f)),
        ),
    )
}
val progress by animateFloatAsState(if (exploded) 1f else 0f)

Scene3D(controller) {
    model(
        key = "gearbox",
        source = ModelSource.Resource("files/gearbox.glb"),
        partOverrides = explodedView.overrides(progress, selectionOverrides),
    )
}
```

An entry pointing to a hierarchy node moves its complete subtree, which is useful for nested
assemblies. Translation vectors are expressed in that part's authored local coordinate system.

## Model-part annotations

Resolve any point in a model part and keep a Compose label attached while the camera or assembly
moves. Coordinates use the viewport's top-left corner and `visible` includes frustum checks:

```kotlin
var viewportSize by remember { mutableStateOf(IntSize.Zero) }
val labelPosition by rememberModelPartScreenPosition(
    controller = controller,
    anchor = ModelPartAnchor3D(
        nodeKey = NodeKey("gearbox"),
        partKey = ModelPartKey("Gearbox/Shaft"),
        localPosition = Vec3(0f, 0.2f, 0f),
    ),
    cameraState = cameraState,
    viewportWidth = viewportSize.width,
    viewportHeight = viewportSize.height,
)

Box(Modifier.fillMaxSize().onSizeChanged { viewportSize = it }) {
    FilamentViewport(renderer, cameraState = cameraState)
    labelPosition?.takeIf { it.visible }?.let { position ->
        Text(
            "Drive shaft",
            Modifier.offset { IntOffset(position.x.toInt(), position.y.toInt()) },
        )
    }
}
```

## Example

```kotlin
val controller = rememberSceneController(renderer)
val assemblyRotation = Quaternion(0f, sin(angle / 2f), 0f, cos(angle / 2f))

Scene3D(controller) {
    group(
        key = "product-assembly",
        transform = Transform(rotation = assemblyRotation),
    ) {
        sphere(
            key = "accent",
            material = PbrMaterial(
                baseColor = Color3D(0.9f, 0.55f, 0.12f),
                metallic = 1f,
                roughness = 0.2f,
            ),
            transform = Transform(translation = Vec3(-1.5f, 0f, 0f)),
        )
        model(
            key = "product",
            source = ModelSource.Resource("files/product.glb"),
            transform = Transform(scale = Vec3(0.5f, 0.5f, 0.5f)),
        )
    }
    directionalLight(key = "sun", intensity = 50_000f)
    pointLight(
        key = "warm-fill",
        intensity = 1_500f,
        color = Color3D.rgb(255, 170, 100),
        transform = Transform(translation = Vec3(-2f, 2f, 2f)),
    )
}
```

`group { ... }` creates a real scene-graph node. Child transforms are local to their parent;
translation, quaternion rotation and scale are inherited through any number of nested groups.
Node keys remain unique across the entire tree, and picking still reports the leaf node key.

Custom indexed triangle meshes use portable CPU-side arrays. Positions and normals contain three
floats per vertex; UVs contain two and are required for `TexturedMaterial`. Indices use
counter-clockwise triangle winding when viewed from the front:

```kotlin
val triangle = Geometry3D(
    positions = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
    indices = intArrayOf(0, 1, 2),
    normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
    uvs = floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f),
)

mesh(
    key = "triangle",
    geometry = triangle,
    material = PbrMaterial(baseColor = Color3D.Magenta),
)
```

The Filament backend calculates the bounding box and tangent-frame quaternions, uploads immutable
vertex/index buffers, participates in scene hierarchy and picking, and releases native resources
when geometry leaves the composition.

Lines and arrows are solid, pickable 3D geometry and work in local group coordinates. Their
thickness is measured in scene units, so they can be used for callout leaders, dimensions, axes,
force vectors and animated instructional overlays on Android, iOS and Web:

```kotlin
line(
    key = "callout-leader",
    start = Vec3(0f, 0.4f, 0f),
    end = Vec3(1.2f, 1.1f, 0f),
    radius = 0.01f,
    material = UnlitMaterial(Color3D.Cyan),
)
arrow(
    key = "force-vector",
    start = Vec3.Zero,
    end = Vec3(0f, 1.5f, 0f),
    headLength = 0.2f,
    material = UnlitMaterial(Color3D.Yellow),
)
```

Because they are regular scene nodes, transforms, parent groups, picking and optional shadows
behave exactly like other primitives. `UnlitMaterial` is the default so annotations stay legible
independently of scene lighting; PBR and other portable materials are also supported.

Linear engineering dimensions combine two inward-facing arrows with extension lines. The measured
points and offset use local scene coordinates, while `labelAnchor` provides the local midpoint for
a Compose text label:

```kotlin
val widthDimension = LinearDimensionNode(
    key = NodeKey("housing-width"),
    start = Vec3(-1f, 0f, 0f),
    end = Vec3(1f, 0f, 0f),
    offset = Vec3(0f, 0.5f, 0f),
)

linearDimension(
    key = widthDimension.key.value,
    start = widthDimension.start,
    end = widthDimension.end,
    offset = widthDimension.offset,
)
val labelPosition by rememberDimensionScreenPosition(
    widthDimension, cameraState, viewportWidth, viewportHeight,
)
// Draw "120 mm" as regular Compose UI at labelPosition.x / labelPosition.y.
```

Radius, arrow-head dimensions, extension gap/overshoot, segment count, material, and transforms are
configurable. The node remains domain-neutral: unit formatting, diameter symbols, tolerances, and
lesson text belong to the consuming application.

Radial and angular measurements use the same portable geometry and label-anchor model:

```kotlin
radialDimension("bore-radius", center = Vec3.Zero, edge = Vec3(0.5f, 0f, 0f))
angularDimension(
    "keyway-angle", center = Vec3.Zero,
    startDirection = Vec3(1f, 0f, 0f), endDirection = Vec3(0f, 1f, 0f),
    arcRadius = 0.7f,
)
```

`RadialDimensionNode` and `AngularDimensionNode` expose `labelAnchor` for app-owned Compose text.
`rememberDimensionScreenPosition` reacts to orbit, pan, zoom, projection, and viewport changes without
polling the renderer. `rememberScreenPosition(SceneAnchor3D(...))` provides the same mechanism for
arbitrary instructional labels. Pass `worldTransform` when a dimension also inherits a parent-group
transform.

Dimension text and crowded overlays can stay portable and deterministic:

```kotlin
val text = formatDimensionValue(
    12.5,
    DimensionTextFormat(
        decimals = 2, unit = "mm", prefix = "⌀",
        tolerance = DimensionTolerance(upper = 0.1, lower = -0.05),
    ),
)
val positioned = layoutScreenLabels(labels, viewportWidth, viewportHeight)
```

The layout keeps labels inside the viewport, resolves overlaps by priority and camera depth, and
marks labels as hidden when no collision-free position is available. Compose remains responsible
for measuring and drawing the actual text.

Interactive annotations share selection and accessibility behavior on Android and iOS:

```kotlin
val selection = rememberAnnotationSelectionState()
val bearing = SceneAnnotation3D(
    key = "bearing",
    anchor = SceneAnchor3D(Vec3(0f, 0.7f, 0f)),
    label = "Bearing",
    contentDescription = "Deep groove ball bearing, opens construction details",
)

Text(
    bearing.label,
    Modifier.sceneAnnotationInteraction(bearing, selection) { openDetails(it.key) },
)
```

The modifier exposes button role, selected/disabled state, and descriptions to TalkBack and
VoiceOver. Visual styling of selected annotations stays application-owned.

Programmatic camera focus frames a point or bounding sphere on every backend:

```kotlin
scope.launch {
    cameraState.focusOn(
        CameraFocus3D(center = bearingCenter, radius = bearingRadius, padding = 1.3f),
        durationMillis = 700,
    )
}
```

Perspective distance is derived from vertical FOV; orthographic focus adjusts `verticalSize`.
`animateTo` supports arbitrary camera destinations and returns `false` when a user orbit, pan, zoom,
or reset cancels the flight.

Visibility can be controlled for an entire articulated subtree without removing its declarations:

```kotlin
group("internal-parts", visible = showInternals) {
    model("bearing", bearingSource)
    model("shaft", shaftSource)
}
```

Nested groups inherit hidden state on Filament and Web, allowing lesson steps to reveal assemblies
without application-side filtering or unstable node identities.

Declarative materials can be faded without changing their source definition:

```kotlin
mesh("cover", coverGeometry, material = OpacityMaterial(coverMaterial, opacity = 0.35f))
```

`OpacityMaterial` uses transparent rendering on Filament and Web. `TexturedMaterial` retains its
albedo texture and PBR metallic/roughness factors; color materials retain their shading parameters.
Imported glTF material fading requires the forthcoming custom glTF material-provider path.

Opacity can also be inherited by a complete declarative subtree; nested values multiply:

```kotlin
group("housing", opacity = 0.35f) {
    mesh("cover", coverGeometry, material = coverMaterial)
    group("fasteners", opacity = 0.5f) { /* effective opacity: 0.175 */ }
}
```

World-space clipping planes provide dynamic section views on the Filament Android/iOS backend.
`ClippedPbrMaterial` accepts one to three half-spaces and can be attached to primitives, custom
meshes, or imported parts through `ModelPartOverride.material`:

```kotlin
val section = ClippedPbrMaterial(
    planes = listOf(
        ClippingPlane3D(
            normal = Vec3(1f, 0f, 0f),
            offset = sectionPosition,
        ),
    ),
    baseColor = Color3D(0.8f, 0.35f, 0.12f),
    metallic = 0.25f,
    roughness = 0.45f,
)

mesh("housing-section", geometry = housing, material = section)
```

The plane retains points where `dot(normal, worldPosition) >= offset`; set `keepPositive = false`
to retain the opposite side. Changing `offset` from Compose state animates or scrubs the cut. The
Shader-clipped primitives and imported parts currently render an open section surface.

For custom geometry, `sectionedMesh` performs the cut on the CPU and generates a real cap mesh with
its own material and planar UV coordinates:

```kotlin
sectionedMesh(
    key = "housing-section",
    geometry = housingGeometry,
    plane = ClippingPlane3D(Vec3(1f, 0f, 0f), offset = sectionPosition),
    material = PbrMaterial(baseColor = Color3D.Blue),
    capMaterial = HatchMaterial(
        backgroundColor = Color3D(0.95f, 0.75f, 0.2f),
        lineColor = Color3D(0.12f, 0.1f, 0.06f),
        spacing = 0.09f,
        lineWidth = 0.009f,
        angleRadians = 0.7853982f,
    ),
)
```

The cap implementation reconstructs actual cut-edge loops, triangulates concave contours, handles
multiple disconnected regions, and preserves nested contours as holes. This covers hollow shafts,
tubes, and housings with internal openings. Generated planar UVs make the result ready for hatch
materials.

`HatchMaterial` is the default cap material. Its pattern is generated directly by the Android/iOS
Filament shader from the cap's planar UVs, so no texture asset is required. Angle, spacing, line
width, background, and line colors are declarative and can be animated from Compose state. Use
opposite angles for adjacent parts to keep an assembly section visually unambiguous.

`Color3D` distinguishes sRGB input from linear-sRGB values and supports RGB/RGBA/ARGB factories
and named colors. Primitive materials can be `PbrMaterial`, `UnlitMaterial`, `EmissiveMaterial`,
`TexturedMaterial` or `TransparentMaterial`.

```kotlin
sphere(
    key = "glass",
    material = TransparentMaterial(
        color = Color3D(0.2f, 0.65f, 1f, alpha = 0.35f),
        roughness = 0.12f,
    ),
)
```

Transparent colors are converted to linear-sRGB and premultiplied by alpha before reaching the
shader. The bundled blended material is compiled for all Filament backends with the exact `matc`
version used by the runtime dependency.

Texture data can come from common resources, URLs or in-memory bytes without exposing a Filament
type to shared code:

```kotlin
plane(
    key = "floor",
    material = TexturedMaterial(
        baseColorTexture = TextureSource.Resource("files/floor-albedo.png"),
        normalTexture = TextureSource.Resource("files/floor-normal.png"),
        metallicRoughnessTexture = TextureSource.Resource("files/floor-mr.png"),
        emissiveTexture = TextureSource.Resource("files/floor-emissive.png"),
        ambientOcclusionTexture = TextureSource.Resource("files/floor-ao.png"),
        normalScale = 1f,
        emissiveColor = Color3D.White,
        emissiveIntensity = 0.5f,
        ambientOcclusionStrength = 1f,
        roughness = 0.9f,
    ),
)
```

Metallic-roughness maps follow the glTF convention: roughness is read from the green channel and
metallic from blue. Albedo and emissive maps decode as sRGB; normal, metallic-roughness and AO maps
remain linear data. Missing optional maps do not consume placeholder application assets.

The WebGL2 backend implements direct-light metallic/roughness PBR with a GGX specular BRDF,
tone mapping and perspective-correct base-color texture sampling. It consumes the first
`DirectionalLightNode` in the scene and supports all three `TextureSource` variants, generates
mipmaps after browser image decoding, and redraws the viewport when asynchronous loading completes.
Normal maps use a derivative-based tangent frame, so custom geometry does not need explicit tangent
attributes. Metallic-roughness (glTF green/blue channels), emissive and AO maps are supported on
Web as well as Filament. Web lighting supports one directional light plus up to four point and four
spot lights. Positional lights honor nested transforms, distance falloff and spot cone angles.

Web models accept the same `ModelSource.Resource`, `Url` and `Bytes` variants. The current loader
supports GLB 2.0 JSON/BIN chunks, triangle primitives, indexed or non-indexed accessors, interleaved
buffer views, node TRS/matrices and embedded base-color images. Resource and URL sources may also
be JSON `.gltf` files with one external or data-URI buffer and external images. Loader failures can
be observed through `WebRenderer(onModelError = ..., onTextureError = ...)`. Multiple buffers,
sparse accessors, morph targets, skins and animation are intentionally rejected or ignored for now.

For Filament, `TextureSource.Bytes` works with the default renderer. Resource and URL sources use
an application-provided `TextureByteLoader`, following the same pattern as `ModelByteLoader`.

Preprocessed KTX1 cubemaps provide specular reflections, diffuse spherical-harmonic irradiance and
an optional visible skybox:

```kotlin
val environment = EnvironmentMap(
    reflections = TextureSource.Resource("files/studio_ibl.ktx"),
    skybox = TextureSource.Resource("files/studio_skybox.ktx"),
    intensity = 18_000f,
)

FilamentViewport(renderer = renderer, environment = environment)
```

To select an individual named glTF/GLB part on Android or iOS, use the picking overload. Primitive
geometry produces a result without `modelPartKey`; imported model renderables include the stable
part key exposed by `modelParts`:

```kotlin
FilamentViewport(
    renderer = renderer,
    onPicked = { result ->
        selectedNode = result?.nodeKey
        selectedPart = result?.modelPartKey
    },
)
```

Generate both files offline with Filament `cmgen -f ktx -x output environment.hdr`. Runtime HDR
conversion is intentionally excluded: preprocessing produces smaller assets and deterministic
results on Android, Desktop and iOS.

Shadow participation is configured independently from the view-wide technique and the light's
shadow map:

```kotlin
FilamentViewport(
    renderer = renderer,
    shadows = ShadowTechnique3D.Pcf,
)

Scene3D(controller) {
    sphere(key = "caster", castShadows = true, receiveShadows = true)
    plane(key = "ground", castShadows = false, receiveShadows = true)
    directionalLight(
        key = "sun",
        intensity = 100_000f,
        shadow = ShadowMap3D(
            mapSize = 2048,
            cascades = 2,
            contactShadows = true,
            bulbRadius = 0.05f,
        ),
    )
}
```

`Pcf`, `Pcfd`, `Vsm`, `Dpcf` and `Pcss` are portable view techniques. Directional and spot lights
can own a `ShadowMap3D`; point-light shadows are deliberately unsupported because Filament does
not implement the required cubemap shadow maps. Passing `shadows = null` disables the view-wide
shadow pass.

`Dpcf` and `Pcss` additionally require compatible VSM receiver variants in every material,
including materials embedded in loaded GLB assets. Use `Pcf` as the portable default when asset
provenance is unknown.

On Web, the first shadow-enabled directional and spot lights each get a depth map with 3x3 PCF.
The directional map uses an orthographic projection and the spot map follows its position,
direction, outer cone and falloff with a perspective projection. `mapSize` is honored up to 2048,
and node-level `castShadows`/`receiveShadows` flags are supported. The directional frustum fits the
current world-space geometry with padding, texel-grid stabilization and polygon-offset acne
protection. Cascades, contact shadows and the other filtering techniques still fall back to PCF.

## Roadmap

1. Add Web glTF sampler settings and texture-coordinate set selection.
2. Continue glTF feature coverage and optimize Web GPU resource submission.
3. Stabilize the public API based on cross-backend experience.

## Running the samples

Android:

```shell
./gradlew :samples:android-app:assembleDebug
```

Desktop requires JDK 22+ because Filament uses Project Panama FFM bindings:

```shell
./gradlew :samples:desktop-app:run
```

iOS: open `samples/ios-app/iosApp.xcodeproj`, choose an arm64 simulator and run the `iosApp` scheme.
Xcode builds and embeds the Kotlin framework automatically.

Web/Wasm:

```shell
./gradlew :samples:web-app:wasmJsBrowserDevelopmentRun
```

The command starts the webpack development server and opens the sample in a browser. Drag to
orbit, use the mouse wheel to zoom, and use a secondary-button drag to pan.

## Continuous integration

The macOS GitHub Actions workflow runs common/JVM tests, builds the Android sample, compiles the
Desktop sample, links simulator and device iOS frameworks, and builds the complete SwiftUI host.
The workflow deliberately targets arm64 because Filament KMP publishes an arm64 iOS Simulator
artifact.

## Local Maven alpha

Publish all library modules to a repository under `build/maven-alpha`:

```shell
./gradlew publishAllPublicationsToLocalAlphaRepository
```

Consumer projects can then use:

```kotlin
repositories {
    maven { url = uri("/path/to/ComposeScene3D/build/maven-alpha") }
}

dependencies {
    implementation("io.github.aleksandrkdev:scene-compose:0.1.0-alpha03-SNAPSHOT")
    implementation("io.github.aleksandrkdev:renderer-filament:0.1.0-alpha03-SNAPSHOT")
}
```

## Binary API compatibility

The published modules keep JVM and KLIB ABI baselines under their `api/` directories.
`./gradlew checkKotlinAbi` detects accidental public API changes; intentionally accepted changes
are recorded with `./gradlew updateKotlinAbi` after reviewing the diff.

## GitHub Packages

The `Publish alpha` workflow publishes every KMP variant to this repository's GitHub Packages
registry on manual dispatch or a `v*` tag. It uses the workflow `GITHUB_TOKEN`. After publication,
the workflow compiles an independent consumer project from `verification/published-consumer`,
resolving the modules back from GitHub Packages rather than from this build.

GitHub Packages requires authentication when consuming Maven packages. Add the repository and use
a GitHub personal access token with `read:packages` permission:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/AleksandrKdev/compose-scene-3d")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
    google()
}

dependencies {
    implementation("dev.composescene3d:scene-compose:0.1.0-alpha01")
    implementation("dev.composescene3d:renderer-filament:0.1.0-alpha01")
}
```

Keep credentials outside the project, for example in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

## Maven Central

Version `0.1.0-alpha02` is available from Maven Central, the primary public repository. Consumers
only need `mavenCentral()` and do not need GitHub credentials:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.aleksandrkdev:scene-compose:0.1.0-alpha02")
    implementation("io.github.aleksandrkdev:renderer-filament:0.1.0-alpha02")
}
```

Maintainers publish tags through the `Publish Maven Central` workflow. It expects Central Portal
user-token secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`, plus the armored private
key `SIGNING_KEY` and its `SIGNING_PASSWORD`.

For Android-only development Android Studio may use its bundled JDK 21. Do not configure a
project-wide Gradle daemon JVM criterion for Java 22: that can prevent initial sync before Gradle's
toolchain resolver is loaded. Select an installed JDK 22 as the Gradle JDK only when running the
Desktop sample.

## License

Apache-2.0.
