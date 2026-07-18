# ComposeScene3D

An experimental retained-mode 3D scene API for Kotlin and Compose Multiplatform.

The project deliberately separates the public scene model from the renderer. Compose describes
the desired scene, the reconciler emits a small set of backend commands, and GPU resources remain
owned by a renderer implementation rather than by recomposition.

## Status

Early architecture prototype with working Filament primitive and GLB rendering on Android,
Desktop and iOS/Metal. A stable public release is not available yet.

Current development coordinates: `dev.composescene3d:*:0.1.0-alpha01`.

Source repository: [AleksandrKdev/compose-scene-3d](https://github.com/AleksandrKdev/compose-scene-3d).

## Modules

- `scene-core`: immutable scene descriptions, retained scene reconciliation and renderer commands.
- `scene-compose`: a Compose DSL that produces scene descriptions without running a frame loop
  through Compose state.
- `renderer-filament`: a retained adapter over Filament KMP; Filament types stay private to the
  backend implementation. Models with the same cache key share one imported GPU asset while
  retaining independent instances and transforms. It provides orbit/pan/zoom interaction and maps
  Filament picking results back to stable `NodeKey` values.
- `samples/android-app`, `samples/desktop-app` and `samples/ios-app`: interactive GLB samples.

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

Scene3D(controller) {
    model(
        key = "product",
        source = ModelSource.Resource("files/product.glb"),
        transform = Transform(scale = Vec3(0.5f, 0.5f, 0.5f)),
    )
    directionalLight(key = "sun", intensity = 50_000f)
}
```

## Roadmap

1. iOS lifecycle hardening and CI.
2. API stabilization and the first Maven alpha publication.
3. Web backend and backend capability conformance tests.

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
    implementation("dev.composescene3d:scene-compose:0.1.0-alpha01")
    implementation("dev.composescene3d:renderer-filament:0.1.0-alpha01")
}
```

## Binary API compatibility

The three published modules keep JVM and KLIB ABI baselines under their `api/` directories.
`./gradlew checkKotlinAbi` detects accidental public API changes; intentionally accepted changes
are recorded with `./gradlew updateKotlinAbi` after reviewing the diff.

## GitHub Packages

The `Publish alpha` workflow publishes every KMP variant to this repository's GitHub Packages
registry on manual dispatch or a `v*` tag. It uses the workflow `GITHUB_TOKEN`; optional armored PGP
secrets `SIGNING_KEY` and `SIGNING_PASSWORD` enable artifact signing.

For Android-only development Android Studio may use its bundled JDK 21. Do not configure a
project-wide Gradle daemon JVM criterion for Java 22: that can prevent initial sync before Gradle's
toolchain resolver is loaded. Select an installed JDK 22 as the Gradle JDK only when running the
Desktop sample.

## License

Apache-2.0.
