# S Engine Development Roadmap

S Engine is an Android-first 2D (and 3D) game development studio inspired by Godot. Below is the multi-stage roadmap tracking engine capabilities, editor tools, scripting, export, and AI features.

---

## Milestone 1: Engine Core & Renderer (Completed)
- [x] Hardware-accelerated OpenGL ES 2.0 2D and 3D renderer.
- [x] GameObject and parent-child scene hierarchy system.
- [x] Transform system with 2D/3D matrices and screen-space UI anchoring.
- [x] Custom GLSL shader support and post-processing pipeline (Bloom, CRT, Vignette, Chromatic Aberration).
- [x] ParticleEmitter system with 15+ presets (Fire, Smoke, Explosion, Rain, Magic).

## Milestone 2: Scene & Editor Tools (Completed)
- [x] Touch-first 2D/3D viewport with pan, zoom, orbit, and snapping gizmos.
- [x] Interactive Hierarchy tree with parent/child operations, duplicate, and reordering.
- [x] Inspector panel with dynamic property controls (Color, Choice, Number, Asset, String).
- [x] Snapshot-based Undo / Redo history.
- [x] Live Play / Pause / Step in-editor game runtime with state restoration.

## Milestone 3: Asset System & Dedicated Studios (Completed)
- [x] Project Manager for project creation, ZIP export/import, and templates.
- [x] Asset Store with 180+ built-in procedural assets, sprites, audio, models, and scripts.
- [x] Sprite Studio: Pixel art editor with layers, animation frames, onion skin, palettes.
- [x] Texture Studio: 30 procedural pattern styles (Bricks, Wood, Grass, Lava, Sci-Fi) with seamless tiling.
- [x] UI Creator: Canvas-based visual drag & drop designer for HUD, Main Menu, Pause screens.
- [x] 3D Model & Animation Studio: Blender-style editing with extrude, bevel, loop cut, rigging, auto-animations.

## Milestone 4: Scripting & Logic Subsystem (Completed)
- [x] JavaScript scripting engine powered by Mozilla Rhino with `start`, `update`, `onCollision`, `onTrigger`, `onTap` callbacks.
- [x] C++ Native Scripting via embedded C++ interpreter inside `libsengine.so`.
- [x] Blueprints visual node-based graph scripting with 45+ nodes compiling to JS.
- [x] Built-in Code Editor with syntax highlighting, API documentation, auto-indent, error reporting.
- [x] GDScript (`.gd`) and Lua (`.lua`) syntax transpilation & runtime execution.

## Milestone 5: Physics, Audio & Tilemaps (Completed)
- [x] 2D Impulse physics engine with Rigidbody2D, Box & Circle Colliders, triggers, friction, bounciness.
- [x] 2D Water physics with buoyancy, drag, surface waves, and splash particles.
- [x] Audio subsystem with AudioSource and procedural music synthesizer.
- [x] Tilemap system with `Tilemap` component and dedicated touch-first `TilemapEditorActivity`.
- [x] GameObject Layers and Groups with script filtering (`scene.getInGroup("enemies")`).

## Milestone 6: APK Export directly on Android (Completed)
- [x] Embedded APK build pipeline.
- [x] Android Manifest XML patcher and binary AXML encoder.
- [x] APK Signature Scheme v2 on-device signing with Keystore management.
- [x] Standalone package generation installable directly on Android devices.

## Milestone 7: AI Coding & Development Agent (Completed)
- [x] Multi-provider LLM support: OpenAI, Google Gemini, Anthropic Claude, DeepSeek, Groq, OpenRouter, Ollama/Local, Custom URLs.
- [x] Offline Local Planner operating without internet connection.
- [x] 26+ Native AI Agent tools to generate scenes, write code, create assets, run Game Doctor, and build projects.

---

## Future Enhancements
- [ ] Compute-shader accelerated particle physics.
- [ ] Extended 2D skeletal animation / ragdoll system.
- [ ] WebAssembly / WebGL export support.
