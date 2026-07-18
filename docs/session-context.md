# Session context

Updated: 2026-07-18

## Goal

Create an open-source 3D library for Kotlin Multiplatform and Compose Multiplatform. It should be
suitable for embedding interactive 3D viewports into regular applications rather than trying to
replace Unity, Unreal or a complete game engine.

Primary use cases:

- glTF/GLB model viewers;
- product configurators;
- engineering and scientific visualization;
- maps and interactive scenes;
- reusable 3D viewports inside Compose UI.

`ComposeScene3D` is currently a working project name.

## Research conclusion

The Kotlin 3D ecosystem contains promising projects, but no established standard for a genuinely
shared Compose Multiplatform 3D viewport:

- Filament KMP is the strongest candidate for a shared production-quality renderer, but is young
  and its multiplatform bindings are expensive to maintain.
- SceneView is strong and stable on Android, but uses different renderers and UI frameworks across
  Android, Apple and Web, so it is not one unified Compose renderer.
- Materia is technically interesting but has an overly broad scope for an alpha project.
- KorGE is mature primarily as a 2D game engine, not as an embeddable Compose-first 3D library.
- WebGPU bindings are foundations, not complete scene/rendering libraries.

The agreed direction is therefore not to write a new PBR renderer from scratch. Build a stable,
backend-neutral KMP scene API and retained Compose DSL, initially backed by Filament.

## Architecture decisions

- Public APIs live in `commonMain` and must not expose Filament or native renderer types.
- Compose describes desired scene state; it does not own GPU objects.
- The scene is retained between recompositions.
- Stable `NodeKey` values define node identity; list position does not.
- A reconciler emits ordered `Create`, `Update` and `Remove` commands.
- Renderer implementations exclusively own native handles and GPU allocations.
- Per-frame animation, camera inertia and transforms belong to a backend frame loop, not Compose
  recomposition.
- Renderer differences must be represented through explicit capabilities.
- Controller and resource closing must be idempotent.
- Filament should be an implementation detail behind a narrow, stable API.

The intended module direction is:

```text
scene-compose
    Declarative Compose API and lifecycle
        ↓
scene-core
    Scene descriptions, reconciliation and backend contracts
        ↓
renderer-filament
    Android / iOS / Desktop / Web implementation
```

## Current implementation

Project location:

```text
/Users/darakucybala/AndroidStudioProjects/ComposeScene3D
```

Existing modules:

- `scene-core`
  - `Vec3`, `Quaternion` and `Transform`;
  - `SceneNode`, `GroupNode`, `ModelNode` and `DirectionalLightNode`;
  - `ModelSource` for resources, URLs and byte arrays;
  - validation of unique node keys;
  - `RendererCapabilities` and `SceneRenderer`;
  - `SceneCommand.Create`, `Update` and `Remove`;
  - `SceneController` with idempotent cleanup;
  - platform-independent scene reconciliation.
- `scene-compose`
  - `SceneScope` DSL;
  - `rememberSceneController`;
  - `Scene3D`;
  - renderer submission through `SideEffect`;
  - cleanup through `DisposableEffect`.

The core project and Filament renderer target Android, JVM, iOS arm64 and iOS simulator arm64.
Primitive and GLB rendering work on Android, Desktop and iOS/Metal.

Additional modules:

- `renderer-filament`
  - depends internally on Filament KMP `0.1.3-beta04`;
  - exposes `FilamentRenderer` and `FilamentViewport` without Filament types in their signatures;
  - retains scene nodes by `NodeKey`;
  - renders `BoxNode` and `DirectionalLightNode`;
  - loads GLB models with shared asset ownership and independent instances;
  - declares primitive and PBR capabilities.
- `samples/android-app`
  - builds an installable debug APK;
  - moves a box through recomposition while keeping the same key.
- `samples/desktop-app`
  - runs on JDK 22+;
  - initializes Filament's Metal backend successfully on Apple M2.
- `samples/ios-shared` and `samples/ios-app`
  - export a static Compose framework for simulator and physical arm64 devices;
  - provide a runnable SwiftUI/Xcode host with automatic Gradle framework embedding.

## Verification

The following command passed successfully:

```shell
GRADLE_USER_HOME=/Users/darakucybala/AndroidStudioProjects/ComposeScene3D/.gradle-user \
    ./gradlew :scene-core:jvmTest :scene-compose:jvmTest
```

Result:

```text
BUILD SUCCESSFUL
```

Reconciler tests cover creating, updating, reverse-order removal and duplicate-key rejection.

## Completed Filament spike

Verified on 2026-07-17:

```text
:renderer-filament:jvmTest              PASSED
:samples:android-app:assembleDebug      PASSED
:samples:desktop-app:compileKotlinJvm   PASSED
:samples:desktop-app:run                PASSED
Filament backend                        Metal
Physical device                         Apple M2
```

## Completed GLB loading milestone

- `ModelSource.Bytes` loads asynchronously through Filament KMP.
- Applications can provide `ModelByteLoader` implementations for resource and URL sources.
- Models are grouped by `ModelAssetKey`; one GPU asset backs multiple independent instances.
- Removing the final model group cancels an in-flight suspend loader and destroys its Filament
  asset through `rememberGltfAsset` lifecycle ownership.
- Model transforms update independently through stable node keys.
- Load failures are reported through `onModelError`.
- Android and Desktop samples include and render `Duck.glb`.
- Tests verify shared cache grouping and retained node updates.

## Completed camera and interaction milestone

- Backend-neutral `CameraDescription` and validated perspective/orthographic projections.
- Hoisted `SceneCameraState` in the Compose module.
- Filament camera synchronization in both directions.
- Mouse/touch orbit, pan and zoom without rebuilding the retained scene.
- Entity registration for primitives and every glTF renderable.
- Picking results map back to `NodeKey`; Filament entity IDs remain private.
- Entity mappings are removed together with retained nodes and covered by tests.
- Samples display the selected node key and interaction instructions.
- `SceneCameraGestureController` now belongs to the backend-neutral Compose layer instead of relying
  on Filament's limited gesture modifier. One pointer orbits, two pointers pan and zoom together,
  secondary-button drag pans, and the mouse wheel zooms.
- Controller tests verify that orbit preserves camera distance, pan moves eye and target together,
  and spreading two pointers zooms in.

## Completed iOS/Metal milestone

- `renderer-filament` compiles for `iosArm64` and `iosSimulatorArm64`.
- Static frameworks link for simulator and physical arm64 devices.
- The SwiftUI Xcode host builds, installs and launches on an iPhone 17 Pro arm64 simulator.
- Runtime screenshot verification confirms Metal rendering of `Duck.glb` and the retained box.
- Compose resources are synchronized into the app by the Xcode Gradle build phase.
- The SwiftUI host now removes the 3D viewport from composition on
  `UIApplicationWillResignActiveNotification` and recreates it on
  `UIApplicationDidBecomeActiveNotification`. This destroys and recreates the Metal swap chain
  instead of rendering against an unavailable background drawable.
- `SceneCameraState` is hoisted above that lifecycle gate, so orbit/pan/zoom survive background →
  foreground recreation while native GPU resources are still rebuilt.
- `.github/workflows/ci.yml` verifies common tests, Android/Desktop compilation, iOS simulator and
  device framework linkage, and the complete SwiftUI host on a macOS runner.
- The Xcode host deployment target is iOS 18.5, matching the minimum version used by native objects
  in the current Filament KMP distribution.
- A complete unsigned physical-device build was verified with Xcode on 2026-07-18:

```text
Target: arm64-apple-ios18.5
** BUILD SUCCEEDED **
```

## Next milestone

Harden the iOS integration:

1. Manually verify background/foreground recreation in Simulator and on a physical device (GUI
   Home-key automation needs macOS Accessibility permission).
2. Manually exercise picking and the new backend-neutral gesture controller on physical Android
   and iOS devices: one pointer orbits, two pointers pan and zoom together, secondary-button drag
   pans, and the mouse wheel zooms.
3. Verify on a real device. `iPhone (Alexander)` was detected on 2026-07-18 but was offline and
   unavailable; reconnect/unlock/trust it, enable Developer Mode, configure the Apple team, then
   install from Xcode.

Current device identifier reported by Xcode:

```text
iPhone (Alexander), iPhone 15 Pro, iOS 26.5
UDID: 00008130-000A419A0C51001C
CoreDevice ID: FDD4E83D-7498-5C4D-9209-B8DDF887E224
State: unavailable/offline
```

## Maven publication status

- `0.1.0-alpha01` was published to GitHub Packages under the legacy
  `dev.composescene3d` group.
- `0.1.0-alpha02` uses Maven Central-compatible coordinates under `io.github.aleksandrkdev`.
- The Vanniktech Maven Publish plugin `0.37.0` generates Central-compatible KMP publications,
  including sources and javadoc JARs for Android, JVM and both iOS targets.
- `.github/workflows/publish-central.yml` validates tests and ABI, signs every publication, uploads
  it through Central Portal and enables automatic release.
- Local Maven publication and an independent external consumer compile both pass for the new
  coordinates.
- Before the first Central release, verify namespace `io.github.aleksandrkdev`, generate a Central
  Portal user token, distribute a public GPG key, and add the four documented repository secrets.
- GitHub macOS jobs use `macos-15` with Xcode 26.3 selected explicitly. The current Compose and
  Filament native binaries reference SDK 26 UIKit symbols (`UIViewLayoutRegion`/`UIUtilities`), so
  Xcode 16.4 can read the project but cannot link the complete SwiftUI host.
- `v0.1.0-alpha02` is publicly available from Maven Central under `io.github.aleksandrkdev` and was
  independently resolved and compiled without credentials.

## Backend conformance milestone

- Added the internal `renderer-testkit` KMP module.
- `RendererConformanceSuite` defines shared retained create/update/remove, invalid command,
  idempotent close and capability declaration contracts.
- `renderer-filament` is the first backend wired to the shared suite and passes all contract tests.
- The current Filament KMP dependency has Android, JVM and iOS variants but no Web/Wasm variant;
  the Web renderer therefore needs to be an independent backend behind the same `SceneRenderer`
  contract.

## Primitive and material milestone

- Development version advanced to `0.1.0-alpha03-SNAPSHOT`; released Central coordinates remain
  `0.1.0-alpha02`.
- Added backend-neutral `PbrMaterial` with base color, metallic, roughness and reflectance.
- Added validated `SphereNode`, `PlaneNode` and `CylinderNode` plus matching Compose DSL functions.
- Filament maps the new primitives to its shared standard lit material on Android, Desktop and iOS.
- Primitive transforms include translation, scale and quaternion rotation; Box rotation now also
  reaches the Filament primitive instead of being ignored.
- Android, Desktop and iOS samples show a metallic sphere, rough cylinder and ground plane.

## Color, material and local lighting milestone

- Added backend-neutral `Color3D` with explicit sRGB/linear-sRGB semantics, alpha, 8-bit
  RGB/RGBA/ARGB factories and named colors.
- Primitive nodes now accept the sealed `Material3D` API: `PbrMaterial`, `UnlitMaterial` and
  `EmissiveMaterial`.
- Added validated `PointLightNode` and `SpotLightNode` plus matching Compose DSL functions.
- Filament converts sRGB colors to its required linear color space and maps all three material
  modes and both local light types to its built-in cross-platform implementations.
- The standard shaders in this stage remain opaque. Textures, transparent blending and HDR
  environment/IBL loading belong to the following material-resource milestone.

## Texture material milestone

- Added `TextureSource.Resource`, `TextureSource.Url` and content-aware `TextureSource.Bytes` plus
  stable `TextureAssetKey` values.
- Added validated `TexturedMaterial` for base-color textures with metallic and roughness controls.
- Added `TextureByteLoader` to the Filament adapter while preserving its existing constructor.
- Texture loading is asynchronous; primitives use a neutral PBR fallback while bytes decode, then
  switch to Filament's shared textured material.
- Android, Desktop and iOS compilation and linkage pass. HDR environment maps still require a
  portable preprocessed cubemap format; transparency requires a separate blended shader.

## Completed local Maven alpha milestone

- Fixed project coordinates: `dev.composescene3d:*:0.1.0-alpha01`.
- `scene-core`, `scene-compose`, and `renderer-filament` use Gradle Maven Publish.
- `publishAllPublicationsToLocalAlphaRepository` writes the repository to `build/maven-alpha`.
- Publications include Kotlin Multiplatform root metadata, Android AAR, JVM JAR, iOS arm64 and
  simulator arm64 KLIBs, sources, POMs, Gradle module metadata, and checksums.
- POMs include the Apache-2.0 license and resolve internal module dependencies with the same alpha
  version.
- A completely separate JVM project under `/private/tmp/compose-scene3d-alpha-consumer` resolved
  `dev.composescene3d:renderer-filament:0.1.0-alpha01`, imported both renderer and core APIs, and
  completed `clean build` successfully.
- CI now regenerates all local Maven publications on every push and pull request.

## Following milestone

Before publishing outside the local machine:

1. Choose the permanent GitHub organization/repository URL and Maven group ownership.
2. Add complete POM metadata (project URL, SCM, developer) and artifact signing.
3. Publish `0.1.0-alpha01` to a remote staging repository, then validate Android, Desktop, and iOS
   consumers against remote coordinates.

## Completed binary API validation milestone

- Enabled the binary compatibility validator built into Kotlin Gradle Plugin 2.4.10 for
  `scene-core`, `scene-compose`, and `renderer-filament`.
- Baselines are committed conceptually under each module's `api/` directory, with separate JVM
  `.api` and common KLIB `.klib.api` dumps.
- `checkKotlinAbi` succeeds against the initial `0.1.0-alpha01` surface and runs in CI.
- `CONTRIBUTING.md` documents when and how `updateKotlinAbi` may be used.

## GitHub repository and package publication

- Permanent repository: `https://github.com/AleksandrKdev/compose-scene-3d`.
- POM metadata now includes the project URL, Apache-2.0 license, developer `AleksandrKdev`, and
  HTTPS/SSH SCM coordinates.
- Every published module has a `GitHubPackages` Maven repository pointing at the GitHub project.
- `.github/workflows/publish-alpha.yml` publishes on manual dispatch or a `v*` tag with the
  workflow-provided `GITHUB_TOKEN` and `packages: write` permission.
- Optional repository secrets `SIGNING_KEY` (armored private PGP key) and `SIGNING_PASSWORD` enable
  in-memory signing; GitHub Packages publication works without them.
- Gradle configuration, generated POM content, workflow YAML, and the aggregate
  `publishAllPublicationsToGitHubPackagesRepository` task were verified locally. No remote package
  was pushed from the local machine.

The original GitHub Packages alpha milestone is complete. Maven Central `0.1.0-alpha02` is the
current public release; ongoing development uses `0.1.0-alpha03-SNAPSHOT`.

## Useful files

- `README.md` — overview and roadmap.
- `docs/architecture.md` — state ownership and Compose/backend boundaries.
- `scene-core/src/commonMain/kotlin/dev/composescene3d/core/Scene.kt` — public scene model.
- `scene-core/src/commonMain/kotlin/dev/composescene3d/core/Renderer.kt` — reconciliation and backend contract.
- `scene-compose/src/commonMain/kotlin/dev/composescene3d/compose/Scene3D.kt` — Compose DSL.
- `scene-compose/src/commonMain/kotlin/dev/composescene3d/compose/SceneCameraGestures.kt` — shared
  orbit/pan/zoom math and pointer handling.
- `samples/ios-app/README.md` — simulator and physical-device signing instructions.
- `.github/workflows/ci.yml` — macOS multiplatform and Xcode build checks.
- `CONTRIBUTING.md` — contribution and API rules.

## Prompt for a new session

```text
Continue developing ComposeScene3D in
/Users/darakucybala/AndroidStudioProjects/ComposeScene3D.
Read docs/session-context.md, docs/architecture.md and README.md first.
Then implement the next milestone without exposing Filament types in the public commonMain API.
```
