# S Engine — Architecture

S Engine is an Android-first 2D/3D game engine **and** visual editor that runs entirely on the
device. This document describes the module boundaries, the data formats, and the rules that keep
the engine usable without the editor.

> Scope note: this document describes the architecture **as implemented in this repository**.
> See `ROADMAP.md` for milestone status and `TODO.md` for the outstanding work.

---

## 1. Layering

The engine is split into layers with one rule: **dependencies point downward only.**
The editor may reach into the engine; the engine never reaches into the editor.

```
                    ┌──────────────────────────────────────────────┐
                    │  Editor (ui/)                                │
                    │  Projects · Scene viewport · Inspector ·     │
                    │  Tilemap · Sprite/Texture/Model/Music/UI     │
                    │  studios · Script · Blueprint · Build APK    │
                    └───────────────┬──────────────────────────────┘
                                    │ (EditorHost / EditorState)
    ┌───────────────────────────────┴──────────────────────────────┐
    │  AI Agent (agent/)                                            │
    │  GameAgent orchestrator · AgentTools (tool protocol) ·        │
    │  AgentConfig/ModelProfile (providers) · LocalPlanner (offline)│
    └───────────────┬──────────────────────────────────────────────┘
                    │
    ┌───────────────┴───────────────┐   ┌──────────────────────────┐
    │  Android Build System (export/)│   │  Project System          │
    │  ApkBuilder · AxmlPatcher ·    │   │  (project/)              │
    │  ApkSignerV2 · ZipWriter ·     │   │  Project · ProjectManager│
    │  SigningKeys · GameRuntime     │   │  Templates · Prefabs ·   │
    └───────────────┬───────────────┘   │  AssetLibrary · Doctor   │
                    │                   └────────────┬─────────────┘
                    └───────────────┬────────────────┘
                                    │
    ┌───────────────────────────────┴──────────────────────────────┐
    │  Engine Core (engine/)                                        │
    │  Engine loop · Scene/GameObject/Component · Renderer2D/3D ·   │
    │  Physics2D/3D · Animation · Audio · UI · Scripting · Assets   │
    │  (Kotlin)  ─────  libsengine.so (C++17, JNI)                  │
    └──────────────────────────────────────────────────────────────┘
```

### Engine Core — `app/src/main/java/com/sengine/engine/`

| Module | Package | Responsibility |
|---|---|---|
| **Loop / runtime** | `engine/Engine.kt`, `GameServices.kt`, `Input.kt`, `AudioSystem.kt` | Frame loop, play/pause/step/stop, mode transitions, camera follow, particles, input (touch, keyboard, gamepad), platform hooks |
| **Scene system** | `engine/core/GameObject.kt`, `Scene.kt`, `Component.kt`, `Prop.kt` | GameObject graph (parent/child, tags, order), components, the `Prop` property descriptor system, JSON scene serializer |
| **Components** | `engine/core/Components.kt`, `UIComponents.kt`, `Landscape.kt`, `Water.kt`, `TileMap.kt` | `ComponentRegistry` (type → factory + editor categories) and every built-in component |
| **Math** | `engine/math/Affine.kt`, `Mat4.kt` | 2D affine transforms and 4×4 column-major matrices |
| **Renderer** | `engine/render/GL.kt`, `Textures.kt`, `Shaders.kt`, `Renderer2D.kt`, `Renderer3D.kt`, `SceneRenderer.kt`, `Meshes.kt`, `PostProcessor.kt`, `View2D/View3D`, `EditorState.kt` | OpenGL ES 2.0 immediate-mode drawing, texture cache + text atlas, GLSL programs, mesh library, post FX, editor grid/gizmos |
| **Physics** | `engine/physics/PhysicsWorld.kt` (2D), `PhysicsWorld3D.kt`, `WaterPhysics.kt` | Impulse-based rigidbodies, box/circle (2D) and box/sphere (3D) colliders, triggers, raycasts, buoyancy |
| **Animation** | `engine/anim/AnimationClip.kt` + `Animator` | Sprite-sheet clips (`.anim`), frame timing |
| **Audio** | `engine/AudioSystem.kt`, `engine/audio/Song.kt` | Clip playback and synthesized music |
| **UI** | `UIComponents.kt` (`UIPanel`, `UIButton`, `UIProgress`), `ui/UICreatorActivity.kt` | Screen-space canvas, anchors, WYSIWYG designer |
| **Scripting** | `engine/script/ScriptSystem.kt`, `Api.kt`, `NativeScripts.kt`, `blueprint/Blueprint.kt` | JavaScript (Mozilla Rhino), C++ subset (native), visual-script graphs compiled to JS |
| **Native layer** | `app/src/main/cpp/` → `libsengine.so` | C++17 script VM (`script/`), landscape + mesh toolkit (`engine/`), JNI bridge (`jni/`); host tests in `cpp/tests/` |
| **Assets** | `engine/texture/`, `project/AssetLibrary.kt`, `render/Textures.kt` | PNG encoder/decoder, procedural texture/sprite/music generators, asset kinds |

**Editor independence.** `engine/render/SceneRenderer.kt` takes an optional `EditorState?`; when it
is `null` the renderer draws pure game output and skips grids and gizmos. The `PlayerActivity` and
the exported APK runtime use exactly that path, so gameplay never depends on editor code.

---

## 2. Scene model

Everything in a scene is a `GameObject` with a transform, an optional parent, and a list of
`Component`s. Components describe themselves twice — to the inspector and to the serializer —
through one mechanism:

```kotlin
class TileMap : Component() {
    override val type = "TileMap"
    var map = ""; var tileSize = 1f
    override fun props() = listOf(
        Prop.Asset("Tilemap", AssetKind.TILEMAP, { map }, { map = it; invalidate() }),
        Prop.F("Tile Size", { tileSize }, { tileSize = it.coerceIn(0.01f, 64f) }, 0.05f),
        …
    )
}
```

`Prop.F/I/B/S/Color/Choice/Asset` cover every editable field, so adding a component automatically
adds inspector rows, undo snapshots, and JSON persistence — no serializer edits required.

Registration is declarative:

```kotlin
object ComponentRegistry {
    val types = linkedMapOf("SpriteRenderer" to { SpriteRenderer() }, … "TileMap" to { TileMap() })
    val categories = linkedMapOf("Rendering 2D" to listOf(…, "TileMap", …), …)
}
```

`ComponentRegistry.create(type)` is used by `SceneSerializer.fromJson`, so **old scenes keep
loading** when new components are added and unknown component types are skipped rather than
crashing a project.

### Scene ↔ text mapping

```
<project>/project.json                 project metadata (name, start scene, orientation)
<project>/scenes/Main.scene.json       objects + transforms + component props
<project>/assets/*.png|.js|.wav|…      imported / generated assets
<project>/assets/Level1.tmap           tilemap documents (see §4)
<project>/.saves/                      runtime save data (never exported)
<project>/controls.json                on-screen control layout
```

---

## 3. Rendering pipeline

`SceneRenderer` is a `GLSurfaceView.Renderer`. Each frame, inside `engine.lock`:

1. `engine.tick(dt)` updates scripts, physics, animation, particles.
2. Mode selection: editor 2D view / editor 3D orbit view / game camera.
3. Post-processing target is bound when the camera requests a post FX.
4. **2D path** — grid (edit mode), then objects sorted by `order`: UI components, tilemaps
   (run-merged quads), sprites, text, particles, water, then the editor overlay (colliders,
   camera frames, gizmos).
5. **3D path** — shadow pass, sky, opaque meshes (with frustum culling), then transparent
   meshes/sprites/particles sorted back-to-front.
6. Screen-space UI layer is drawn with its own 16:9 orthographic view.
7. Post-processing resolves to the framebuffer.

Draw data is immediate-mode over a single unit-quad vertex buffer with a uniform `shape` selector
(`0` rect, `1` circle, `2` triangle, `3` ring, `4` rounded rect). The profiler reports draw calls,
frame ms, script ms and physics ms.

---

## 4. TileMap system (Milestone 1)

Added in this milestone; it is the reference example of extending the engine.

```
Tilemap  (engine/core/TileMap.kt, pure Kotlin + org.json — unit-testable on the JVM)
├── tileset   : asset name of the atlas texture
├── tileW/H   : atlas cell size in pixels
├── solidTiles: set of atlas cells that collide
└── layers[]  : TileLayer { name, cols, rows, visible, opacity, solid, cells: IntArray }
                 cells are row-major from the bottom-left, -1 = empty

TileMap : Component            → places a .tmap in a scene (map, tileSize, opacity, tint)
```

* **Cells** — `cell(col, row)` occupies `[col, col+1) × [row, row+1)` in tile units measured from
  the owning object's origin, so the object transform, parent hierarchy and camera all apply.
* **Painting** — `TilemapEditorActivity` (touch-first: paint / erase / rectangle / eyedropper,
  pan + pinch-zoom, layer panel, undo/redo per stroke, resize, and an offline starter-tileset
  generator written through `PngEncoder`).
* **Rendering** — `SceneRenderer.drawTilemap` loads the `.tmap` through
  `TileMap.ensure(key, load)` (cached by `asset|lastModified|length`, mirroring `VoxelWorld`),
  slices UVs from `Tex.w/h`, and merges horizontal runs of identical tiles into a single stretched
  quad — a solid floor row costs one draw call.
* **Collision** — `Tilemap.solidAt()` and `solidRects()` (vertically merged boxes) are already
  implemented and unit tested; wiring them into `PhysicsWorld` is the next milestone (see `TODO.md`).
* **Storage** — layer data uses a run-length codec (`-3*12,5,-1*4`) so sparse levels stay small.

---

## 5. AI agent layer

The agent is a *client of the project system*, not part of the engine. It is provider-agnostic by
construction:

```
AgentActivity ──▶ GameAgent ──▶ ChatModel (interface)
                                ├── LlmClient(profile)  → HTTP
                                └── LocalPlanner        → offline, deterministic
                                      ▲
AgentConfig/ModelProfile (persisted list of providers, switchable at runtime)
```

* **`ProviderKind`** — OpenAI, Anthropic Claude, Google Gemini, OpenRouter, Groq, DeepSeek,
  Mistral, xAI Grok, Together, Ollama / LM Studio (local), and any custom OpenAI-compatible URL,
  plus `LOCAL` for the built-in offline planner. No provider is privileged.
* **Three API dialects** are covered by one client: OpenAI-compatible chat completions,
  Anthropic Messages, and Gemini `generateContent`.
* **`AgentTools`** is the capability surface — scene/object creation and editing, script
  generation and editing, asset generation, project search, Game Doctor runs, headless play-tests
  and APK builds. Tools are addressed by name + JSON args and therefore reusable by any model.
* **`LocalPlanner`** implements the same `ChatModel` interface offline, so the AI features
  degrade gracefully instead of failing without a network or key.

Adding a provider means adding a `ProviderKind` entry (and, only if it is not
OpenAI-compatible, a small adapter function). Engine code never imports an AI SDK.

---

## 6. Android build system

`export/` builds a standalone APK **on the device**: `GameRuntime` packages the runtime plus the
project's `assets/`, `AxmlPatcher` rewrites the binary manifest (app name, package, version),
`ZipWriter` assembles, and `ApkSignerV2` signs with a device-keystore key or a user-supplied
`.p12`/`.bks`. The native `libsengine.so` slices are carried into the exported APK. CI verifies the
result with `apksigner verify` and `aapt2 dump badging`.

---

## 7. Verification

| Check | Command |
|---|---|
| Kotlin typecheck (all sources) | `./gradlew compileDebugKotlin` |
| JVM engine/platform tests | `./gradlew testDebugUnitTest` |
| Native C++ tests (host) | `g++ -std=c++17 -O2 tests/script_tests.cpp script/*.cpp engine/*.cpp -o t && ./t` |
| APK build | `./gradlew assembleDebug` |
| Exported-game validation | `.github/workflows/android.yml` (apksigner + aapt2 assertions) |

Everything is also enforced by `.github/workflows/android.yml` on every push.

---

## License

MIT (see `README.md`).
