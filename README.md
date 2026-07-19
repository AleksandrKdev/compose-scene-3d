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
  PBR shaders and shadows remain future work.
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

## Roadmap

1. Add Web directional and spot shadows.
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
