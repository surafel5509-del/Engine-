# S Engine — 3rd Edition

**S Engine** is a 2D **and 3D** game engine **and** full visual editor that runs entirely on an Android phone or tablet – think "a small Unity in your pocket". Create a project, build scenes with a hierarchy / inspector / gizmos, write JavaScript behaviours in the built-in code editor, press **Play** to test immediately, then run your game full-screen.

> Written from scratch in Kotlin. OpenGL ES 2.0 renderer (2D + 3D), custom 2D/3D physics, Mozilla Rhino JavaScript runtime, visual blueprints, custom GLSL shaders, and an on-device APK builder. No NDK, no external game frameworks.

## 🖤 3rd Edition (v4)

| Area | What's new |
|---|---|
| **Look & feel** | New high-contrast **black & white** theme across every screen, redesigned dashboard with hero card and sample-game cards |
| **AI Agent mode** | Type a game idea → the agent **plans a task list, creates the project, writes scripts, generates textures / sprites / music / 3D models, places objects, sets controls, runs the Game Doctor, play-tests headlessly, fixes errors and can build the APK**. 26 tools. Bring your own key: **OpenAI, Anthropic Claude, Google Gemini, OpenRouter (any model), Groq, DeepSeek, Mistral, xAI Grok, Together, Ollama/LM Studio, any OpenAI-compatible URL** — switch models anytime in *Agent Settings*. Works offline with the built-in planner too |
| **Sprite Studio** | Pixel editor with layers (opacity / visibility), frames + onion skin, pencil / eraser / fill / line / rect / ellipse / picker, mirror drawing, palettes, outline / shadow / flip / rotate / shift, undo, export PNG, sprite sheet + ready `.anim` clip |
| **Texture Studio** | 30 procedural styles (bricks, wood, marble, lava, camo, grass, metal, tiles, sci-fi…), seamless tiling preview, colours, scale / roughness / contrast, 64–512 px, optional **normal map** |
| **UI Creator** | WYSIWYG 16:9 canvas editor for game UI (panels, buttons, text, progress bars, images) with drag + snap, layers, properties, and one-tap **ready-made screens**: main menu, HUD, pause, game over, dialog, shop |
| **Water physics** | `Water` component — **buoyancy, drag, currents, rolling waves, spring ripples and splash droplets**; 2D side view and 3D volumes; `obj.submerged` / `obj.inWater` in scripts |
| **Fire & effects** | Particle **presets**: Fire, Torch, Smoke, Dust, Splash, Sparks, Explosion, Rain, Snow, Magic, Bubbles, Steam, Blood, Leaves, Muzzle Flash + turbulence and wind |
| **New games** | **Strike Force** (3D FPS: day + night missions, soldier AI with line of sight, rifle / shotgun / pistol with ADS, recoil and headshots, grenades, exploding barrels, regenerating health, extraction) and **Iron Tanks** (2D tank battle: aiming turret, destructible walls, rivers, bushes, 3 enemy types, HQ defence, HE shells, power-ups, 3 missions with stars) |
| **Engine** | Scripts start before their first physics event (no lost triggers), `scene.find()` prefers live objects, `raycastHit(..., ignore)`, `findInRadius3`, FPS control layout |

### Sample games (all complete: menus, settings, HUD, sounds, music, saves)
* **Sky Strike** – 2D shoot 'em up with 3 levels, bosses and stars
* **Dead Zone** – 2D twin-stick zombie survival with waves, weapons and upgrades
* **Iron Tanks** – 2D top-down tank battle *(new)*
* **Turbo Rally** – 3D racing, 2 maps × 3 roads, AI drivers
* **MiniCraft** – 3D voxel sandbox
* **Strike Force** – 3D first-person shooter *(new)*

## ✨ Ultimate Edition (v2)

| Area | What's new |
|---|---|
| **3D engine** | Meshes (cube, sphere, plane, cylinder, cone, torus, capsule, pyramid, **OBJ models**), Blinn-Phong lighting with a directional light + 4 point lights, ambient, fog, emission, textures with tiling, perspective `Camera3D` with follow/smoothing and sky gradient |
| **3D physics** | `Rigidbody3D` (dynamic / kinematic / static, mass, drag, bounce, friction), box & sphere `Collider3D`, triggers, grounded detection, `scene.raycast()` |
| **3D editor** | Orbit / pan / zoom viewport, 3D gizmos, 2D ⇄ 3D toggle, full 3D transform in the inspector, 3D object menu |
| **Render tools** | Custom **GLSL effect shaders** for sprites and meshes (`uTime`, `uParam`, `uTex`…), camera **post-processing** (grayscale, sepia, vignette, CRT, pixelate, bloom, invert, chromatic aberration, custom shader), camera shake, **profiler overlay** (FPS, frame ms, draw calls, objects) |
| **Sprite animation** | `Animator` component + `.anim` clips; **Animation Editor** with sprite-sheet slicing, tap-to-add frames, live preview, frame strip, all / row / reverse / ping-pong tools |
| **Blueprints** | Node-based **visual scripting** (`.bp`) — ~45 nodes across Events, Flow, Movement, Physics, Objects, Variables, Game — compiled to JavaScript at play time; pan/zoom canvas, drag wires, view generated code |
| **Asset Store** | 70+ built-in, procedurally generated assets: tileable textures, pixel-art sprites, **sprite sheets with ready clips**, synthesized **sound effects & music**, shaders, scripts, blueprints, **3D models**, plus themed packs — one tap to add |
| **Game builder** | **Build APK** on the phone: the runtime is repackaged with your project, the manifest is patched (app name, package, version), the APK is zip-aligned and signed with **APK Signature Scheme v2** (device key in Android Keystore, or your own `.p12`/`.bks`). Install, share or save the result |
| **Scripting** | 3D API (`z rotX rotY vz setPosition(x,y,z) addForce(x,y,z) forward()`), animation (`play() stopAnimation() isAnimationFinished()`), `setShaderParam()`, `scene.shake()`, `scene.raycast()` |
| **Templates** | New **3D Demo**, **Animated Platformer**, **Blueprint Demo** |
| **Quality** | Headless JVM tests run the real engine loop for every template; CI exports a sample game APK and verifies it with `apksigner` and `aapt2` |

---

## Features

| Area | What you get |
|---|---|
| **Project manager** | Create from templates, open, play, rename, duplicate, delete, export / import projects as `.zip` |
| **Scene editor** | Viewport with grid, pan (drag) & pinch-zoom, tap-to-select, **Move / Rotate / Scale gizmos** with axis handles, snapping, frame selected |
| **Hierarchy** | Parent/child tree, collapse, visibility toggle, rename / duplicate / delete / reorder / create child / unparent |
| **Inspector** | Edit name, tag, sorting order, parent, transform (drag labels to scrub values), every component property, color picker, asset pickers, add / remove / reorder / reset components |
| **Undo / Redo** | Snapshot-based history for every edit |
| **Play mode** | Play / Pause / Step frame inside the editor; scene is restored when you stop (like Unity) |
| **Rendering** | Squares, circles, triangles, textured sprites (PNG/JPG/WebP) with flip, text, particles, sorting order, camera background |
| **Physics 2D** | Dynamic / Kinematic / Static rigidbodies, box & circle colliders, gravity, bounciness, friction, drag, triggers, collision & trigger callbacks, `grounded` detection |
| **Scripting** | JavaScript (ES6 subset via Rhino) with `start`, `update(dt)`, `onCollision`, `onTrigger`, `onTap`… plus timers, spawning, messaging |
| **Code editor** | Syntax highlighting, auto-indent, undo/redo, quick-symbol keyboard row, built-in API reference |
| **Assets** | Import images and sounds from the device, create scripts, preview / assign / attach from the Assets panel |
| **Audio** | `AudioSource` component + `audio.play()` / `audio.beep()` |
| **Input** | On-screen joystick + A/B buttons, touch position / taps in world space, hardware keyboard & gamepad (WASD / arrows / Space / Enter) |
| **Scenes** | Multiple scenes per project, start scene, `scene.load("Level2")` |
| **Player** | Full-screen runtime ("Build & Run") with landscape / portrait setting |

### Built-in components
`SpriteRenderer`, `TextRenderer`, `Camera` (size, background, follow target with smoothing), `Rigidbody2D`, `Collider2D`, `Script`, `ParticleEmitter`, `AudioSource`.

### Templates
* **Empty 2D** – camera + square
* **Platformer Demo** – run, jump, moving platform, coins with particle bursts, score UI
* **Space Shooter** – spawning enemies, bullets, explosions, score, game over
* **Physics Sandbox** – tap to drop bouncy balls and crates onto a pyramid
* **3D Demo** – 3D character with physics, crates, balls, OBJ trees/rocks, lights, fog, toon & pulse shaders, pickups
* **Animated Platformer** – sprite-sheet hero with run animation, spinning coins, patrolling slime, textures & sounds
* **Blueprint Demo** – a player, pickups and a spinner built entirely with visual nodes

### Building a standalone game (APK)
Editor menu **⋮ → Build APK…** → set app name, package (e.g. `com.mystudio.mygame`) and version → choose signing
(device key or your own keystore) → **Build**. The finished APK in `builds/` can be installed directly, shared or saved.
Keep using the **same key** for updates of the same game.

---

## Scripting example

```js
// Player.js  – params on the Script component: "speed=6, jump=11"
var coins = 0;

function update(dt) {
    self.vx = input.axisX * speed;              // joystick / A-D keys
    if (input.aDown && self.grounded) {         // A button / Space
        self.vy = jump;
        audio.beep();
    }
    if (self.y < -12) scene.reload();
}

function onTrigger(other) {
    if (other.tag == "Coin") {
        coins++;
        var fx = scene.spawn("CoinFX", other.worldX, other.worldY);
        fx.burst(24);
        after(1.5, function () { fx.destroy(); });
        other.destroy();
        scene.find("ScoreText").text = "Coins: " + coins;
    }
}
```

### API summary

* **Lifecycle:** `start()`, `update(dt)`, `onCollision(other)`, `onTrigger(other)`, `onTriggerExit(other)`, `onTap()`, `onDestroy()`, `onStop()`
* **self / transform:** `name tag active order x y rotation scaleX scaleY worldX worldY vx vy grounded color visible flipX text size`, `setPosition() move() rotate() addForce() setVelocity() destroy() child() parent distanceTo() overlaps() send() burst() setEmitting() setTexture() hasComponent() setComponentEnabled()`
* **input:** `axisX axisY a b aDown bDown touching tapped touchX touchY`
* **scene:** `find(name) findAll(tag) count(tag) spawn(name, x, y) load(name) reload() camera gravityX gravityY`
* **time:** `time.time time.frame time.fps` — **audio:** `play(file) beep() stopAll()`
* **helpers:** `log() warn() error() after(sec, fn) every(sec, fn) random() randomInt() clamp() lerp()`
* **3D:** `z rotX rotY rotZ scaleZ worldZ vz`, `setPosition(x,y,z) move(dx,dy,dz) rotate(rx,ry,rz) setVelocity(x,y,z) addForce(x,y,z) distanceTo3(o) forward() setMeshColor(c)`
* **animation / render:** `play(clip) stopAnimation() animation isAnimationFinished() setAnimSpeed(s) setShaderParam(v)`
* **scene (v2):** `spawn(name,x,y,z) shake(amount) raycast(ox,oy,oz,dx,dy,dz,max) camera3D gravity3D`
* **shaders:** `vec4 effect(vec4 color, vec2 uv)` with `uTime uParam uTex uUseTex uColor uResolution`

Inactive objects make great **prefab templates** – `scene.spawn("Enemy", x, y)` clones them and activates the copy.

---

## Download

Every push is built by GitHub Actions. Grab the APK from:

* **Releases → "S Engine – latest build"** → `SEngine.apk`, or
* **Actions → latest "Build S Engine APK" run → Artifacts → `SEngine-debug-apk`**

Enable "Install unknown apps" for your browser / file manager, then open the APK.

## Building

Requirements: JDK 17 and the Android SDK (API 34). Android Studio Hedgehog or newer works out of the box.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push is also built by GitHub Actions (`.github/workflows/android.yml`); download the APK from the run's **Artifacts** section.

Minimum Android version: 8.0 (API 26). Requires OpenGL ES 2.0.

## Project layout

```
app/src/main/java/com/sengine/
├── engine/
│   ├── Engine.kt            main loop, play/pause/stop, camera follow, particles
│   ├── Input.kt, AudioSystem.kt
│   ├── core/                GameObject, Component, Prop system, components, Scene + JSON serializer
│   ├── math/                Affine (2D) and Mat4 (3D) transforms
│   ├── physics/             impulse-based 2D and 3D physics
│   ├── anim/                animation clips + Animator system
│   ├── blueprint/           visual-script graph, node library, JS compiler
│   ├── render/              GLES2 2D/3D renderers, meshes/OBJ, shader library, post-processing, gizmos
│   └── script/              Rhino JavaScript runtime + script API
├── agent/                   AI Agent: LLM clients (many providers), tool protocol, offline planner
├── export/                  APK builder: zip writer, manifest (AXML) patcher, v2 signer, keys, game runtime
├── project/                 project storage, zip import/export, templates, asset store library
└── ui/                      Projects screen, Editor (hierarchy, inspector, viewport, assets, console),
                             Script / shader editor, Blueprint editor, Animation editor, Asset Store,
                             Build APK screen, full-screen Player, joystick, color picker
```

Projects are stored in app-private storage as plain JSON scenes plus an `assets/` folder:

```
<project>/project.json
<project>/scenes/Main.scene.json
<project>/assets/Player.js, hero.png, jump.wav …
```

## License

MIT
