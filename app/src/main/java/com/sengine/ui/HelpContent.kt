package com.sengine.ui

/**
 * Built-in documentation. Articles use a tiny markup:
 *   "# " heading, "## " sub-heading, "- " bullet, "```" code block toggle, blank line = paragraph.
 */
object HelpContent {
    class Topic(val id: String, val title: String, val icon: String, val body: String)

    val topics: List<Topic> = listOf(
        Topic("start", "Getting Started", "rocket", """
# Welcome to S Engine
S Engine is a complete 2D + 3D game engine and editor that runs entirely on your phone. You build scenes, write scripts (JavaScript) or blueprints, make music, model and animate 3D objects, design UI and controllers, then export a real installable APK.

## Your first game in 5 minutes
- On the Projects screen tap New Project and pick a template (Platformer, Space Shooter, 3D Demo, Racing, MiniCraft...).
- The editor opens: Hierarchy on the left, the Scene view in the middle, the Inspector on the right, Console/Assets at the bottom.
- Press Play (green triangle) to run the game inside the editor. Press Stop to return — every change made while playing is reverted.
- Select an object, change its properties in the Inspector, press Play again.
- Open Build to export an APK you can install or share.

## Where things are
- Toolbar: Play, Pause, Step, Undo/Redo, Move/Rotate/Scale tools, 2D/3D toggle, Scenes, Build, Help.
- Assets panel: + Script, + Blueprint, + Shader, + Animation, + Song, + 3D Model, Asset Store, Import.
- Long-press any toolbar icon to see what it does.

## Learn by example
The four sample games (Top-down Shooter, Dead Zone, Racing, MiniCraft) are complete and open-source inside the app: create them from the templates and read their scripts.
"""),
        Topic("editor", "Editor Tour", "layers", """
# The Editor
## Hierarchy
Shows every object in the scene as a tree. Tap to select, use the menu to rename, duplicate, delete, create children or reorder. Inactive objects are shown dimmed; they don't run but can be spawned as templates (scene.spawn).

## Scene view
- 2D: drag with one finger to use the current tool, two fingers to pan and pinch to zoom.
- 3D: one finger orbits (or uses the tool when an object is selected), two fingers pan/zoom.
- Grid snapping, gizmos for move/rotate/scale and a camera preview frame are available from the toolbar.

## Inspector
Every component shows its properties: numbers (drag or type), colours (colour picker), choices, asset pickers and text. Add components with + Add Component. Remove or disable with the component menu.

## Console
Logs from scripts (log/warn/error), physics warnings and script errors with line numbers. Tap an error to jump to the script.

## Play mode
The scene is snapshotted when you press Play and restored on Stop. The profiler overlay shows FPS, frame time, draw calls and object counts.
"""),
        Topic("objects", "Objects & Components", "cube", """
# Game Objects
Everything in a scene is a GameObject with a transform (position, rotation, scale — plus z, rotX, rotY, scaleZ for 3D) and a list of components.

## 2D components
- SpriteRenderer — shape (rect, circle, triangle, ring, rounded) or texture, colour, flip, tiling, shader.
- TextRenderer — world-space text.
- Camera2D — size, background colour, follow target with smoothing, bounds, Post FX.
- Rigidbody2D / Collider2D — physics bodies (dynamic, kinematic, static), box/circle colliders, triggers.
- ParticleEmitter — rate, bursts, lifetime, speed, gravity, colours, size over life.
- Animator — plays sprite-sheet .anim clips.
- AudioSource — plays a sound or .song on start.
- ScriptComponent — runs a .js or .bp script with parameters.

## 3D components
- MeshRenderer — Cube, Sphere, Plane, Cylinder, Cone, Capsule, Torus, imported OBJ or Custom Model (.smodel with animations), colour, texture, shader, cast shadows.
- Camera3D — FOV, clear colour, fog, skybox, follow, shadows, shadow distance, sun disc and quality.
- Light — directional, point and ambient.
- Rigidbody3D / Collider3D — box/sphere/capsule physics with friction, bounciness, sleeping.
- VoxelWorld — block worlds with terrain generation, trees, water and saving.

## UI components
UIPanel, UIButton and UIProgress live in screen space with anchors (see Game UI).
"""),
        Topic("scripting", "Scripting Basics", "code", """
# Scripts (JavaScript)
Scripts are ES6-style JavaScript. Create one with + Script, attach it to an object and define lifecycle functions:

```
var speed = 5;
function start() { log("Hello from " + self.name); }
function update(dt) {
  self.x += input.axisX * speed * dt;
  if (input.aDown && self.grounded) self.vy = 9;
}
function onCollision(other) {
  if (other.tag == "Coin") { other.destroy(); audio.play("coin.wav"); }
}
```

## Parameters
Put "speed=5, jump=10" in the Script component's Params field — they become variables so you can tune each object in the Inspector.

## Talking to other objects
- scene.find("Player"), scene.findAll("Enemy"), scene.nearest("Enemy", x, y)
- other.send("takeDamage", 10) calls takeDamage(10) in the other object's scripts.
- scene.spawn("Bullet", x, y) clones an (often inactive) template object.

## Timers
after(2, function() { ... }) and every(0.5, function() { ... }).

## Errors
Syntax and runtime errors appear in the Console with file and line. Game Doctor (Help → Doctor) checks all scripts at once.

The full API reference is in the next topic.
"""),
        Topic("api", "Script API Reference", "book", "# Script API\n```\n" + ScriptEditorActivity.API_DOC + "\n```\n"),
        Topic("input", "Input & Game Controls", "gamepad", """
# Input
Touch, on-screen controllers, keyboards and gamepads all feed the same input API.

## On-screen controller
Open Controls (gamepad icon) to design the controller for your game:
- Start from a preset: Classic (Joystick + A/B), Platformer (D-Pad), Twin-Stick Shooter, Action (Fire + Jump), Racing, First Person / Builder, Touch Only.
- Add buttons, joysticks, D-pads, steering arrows, look pads and aim sticks.
- Set each control's id, label, icon, colour, size and opacity. Drag to position. Tap Test to try it live.
- Save — it is used by Play and by exported games.

## Reading input in scripts
```
input.axisX, input.axisY       // "move" joystick / D-pad / WASD
input.button("Fire")           // held
input.buttonDown("A")          // pressed this frame
input.stickX("aim")            // second joystick
input.lookX, input.lookY       // look-pad drag (FPS camera)
input.tapped, input.touchX     // taps in world space
```

## Switching controllers at runtime
input.setControls("Racing"), input.setControls("project") and input.showControls(false) — e.g. hide the controller in menus.
"""),
        Topic("ui", "Game UI System", "ui", """
# Game UI
Build menus, HUDs, pause screens and settings with UI components. UI uses screen coordinates: the centre is 0,0, the screen is 10 units high and the width depends on the aspect ratio. Anchors (Top Left, Bottom, Right...) keep elements glued to screen edges on every phone.

## Components
- UIPanel — background rectangle with colour, corner radius, border and optional texture.
- UIButton — text, colours (normal/pressed/disabled), corner radius, font size, icon, click sound and an Action.
- UIProgress — health/energy/loading bars with fill colour and direction.
- TextRenderer works for labels.

## Button actions (no code needed)
Separate several with ';':
- scene:Level1 — load a scene
- show:PausePanel / hide:PausePanel / toggle:Settings
- pause / resume — time.scale 0 / 1
- call:startGame — calls startGame(buttonName) in every script
- reload, quit, url:https://example.com

## From scripts
ui.setText("Score", "Score: " + score), ui.setProgress("HealthBar", hp / 100), ui.show("GameOver"), function onUIClick(name) { ... }.

A click sound (ui_click.wav) plays automatically if present in Assets.
"""),
        Topic("physics", "Physics 2D & 3D", "physics", """
# Physics
## 2D
Rigidbody2D (Dynamic, Kinematic, Static) + Collider2D (Box/Circle, trigger). Gravity is per scene (scene.gravityY). grounded tells you when a body stands on something. Use addForce for impulses and setVelocity for direct control. scene.raycast2D casts rays against colliders.

## 3D
Rigidbody3D + Collider3D (box, sphere, capsule) with mass, friction, bounciness and drag. The solver uses a sweep-and-prune broad phase and iterative contacts, bodies go to sleep when resting (wake() to wake them), and bodies collide with voxel terrain. scene.raycastHit returns the hit point and normal.

## Triggers
Tick Is Trigger: onTrigger(other) and onTriggerExit(other) are called instead of solid collisions — perfect for pickups, checkpoints and damage zones.
"""),
        Topic("threed", "3D Worlds & Graphics", "sun", """
# 3D
Toggle 3D in the toolbar. Add 3D objects from the + menu: primitives, lights, cameras, models, voxel worlds.

## Graphics quality
Camera3D has Quality (Low / Medium / High / Ultra), real-time directional shadows (with distance), fog, sky gradient + sun disc, and Post FX (bloom, vignette, colour grading, custom shaders). Low quality disables shadows for older phones.

## Models
- Custom Model: .smodel files made in the 3D Model Editor, with animations (playModelAnim("Walk")).
- OBJ import for models made in other tools.

## Performance tips
- Keep draw calls low: reuse materials/colours, avoid thousands of separate objects (use VoxelWorld or tiling).
- Use Low quality and fewer shadow casters on older devices.
- The profiler shows FPS and draw calls while playing.
"""),
        Topic("model", "3D Model Editor", "mesh", """
# 3D Model Editor (Blender-lite)
Create, edit and animate models right on your phone. Create one with + 3D Model in Assets, or open any .smodel.

## Modes
- Object — parts (meshes) with position/rotation/scale, colour, smooth shading and parenting. Add primitives (Cube, Plane, Cylinder, Cone, Sphere, Torus, Wedge, Tube) or preset models (Car, Character with Walk/Idle/Wave, Tree, House, Sword, Rock, Spaceship, Turret).
- Edit — select vertices or faces and model: Extrude, Inset, Subdivide, Smooth Subdivide (Catmull-Clark), Smooth, Mirror X, Merge by distance, Flip normals, Paint face colours, Delete, Randomize.
- Animate — clips (Idle, Walk...) with keyframes per part. Auto-key: move/rotate/scale a part at the playhead and a key is written. Play to preview, set clip length and looping.

## Gestures
- One finger: current tool (Move/Rotate/Scale) — or orbit with the Select tool.
- Two fingers: orbit + pinch zoom. Three fingers: pan. Double-tap: focus selection.
- Lock an axis with X / Y / Z. The magnet snaps to 0.1 units / 15°.

## Using models in games
Tap the model in Assets → Create 3D Model Object. In scripts: self.playModelAnim("Walk"). Export OBJ to use elsewhere.
"""),
        Topic("anim", "Animation", "film", """
# Animation
## Sprite animation (.anim)
Open + Animation: pick a sprite sheet, slice it into cells, select frames, set FPS and looping, preview and save. Add an Animator to a SpriteRenderer object (or self.play("Run.anim") from scripts).

## Model animation
Keyframed part animation in the 3D Model Editor (see 3D Model Editor). MeshRenderer has Animation + Anim Speed fields, or use playModelAnim / stopModelAnim / setModelAnimSpeed from scripts.

## Code animation
Tween values yourself with lerp / smoothDamp in update(dt).
"""),
        Topic("music", "Music Editor & Audio", "music", """
# Music Editor
Compose game music with a built-in synthesizer — no samples needed. Create with + Song.

## Tracks
Each track has an instrument (Square Lead, Soft Sine, Triangle Bass, Saw Pad, Pluck, Drum Kit, Chip Arp, Organ, Brass, Bell), volume, mute/solo and echo.

## Piano roll
- Tap an empty cell to add a note; drag to set its length.
- Tap a note to delete it; drag its start to move it (also up/down to change pitch).
- Two fingers scroll. Tap piano keys to preview. The Drum Kit shows 8 rows: Kick, Snare, Hats, Clap, Toms, Crash.

## Auto Compose
Generates a full arrangement (drums, bass, chords, melody) in 8 styles: Chiptune Adventure, Action Battle, Chill Lo-Fi, Racing Rush, Spooky Night, Victory Fanfare, Menu Theme, Block World. Change the seed for variations and the key.

## Using music
- audio.playMusic("Theme.song") — songs are rendered once and cached.
- Or Export WAV into Assets.
- AudioSource with a .song clip plays it on start.

## Sound effects
The Asset Store includes procedurally generated SFX (jump, coin, laser, explosion, hit, power-up, UI click, engine...). audio.play(name, volume, pitch).
"""),
        Topic("blueprints", "Blueprints (Visual Scripting)", "blueprint", """
# Blueprints
Visual scripting with nodes: events (Start, Update, Collision, Trigger, Tap, Button), actions (move, set velocity, spawn, destroy, play sound, load scene, log) and logic (if, compare, math, variables, random, timers). Connect execution pins and data pins, then attach the .bp file like a script — it compiles to JavaScript automatically.
"""),
        Topic("shaders", "Shaders & Post FX", "shader", """
# Shaders
Write GLSL effect functions:
```
vec4 effect(vec4 color, vec2 uv) {
  float w = sin(uv.x * 20.0 + uTime * 4.0) * 0.5 + 0.5;
  return vec4(color.rgb * (0.6 + 0.4 * w), color.a);
}
```
Uniforms: uTime, uParam (self.setShaderParam), uTex, uUseTex, uColor, uResolution. Assign to SpriteRenderer/MeshRenderer Shader, or to a camera's Post FX = Custom Shader. The Asset Store has ready shaders (water, hologram, dissolve, outline, pixelate, CRT, toon...).
"""),
        Topic("voxel", "Voxel Worlds (MiniCraft)", "mountain", """
# Voxel Worlds
Add a VoxelWorld component for Minecraft-style worlds: size, seed, terrain height, trees, water level. Blocks: grass, dirt, stone, sand, water, wood, leaves, planks, brick, glass, cobblestone, snow, bedrock, gold.

```
var hit = voxel.raycast(cam.x, cam.y, cam.z, f.x, f.y, f.z, 6);
if (hit) voxel.setBlock(hit.x, hit.y, hit.z, 0);   // break
voxel.save("world1"); voxel.load("world1");
```
Rigidbody3D objects collide with blocks. See the MiniCraft sample for a complete builder game with hotbar, day/night and saving.
"""),
        Topic("save", "Saving Data", "save", """
# Saving
storage keeps values between sessions (per game, separate from the editor):
```
var best = storage.getNumber("best", 0);
if (score > best) storage.set("best", score);
storage.set("settings", { music: 0.8, sfx: 1 });
```
Voxel worlds save with voxel.save(name). Data files (.json/.txt/.csv) in Assets can be read with assets.text(name) or loadJSON(name).
"""),
        Topic("scenes", "Scenes, Menus & Levels", "scene", """
# Scenes
A game is a set of scenes: Menu, Level1, Level2, GameOver... Create them from the Scenes menu. The Start scene (Project settings) opens first.
- scene.load("Level2") or a UIButton with Action scene:Level2.
- Keep global data (score, unlocked levels) in storage.
- Duplicate a scene to make new levels quickly.
"""),
        Topic("build", "Build & Publish APK", "hammer", """
# Building your game
Open Build (hammer icon).
## App
App name, package name (com.studio.game), version name and code (auto-increment).
## Icon & logo
Upload any image — it is cropped to a square, resized to 192×192 and becomes the launcher icon and the splash logo.
## Display & startup
Orientation (project default / landscape / portrait / auto-rotate), start scene, fullscreen, keep screen on, FPS counter, splash screen text and duration.
## Signing
Device key (automatic, stored in Android KeyStore) or your own .p12/.bks keystore. Always sign updates with the same key.
## Result
Install directly, share, or save the APK. The APK is v2-signed and contains the S Engine runtime + your project.
"""),
        Topic("doctor", "Game Doctor", "doctor", """
# Game Doctor
Help → Doctor scans your project and lists problems by severity with one-tap fixes:
- script syntax errors (with line numbers)
- missing textures, sounds, scripts or models referenced by components
- scenes without cameras, missing start scene
- rigidbodies without colliders
- buttons that open missing scenes or target missing objects
- scripts that load missing scenes or play missing sounds
- oversized textures and heavy scenes
Run it before every build.
"""),
        Topic("samples", "Sample Games", "trophy", """
# Sample games
All four are made with S Engine only (scenes, scripts, UI, controls, music) — create them from New Project → templates and study or remix them.
- Top-down Shooter — 3 levels, enemy waves, boss fight, pickups, menu, HUD, pause, level select, high score.
- Dead Zone (2D zombies) — endless waves, auto-aim, pistol/shotgun, reloading, ammo pickups, high score.
- Racing — 3D car racing with 2 maps × 3 roads, AI opponents with waypoints and rubber-banding, laps, countdown, positions, nitro, settings and a racing controller.
- MiniCraft — 3D voxel builder: first-person look pad, break/place blocks, hotbar, day/night cycle, world saving.
"""),
        Topic("faq", "Troubleshooting & FAQ", "help", """
# FAQ
## My object falls through the floor
Both need colliders; the floor should be Static. Game Doctor detects rigidbodies without colliders.
## Nothing shows in 3D
Add a Camera3D and a Light; check the camera looks at your objects (Camera3D follow or rotation).
## Buttons don't respond
UI buttons must be active and on top; check the Action text. Scripts receive onUIClick(name).
## The game is slow
Lower Camera3D quality, reduce shadow casters and particles, keep object counts reasonable, use the profiler.
## My script doesn't run
It must be attached with a ScriptComponent and have no syntax errors (see Console or Game Doctor).
## Can I use my own models/sounds/images?
Yes — Import in Assets: PNG/JPG, WAV/OGG/MP3, OBJ.
## How do I back up a project?
Projects screen → project menu → Export (zip). Import it on another device.
"""),
    )

    val recipes: List<com.sengine.project.ScriptRecipes.Recipe> get() = com.sengine.project.ScriptRecipes.all
}
