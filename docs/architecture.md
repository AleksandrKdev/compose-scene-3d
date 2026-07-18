# Architecture

## State ownership

Application code owns immutable scene descriptions. `SceneController` owns the last successfully
submitted description. A renderer owns every native handle and GPU allocation.

Submitting a description produces ordered commands:

1. Removed nodes are released in reverse order.
2. Existing nodes with changed values are updated in place.
3. New nodes are created in description order.

Node identity is determined only by `NodeKey`.

## Compose boundary

The Compose module builds an immutable description during composition and submits it from
`SideEffect`, so a failed or abandoned composition cannot mutate the renderer. `DisposableEffect`
closes the controller with the composition lifecycle.

Compose does not own a render loop. A backend is expected to update animations, camera inertia and
other frame-time state without causing recomposition.

## Backend contract

The initial command API is intentionally small. A Filament backend will translate commands into
entities, transforms, materials and renderables stored behind `NodeKey`. Native handles must remain
private to that backend.

Capabilities are reported explicitly. Future optional features will require a capability instead of
silently behaving differently across platforms.
