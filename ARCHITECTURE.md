# S Engine Architecture

S Engine is a professional, standalone, Android-first 2D (and 3D) game engine and visual game editor studio inspired by Godot's node/scene workflow, designed to run completely on mobile phones and tablets without needing a PC.

## High-Level Architecture Overview

```
+---------------------------------------------------------------------------------+
|                                 S Engine Studio                                 |
+---------------------------------------------------------------------------------+
|                                                                                 |
|  +---------------------+  +----------------------+  +------------------------+  |
|  |     Android UI      |  |   Built-In Editors   |  |        AI Agent        |  |
|  |  (Scene Viewport,   |  | (Script, Sprite,     |  | (OpenAI, Gemini,       |  |
|  |   Hierarchy,        |  |  Texture, UI, Tile,  |  |  Anthropic, DeepSeek,  |  |
|  |   Inspector)        |  |  Blueprint, Model)   |  |  Local / Offline)      |  |
|  +----------+----------+  +----------+-----------+  +-----------+------------+  |
|             |                        |                      |                   |
+-------------|------------------------|----------------------|-------------------+
              |                        |                      |
              v                        v                      v
+---------------------------------------------------------------------------------+
|                                   Engine Core                                   |
+---------------------------------------------------------------------------------+
|                                                                                 |
|  +--------------------+  +--------------------+  +---------------------------+  |
|  |    Scene System    |  |    Asset System    |  |     Scripting Engine      |  |
|  |  (GameObject,      |  |  (Textures, Audio, |  | (JS Rhino, C++ Native,    |  |
|  |   Node Hierarchy,  |  |   Models, Scripts, |  |  GDScript & Lua           |  |
|  |   Layers & Groups) |  |   Shaders, Scenes) |  |  Transpiler, Blueprints)  |  |
|  +---------+----------+  +---------+----------+  +-------------+-------------+  |
|            |                       |                         |                  |
|            +-----------------------+-------------------------+                  |
|                                    |                                            |
|                                    v                                            |
|  +--------------------+  +--------------------+  +---------------------------+  |
|  |   Physics Engine   |  |    Audio System    |  |      GLES2 Renderer       |  |
|  | (2D Box/Circle,    |  |  (AudioSource,     |  |  (Sprites, Tilemaps,      |  |
|  |  Water & Buoyancy, |  |   Music Synth,     |  |   Shaders, Particles,     |  |
|  |  3D Rigidbodies)   |  |   Sound Effects)   |  |   Post-Processing)        |  |
|  +--------------------+  +--------------------+  +---------------------------+  |
|                                                                                 |
+---------------------------------------------------------------------------------+
|                            Android Build System                                 |
|        (Manifest Patching, ZIP Alignment, Android Keystore v2 Signing)         |
+---------------------------------------------------------------------------------+
```

---

## Engine Core Subsystems

### 1. Scene System (`com.sengine.engine.core`)
* **GameObject & Component Model:** Game entities consist of `GameObject` instances with transforms (Position, Rotation, Scale in 2D & 3D), layers, groups, tags, and attached components.
* **Parent-Child Hierarchy:** Scene graph supporting arbitrary parent/child nesting with local and world transform recalculation.
* **Serialization:** JSON-based scene and prefab serialization (`SceneSerializer`), enabling instant save/load and deep duplication.
* **Layers & Groups:** Dynamic collision/query filtering and tag/group lookups (`scene.getInGroup("enemies")`).

### 2. Renderer (`com.sengine.engine.render`)
* **OpenGL ES 2.0 Engine:** Hardware-accelerated 2D sprite, text, particle, and tilemap rendering, alongside 3D mesh rendering.
* **Custom GLSL Shaders:** Custom fragment/vertex shaders with uniforms (`uTime`, `uParam`, `uTex`, `uResolution`).
* **Post-Processing Pipeline:** Bloom, CRT, Vignette, Sepia, Pixelate, Invert, Chromatic Aberration, and custom post-process passes.
* **Gizmos & Selection:** Touch-interactive 2D and 3D translation/rotation/scale handles.

### 3. Asset System (`com.sengine.project`)
* **Project Storage:** Single self-contained project directories containing `project.json`, `scenes/`, and `assets/`.
* **Asset Types:** PNG/JPG/WebP textures, WAV/MP3 audio, JSON/text configs, `.js`/`.cpp`/`.gd`/`.lua` scripts, `.anim` clips, `.bp` visual scripts, and `.obj`/`.smodel` 3D models.
* **Built-in Asset Store:** Procedural generators for textures, sprites, music, and pre-made templates.

### 4. Physics Engine (`com.sengine.engine.physics`)
* **2D Physics:** Impulse-based rigidbodies, Box & Circle colliders, trigger callbacks, friction, bounciness, gravity, and ground detection.
* **2D Water Physics:** Buoyancy, drag, rolling surface waves, and particle splashes.
* **3D Physics:** Box & Sphere 3D colliders, raycasting, and landscape heightfield collisions.

### 5. Animation Engine (`com.sengine.engine.anim`)
* **Sprite Sheet Animator:** Frame-based sprite sheet slicing, loop/ping-pong controls, and `.anim` clip serialization.
* **3D Rigging & Animation:** Bone keyframe timeline and retargetable `.sanim` animation clips.

### 6. Audio Subsystem (`com.sengine.engine.AudioSystem`)
* **AudioSource Component:** Spatial or 2D sound clip playback with looping, pitch, and volume parameters.
* **Synthesizer & Music Editor:** Multi-track chiptune and procedural song composer.

### 7. Scripting System (`com.sengine.engine.script`)
* **Multi-Language Runtime:**
  - **JavaScript:** Mozilla Rhino ES6 environment with rich engine bindings (`input`, `scene`, `time`, `audio`, `storage`, `ui`, `voxel`).
  - **C++ (Native):** High-performance C++ interpreter running inside `libsengine.so`.
  - **GDScript & Lua:** Built-in GDScript and Lua syntax transpilers to native JS execution.
  - **Blueprints:** Visual node graph script editor compiling directly to executable code.

### 8. Tilemap Engine (`com.sengine.engine.core.Tilemap`)
* **Grid Rendering & Tile Editing:** High-performance grid rendering for 2D tilemaps with atlas indexing.
* **Automatic Collision Generation:** Generates static box colliders for solid tiles in tilemaps.

---

## Android Visual Editor

* **Scene Editor & Viewport:** Touch gesture pan/zoom/rotate camera with gizmo controls.
* **Hierarchy & Inspector Panel:** Interactive tree layout, component adder, and Property Form editors.
* **Dedicated Studios:**
  - **Script Editor:** Code editor with syntax highlighting, line numbers, error diagnostics, and quick symbol bar.
  - **Sprite & Texture Studio:** Layered pixel editor, frame onion skinning, procedural texture generator.
  - **UI Creator:** WYSIWYG 16:9 canvas drag & drop designer with predefined screens.
  - **Tilemap Editor:** Tap-to-paint grid tilemap builder with tile selection and eraser tools.
  - **Blueprint Editor:** Visual node graph builder with wire connections.

---

## Standalone APK Export (`com.sengine.export`)

* **Standalone Runtime Bundle:** Packs the project JSON, assets, and engine runtime into a standalone APK.
* **Manifest Patching & AXML Encoder:** Modifies `AndroidManifest.xml` with package name, app title, and launcher icon.
* **APK Signature Scheme v2:** Signs the generated APK directly on device using Android Keystore keys or custom keystores.

---

## AI Agent Subsystem (`com.sengine.agent`)

* **Provider Agnostic Design:** Decoupled LLM interface (`ChatModel` / `LlmClient`) supporting:
  - OpenAI (`gpt-4o`, `gpt-4o-mini`)
  - Anthropic Claude (`claude-3-5-sonnet`)
  - Google Gemini (`gemini-1.5-flash`)
  - DeepSeek (`deepseek-chat`)
  - Groq (`llama-3.3-70b`)
  - OpenRouter, xAI Grok, Together AI, Mistral
  - Ollama / LM Studio (Local network LLM)
  - Custom OpenAI-compatible endpoints
  - S Engine Offline Local Planner
* **Engine Tool Integration:** 26+ native engine tools allowing the AI agent to search projects, modify scenes, create scripts, diagnose errors with Game Doctor, generate procedural assets, and test builds.
