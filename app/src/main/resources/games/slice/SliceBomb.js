// SliceBomb.js — spinning bomb; slicing it ends the run with a blast.
var boom = false;

function update(dt) {
    self.rotation += 200 * dt;
    var glow = self.child("BombGlow");
    if (glow != null) glow.scaleX = 1 + 0.12 * Math.sin(time.time * 8);
}

function onTap() {
    if (boom) return;
    boom = true;
    scene.shake(0.5);
    var fx = scene.find("SliceFX");
    if (fx != null) { fx.x = self.x; fx.y = self.y; fx.burst(60); }
    var sp = scene.find("Sparks");
    if (sp != null) { sp.x = self.x; sp.y = self.y; sp.burst(30); }
    audio.play("explosion.wav");
    var d = scene.find("Director");
    if (d != null) d.send("sliced", true);
    self.destroy();
}
