# S Engine — Roadmap

Milestones are ordered so the project **builds and runs after every one of them**. Each milestone
ends with: docs updated, Kotlin typecheck green, JVM tests green, native tests green, APK builds.

Legend: ✅ done · 🟡 partial · ⬜ planned

---

## Where this project started

The repository already contained a mature Android engine + editor ("S Engine", v1–v6): NDK C++17
layer, OpenGL ES 2.0 renderer, 2D + 3D physics, scripting (JS + C++ subset + blueprints),
animation, audio, UI designer, asset store, APK export and a multi-provider AI agent.

The request re-specifies that product. So instead of rebuilding from zero, the roadmap **gaps the
existing engine against the request** and fills what is missing, milestone by milestone, keeping
the build green.

| Requested capability | Status before M1 | Notes |
|---|---|---|
| Visual 2D scene editor | ✅ | Hierarchy, inspector, gizmos, undo/redo, play-in-editor |
| Scene / node system | ✅ | `GameObject` + `Component` + JSON serializer |
| Sprite & texture system | ✅ | `SpriteRenderer`, texture cache, procedural Texture Studio |
| Asset manager / browser | ✅ | Assets panel, import (image/audio/model), `.zip` project export |
| Import images, audio, fonts, JSON | 🟡 | images/audio/model/json yes; **font import** planned (M7) |
| Sprite animation editor | ✅ | Slicing, frame strip, `.anim` clips |
| **Tilemap editor** | ⬜ → ✅ | **Built in M1** (see below) |
| Camera system | ✅ | 2D size/background/follow/smoothing, 3D perspective + orbit editor |
| 2D physics & collision | ✅ | Rigidbodies, box/circle colliders, triggers, raycasts |
| Particles | ✅ | Presets + turbulence/wind |
| Audio system | ✅ | Clips, synthesized music, `AudioSource` |
| UI designer | ✅ | WYSIWYG canvas + ready-made screens |
| Layers and groups | 🟡 | Parent/child groups + sorting order; **explicit layer groups** planned (M3) |
| Prefabs / reusable objects | ✅ | `Prefabs.kt` + inactive-object cloning |
| Project manager | ✅ | Create/open/duplicate/delete/export |
| Built-in code editor | ✅ | JS + C++ highlighting, Check, API reference |
| Scripting (GDScript/Lua-like) | 🟡 | JavaScript (Rhino) + C++ subset + blueprints; a dedicated `.s` script language is planned (M6) |
| Visual scripting | ✅ | Blueprint graphs → compiled JS |
| Game preview / run | ✅ | Play / pause / step in-editor, fullscreen player |
| Debug console & error reporting | ✅ | Console, Game Doctor, line-accurate script errors |
| Project save / load | ✅ | JSON scenes + assets |
| **APK build/export on device** | ✅ | Zip + AXML patch + v2 signing |
| Touch-first interface | ✅ | Touch-first editor, joystick/buttons, pinch-zoom |
| Keyboard / mouse support | ✅ | WASD/arrows/Space, gamepad |
| Dark modern UI | ✅ | Monochrome "Noir" theme |
| Offline core features | ✅ | No network needed except optional AI |
| **Multi-provider AI agent** | ✅ | 11 provider kinds + offline planner, no hard-coded vendor |

---

## M0 — Architecture & docs ✅

- ✅ `ARCHITECTURE.md` — layer map, dependency rules, scene/asset formats, verification matrix.
- ✅ `ROADMAP.md`, `TODO.md`, `README.md` updated.
- ✅ Gap analysis of the existing engine against the request (table above).
- ✅ Build verification harness documented so each later milestone can be checked quickly.

## M1 — Tilemap system (engine + editor) ✅ *(this milestone)*

Delivered end-to-end, engine-first:

- ✅ `Tilemap` document model: layers, RLE cell codec, `at()` eyedropper, `solidAt()`,
  `solidRects()` (vertical merging), resize preserving the bottom-left anchor.
- ✅ `TileMap` component registered in `ComponentRegistry` (inspector + serializer, no serializer
  changes needed).
- ✅ New `AssetKind.TILEMAP` (`.tmap`), so tilemaps live in the asset manager and are reusable
  across scenes and prefabs.
- ✅ `SceneRenderer.drawTilemap`: UV slicing from the atlas, run-merged quads (one draw call per
  horizontal run), per-layer visibility/opacity, component tint/opacity, cache keyed on
  `asset|lastModified|length`. Editor overlay draws map bounds.
- ✅ `TilemapEditorActivity`: paint / erase / rectangle / eyedropper tools, pan + pinch-zoom,
  drag painting with path interpolation, per-stroke undo/redo, layer panel (add/rename/hide/
  solid/opacity/clear/delete), map resize, tileset picker with automatic tile-size guessing,
  and an **offline starter-tileset generator** (16 px, 8×4 atlas) written through `PngEncoder`.
- ✅ Editor integration: `+ Tilemap` asset button, "Tile Map" object, "Edit Tilemap…" /
  "New Tilemap…" object menu, "Tilemap Editor" main-menu entry, `.tmap` asset menu
  ("Create Tilemap Object", "Use tilemap on …").
- ✅ Tests: `EngineTilemapTest` (14 tests) covering cells, bounds, resize, runs, solids,
  collision rectangles, the RLE codec, JSON round-trip, starter map, component caching and
  scene serialization.

## M2 — Tilemap physics & sorting ⬜ *(next)*

- ⬜ Feed `Tilemap.solidRects()` into `PhysicsWorld` as static geometry (synthetic static bodies,
  no scene pollution) so `Rigidbody2D` characters stand on tiled floors.
- ⬜ Tile-level collision overrides in the Tilemap Editor (mark a tile solid/one-way/platform).
- ⬜ One-way platforms and slope tiles.
- ⬜ Y-sorting mode: interleave tilemap layers with sprites by depth instead of one object order.
- ⬜ Script API: `tilemap.get(x, y)`, `tilemap.set(x, y, tile)`, `tilemap.solidAt(x, y)`.

## M3 — Layers, groups & scene organisation ⬜

- ⬜ Named layer groups (visibility + lock + draw order) on top of the hierarchy.
- ⬜ Layer-aware selection, multi-select and box-select in the viewport.
- ⬜ Object pooling/reuse helpers for large tile levels.

## M4 — Prefab workflow upgrade ⬜

- ⬜ Prefab documents (`.prefab`) with instance overrides instead of inactive scene templates.
- ⬜ Nested prefabs and "apply/revert" from the inspector.

## M5 — Asset pipeline completion ⬜

- ⬜ Bitmap font import (`.fnt` / image + metrics) and a `Font` asset kind.
- ⬜ JSON/data asset editing UI, CSV → tilemap import.
- ⬜ Tileset asset (`.tset`) with per-tile metadata (name, animation frames, collision shape)
  so tilesets become first-class, shared resources.

## M6 — Scripting depth ⬜

- ⬜ A GDScript/Lua-like `.s` language: indentation-based, engine-native, beside JS and C++.
- ⬜ Debugger: breakpoints, variable watch, call stack in the debug console.
- ⬜ Script hot-reload in play mode.

## M7 — Audio, animation & particles polish ⬜

- ⬜ Audio buses, per-bus volume, streaming for long tracks.
- ⬜ Animation events (callbacks on frame), blend between clips, tilemap tile animation.
- ⬜ Particle curve editors and sprite-sheet particles.

## M8 — AI agent expansion ⬜

- ⬜ Streaming responses and cancellable tool runs.
- ⬜ Project-wide search/replace and refactor tools.
- ⬜ Agent-created tests + headless regression runs after each change.
- ⬜ Provider plugins via a documented adapter interface (no core changes to add one).

## M9 — Export & distribution ⬜

- ⬜ AAB export, asset compression, per-ABI split building.
- ⬜ Versioned update signing, install + share flows hardening.
- ⬜ Play-test recorder (input capture) and deterministic replay in CI.

## M10 — Hardening ⬜

- ⬜ Multi-layer merge/autotile painting tools.
- ⬜ Undo/redo for asset edits across every studio.
- ⬜ Performance pass: draw-call batching, tilemap chunking, texture atlas packing.
- ⬜ Accessibility: scalable UI, large-target touch mode, RTL layout review.

---

## Milestone discipline

For every milestone:

1. Keep the engine independent of the editor (new features land in `engine/` first).
2. Add unit tests for engine-level logic (runnable headlessly on the JVM).
3. Run the verification matrix from `ARCHITECTURE.md §7` — no milestone is "done" with a red build.
4. Update `ARCHITECTURE.md`, `ROADMAP.md`, `TODO.md` and the `README.md` feature tables.
