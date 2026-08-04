# Changelog

## 0.1.0-alpha03 — 2026-08-04

- Added Android/iOS/Web custom geometry, expanded materials, environment lighting and shadows.
- Added imported hierarchy inspection, picking, part overrides, highlighting and exploded views.
- Added clipping planes, generated section caps and procedural section hatching.
- Added solid lines, arrows and linear, radial and angular engineering dimensions.
- Added reactive screen anchors, collision-free labels, formatting and accessible annotations.
- Added cancellable camera focus flights and subtree visibility/opacity.
- Added textured opacity materials while explicitly capability-gating imported glTF fading.
- Added WebGL2 rendering and Web/Wasm CI coverage.
- Unified boxes with the common `Material3D` API and fixed Compose controller ownership.

### Migration from alpha02

Replace `box(color = Vec3(...))` with `box(material = PbrMaterial(baseColor = Color3D(...)))`.

## 0.1.0-alpha02

- First Maven Central release.
