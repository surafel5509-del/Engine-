# S Engine — Ultimate Edition (v7) — engineering plan & module contracts

Goal: turn S Engine v6 into a professional, Unity/Unreal-class engine core — a data oriented
runtime, a full rigid body physics engine, AAA-class animation, AI/navigation, a render pipeline,
an asset pipeline, networking, audio DSP, VFX, cinematics, input and editor tooling — all of it
headless-testable on the JVM and running on Android (OpenGL ES 2.0 + NDK).

Everything in this document is a **contract**. Follow it exactly so the modules integrate.

---

## 1. Ground rules

1. **Pure Kotlin, no new dependencies.** Only `kotlin.*`, `java.*` and `org.json` (already on the
   classpath). Never import `android.*` inside `com.sengine.engine.*` subsystems — those must run in
   headless JVM unit tests. (Rendering code that must talk to GL belongs in the existing
   `engine/render/*.kt` files owned by the lead.)
2. **File ownership is exclusive.** Only write files in your own directories (table in §2). Never
   edit files owned by another module, by the lead, or anything under `com/sengine/ui/`,
   `com/sengine/project/`, `Engine.kt`, `Scene.kt`, `Components.kt`, `README.md`.
3. **Style.** Kotlin, 4-space indent, `camelCase` functions, `PascalCase` types, KDoc on every public
   class/function of substance. `Float` maths. Deterministic ordering everywhere (sort by id, never
   rely on `HashMap` iteration order). No global mutable singletons — instantiate classes and let the
   lead own the instance in `Engine`.
4. **Thread safety.** Subsystems may use `com.sengine.engine.jobs.JobSystem` for parallelism. Shared
   state must be written per-index (no lock-free append from workers).
5. **Tests are mandatory.** Every module ships `app/src/test/java/com/sengine/<Name>Test.kt`
   (JUnit 4, unique class name, no Android APIs). Tests must actually assert engine behaviour
   (stability, invariants, round-trips, determinism) — not just `assertNotNull`. Print a `SIM <module> ...`
   line at the end of each test, matching the existing house style.
6. **Never run git.** The lead commits. Never run Gradle.
7. **Verify before you report.** See §4.

---

## 2. Module ownership

| Module | Directory (create files there) | Test class |
|---|---|---|
| **Lead (reserved)** | `engine/math/**`, `engine/jobs/**`, `engine/ecs/**`, `engine/core/**`, `engine/Engine.kt`, `engine/script/**`, existing `engine/render/*.kt`, `com/sengine/ui/**`, `docs/**`, `README.md` | lead |
| **Physics Pro** | `engine/physics/*.kt` (new files only, keep the existing 3 files untouched) | `UltimatePhysicsTest` |
| **AI & Navigation** | `engine/ai/**` | `UltimateAiTest` |
| **Animation Pro** | `engine/anim/*.kt` (new files only) | `UltimateAnimTest` |
| **Render pipeline** | `engine/render/pipeline/**` | `UltimateRenderTest` |
| **Assets & serialization** | `engine/assets/**` | `UltimateAssetsTest` |
| **Networking** | `engine/net/**` | `UltimateNetTest` |
| **Audio DSP + VFX** | `engine/audio/*.kt` (new files), `engine/vfx/**` | `UltimateAudioVfxTest` |
| **Gameplay framework** | `engine/gui/**`, `engine/input/**`, `engine/cinematics/**`, `engine/editor/**` | `UltimateGameplayTest` |

---

## 3. Foundation APIs (already implemented, use them)

`com.sengine.engine.math`
* `Vec3(x, y, z)` — mutable, operators `+ - * /`, `add`, `addScaled`, `crossSet`, `dot`, `normalize`,
  `normalizedOr(fallback)`, `length`, `distanceTo`, `lerp`, `clampLength`, `min/max/abs`,
  `angleTo`, `rotateAxis`, `isFinite`, `Vec3.of/UP/FORWARD/fromYawPitch/toYaw/toPitch`.
* `Quat(x, y, z, w)` — `mul`, `rotate(v)`, `rotateInverse`, `normalize`, `inverse`, `toMat3/toMat4`,
  `toEulerDeg`, `Quat.fromAxisAngle/fromEulerDeg/fromTo/fromMat3/fromMat4/slerp/nlerp/integrate`.
* `Aabb3` — `min/max` corners, `expand`, `grow`, `contains`, `intersects`, `intersectsObb`,
  `center`, `extents`, `size`, `radius`, `surfaceArea`, `volume`, `transformedBy(mat4)`, `sweep(dx,dy,dz)`,
  `Aabb3.fromCenterExtents/fromCenterRadius/fromPoints`.
* `Ray3(origin, dir)`, `Plane3(nx,ny,nz,d)` with `set`, `setPointNormal`, `distance`,
  `Frustum3` with `setFromMatrix(columnMajor4x4)`, `testSphere`, `classifySphere`, `testAabb`, `classifyAabb`.
* Existing: `Mat4` (column-major, `mul`, `trs`, `perspective`, `lookAt`, `invert`, `point`, `dir`), `Affine`.

`com.sengine.engine.jobs`
* `JobSystem(threadCount)` — `parallelFor(count, grain, name) { i -> ... }`, `submit { }`, `complete()`,
  `waitAll()`, `checkFailures()`, `workerCount()`, `shutdown()`, `stats()`. Use `workerCount()` to decide
  whether parallelism is worth it. A `parallelFor` body must only write the slot it is given.

`com.sengine.engine.ecs`
* `Entity` (value class, `Entity.of(index, gen)`, `Entity.NULL`, `index`, `generation`, `isNull`).
* `EcsWorld` — `create(name, parent)`, `destroy`, `isAlive`, `add/get/remove/has(e, type)`,
  `store<T>()`, `query(vararg Class)`, `queryEntities`, `each(typeA, typeB) {}`, `count`, `nameOf`,
  `register(typeName, type, write, read)`, `toJson()/fromJson()`, `clone()`, `clear()`, `stats()`, `events`.
* `ComponentStore<T>` — dense `entityAt(i)`, `itemAt(i)`, `size`.
* `EcsEvents`, `CommandBuffer`, `EcsSystem`, `SystemScheduler` (see `engine/ecs/EcsSystems.kt`).

---

## 4. Verification (run these yourself)

```bash
# quick type-check of just your files (fast, ~20-40 s, serialized):
bash /tmp/tools/check-fast.sh app/src/main/java/com/sengine/engine/ai/NavMesh.kt app/src/main/java/...

# full project type-check (slower, ~2 min):
bash /tmp/tools/check.sh

# actually run your unit tests headlessly (compiles main+test, then JUnit):
bash /tmp/tools/run-tests.sh com.sengine.UltimateAiTest
```

All three scripts serialize on a lock file because the sandbox has a 6 GB memory cap — if a script
seems to hang it is waiting for another compile to finish. Never run two compiles at once, never
run Gradle, never edit files outside your module while a compile runs.

---

## 5. Deliverable requirements per module

### 5.1 Physics Pro (`engine/physics`, test `UltimatePhysicsTest`)
A real rigid-body engine, not the toy AABB/sphere one:
* Shapes: sphere, box (OBB), capsule, cylinder, convex hull, triangle mesh (static, with BVH), plane,
  heightfield, compound. `Shape3D` hierarchy with `support(dir)`, `aabb`, `massProperties(density)`.
* Broadphase: dynamic AABB tree **and** sweep-and-prune, with fat AABBs, pair caching and
  `collisionFilter` (layer/mask).
* Narrowphase: GJK + EPA for convex pairs, SAT for box/box manifolds, analytic sphere/capsule/plane
  paths. Persistent contact manifolds with stable feature ids for warm starting.
* Solver: sequential impulses with warm starting, Coulomb friction (2 axes), restitution with
  velocity threshold, Baumgarte/position correction, sleeping islands, sub-stepping, CCD (swept
  sphere + conservative advancement) and fixed timestep accumulator with interpolation.
* Joints: fixed, hinge (motor + limits), slider, ball-socket (cone limit), distance/spring, gear,
  wheel, breakable. `Joint3D` hierarchy with `prepare(dt)` / `solve(iterations)`.
* Characters: capsule character controller (slope limit, step offset, snap-to-ground, ground probing).
* Vehicles: raycast vehicle (suspension, engine torque, steering, tyre friction curves).
* Ragdolls: build a ragdoll from a skeleton (`engine/anim` skeleton type is off-limits — accept
  plain joint lists of `Vec3` positions/parents) with joint limits.
* Destruction: pre-fracture a box/mesh into convex pieces, breakable joints/constraints, impulse
  thresholds, debris lifetime and pooling.
* Soft bodies: PBD mass-spring cloth and rope with structural/shear/bend constraints, pinning and
  collision against the rigid world.
* Queries: raycast, sphere/box overlap, shape cast, closest point, per-filter, with hit normals,
  body ids, sub-shape and distance. Deterministic ordering.
* Integration hook: `ProPhysicsWorld` with `step(dt)`, `addBody`, `removeBody`, `raycast`, `overlapSphere`,
  stats (`bodyCount`, `contactCount`, `activePairs`, `islands`, `stepMs`) and a `PhysicsBody3D`
  description class. Provide a `SyncFromScene`/`SyncToScene` pair that maps `GameObject` transforms
  (the lead wires it up) — keep the mapping functions explicit and documented.
* Tests: box stack stability over 600 steps (< 1 cm drift), pendulum energy conservation,
  sphere-plane bounce, capsule character walks up a 30° slope and steps over a 0.3 m curb,
  CCD stops a fast bullet at a thin wall, sleeping wakes on impact, cloth settles within 2 % of
  rest length, convex hull collision normals point along the separating axis, raycast hits.

### 5.2 AI & Navigation (`engine/ai`, test `UltimateAiTest`)
* NavMesh build from triangle soup: voxelisation → compact heightfield → distance field → regions →
  contours → polygon mesh → detail mesh, with agent radius/height/step/max-slope parameters.
* A* over the polygon graph + funnel (string-pulling) path smoothing, path corridor queries,
  partial paths, off-mesh links, dynamic obstacles (tile/area invalidation + rebuild).
* `NavMeshAgent` steering: seek/flee/arrive/pursue/evade/wander/follow-path/avoid-obstacles, RVO-style
  local avoidance for crowds, separation, formation.
* Behaviour trees: composites (sequence/selector/parallel/random), decorators (inverter/succeeder/
  cooldown/repeat/until-fail/condition), leaves, blackboard with typed keys, node status, tick budget,
  tree visualisation (`describe()`), and a `BehaviourTreeRunner`.
* Perception: sight (cone + FOV + line of sight via a caller-supplied occlusion callback), hearing
  (stimulus radius), memory decay, target selection with priorities.
* Utility AI: scored actions with response curves, considerations, cooldowns; GOAP planner
  (preconditions/effects, A* over world state) with plan validation and replanning.
* Squad/team logic: shared blackboard, roles, orders queue, cover point selection.
* Tests: navmesh over a plane with a wall produces a path that does not cross the wall; funnel
  path length <= raw A* path length and is collision free; agent reaches its target in a simulated
  loop; BT sequence/selector semantics incl. running state and cooldowns; perception detects and
  loses targets with memory; GOAP finds the shortest plan; crowd of 50 agents keeps min separation.

### 5.3 Animation Pro (`engine/anim`, test `UltimateAnimTest`)
* `Skeleton` (joints with parent indices, rest pose TRS, inverse bind matrices), `SkeletonPose`
  (local + world TRS arrays), `computeWorld()` with `JobSystem` parallel-for over joints, `skinMesh()
  (bind pose + skin weights, up to 4 influences)`, skin matrix palette.
* Animation clips: keyframe tracks (pos/rot/scale + generic float curves), step/linear/cubic
  (Catmull-Rom) interpolation, looping/ping-pong/clamp wrap modes, event tracks, root motion
  extraction (bake to curves or apply to a transform), clip sampling with `JobSystem`.
* Blending: 1D blend tree (thresholds), 2D blend tree (freeform/ cartesian), additive layers,
  layer masks (per-joint weights), mirroring, time sync (sync groups), playable graph with
  mixers, weight fading/crossfade.
* State machine: states, transitions (conditions, exit time, duration, interruption sources),
  parameters (float/bool/trigger), any-state transitions, sub-state machines, transition
  crossfade duration, `AnimatorController` description + `AnimatorRuntime` evaluation.
* IK: two-bone analytic IK (with pole vector + joint limits), FABRIK chains, foot placement on a
  height probe, look-at, hand IK, full-body pass with weight blending.
* Retargeting: map skeletons by joint role/name, transfer clips preserving proportion (normalised
  limb lengths), root motion scale.
* Tests: skinning a cylinder with 4 weights matches a hand-computed matrix; cubic sampling matches
  known values; blend tree weights sum to 1 and blend linearly; state machine transitions on
  parameter + exit time and crossfades; two-bone IK places the hand within 1 mm of a reachable
  target and clamps an unreachable one; retargeting a walk clip keeps foot contact timing.

### 5.4 Render pipeline (`engine/render/pipeline`, test `UltimateRenderTest`)
CPU-side, headless-testable pipeline + GLSL ES sources (strings) that the lead can hook into
`Renderer3D`/`SceneRenderer` later:
* `RenderGraph`: passes with read/write attachments, automatic dependency ordering, aliasing of
  transient targets, culling of unused passes, `execute()` with timing stats.
* Visibility: frustum culling (uses `Frustum3`), occlusion culling (hierarchical Z / portal-lite),
  shadow-caster culling, per-camera visible sets, sort keys (opaque front-to-back, transparent
  back-to-front), instancing/batching groups (by material+mesh), draw-call and triangle counters.
* LOD: discrete LOD chains with screen-space-error or projected-size thresholds + hysteresis,
  cross-fade (dithered) weights, automatic LOD generation by mesh decimation (quadric-lite /
  vertex clustering) with bounds preservation.
* Lighting: directional/point/spot/area parameters, clustered forward light culling (cluster grid,
  light lists per cluster), light probes + spherical harmonics ambient (L1), reflection probes,
  shadow atlas allocation, cascaded shadow maps (3-4 cascades, stable splits, texel snapping).
* Materials: `Material` (PBR metallic-roughness, textures, keywords, variants cache, instancing
  buffer layout, `keywordHash`), shader permutation manager with variant invalidation.
* Post FX maths: ACES/Filmic/Reinhard/Neutral tonemapping, exposure adaptation, bloom (bright pass +
  13-tap/dual-filter downsample chain), SSAO (hemisphere sampling with rotation noise), FXAA,
  colour grading (lift/gamma/gain, LUT), vignette, chromatic aberration, motion blur accumulation,
  temporal AA jitter sequence — all as pure functions on float arrays so they are unit testable,
  plus matching GLSL ES source strings.
* Frame stats/profiler hooks: per-pass GPU/CPU ms ring buffers, budget warnings.
* Tests: graph orders passes correctly and drops dead ones; culling keeps what the frustum sees
  and rejects what it does not; LOD picks the right level incl. hysteresis; cluster assignment
  finds lights within their range; CSM split distances monotonic and cover the range; tonemap
  curves monotonic and in [0,1]; bloom energy preserved within tolerance; FXAA does not blur
  flat regions; material keyword hash stable.

### 5.5 Assets & serialization (`engine/assets`, test `UltimateAssetsTest`)
* `AssetGuid` (stable 128-bit ids, `fromString`, `random`, `ZERO`, short form), `AssetRef`
  (guid + type + sub-asset), `AssetMeta` (importer settings, version, dependencies, labels).
* `AssetDatabase`: in-memory index, add/remove/move/rename, guid<->path lookup, dependency graph
  (direct + transitive), cycle detection, garbage collection of unreferenced assets, folder
  watching/hot reload with change callbacks, search by type/label/name, deterministic listing.
* Importers: OBJ (+MTL) mesh importer producing vertices/normals/uvs/triangles with index dedup,
  PNG header parser (size/bit depth/colour type, no decode), WAV parser (format/rate/channels),
  JSON model importer, sprite-sheet slicer, and an importer registry with settings round-trip.
* `PackFile`: a binary asset pack format — magic + version + little-endian table of contents
  (guid, offset, size, crc32), payload blocks, deterministic byte output, integrity verification,
  random access by guid, streaming reader. Include `crc32` and `sha256Short` helpers.
* `AssetBundle`/`StreamingLoader`: reference-counted async load with dependency prefetch, a
  `JobSystem`-based worker, priority queue, progress reporting, cancellation, memory budget and
  LRU eviction.
* `PrefabSystem`: prefab asset with nested prefab support, instance overrides (per property path),
  apply/revert, instance unpacking, override diffing.
* `SceneFormat`: versioned scene/prefab JSON with migration steps (v1→v2→v3), unknown-field
  tolerance, validation with actionable error messages, and a compact binary variant.
* Tests: guid round-trip + uniqueness; dependency graph transitive closure + cycle detection;
  OBJ import of an inline cube gives 8 verts/12 tris; PNG/WAV headers parsed; pack file written
  then read back byte-identical and by-guid random access; crc mismatch detected; async loader
  resolves dependencies before dependents; prefab override apply/revert; scene migration from an
  old version keeps every component.

### 5.6 Networking (`engine/net`, test `UltimateNetTest`)
* `NetTransport` abstraction with `LoopbackTransport` (deterministic, latency/jitter/loss/packet
  reorder simulation) and a UDP-shaped `DatagramTransport` interface (the lead wires Android
  sockets); reliable-ordered channel + unreliable channel with sequence numbers and acks.
* Serialisation: `BitWriter`/`BitReader` with quantised floats (position/rotation/velocity),
  varint, delta encoding against a baseline, string tables, deterministic byte output.
* Replication: `NetworkIdentity` (netId, owner, authority), spawn/despawn, transform + component
  replication with change detection and dirty masks, snapshot delta compression against an acked
  baseline, delta thresholding, per-client interest management (grid/radius + layer), relevancy
  and priority accumulators, bandwidth budget with a send-rate governor.
* Client-side prediction + server reconciliation: input ring buffer, replay of unacked inputs,
  error smoothing, deterministic fixed-step tick integration.
* Lag compensation: server-side rewind buffer, `rewind(tick)` + `restore()`, hitbox history, lag
  compensation window, extrapolation limit.
* RPCs: `[Server]`/`[Client]`/`[Multicast]` semantics, reliable/unreliable, parameter serialisation
  by type, invocation ordering, authority checks, rate limiting.
* Session layer: `NetClock` (NTP-style offset/RTT estimation, drift correction, tick<->time),
  lobby/matchmaking (rooms, players, ready state, teams), chat channel, connection state machine
  (connecting/connected/timed out/disconnected with backoff), server tick loop with snapshots.
* Tests: loopback two peers connect and exchange reliable messages in order under 20 % loss;
  quantised float round-trip error < 1e-3; snapshot delta smaller than full snapshot and applies
  exactly; prediction + reconciliation converges to server state; lag compensation rewind restores
  the world; RPC ordering and authority checks; clock offset converges within 5 ms over simulated
  RTT; interest management hides far players.

### 5.7 Audio DSP + VFX (`engine/audio`, `engine/vfx`, test `UltimateAudioVfxTest`)
Audio:
* `AudioMixer`: bus tree (master → music/sfx/voice/ui), per-bus volume/mute/solo, ducking/sidechain,
  snapshots with transitions, voice management (priority, stealing, virtualisation, max voices).
* `Dsp` effects as pure float-buffer processors: biquad (LP/HP/BP/notch/peak/shelf) with cookbook
  coefficients, 3-band EQ, compressor/limiter (attack/release/knee/ratio, gain reduction metering),
  gate, delay (+feedback), chorus/flanger, plate/schroeder reverb, distortion, tremolo, ring mod,
  pitch shift (simple), stereo widener, RMS/peak metering.
* `Spatial3D`: distance attenuation curves (linear/inverse/exponential/custom rolloff), cone
  directivity, doppler, air absorption by distance, panning (stereo + binaural-ish ITD/ILD),
  listener transform, occlusion via callback, reverb zones.
* `AudioSequencer`: sample-accurate scheduling, tempo/meter, loop points, crossfades, streaming
  decode chunks (WAV PCM), resampling (linear + cubic), voice pooling, `render(frames, out)` for
  offline test rendering.
* Tests: biquad LP attenuates 8 kHz by > 12 dB while keeping 200 Hz within 1 dB; compressor reduces
  gain above threshold and respects ratio; reverb tail decays monotonically; delay repeats at the
  right sample offsets; attenuation curve matches the analytic value at 5 distances; doppler sign
  and magnitude; mixer bus routing sums correctly and ducking lowers the target bus; sequencer
  schedules an event at the exact sample frame; resampler preserves a sine's frequency.
VFX:
* `ParticleSystem2`: modules (emission rate/burst, shapes: point/sphere/box/cone/circle/edge, velocity
  over lifetime, gravity/force fields, noise/turbulence, size/colour/rotation over lifetime with
  gradients + curves, collision with a plane/sphere, sub-emitters on birth/death/collision, trails,
  sorting, texture-sheet animation), deterministic with a seeded RNG, pooling, CPU simulation with
  `JobSystem`, and `writeInstanceData()` producing interleaved float arrays for the renderer.
* `VfxGraph`: node graph (emitter/init/update/render contexts, operators, curves, gradients,
  attributes) with topological evaluation, validation errors, and a `compile()` that produces the
  same module list the runtime consumes; property blocks with typed attributes.
* `Decals`, `TrailRenderer`, `BeamRenderer` CPU data generation; `PostVolume` (exposure/bloom/
  grade/DoF/vignette settings with priority blending).
* Tests: deterministic emission with a fixed seed (same output twice); particle count matches
  rate*lifetime; gravity/noise change velocity as configured; sub-emitter spawns on death;
  gradient/curve evaluation at 0/0.5/1; instance data layout and stride correct; VFX graph
  topological order + validation; post volume blending by priority; trail sampling.

### 5.8 Gameplay framework (`engine/gui`, `engine/input`, `engine/cinematics`, `engine/editor`, test `UltimateGameplayTest`)
* GUI (`engine/gui`): retained-mode canvas — `Canvas` (scale modes: constant pixel size, scale with
  screen, constant physical size; reference resolution, safe area), `UiNode` tree, layout engine
  (anchor + pivot, horizontal/vertical/grid layout groups with spacing/padding/child alignment,
  content size fitter, aspect ratio fitter, layout rebuild + dirty flags), widgets (panel, image,
  text, button with states/transitions, toggle, slider, scroll view with inertia + snapping,
  dropdown, input field, progress bar, tabs, tooltip, joystick, modal dialog), event system
  (raycast hit testing, capture, bubbling/tunnelling, focus navigation for keyboard/gamepad),
  data binding (`Observable<T>` with change notification), theme/skin, animation transitions
  (tween engine with easing functions, sequences, delays, loops, curves).
* Input (`engine/input`): `InputAction`/`ActionMap` (bindings: key, mouse, touch, gamepad button/axis,
  composite 2D/3D vectors, modifiers), processors (dead zone radial/axial, scale, invert, normalise,
  clamp, smoothing), interactions (tap, hold, multi-tap, slow tap, charge, press/release), control
  schemes + rebinding with conflict detection and persistence (JSON), action state machine with
  buffers, one-shot triggers, device connect/disconnect, touch gestures (tap/double-tap/long-press/
  pan/pinch/rotate/swipe/3-finger), input recording & deterministic replay.
* Cinematics (`engine/cinematics`): `Timeline` with tracks (activation, transform, animation,
  audio, event, particle, subtitles, camera), clips with blend-in/out, easing, looping, hold,
  marker/event dispatch, `Sequencer` evaluation with time scrubbing; `VirtualCamera` with priority,
  blend (cut/ease-in-out), follow/look-at targets, damping, screen-space offsets, dead zone,
  confiner (box/polygon), impulse/shake sources and noise, camera stack + blend weights.
* Editor core (`engine/editor`): `CommandStack` (undo/redo, transactions, coalescing, merge windows,
  composite commands, serialisation of the stack), `Selection` (multi-select, sub-object selection,
  pivot), `Gizmo` maths (translate/rotate/scale, axis/plane constraints, screen-space picking,
  snapping, local/world space, distance-scaled handles), `TransformTool` with grid snapping and
  surface snapping, `PrefabOverrideTracker`, `PropertyPath` get/set by string path, `InspectorModel`
  (property drawers metadata, validation, multi-edit), `SceneQuery` (name/tag/component search,
  fuzzy search, saved searches), `BuildSettings` (scenes list, package name, version, icons, ABIs,
  keystore info, validation before build), `PluginRegistry` (extension points: menu items, panels,
  importers, property drawers, build steps, validation with dependency ordering), `ProfilerModel`
  (frame stats, counters, named samples, flamegraph-ish aggregation, budget thresholds), `TestRunner`
  (register + run named checks with pass/fail output), `Localization` (string tables, plural rules,
  language fallback, missing-key report), `ProjectSettings` (serialisable key/value groups with
  typed accessors and defaults), `AssetImporterSettings` validation.
* Tests: undo/redo restores exact state through a transaction and coalescing merges same-frame
  edits; gizmo picking selects the nearest axis and screen->world snapping rounds correctly;
  layout groups place children as configured (row/column/grid, padding, alignment, spacing);
  scroll view clamps content and snaps; input action fires on tap and not on hold; dead zone and
  rebinding persist through JSON; gestures recognise pinch/pan/swipe with thresholds; timeline
  evaluation honours blend-in/out, loop and markers; virtual camera priority blending and
  confiner clamp; plugin build steps run in dependency order; profiler aggregates samples;
  localization falls back and reports missing keys.

---

## 6. Reporting back (required)

When your module is done, report: files created, the public API entry points (class + key methods),
how the lead should instantiate/hook it into `Engine`/`Scene`, the exact test command you ran, its
output, and anything you deliberately left out. Keep it short and factual.
