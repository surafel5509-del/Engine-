package com.sengine.project

/** Ready-to-use script snippets shown in Help → Recipes and in the Asset Store. */
object ScriptRecipes {
    class Recipe(val title: String, val icon: String, val description: String, val file: String, val code: String)

    val all: List<Recipe> = listOf(
        Recipe("Platformer movement", "rocket", "Run and jump with coyote time and variable jump height.", "PlayerMove.js", """
var speed = 6, jump = 11, coyote = 0;
function update(dt) {
  self.vx = input.axisX * speed;
  if (input.axisX != 0) self.flipX = input.axisX < 0;
  coyote = self.grounded ? 0.1 : coyote - dt;
  if (input.aDown && coyote > 0) { self.vy = jump; coyote = 0; audio.play("jump.wav"); }
  if (!input.a && self.vy > 0) self.vy *= 0.9;   // short hop when released
}"""),
        Recipe("Top-down movement + aim", "target", "8-way movement with a twin-stick aim that fires bullets.", "TopDown.js", """
var speed = 5, fireRate = 0.15, cd = 0;
function update(dt) {
  self.setVelocity(input.axisX * speed, input.axisY * speed);
  cd -= dt;
  var ax = input.stickX("aim"), ay = input.stickY("aim");
  if ((ax * ax + ay * ay) > 0.2 && cd <= 0) {
    self.rotation = Math.atan2(ay, ax) * 180 / Math.PI;
    var b = scene.spawn("Bullet", self.x, self.y);
    if (b) b.setVelocity(ax * 14, ay * 14);
    cd = fireRate; audio.play("laser.wav", 0.6);
  }
}"""),
        Recipe("Enemy chase", "fire", "Follow the nearest player and deal contact damage.", "Chaser.js", """
var speed = 2.5;
function update(dt) {
  var p = scene.nearest("Player", self.x, self.y);
  if (p) self.moveTowards(p.x, p.y, speed * dt);
}
function onCollision(other) {
  if (other.tag == "Player") other.send("damage", 10);
}"""),
        Recipe("Health + damage + game over", "heart", "Health with invulnerability frames, HUD bar and game over panel.", "HealthHUD.js", """
var hp = 100, inv = 0;
function update(dt) { inv -= dt; }
function damage(n) {
  if (inv > 0) return;
  hp -= n; inv = 0.6; scene.shake(0.3); platform.vibrate(40);
  ui.setProgress("HealthBar", hp / 100);
  if (hp <= 0) { ui.show("GameOver"); time.scale = 0; }
}"""),
        Recipe("Score + high score", "trophy", "Score counter saved as a persistent high score.", "Score.js", """
var score = 0;
function start() { ui.setText("Best", "Best: " + storage.getNumber("best", 0)); }
function addScore(n) {
  score += n; ui.setText("Score", "Score: " + score);
  if (score > storage.getNumber("best", 0)) { storage.set("best", score); ui.setText("Best", "Best: " + score); }
}"""),
        Recipe("Enemy wave spawner", "sparkle", "Spawns increasingly large waves from template objects.", "Waves.js", """
var wave = 0;
function start() { nextWave(); }
function update(dt) { if (scene.count("Enemy") == 0) nextWave(); }
function nextWave() {
  wave++; ui.setText("Wave", "Wave " + wave);
  for (var i = 0; i < 3 + wave * 2; i++) {
    var a = random(0, Math.PI * 2);
    scene.spawn("Enemy", Math.cos(a) * 9, Math.sin(a) * 6);
  }
}"""),
        Recipe("Pause menu", "pause", "Toggle a pause panel with a button (Action: call:togglePause).", "Pause.js", """
var paused = false;
function togglePause() {
  paused = !paused;
  time.scale = paused ? 0 : 1;
  if (paused) ui.show("PausePanel"); else ui.hide("PausePanel");
  input.showControls(!paused);
}"""),
        Recipe("Settings with saved volumes", "sliders", "Music/SFX volume buttons that persist.", "Settings.js", """
function start() { audio.musicVolume = storage.getNumber("music", 0.8); audio.sfxVolume = storage.getNumber("sfx", 1); refresh(); }
function onUIClick(name) {
  if (name == "MusicUp") audio.musicVolume = Math.min(1, audio.musicVolume + 0.1);
  if (name == "MusicDown") audio.musicVolume = Math.max(0, audio.musicVolume - 0.1);
  if (name == "SfxUp") audio.sfxVolume = Math.min(1, audio.sfxVolume + 0.1);
  if (name == "SfxDown") audio.sfxVolume = Math.max(0, audio.sfxVolume - 0.1);
  storage.set("music", audio.musicVolume); storage.set("sfx", audio.sfxVolume); refresh();
}
function refresh() {
  ui.setText("MusicLabel", "Music " + Math.round(audio.musicVolume * 100) + "%");
  ui.setText("SfxLabel", "SFX " + Math.round(audio.sfxVolume * 100) + "%");
}"""),
        Recipe("3D third-person controller", "cube", "Joystick movement relative to the camera with jump.", "ThirdPerson.js", """
var speed = 6;
function update(dt) {
  var mx = input.axisX, mz = -input.axisY;
  if (mx * mx + mz * mz > 0.01) {
    self.rotY = Math.atan2(-mx, -mz) * 180 / Math.PI;
    self.setVelocity(mx * speed, self.vy, mz * speed);
    self.playModelAnim("Walk");
  } else { self.setVelocity(0, self.vy, 0); self.playModelAnim("Idle"); }
  if (input.aDown && self.grounded) self.vy = 7;
}"""),
        Recipe("First-person look + move", "eye", "FPS camera with look pad and joystick.", "FirstPerson.js", """
var speed = 5, pitch = 0;
function update(dt) {
  self.rotY -= input.lookX * 0.15;
  pitch = clamp(pitch - input.lookY * 0.15, -85, 85);
  var cam = scene.camera3D; if (cam) cam.rotX = pitch;
  var f = self.forward(), r = self.right();
  var vx = (f[0] * input.axisY + r[0] * input.axisX) * speed;
  var vz = (f[2] * input.axisY + r[2] * input.axisX) * speed;
  self.setVelocity(vx, self.vy, vz);
  if (input.aDown && self.grounded) self.vy = 6;
}"""),
        Recipe("Collectible coin", "star", "Spinning trigger pickup with sound and score.", "Coin.js", """
function update(dt) { self.rotation += 180 * dt; }
function onTrigger(other) {
  if (other.tag != "Player") return;
  audio.play("coin.wav"); scene.find("GameManager").send("addScore", 10);
  self.destroy();
}"""),
        Recipe("Moving platform", "move", "Ping-pong platform between two points.", "Mover.js", """
var dist = 3, speed = 1.5, sx, t = 0;
function start() { sx = self.x; }
function update(dt) { t += dt * speed; self.x = sx + Math.sin(t) * dist; }"""),
        Recipe("Level timer + stars", "clock", "Countdown timer that awards 1-3 stars.", "LevelTimer.js", """
var left = 90;
function update(dt) {
  left -= dt; ui.setText("Timer", formatTime(left));
  if (left <= 0) { ui.show("GameOver"); time.scale = 0; }
}
function finish() {
  var stars = left > 60 ? 3 : left > 30 ? 2 : 1;
  storage.set("stars_" + scene.name, Math.max(stars, storage.getNumber("stars_" + scene.name, 0)));
  ui.setText("Stars", stars + " ★"); ui.show("Win");
}"""),
        Recipe("Background music per scene", "music", "Plays a song asset when the scene starts.", "Music.js", """
var track = "Theme.song";
function start() { audio.playMusic(track, 0.8); }"""),
        Recipe("Camera shake on hit", "bolt", "Screen shake + slow motion effect.", "Juice.js", """
function onCollision(other) {
  if (other.tag == "Enemy") {
    scene.shake(0.4); time.scale = 0.3;
    after(0.08, function() { time.scale = 1; });
  }
}"""),
    )
}
