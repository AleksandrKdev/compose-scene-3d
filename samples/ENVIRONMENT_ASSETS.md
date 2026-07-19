# Environment sample assets

`lightroom_ibl.ktx` and `lightroom_skybox.ktx` are generated from Filament's
`third_party/environments/lightroom_14b.hdr`. The upstream environment assets are CC0.

They were processed with `cmgen` from official Filament 1.72.0, matching the project's current
Filament KMP runtime:

```shell
cmgen -q -s 64 -f ktx --ibl-samples=128 -x output lightroom_14b.hdr
```

The 64-pixel cubemap keeps the multiplatform samples small; applications should choose a resolution
appropriate for their visual quality and package-size requirements.
