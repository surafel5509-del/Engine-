# S Engine — Studio Edition #

**S Engine** is a professional, Android-first **2D and 3D** game engine and full visual development studio inspired by the node/scene workflow of Godot. Build, script, design, play-test, and export complete games directly on your Android phone or tablet — no PC required.

> Native Android SDK + NDK project: combining Kotlin runtime with OpenGL ES 2.0/3.0 rendering, 2D/3D physics, visual editors, multi-language scripting, and standalone APK export with on-device signing.

---

## 🚀 Key Features

* **Visual 2D & 3D Scene Editor:** Touch-first viewport with pan, zoom, orbit, snapping, and 3D gizmo handles.
* **Scene & Node System:** Parent-child transform graph, object prefabs, dynamic scene switching, and JSON serialization.
* **Layers & Groups:** Assign objects to layers and groups for collision filtering and script lookups (`scene.getInGroup("enemies")`).
* **Tilemap System & Tile Editor:** Integrated `Tilemap` component and touch-based `TilemapEditorActivity` for painting 2D levels with auto-collisions.
* **Multi-Language Scripting:**
  * **JavaScript (ES6):** Fast runtime via Rhino with full engine APIs.
  * **GDScript & Lua:** GDScript (`.gd`) and Lua (`.lua`) syntax transpilation for Godot and Lua developers.
  * **C++ (Native):** Fast embedded C++ engine code running inside `libsengine.so`.
  * **Blueprints:** Visual node graph editor with ~45 nodes compiling directly to executable code.
* **Sprite & Texture Studio:** Built-in pixel art editor with onion skinning and 30 procedural texture generators.
* **UI Creator:** Visual canvas editor for HUDs, Main Menus, Dialogs, and Pause screens.
* **Physics & Water Engine:** 2D/3D rigidbodies, box & circle colliders, raycasting, plus 2D/3D water buoyancy and surface waves.
* **Audio & Music Synthesizer:** Spatial audio sources and multi-track chiptune music composer.
* **Particle System:** 15+ presets including Fire, Smoke, Rain, Snow, Explosions, Sparks, and Magic.
* **Built-in AI Coding Agent:** Optional multi-provider AI assistant (OpenAI, Gemini, Claude, DeepSeek, Groq, Ollama/Local, Custom URLs, and Offline Planner) that can create scenes, write scripts, fix errors, generate assets, and test builds.
* **Standalone APK Export:** Package, zip-align, and sign standalone APKs directly on device with Android Keystore v2 signing.

---

## 🏗 Modular Architecture

```
Engine Core
 ├── Renderer (GLES2 2D/3D, Shaders, Particles, Post-Processing)
 ├── Scene System (GameObject, Transform Graph, Layers, Groups, Prefabs)
 ├── Asset System (Textures, Audio, Models, Scripts, Scenes)
 ├── Physics Engine (2D/3D Impulse Physics, Water Buoyancy)
 ├── Animation (Sprite Sheet Animator, 3D Skeleton Rigging)
 ├── Audio System (AudioSource, Synthesizer)
 ├── UI Engine (Canvas Widgets, Anchoring)
 └── Scripting Engine (Rhino JS, Native C++, GDScript/Lua Transpiler, Blueprints)
Editor Suite
 ├── Viewport & Inspector
 ├── Script Editor & Code Inspector
 ├── Tilemap Editor
 ├── Sprite & Texture Studios
 ├── UI Creator
 └── Model & Music Editors
Android Build System (APK Writer, AXML Encoder, v2 Signer)
AI Agent (Multi-Provider LLM Client, 26+ Tool Protocol, Offline Planner)
```

---

## 📝 Scripting Examples

### GDScript (`.gd`) Syntax
```gdscript
extends Node2D

var speed = 6.0
var jump_force = 11.0

func _ready():
    print("Player initialized!")

func _process(delta):
    self.vx = input.axisX * speed
    if input.aDown and self.grounded:
        self.vy = jump_force
        audio.beep()
```

### Lua (`.lua`) Syntax
```lua
local coins = 0

function start()
    print("Game started!")
end

function update(dt)
    self.vx = input.axisX * 6.0
    if input.aDown and self.grounded then
        self.vy = 11.0
        audio.beep()
    end
end

function onTrigger(other)
    if other.group == "coins" then
        coins = coins + 1
        other:destroy()
    end
end
```

---

## 🛠 Building from Source

Requirements: JDK 17 and Android SDK (API 34).

```bash
./gradlew assembleDebug
# Test engine & tool suites:
./gradlew test
```

## 📄 License

MIT
