# S Engine — TODO

Working list. Ordered by priority; every item keeps the build green when finished.
See `ROADMAP.md` for milestone grouping and `ARCHITECTURE.md` for the module map.

---

## Now — finish the TileMap milestone (M2)

- [ ] **Tilemap collision in `PhysicsWorld`** — turn `Tilemap.solidRects()` into static collision
      geometry so `Rigidbody2D` bodies (players, crates) rest on tiled floors. Use synthetic
      *static* bodies created per tilemap object and refreshed when the `.tmap` changes
      (`TileMap.docKey`), so the scene graph stays clean. Cover with a headless simulation test:
      drop a body onto a floor layer and assert it comes to rest above `y = row * tileSize`.
- [ ] **`grounded` + one-way platforms** — report `grounded` from tilemap contacts; add a per-layer
      "one-way" flag (collide only when the body is falling and above the surface).
- [ ] **Per-tile collision editor** — in `TilemapEditorActivity`, tap-and-hold a palette tile to
      toggle "solid", and mark tiles as slope/one-way. Persist in `Tilemap.solidTiles`.
- [ ] **Slope tiles** — 45° ramp collision for platformers (needs a `slopeLeft/slopeRight` flag).
- [ ] **Script API** — expose `tilemap.get(x, y)`, `tilemap.set(x, y, tile)`,
      `tilemap.solidAt(x, y)`, `tilemap.size()` in `engine/script/Api.kt` (JS), the blueprint node
      library, and the C++ interpreter (`cpp/`).
- [ ] **Y-sorting** — optional per-tilemap "sort with sprites" mode so players can walk *behind*
      tiles. Requires splitting the tilemap draw into per-row quads interleaved with the object
      draw pass.

## Next — layer groups, prefabs, asset pipeline (M3–M5)

- [ ] Named, lockable layer groups in the scene editor (visibility + draw order + selection filter).
- [ ] Multi-select and box-select in the viewport, with group transform and group delete.
- [ ] `.prefab` documents with per-instance overrides; migrate `Prefabs.kt` templates to them.
- [ ] `Font` asset kind + bitmap-font import and `TextRenderer` font selection.
- [ ] `.tset` tileset asset: per-tile name, collision shape, terrain tag and animation frames,
      shared across tilemaps (removes the current per-document tileset fields).
- [ ] CSV / JSON → tilemap import (Tiled `.tmx`/`.tsx` importer for interop).
- [ ] Tilemap chunking for very large levels (only rebuild/upload visible chunks).

## Later — scripting, audio, AI, hardening (M6–M10)

- [ ] `.s` script language (indentation-based, engine-native) alongside JS and C++.
- [ ] Debugger: breakpoints, variable watch, call stack, step-into in the script editor.
- [ ] Script hot-reload while in play mode.
- [ ] Audio buses, per-bus volume, streaming long tracks.
- [ ] Animation events, clip blending, animated tiles in tilemaps.
- [ ] Agent: streaming responses, cancellable runs, project-wide search/replace tools.
- [ ] Agent: run the test suite headlessly after edits and report regressions.
- [ ] Documented provider adapter interface so a new LLM vendor needs no core changes.
- [ ] AAB export, per-ABI split builds, asset compression.
- [ ] Undo/redo coverage audit across every studio (Sprite, Texture, Tilemap, UI, Model, Music).
- [ ] Perf: draw-call batching, texture atlas packing, profiler budgets per subsystem.
- [ ] Accessibility: scalable UI density, large-target touch mode, RTL review.

## Known limitations (documented, not yet fixed)

- Tilemaps render in the **2D viewport only**; a 3D scene ignores `TileMap` components.
- Tilemap collision is **not wired into `PhysicsWorld` yet** — tiles are visual only until M2.
  `Tilemap.solidAt()` / `solidRects()` are implemented and tested, ready for that integration.
- A tilemap object draws as one unit, so `order` sorts it against other objects but tiles do not
  interleave with sprites (no Y-sorting yet).
- The `.tmap` document holds its tileset name and atlas cell size; there is no shared tileset
  resource yet, so two tilemaps can drift apart (M5 fixes this with `.tset`).
- Tileset generation writes a fixed 8×4 × 16 px atlas; custom atlases must be imported.
- `local.properties` is machine-specific and git-ignored — CI uses `ANDROID_HOME` instead.
- The standalone Kotlin typecheck harness used during development compiles `main` and `test`
  sources as separate modules, so tests touching `internal` declarations only compile under
  Gradle; this is a harness artifact, not a code problem.

## Verification checklist (run before calling a milestone done)

- [ ] `./gradlew compileDebugKotlin` — clean.
- [ ] `./gradlew testDebugUnitTest` — green (CI runs native host tests + JVM tests).
- [ ] Native host tests: 51+ passing (`cpp/tests/script_tests.cpp`).
- [ ] `./gradlew assembleDebug` — APK builds; `libsengine.so` present for arm64-v8a,
      armeabi-v7a, x86_64.
- [ ] Exported sample game APK passes `apksigner verify` and shows the patched package/version.
- [ ] `ARCHITECTURE.md`, `ROADMAP.md`, `TODO.md`, `README.md` reflect the new state.
