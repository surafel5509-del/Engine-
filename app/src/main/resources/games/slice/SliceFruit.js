// SliceFruit.js — a flying fruit. Slice it by touching it, or count it as missed when it falls.
// Also used for the drifting menu clouds ("cloud=true" param).
var slicedYet = false;

function start() {
    if (cloud) return;
    slicedYet = false;
    self.rotation = 0;
}

function update(dt) {
    if (cloud) {
        self.x += 0.15 * dt;
        if (self.x > 10) self.x = -10;
        return;
    }
    self.rotation += 140 * dt;
    if (self.y < -8 && !slicedYet) {
        slicedYet = true;
        var d = scene.find("Director");
        if (d != null) d.send("missed");
        self.destroy();
    }
}

function onTap() {
    if (cloud || slicedYet) return;
    slice();
}

function slice() {
    if (slicedYet) return;
    slicedYet = true;
    var fx = scene.find("SliceFX");
    if (fx != null) { fx.x = self.x; fx.y = self.y; fx.burst(16); }
    var sp = scene.find("Sparks");
    if (sp != null) { sp.x = self.x; sp.y = self.y; sp.burst(6); }
    audio.play("laser.wav", 0.5);
    var d = scene.find("Director");
    if (d != null) d.send("sliced", false);
    self.destroy();
}
