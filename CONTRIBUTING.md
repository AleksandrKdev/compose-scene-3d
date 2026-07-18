# Contributing

Thanks for helping build ComposeScene3D. Before a stable API exists, please start substantial work
with an issue describing the use case, target platforms and expected renderer behavior.

## Local checks

```shell
./gradlew :scene-core:jvmTest :scene-compose:jvmTest :renderer-filament:jvmTest checkKotlinAbi
```

Changes to reconciliation must include platform-independent tests. Backend changes should include a
small reproducible scene and document capability differences rather than hiding them.

Public JVM and KLIB API baselines live in each published module's `api/` directory. If a public API
change is deliberate, review the generated diff and then run `./gradlew updateKotlinAbi`. Never
update the baselines merely to make CI green; incompatible changes require an explicit versioning
decision.

## API guidelines

- Keep renderer-specific types out of `commonMain` public APIs.
- Prefer immutable descriptions and explicit ownership.
- Do not drive per-frame state through Compose recomposition.
- New optional rendering behavior must have a corresponding capability.
- Closing a controller or resource must be idempotent.
